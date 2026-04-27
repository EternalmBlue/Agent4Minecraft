package me.eternalblue.agent4minecraft.bootstrap

import me.eternalblue.agent4minecraft.backend.BackendClient
import me.eternalblue.agent4minecraft.backend.BackendClientException
import me.eternalblue.agent4minecraft.config.BackendSettings
import me.eternalblue.agent4minecraft.domain.ProbeCommand
import me.eternalblue.agent4minecraft.domain.ProbeResult
import me.eternalblue.agent4minecraft.i18n.PluginMessages

sealed interface StartupProbeOutcome {
    data class Success(
        val result: ProbeResult,
    ) : StartupProbeOutcome

    data class Failure(
        val lines: List<String>,
    ) : StartupProbeOutcome
}

object StartupProbeVerifier {
    const val BRIDGE_PROTOCOL_VERSION: Int = 1

    private const val BACKEND_ENTRYPOINT = "python -m agent_for_mc.interfaces.grpc"

    fun verify(
        backendClient: BackendClient,
        backendSettings: BackendSettings,
        serverId: String,
        serverInstanceId: String,
        pluginName: String,
        pluginVersion: String,
        messages: PluginMessages,
    ): StartupProbeOutcome {
        return try {
            val probe = backendClient.probe(
                ProbeCommand(
                    serverId = serverId,
                    serverInstanceId = serverInstanceId,
                    pluginName = pluginName,
                    pluginVersion = pluginVersion,
                    protocolVersion = BRIDGE_PROTOCOL_VERSION,
                ),
            )

            when {
                !probe.ack ->
                    StartupProbeOutcome.Failure(
                        buildFailureLines(
                            backendSettings = backendSettings,
                            serverId = serverId,
                            serverInstanceId = serverInstanceId,
                            pluginName = pluginName,
                            pluginVersion = pluginVersion,
                            detail = messages.startupProbeAckFalse(),
                            probe = probe,
                            messages = messages,
                        ),
                    )

                probe.protocolVersion != BRIDGE_PROTOCOL_VERSION ->
                    StartupProbeOutcome.Failure(
                        buildFailureLines(
                            backendSettings = backendSettings,
                            serverId = serverId,
                            serverInstanceId = serverInstanceId,
                            pluginName = pluginName,
                            pluginVersion = pluginVersion,
                            detail = messages.startupProtocolMismatch(),
                            probe = probe,
                            messages = messages,
                        ),
                    )

                else -> StartupProbeOutcome.Success(probe)
            }
        } catch (exception: BackendClientException) {
            StartupProbeOutcome.Failure(
                buildFailureLines(
                    backendSettings = backendSettings,
                    serverId = serverId,
                    serverInstanceId = serverInstanceId,
                    pluginName = pluginName,
                    pluginVersion = pluginVersion,
                    detail = exception.message ?: messages.startupProbeFailedDefault(),
                    probe = null,
                    messages = messages,
                ),
            )
        } catch (exception: Throwable) {
            StartupProbeOutcome.Failure(
                buildFailureLines(
                    backendSettings = backendSettings,
                    serverId = serverId,
                    serverInstanceId = serverInstanceId,
                    pluginName = pluginName,
                    pluginVersion = pluginVersion,
                    detail = exception.message ?: exception::class.java.simpleName,
                    probe = null,
                    messages = messages,
                ),
            )
        }
    }

    private fun buildFailureLines(
        backendSettings: BackendSettings,
        serverId: String,
        serverInstanceId: String,
        pluginName: String,
        pluginVersion: String,
        detail: String,
        probe: ProbeResult?,
        messages: PluginMessages,
    ): List<String> {
        return messages.startupFailureLines(
            backendSettings = backendSettings,
            serverId = serverId,
            serverInstanceId = serverInstanceId,
            pluginName = pluginName,
            pluginVersion = pluginVersion,
            bridgeProtocolVersion = BRIDGE_PROTOCOL_VERSION,
            detail = detail,
            probe = probe,
            backendEntrypoint = BACKEND_ENTRYPOINT,
        )
    }
}
