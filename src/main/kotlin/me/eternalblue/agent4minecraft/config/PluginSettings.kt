package me.eternalblue.agent4minecraft.config

import me.eternalblue.agent4minecraft.i18n.PluginLanguage

data class PluginSettings(
    val serverId: String,
    val debugLogging: Boolean,
    val language: PluginLanguage,
    val qa: QaSettings,
    val backend: BackendSettings,
)

data class QaSettings(
    val rateLimitSeconds: Long,
    val progress: QaProgressSettings,
)

data class QaProgressSettings(
    val enabled: Boolean,
    val intervalSeconds: Long,
)

data class BackendSettings(
    val host: String,
    val port: Int,
    val authToken: String,
    val useTls: Boolean,
    val deadlineMillis: Long,
    val probeTimeoutMillis: Long,
    val askDeadlineMillis: Long,
    val syncDeadlineMillis: Long,
    val maxChunkBytes: Int,
) {
    val endpointLabel: String
        get() = "$host:$port"
}
