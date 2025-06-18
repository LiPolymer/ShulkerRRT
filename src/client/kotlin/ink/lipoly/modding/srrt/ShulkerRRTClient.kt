package ink.lipoly.modding.srrt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWWindowFocusCallbackI
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import kotlin.io.path.Path
import kotlin.time.Duration


object ShulkerRRTClient : ClientModInitializer {
	override fun onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

		val scope = CoroutineScope(Dispatchers.Default)
		val flagPath = Path("./reload.flag")
		val logger = LoggerFactory.getLogger("ShulkerRRT")
		val configFile: File = FabricLoader.getInstance().configDir.resolve("shulkerRRT.json").toFile()
		val json = Json {
			prettyPrint = true
			encodeDefaults = true
			ignoreUnknownKeys = true
		}
		var config = Config()

		if (configFile.exists()) {
			config = try {
				json.decodeFromString<Config>(configFile.readText())
			} catch (e: Exception) {
				Config().also { configFile.writeText(json.encodeToString(config)) }
			}
		}
		configFile.writeText(json.encodeToString(config))

		scope.launch {
			if (config.isShulkerRDKManaged){
				while (isActive) {
					if (Files.exists(flagPath)){
						Files.delete(Path("./reload.flag"))
						try {
							logger.info("Reloading...")
							MinecraftClient.getInstance().reloadResources()
						} catch (e: Exception){
							logger.error(e.message)
						}
					}
					delay(1000)
				}
			} else {
				var targetList = mutableListOf<File>()
				val defaultPath = "./resourcepacks"
				val paxiPath = "./config/paxi/resourcepacks"
				val watchService = FileSystems.getDefault().newWatchService()
				if (config.monitorDefaultPath){
					safeCreateDir(defaultPath)
					targetList.add(File(defaultPath))
					targetList = (targetList + listAllDirectories(File(defaultPath))).distinct().toMutableList()
				}
				if (config.monitorPaxiPath && FabricLoader.getInstance().isModLoaded("paxi")){
					safeCreateDir(paxiPath)
					targetList.add(File(paxiPath))
					targetList = (targetList + listAllDirectories(File(paxiPath))).distinct().toMutableList()
				}
				for (t: File in targetList){
					println("正在记录" + t.path)
					registerWatcher(t.path,watchService)
				}
				logger.info("开始监听")
				var lastUpdate = System.currentTimeMillis()
				while (isActive) {
					val key = watchService.take()
					key.pollEvents().forEach { event ->
						val kind = event.kind()
						val fileName = event.context() as Path
						val eventType = when (kind) {
							StandardWatchEventKinds.ENTRY_CREATE -> "创建"
							StandardWatchEventKinds.ENTRY_MODIFY -> "修改"
							StandardWatchEventKinds.ENTRY_DELETE -> "删除"
							else -> "未知"
						}
						logger.info("检测到文件 ${fileName.fileName}: $eventType")
						try {
							val delta = kotlin.math.abs(System.currentTimeMillis() - lastUpdate)
							if (delta >= 3000){
								MinecraftClient.getInstance().reloadResources()
								lastUpdate = System.currentTimeMillis()
							}
						} catch (e: Exception){
							logger.error(e.message)
						}
					}
					if (!key.reset()) {
						logger.info("监听终止")
						break
					}
				}
			}
		}

		if (config.reloadOnFocusGained){
			scope.launch {
				var isPrev = true
				while (isActive){
					try{
						val isFocused = MinecraftClient.getInstance().isWindowFocused
						if (isFocused && !isPrev){
							logger.info("Focus ReGained Reloading...")
							MinecraftClient.getInstance().reloadResources()
						}
						isPrev = isFocused
					} catch (e: Exception){
						//ignored
					}
				}
			}
		}
	}

	private fun safeCreateDir(path: String) {
		try {
			val dir = Paths.get(path)
			Files.createDirectories(dir)
		} catch (e: Exception) {
			println(e.message)
		}
	}

	private fun registerWatcher(dirPath: String, watchService: WatchService) {
		val path = Paths.get(dirPath)
		path.register(
			watchService,
			StandardWatchEventKinds.ENTRY_CREATE,
			StandardWatchEventKinds.ENTRY_MODIFY,
			StandardWatchEventKinds.ENTRY_DELETE,
			StandardWatchEventKinds.OVERFLOW
		)
	}

	private fun listAllDirectories(rootDir: File): List<File> {
		val directories = mutableListOf<File>()
		if (!rootDir.isDirectory) {
			return directories
		}
		val queue = ArrayDeque<File>().apply {
			add(rootDir)
		}
		while (queue.isNotEmpty()) {
			val currentDir = queue.removeFirst()
			currentDir.listFiles()?.forEach { file ->
				if (file.isDirectory) {
					directories.add(file)
					queue.add(file)
				}
			}
		}
		return directories
	}
}