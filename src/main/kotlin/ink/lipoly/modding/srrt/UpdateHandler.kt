package ink.lipoly.modding.srrt

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLPaths
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

object UpdateHandler {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val flagPath = Paths.get("./reload.flag")
    private val configFile: File = FMLPaths.CONFIGDIR.get().resolve("shulkerRRT.json").toFile()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var config: Config

    fun setup() {
        config = loadConfig()

        scope.launch {
            if (config.isShulkerRDKManaged) {
                monitorReloadFlag()
            } else {
                monitorResourcePacks()
            }
        }

        if (config.reloadOnFocusGained) {
            scope.launch {
                monitorFocus()
            }
        }
    }

    private fun loadConfig(): Config {
        var loadedConfig = Config()
        if (configFile.exists()) {
            loadedConfig = try {
                gson.fromJson(configFile.readText(), Config::class.java) ?: Config()
            } catch (e: Exception) {
                ShulkerRRT.LOGGER.error("Failed to read config, recreating default config", e)
                Config()
            }
        }
        configFile.parentFile?.mkdirs()
        configFile.writeText(gson.toJson(loadedConfig))
        return loadedConfig
    }

    private suspend fun monitorReloadFlag() {
        while (scope.isActive) {
            if (Files.exists(flagPath)) {
                Files.deleteIfExists(flagPath)
                ShulkerRRT.LOGGER.info("Flag detected, reloading...")
                reloadResources()
            }
            delay(1000)
        }
    }

    private suspend fun monitorResourcePacks() {
        FileSystems.getDefault().newWatchService().use { watchService ->
            var targetList = getMonitoringList()
            for (target in targetList) {
                ShulkerRRT.LOGGER.info("Recording ${target.path}")
                registerWatcher(target.path, watchService)
            }
            ShulkerRRT.LOGGER.info("Listener started")

            var lastUpdate = System.currentTimeMillis()
            while (scope.isActive) {
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
                    ShulkerRRT.LOGGER.info("Entry ${fileName.fileName} detected event $eventType")

                    try {
                        val delta = kotlin.math.abs(System.currentTimeMillis() - lastUpdate)
                        if (delta >= 3000) {
                            ShulkerRRT.LOGGER.info("Reloading...")
                            reloadResources(Component.translatable("srrt.reloading.onFileChanged"))
                            lastUpdate = System.currentTimeMillis()
                        }

                        val newList = getMonitoringList()
                        for (target in newList.toSet() - targetList.toSet()) {
                            ShulkerRRT.LOGGER.info("Secondary recording ${target.path}")
                            registerWatcher(target.path, watchService)
                        }
                        targetList = newList
                    } catch (e: Exception) {
                        ShulkerRRT.LOGGER.error("Failed to handle resource pack update", e)
                    }
                }

                if (!key.reset()) {
                    ShulkerRRT.LOGGER.info("A listen point was removed, refactoring the list")
                    targetList = getMonitoringList()
                }
            }
        }
    }

    private suspend fun monitorFocus() {
        var wasFocused = true
        while (scope.isActive) {
            try {
                val client = Minecraft.getInstance()
                val isFocused = client.isWindowActive
                if (isFocused && !wasFocused) {
                    ShulkerRRT.LOGGER.info("Focus regained, reloading...")
                    reloadResources(Component.translatable("srrt.reloading.onFocusRegained"))
                }
                wasFocused = isFocused
            } catch (_: Exception) {
                // Client may not be fully initialized yet.
            }
            delay(100)
        }
    }

    private fun reloadResources(toastMessage: Component? = null) {
        val client = Minecraft.getInstance()
        client.execute {
            if (config.enableToastNotification && toastMessage != null) {
                SystemToast.add(
                    client.toasts,
                    SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal("ShulkerRTT"),
                    toastMessage
                )
            }
            client.reloadResourcePacks()
        }
    }

    private fun safeCreateDir(path: String) {
        try {
            val dir = Paths.get(path)
            Files.createDirectories(dir)
        } catch (e: Exception) {
            ShulkerRRT.LOGGER.error("Failed to create directory $path", e)
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

    private fun getMonitoringList(): MutableList<File> {
        var targetList = mutableListOf<File>()
        val defaultPath = "./resourcepacks"
        val paxiPath = "./config/paxi/resourcepacks"

        if (config.monitorDefaultPath) {
            safeCreateDir(defaultPath)
            targetList.add(File(defaultPath))
            targetList = (targetList + listAllDirectories(File(defaultPath))).distinct().toMutableList()
        }
        if (config.monitorPaxiPath && ModList.get().isLoaded("paxi")) {
            safeCreateDir(paxiPath)
            targetList.add(File(paxiPath))
            targetList = (targetList + listAllDirectories(File(paxiPath))).distinct().toMutableList()
        }
        return targetList
    }
}
