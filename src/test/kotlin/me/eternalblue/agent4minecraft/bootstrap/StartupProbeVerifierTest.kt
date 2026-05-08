package me.eternalblue.agent4minecraft.bootstrap

import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import me.eternalblue.agent4minecraft.backend.GrpcBackendClient
import me.eternalblue.agent4minecraft.config.BackendSettings
import me.eternalblue.agent4minecraft.i18n.PluginMessageLoader
import me.eternalblue.agent4minecraft.proto.AgentBridgeServiceGrpc
import me.eternalblue.agent4minecraft.proto.ProbeRequest
import me.eternalblue.agent4minecraft.proto.ProbeResponse
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StartupProbeVerifierTest {
    @Test
    fun `verify succeeds when backend ack and protocol version match`() {
        val outcome = withProbeClient { request, responseObserver ->
            responseObserver.onNext(
                ProbeResponse.newBuilder()
                    .setAck(true)
                    .setMessage("ready")
                    .setBackendName("AgentForMc")
                    .setBackendVersion("dev")
                    .setProtocolVersion(request.protocolVersion)
                    .addCapabilities("skills_v1")
                    .build(),
            )
            responseObserver.onCompleted()
        }

        val success = assertIs<StartupProbeOutcome.Success>(outcome)
        assertEquals("AgentForMc", success.result.backendName)
        assertEquals(StartupProbeVerifier.BRIDGE_PROTOCOL_VERSION, success.result.protocolVersion)
        assertContains(success.result.capabilities, "skills_v1")
    }

    @Test
    fun `verify fails when backend protocol version mismatches`() {
        val outcome = withProbeClient { _, responseObserver ->
            responseObserver.onNext(
                ProbeResponse.newBuilder()
                    .setAck(true)
                    .setMessage("older backend")
                    .setBackendName("AgentForMc")
                    .setBackendVersion("0.9.0")
                    .setProtocolVersion(99)
                    .build(),
            )
            responseObserver.onCompleted()
        }

        val failure = assertIs<StartupProbeOutcome.Failure>(outcome)
        assertContains(failure.lines.joinToString("\n"), "Bridge protocol version mismatch.")
        assertContains(failure.lines.joinToString("\n"), "protocolVersion=99")
    }

    @Test
    fun `verify fails when backend does not advertise skills capability`() {
        val outcome = withProbeClient { request, responseObserver ->
            responseObserver.onNext(
                ProbeResponse.newBuilder()
                    .setAck(true)
                    .setMessage("ready")
                    .setBackendName("AgentForMc")
                    .setBackendVersion("1.1.0")
                    .setProtocolVersion(request.protocolVersion)
                    .build(),
            )
            responseObserver.onCompleted()
        }

        val failure = assertIs<StartupProbeOutcome.Failure>(outcome)
        assertContains(failure.lines.joinToString("\n"), "skills_v1")
        assertContains(failure.lines.joinToString("\n"), "Upgrade AgentForMc")
    }

    @Test
    fun `verify fails with guidance when backend probe is unavailable`() {
        val outcome = withProbeClient { _, responseObserver ->
            responseObserver.onError(Status.UNAVAILABLE.asRuntimeException())
        }

        val failure = assertIs<StartupProbeOutcome.Failure>(outcome)
        assertContains(failure.lines.joinToString("\n"), "Attempted backend endpoint: 127.0.0.1:50051")
        assertContains(failure.lines.joinToString("\n"), "serverInstanceId=instance-1")
        assertContains(failure.lines.joinToString("\n"), "python -m agent_for_mc.interfaces.grpc")
    }

    private fun withProbeClient(
        probeHandler: (ProbeRequest, StreamObserver<ProbeResponse>) -> Unit,
    ): StartupProbeOutcome {
        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun probe(
                request: ProbeRequest,
                responseObserver: StreamObserver<ProbeResponse>,
            ) {
                probeHandler(request, responseObserver)
            }
        }

        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(service)
            .build()
            .start()

        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        val client = GrpcBackendClient.forChannel(testSettings(), channel)

        return try {
            StartupProbeVerifier.verify(
                backendClient = client,
                backendSettings = testSettings(),
                serverId = "lobby-1",
                serverInstanceId = "instance-1",
                pluginName = "Agent4Minecraft",
                pluginVersion = "1.0.0",
                messages = testMessages(),
            )
        } finally {
            client.close()
            server.shutdownNow()
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun testSettings(): BackendSettings {
        return BackendSettings(
            host = "127.0.0.1",
            port = 50051,
            authToken = "token-123",
            useTls = false,
            deadlineMillis = 5_000L,
            probeTimeoutMillis = 3_000L,
            askDeadlineMillis = 5_000L,
            syncDeadlineMillis = 5_000L,
            maxChunkBytes = 64,
        )
    }

    private fun testMessages() = PluginMessageLoader.loadFromFiles(
        selectedFile = java.io.File("src/main/resources/lang/en_US.yml"),
    )
}
