package ink.lipoly.modding.srrt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.toast.SystemToast
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import kotlin.io.path.Path


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
							logger.info("Flag Detected, Reloading...")
							MinecraftClient.getInstance().reloadResources()
						} catch (e: Exception){
							logger.error(e.message)
						}
					}
					delay(1000)
				}
			}
			else {
				val watchService = FileSystems.getDefault().newWatchService()
				var targetList = getMonitoringList(config)
				for (t: File in targetList){
					logger.info("Recording " + t.path)
					registerWatcher(t.path,watchService)
				}
				logger.info("Listener started")
				var lastUpdate = System.currentTimeMillis()
				while (isActive) {
					val key = watchService.take()
					key.pollEvents().forEach { event ->
						val kind = event.kind()
						val fileName = event.context() as Path
						val eventType = when (kind) {
							StandardWatchEventKinds.ENTRY_CREATE -> "Create"
							StandardWatchEventKinds.ENTRY_MODIFY -> "Modify"
							StandardWatchEventKinds.ENTRY_DELETE -> "Delete"
							else -> "Unknown"
						}
						logger.info("Entry ${fileName.fileName} detected event $eventType")
						try {
							val delta = kotlin.math.abs(System.currentTimeMillis() - lastUpdate)
							if (delta >= 3000){
								logger.info("Reloading...")
								val client = MinecraftClient.getInstance()
								if (config.enableToastNotification){
									client.toastManager.add(
										SystemToast.create(
											client,
											SystemToast.Type.PERIODIC_NOTIFICATION,
											Text.literal("ShulkerRTT"),
											Text.translatable("srrt.reloading.onFileChanged")
										)
									)
								}
								MinecraftClient.getInstance().reloadResources()
								lastUpdate = System.currentTimeMillis()
							}
							val newList = getMonitoringList(config)
							for (t: File in newList.toSet() - targetList.toSet()){
								logger.info("Secondary recording " + t.path)
								registerWatcher(t.path,watchService)
							}
							targetList = newList
						} catch (e: Exception){
							logger.error(e.message)
						}
					}
					if (!key.reset()) {
						logger.info("A listen point was removed, refactoring the list")
						targetList = getMonitoringList(config)
					}
				}
			}
		}
		if (config.reloadOnFocusGained){
			scope.launch {
				var isPrev = true
				while (isActive){
					try{
						val client = MinecraftClient.getInstance()
						val isFocused = client.isWindowFocused
						if (isFocused && !isPrev){
							if (config.enableToastNotification){
								client.toastManager.add(
									SystemToast.create(
										client,
										SystemToast.Type.PERIODIC_NOTIFICATION,
										Text.literal("ShulkerRTT"),
										Text.translatable("srrt.reloading.onFocusRegained")
									)
								)
							}
							logger.info("Focus Regained,Reloading...")
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

	private fun getMonitoringList(config: Config): MutableList<File>{
		var targetList = mutableListOf<File>()
		val defaultPath = "./resourcepacks"
		val paxiPath = "./config/paxi/resourcepacks"
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
		return targetList
	}
}