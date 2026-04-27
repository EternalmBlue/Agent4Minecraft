package me.eternalblue.agent4minecraft.backend

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import me.eternalblue.agent4minecraft.config.BackendSettings
import me.eternalblue.agent4minecraft.domain.AskCommandRequest
import me.eternalblue.agent4minecraft.domain.ProbeCommand
import me.eternalblue.agent4minecraft.domain.ServerPluginInfo
import me.eternalblue.agent4minecraft.proto.AgentBridgeServiceGrpc
import me.eternalblue.agent4minecraft.proto.AskEvent
import me.eternalblue.agent4minecraft.proto.AskProgress
import me.eternalblue.agent4minecraft.proto.AskRequest
import me.eternalblue.agent4minecraft.proto.AskResponse
import me.eternalblue.agent4minecraft.proto.ProbeRequest
import me.eternalblue.agent4minecraft.proto.ProbeResponse
import me.eternalblue.agent4minecraft.proto.SyncCommitRequest
import me.eternalblue.agent4minecraft.proto.SyncCommitResponse
import me.eternalblue.agent4minecraft.proto.SyncPrepareRequest
import me.eternalblue.agent4minecraft.proto.SyncPrepareResponse
import me.eternalblue.agent4minecraft.proto.SyncStatusRequest
import me.eternalblue.agent4minecraft.proto.SyncStatusResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrpcBackendClientTest {
    @Test
    fun `probe skips bearer metadata and returns backend details`() {
        val capturedAuthorization = AtomicReference<String?>("unset")
        val capturedRequest = AtomicReference<ProbeRequest>()
        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun probe(
                request: ProbeRequest,
                responseObserver: StreamObserver<ProbeResponse>,
            ) {
                capturedRequest.set(request)
                responseObserver.onNext(
                    ProbeResponse.newBuilder()
                        .setAck(true)
                        .setMessage("ready")
                        .setBackendName("AgentForMc")
                        .setBackendVersion("dev")
                        .setProtocolVersion(request.protocolVersion)
                        .build(),
                )
                responseObserver.onCompleted()
            }
        }

        val interceptor = object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>,
            ): ServerCall.Listener<ReqT> {
                capturedAuthorization.set(headers.get(AuthTokenClientInterceptor.AUTHORIZATION_HEADER))
                return next.startCall(call, headers)
            }
        }

        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(ServerInterceptors.intercept(service, interceptor))
            .build()
            .start()

        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        val client = GrpcBackendClient.forChannel(testSettings(), channel)

        try {
            val result = client.probe(
                ProbeCommand(
                    serverId = "lobby-1",
                    serverInstanceId = "instance-1",
                    pluginName = "Agent4Minecraft",
                    pluginVersion = "1.0.0",
                    protocolVersion = 1,
                ),
            )

            assertEquals(true, result.ack)
            assertEquals("AgentForMc", result.backendName)
            assertEquals("dev", result.backendVersion)
            assertEquals(null, capturedAuthorization.get())
            assertEquals("instance-1", capturedRequest.get().serverInstanceId)
        } finally {
            client.close()
            server.shutdownNow()
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `ask attaches bearer metadata and installed plugins`() {
        val capturedAuthorization = AtomicReference<String?>()
        val capturedRequest = AtomicReference<AskRequest>()
        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun ask(
                request: AskRequest,
                responseObserver: StreamObserver<AskResponse>,
            ) {
                capturedRequest.set(request)
                responseObserver.onNext(
                    AskResponse.newBuilder()
                        .setRequestId(request.requestId)
                        .setAnswer("pong")
                        .build(),
                )
                responseObserver.onCompleted()
            }

            override fun prepareSync(
                request: SyncPrepareRequest,
                responseObserver: StreamObserver<SyncPrepareResponse>,
            ) {
                responseObserver.onError(Status.UNIMPLEMENTED.asRuntimeException())
            }

            override fun commitSync(
                request: SyncCommitRequest,
                responseObserver: StreamObserver<SyncCommitResponse>,
            ) {
                responseObserver.onError(Status.UNIMPLEMENTED.asRuntimeException())
            }

            override fun getSyncStatus(
                request: SyncStatusRequest,
                responseObserver: StreamObserver<SyncStatusResponse>,
            ) {
                responseObserver.onError(Status.UNIMPLEMENTED.asRuntimeException())
            }
        }

        val interceptor = object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>,
            ): ServerCall.Listener<ReqT> {
                capturedAuthorization.set(headers.get(AuthTokenClientInterceptor.AUTHORIZATION_HEADER))
                return next.startCall(call, headers)
            }
        }

        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(ServerInterceptors.intercept(service, interceptor))
            .build()
            .start()

        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        val client = GrpcBackendClient.forChannel(testSettings(), channel)

        try {
            val result = client.ask(
                AskCommandRequest(
                    serverId = "lobby-1",
                    serverInstanceId = "instance-1",
                    playerId = "player-1",
                    playerName = "tester",
                    question = "ping",
                    requestId = "req-1",
                    timestampMillis = 1L,
                    installedPlugins = listOf(
                        ServerPluginInfo(
                            name = "EssentialsX",
                            version = "2.20.1",
                            enabled = true,
                        ),
                    ),
                ),
            )

            assertEquals("pong", result.answer)
            assertEquals("Bearer token-123", capturedAuthorization.get())
            assertEquals("instance-1", capturedRequest.get().serverInstanceId)
            assertEquals(1, capturedRequest.get().installedPluginsCount)
            assertEquals("EssentialsX", capturedRequest.get().getInstalledPlugins(0).name)
            assertEquals("2.20.1", capturedRequest.get().getInstalledPlugins(0).version)
            assertEquals(true, capturedRequest.get().getInstalledPlugins(0).enabled)
        } finally {
            client.close()
            server.shutdownNow()
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `ask stream reports progress before final answer`() {
        val capturedAuthorization = AtomicReference<String?>()
        val capturedRequest = AtomicReference<AskRequest>()
        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun askStream(
                request: AskRequest,
                responseObserver: StreamObserver<AskEvent>,
            ) {
                capturedRequest.set(request)
                responseObserver.onNext(
                    AskEvent.newBuilder()
                        .setProgress(
                            AskProgress.newBuilder()
                                .setRequestId(request.requestId)
                                .setStage("retrieval")
                                .setMessage("Searching docs")
                                .setElapsedMs(250L)
                                .setSequence(1),
                        )
                        .build(),
                )
                responseObserver.onNext(
                    AskEvent.newBuilder()
                        .setResponse(
                            AskResponse.newBuilder()
                                .setRequestId(request.requestId)
                                .setAnswer("stream pong")
                                .setBackendTraceId("trace-1"),
                        )
                        .build(),
                )
                responseObserver.onCompleted()
            }
        }

        val interceptor = object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>,
            ): ServerCall.Listener<ReqT> {
                capturedAuthorization.set(headers.get(AuthTokenClientInterceptor.AUTHORIZATION_HEADER))
                return next.startCall(call, headers)
            }
        }

        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(ServerInterceptors.intercept(service, interceptor))
            .build()
            .start()

        val channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        val client = GrpcBackendClient.forChannel(testSettings(), channel)

        try {
            val progressEvents = mutableListOf<me.eternalblue.agent4minecraft.domain.AskProgress>()
            val result = client.ask(
                AskCommandRequest(
                    serverId = "lobby-1",
                    serverInstanceId = "instance-1",
                    playerId = "player-1",
                    playerName = "tester",
                    question = "ping",
                    requestId = "req-1",
                    timestampMillis = 1L,
                ),
            ) { progressEvents += it }

            assertEquals("stream pong", result.answer)
            assertEquals("trace-1", result.backendTraceId)
            assertEquals("Bearer token-123", capturedAuthorization.get())
            assertEquals("instance-1", capturedRequest.get().serverInstanceId)
            assertEquals(1, progressEvents.size)
            assertEquals("retrieval", progressEvents.single().stage)
            assertEquals(250L, progressEvents.single().elapsedMillis)
        } finally {
            client.close()
            server.shutdownNow()
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `ask maps deadline exceeded to backend timeout`() {
        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun ask(
                request: AskRequest,
                responseObserver: StreamObserver<AskResponse>,
            ) {
                responseObserver.onError(Status.DEADLINE_EXCEEDED.asRuntimeException())
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

        try {
            assertFailsWith<BackendTimeoutException> {
                client.ask(
                    AskCommandRequest(
                        serverId = "lobby-1",
                        serverInstanceId = "instance-1",
                        playerId = "player-1",
                        playerName = "tester",
                        question = "ping",
                        requestId = "req-1",
                        timestampMillis = 1L,
                    ),
                )
            }
        } finally {
            client.close()
            server.shutdownNow()
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `probe maps deadline exceeded to backend timeout`() {
        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun probe(
                request: ProbeRequest,
                responseObserver: StreamObserver<ProbeResponse>,
            ) {
                responseObserver.onError(Status.DEADLINE_EXCEEDED.asRuntimeException())
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

        try {
            assertFailsWith<BackendTimeoutException> {
                client.probe(
                    ProbeCommand(
                        serverId = "lobby-1",
                        serverInstanceId = "instance-1",
                        pluginName = "Agent4Minecraft",
                        pluginVersion = "1.0.0",
                        protocolVersion = 1,
                    ),
                )
            }
        } finally {
            client.close()
            server.shutdownNow()
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `get sync status maps upload and refresh progress fields`() {
        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun getSyncStatus(
                request: SyncStatusRequest,
                responseObserver: StreamObserver<SyncStatusResponse>,
            ) {
                responseObserver.onNext(
                    SyncStatusResponse.newBuilder()
                        .setSyncId(request.syncId)
                        .setState(me.eternalblue.agent4minecraft.proto.SyncState.SYNC_STATE_INDEXING)
                        .setAcceptedCount(2)
                        .setIndexedCount(0)
                        .setRefreshStarted(true)
                        .setMessage("refresh running")
                        .setUpdatedAtEpochMs(123L)
                        .setRequiredFileCount(3)
                        .setUploadedFileCount(2)
                        .setTotalUploadBytes(300L)
                        .setUploadedBytes(200L)
                        .setCurrentUploadPath("plugins/TestPlugin/config.yml")
                        .setRefreshTotalBundles(4)
                        .setRefreshCompletedBundles(1)
                        .setRefreshFailedBundles(0)
                        .setCurrentRefreshBundle("lobby-1/__server_core__")
                        .setCurrentRefreshPhase("refreshing_bundle")
                        .build(),
                )
                responseObserver.onCompleted()
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

        try {
            val result = client.getSyncStatus("sync-1")

            assertEquals("sync-1", result.syncId)
            assertEquals(3, result.requiredFileCount)
            assertEquals(2, result.uploadedFileCount)
            assertEquals(300L, result.totalUploadBytes)
            assertEquals(200L, result.uploadedBytes)
            assertEquals("plugins/TestPlugin/config.yml", result.currentUploadPath)
            assertEquals(4, result.refreshTotalBundles)
            assertEquals(1, result.refreshCompletedBundles)
            assertEquals("lobby-1/__server_core__", result.currentRefreshBundle)
            assertEquals("refreshing_bundle", result.currentRefreshPhase)
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
}
