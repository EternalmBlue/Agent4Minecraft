package me.eternalblue.agent4minecraft

import me.eternalblue.agent4minecraft.backend.GrpcBackendClient
import me.eternalblue.agent4minecraft.bootstrap.PluginExecutorFactory
import me.eternalblue.agent4minecraft.bootstrap.ServerInstanceIdStore
import me.eternalblue.agent4minecraft.bootstrap.StartupProbeOutcome
import me.eternalblue.agent4minecraft.bootstrap.StartupProbeVerifier
import me.eternalblue.agent4minecraft.command.A4mCommand
import me.eternalblue.agent4minecraft.command.AskCommand
import me.eternalblue.agent4minecraft.config.PluginSettingsLoader
import me.eternalblue.agent4minecraft.i18n.PluginMessageLoader
import me.eternalblue.agent4minecraft.qa.QuestionRequestLimiter
import me.eternalblue.agent4minecraft.qa.QuestionService
import me.eternalblue.agent4minecraft.transfer.ChecksumCache
import me.eternalblue.agent4minecraft.transfer.SensitiveConfigRedactor
import me.eternalblue.agent4minecraft.transfer.SyncService
import me.eternalblue.agent4minecraft.transfer.SyncStatusTracker
import me.eternalblue.agent4minecraft.transfer.SyncUploadPreparer
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class Agent4MinecraftPlugin : JavaPlugin() {
    private var runtime: PluginRuntime? = null

    override fun onEnable() {
        saveDefaultConfig()

        val serverRoot = try {
            resolveServerRoot()
        } catch (exception: IllegalStateException) {
            logger.severe("Could not resolve the server root directory: ${exception.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        val settings = try {
            PluginSettingsLoader.load(config, serverRoot)
        } catch (exception: IllegalArgumentException) {
            logger.severe("Agent4Minecraft configuration is invalid: ${exception.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        val messages = try {
            PluginMessageLoader.load(this, settings.language)
        } catch (exception: Exception) {
            logger.severe("Could not load Agent4Minecraft language files: ${exception.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        val serverInstanceId = try {
            ServerInstanceIdStore.loadOrCreate(dataFolder.toPath())
        } catch (exception: IOException) {
            logger.severe("Could not load or create the Agent4Minecraft server instance id: ${exception.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        val executor = PluginExecutorFactory.create("agent4minecraft-worker")
        val backendClient = GrpcBackendClient(settings.backend, messages)
        val runtimeHandle = PluginRuntime(executor, backendClient)
        val pluginName = pluginMeta.name
        val pluginVersion = pluginMeta.version

        when (
            val probeOutcome = StartupProbeVerifier.verify(
                backendClient = backendClient,
                backendSettings = settings.backend,
                serverId = settings.serverId,
                serverInstanceId = serverInstanceId,
                pluginName = pluginName,
                pluginVersion = pluginVersion,
                messages = messages,
            )
        ) {
            is StartupProbeOutcome.Success -> {
                val probe = probeOutcome.result
                logger.info(
                    "Backend probe acknowledged: backend=${probe.backendName}, " +
                        "backendVersion=${probe.backendVersion ?: "unknown"}, " +
                        "protocolVersion=${probe.protocolVersion}, pluginVersion=$pluginVersion"
                )
            }

            is StartupProbeOutcome.Failure -> {
                probeOutcome.lines.forEach(logger::severe)
                runtimeHandle.close()
                server.pluginManager.disablePlugin(this)
                return
            }
        }

        val questionService = QuestionService(
            backendClient = backendClient,
            serverId = settings.serverId,
            serverInstanceId = serverInstanceId,
            messages = messages,
            debugLogging = settings.debugLogging,
            logger = logger,
        )
        val syncService = SyncService(
            serverId = settings.serverId,
            serverInstanceId = serverInstanceId,
            backendEndpointLabel = settings.backend.endpointLabel,
            backendClient = backendClient,
            uploadPreparer = SyncUploadPreparer(
                serverRoot = serverRoot,
                checksumCache = ChecksumCache(),
                redactor = SensitiveConfigRedactor(),
                redactedUploadRoot = dataFolder.toPath().resolve("sync-cache").resolve("redacted"),
            ),
            tracker = SyncStatusTracker(),
            messages = messages,
            logger = logger,
        )

        registerCommand(
            commandName = "askmc",
            executor = AskCommand(
                plugin = this,
                executor = executor,
                questionService = questionService,
                limiter = QuestionRequestLimiter(settings.qa.rateLimitSeconds, messages),
                messages = messages,
            ),
        )

        val adminCommand = A4mCommand(
            plugin = this,
            executor = executor,
            syncService = syncService,
            messages = messages,
        )
        registerCommand("a4m", adminCommand, adminCommand)

        runtime = runtimeHandle
        logger.info(
            "Agent4Minecraft enabled: serverId=${settings.serverId}, " +
                "serverInstanceId=$serverInstanceId, " +
                "backend=${settings.backend.endpointLabel}, tls=${settings.backend.useTls}, " +
                "serverRoot=$serverRoot"
        )
    }

    override fun onDisable() {
        runtime?.close()
        runtime = null
    }

    private fun resolveServerRoot(): Path {
        val pluginsDir = dataFolder.toPath().toAbsolutePath().normalize().parent
            ?: throw IllegalStateException("Plugin data folder is missing the parent plugins directory.")
        return pluginsDir.parent
            ?: throw IllegalStateException("The plugins directory is missing the server root directory.")
    }

    private fun registerCommand(
        commandName: String,
        executor: CommandExecutor,
        tabCompleter: TabCompleter? = null,
    ) {
        val pluginCommand = requireNotNull(getCommand(commandName)) {
            "plugin.yml is missing the command definition for $commandName"
        }
        pluginCommand.setExecutor(executor)
        if (tabCompleter != null) {
            pluginCommand.tabCompleter = tabCompleter
        }
    }

    private data class PluginRuntime(
        private val executor: ExecutorService,
        private val backendClient: GrpcBackendClient,
    ) {
        fun close() {
            backendClient.close()
            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            } catch (exception: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
}
