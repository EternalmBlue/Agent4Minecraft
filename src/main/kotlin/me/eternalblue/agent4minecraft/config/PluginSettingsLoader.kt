package me.eternalblue.agent4minecraft.config

import me.eternalblue.agent4minecraft.i18n.PluginLanguage
import org.bukkit.configuration.file.FileConfiguration
import java.nio.file.Path

object PluginSettingsLoader {
    fun load(
        configuration: FileConfiguration,
        serverRoot: Path,
    ): PluginSettings {
        val serverId = readServerId(configuration, serverRoot)
        val debugLogging = configuration.getBoolean("plugin.debug", false)
        val language = PluginLanguage.parse(configuration.getString("plugin.language"))
        val qaRateLimitSeconds = configuration.getLong("qa.rateLimitSeconds", 3L)
        require(qaRateLimitSeconds >= 0) {
            "qa.rateLimitSeconds must be >= 0."
        }

        val host = readTextOrDefault(
            configuration = configuration,
            path = "backend.host",
            defaultValue = "127.0.0.1",
        )
        val port = configuration.getInt("backend.port", 50051)
        require(port in 1..65535) {
            "backend.port must be within 1..65535."
        }

        val authToken = requireText(configuration, "backend.authToken")
        val useTls = configuration.getBoolean("backend.useTls", false)
        val deadlineMillis = configuration.getLong("backend.deadlineMillis", 15_000L)
        require(deadlineMillis > 0) {
            "backend.deadlineMillis must be > 0."
        }

        val probeTimeoutMillis = readPositiveLong(
            configuration = configuration,
            path = "backend.probeTimeoutMillis",
            defaultValue = 3_000L,
        )
        val askDeadlineMillis = readPositiveLong(
            configuration = configuration,
            path = "backend.askDeadlineMillis",
            defaultValue = maxOf(deadlineMillis * 8, 120_000L),
        )
        val syncDeadlineMillis = readPositiveLong(
            configuration = configuration,
            path = "backend.syncDeadlineMillis",
            defaultValue = deadlineMillis,
        )
        val maxChunkBytes = configuration.getInt("backend.maxChunkBytes", 262_144)
        require(maxChunkBytes > 0) {
            "backend.maxChunkBytes must be > 0."
        }

        return PluginSettings(
            serverId = serverId,
            debugLogging = debugLogging,
            language = language,
            qa = QaSettings(rateLimitSeconds = qaRateLimitSeconds),
            backend = BackendSettings(
                host = host,
                port = port,
                authToken = authToken,
                useTls = useTls,
                deadlineMillis = deadlineMillis,
                probeTimeoutMillis = probeTimeoutMillis,
                askDeadlineMillis = askDeadlineMillis,
                syncDeadlineMillis = syncDeadlineMillis,
                maxChunkBytes = maxChunkBytes,
            ),
        )
    }

    private fun readServerId(
        configuration: FileConfiguration,
        serverRoot: Path,
    ): String {
        return readOptionalText(configuration, "server.id")
            ?: serverRoot.fileName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException(
                "server.id is blank and could not be derived from the server root directory. " +
                    "Configure server.id explicitly."
            )
    }

    private fun requireText(
        configuration: FileConfiguration,
        path: String,
    ): String {
        return readOptionalText(configuration, path).orEmpty().also { value ->
            require(value.isNotEmpty()) {
                "$path must not be blank."
            }
        }
    }

    private fun readOptionalText(
        configuration: FileConfiguration,
        path: String,
    ): String? = configuration.getString(path)?.trim()?.takeIf { it.isNotEmpty() }

    private fun readTextOrDefault(
        configuration: FileConfiguration,
        path: String,
        defaultValue: String,
    ): String = readOptionalText(configuration, path) ?: defaultValue

    private fun readPositiveLong(
        configuration: FileConfiguration,
        path: String,
        defaultValue: Long,
    ): Long {
        val value = if (configuration.isSet(path)) {
            configuration.getLong(path)
        } else {
            defaultValue
        }
        require(value > 0) {
            "$path must be > 0."
        }
        return value
    }
}
