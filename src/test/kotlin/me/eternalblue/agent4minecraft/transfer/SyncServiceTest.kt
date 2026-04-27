package me.eternalblue.agent4minecraft.transfer

import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import me.eternalblue.agent4minecraft.backend.GrpcBackendClient
import me.eternalblue.agent4minecraft.config.BackendSettings
import me.eternalblue.agent4minecraft.i18n.PluginMessageLoader
import me.eternalblue.agent4minecraft.proto.AgentBridgeServiceGrpc
import me.eternalblue.agent4minecraft.proto.FileChunkUploadRequest
import me.eternalblue.agent4minecraft.proto.FileChunkUploadResponse
import me.eternalblue.agent4minecraft.proto.SyncCommitRequest
import me.eternalblue.agent4minecraft.proto.SyncCommitResponse
import me.eternalblue.agent4minecraft.proto.SyncPrepareRequest
import me.eternalblue.agent4minecraft.proto.SyncPrepareResponse
import me.eternalblue.agent4minecraft.proto.SyncStatusRequest
import me.eternalblue.agent4minecraft.proto.SyncStatusResponse
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `sync uploads only files requested by backend`() {
        val pluginDir = Files.createDirectories(tempDir.resolve("plugins/TestPlugin"))
        Files.writeString(pluginDir.resolve("config.yml"), "enabled: true\npassword: hunter2")
        Files.writeString(tempDir.resolve("server.properties"), "motd=test")

        val manifestPaths = CopyOnWriteArrayList<String>()
        val manifestChecksums = CopyOnWriteArrayList<String>()
        val uploadedPaths = CopyOnWriteArrayList<String>()
        val uploadedPayloads = CopyOnWriteArrayList<String>()
        val prepareInstanceIds = CopyOnWriteArrayList<String>()
        val commitInstanceIds = CopyOnWriteArrayList<String>()
        val committedPaths = AtomicReference<List<String>>(emptyList())

        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun prepareSync(
                request: SyncPrepareRequest,
                responseObserver: StreamObserver<SyncPrepareResponse>,
            ) {
                prepareInstanceIds += request.serverInstanceId
                manifestPaths += request.manifestList.map { it.relativePath }
                manifestChecksums += request.manifestList.map { it.sha256 }
                responseObserver.onNext(
                    SyncPrepareResponse.newBuilder()
                        .setSyncId("sync-1")
                        .addRequiredPaths("plugins/TestPlugin/config.yml")
                        .build(),
                )
                responseObserver.onCompleted()
            }

            override fun uploadFileChunk(responseObserver: StreamObserver<FileChunkUploadResponse>): StreamObserver<FileChunkUploadRequest> {
                val pathRef = AtomicReference<String>("")
                val payload = ByteArrayOutputStream()
                var byteCount = 0L
                var chunkCount = 0
                return object : StreamObserver<FileChunkUploadRequest> {
                    override fun onNext(value: FileChunkUploadRequest) {
                        pathRef.set(value.relativePath)
                        payload.write(value.contentBytes.toByteArray())
                        byteCount += value.contentBytes.size().toLong()
                        chunkCount++
                    }

                    override fun onError(t: Throwable) = Unit

                    override fun onCompleted() {
                        uploadedPaths += pathRef.get()
                        uploadedPayloads += String(payload.toByteArray(), Charsets.UTF_8)
                        responseObserver.onNext(
                            FileChunkUploadResponse.newBuilder()
                                .setSyncId("sync-1")
                                .setRelativePath(pathRef.get())
                                .setReceivedBytes(byteCount)
                                .setReceivedChunks(chunkCount)
                                .setSha256Verified(true)
                                .setMessage("ok")
                                .build(),
                        )
                        responseObserver.onCompleted()
                    }
                }
            }

            override fun commitSync(
                request: SyncCommitRequest,
                responseObserver: StreamObserver<SyncCommitResponse>,
            ) {
                commitInstanceIds += request.serverInstanceId
                committedPaths.set(request.uploadedPathsList)
                responseObserver.onNext(
                    SyncCommitResponse.newBuilder()
                        .setSyncId(request.syncId)
                        .setAcceptedCount(request.uploadedPathsCount)
                        .setIndexedCount(request.uploadedPathsCount)
                        .setRefreshStarted(true)
                        .setMessage("indexed")
                        .build(),
                )
                responseObserver.onCompleted()
            }

            override fun getSyncStatus(
                request: SyncStatusRequest,
                responseObserver: StreamObserver<SyncStatusResponse>,
            ) {
                responseObserver.onNext(
                    SyncStatusResponse.newBuilder()
                        .setSyncId(request.syncId)
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
        val redactedCacheRoot = tempDir.resolve(".agent4minecraft-sync-cache/redacted")
        val syncService = SyncService(
            serverId = "lobby-1",
            serverInstanceId = "instance-1",
            backendEndpointLabel = "in-process",
            backendClient = client,
            uploadPreparer = SyncUploadPreparer(tempDir, ChecksumCache()),
            tracker = SyncStatusTracker(),
            messages = testMessages(),
            logger = Logger.getLogger("SyncServiceTest"),
        )

        try {
            val summary = syncService.startManualSync()

            assertEquals(
                setOf("plugins/TestPlugin/config.yml", "server.properties"),
                manifestPaths.toSet(),
            )
            assertEquals(listOf("instance-1"), prepareInstanceIds.toList())
            assertEquals(listOf("instance-1"), commitInstanceIds.toList())
            assertEquals(listOf("plugins/TestPlugin/config.yml"), committedPaths.get())
            assertEquals(listOf("plugins/TestPlugin/config.yml"), uploadedPaths.toList())
            assertEquals(listOf("enabled: true\npassword: \"\""), uploadedPayloads.toList())
            assertTrue(manifestChecksums.contains(sha256("enabled: true\npassword: \"\"".toByteArray())))
            assertTrue(Files.readString(pluginDir.resolve("config.yml")).contains("hunter2"))
            assertFalse(Files.exists(redactedCacheRoot))
            assertEquals(2, summary.scannedFileCount)
            assertEquals(1, summary.requiredUploadCount)
            assertEquals(1, summary.uploadedFileCount)
            assertTrue(summary.refreshStarted)
        } finally {
            client.close()
            server.shutdownNow()
            server.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `describe status queries remote progress after local sync completion`() {
        val pluginDir = Files.createDirectories(tempDir.resolve("plugins/TestPlugin"))
        Files.writeString(pluginDir.resolve("config.yml"), "enabled: true")

        val statusRequests = CopyOnWriteArrayList<String>()

        val service = object : AgentBridgeServiceGrpc.AgentBridgeServiceImplBase() {
            override fun prepareSync(
                request: SyncPrepareRequest,
                responseObserver: StreamObserver<SyncPrepareResponse>,
            ) {
                responseObserver.onNext(
                    SyncPrepareResponse.newBuilder()
                        .setSyncId("sync-1")
                        .addRequiredPaths("plugins/TestPlugin/config.yml")
                        .build(),
                )
                responseObserver.onCompleted()
            }

            override fun uploadFileChunk(responseObserver: StreamObserver<FileChunkUploadResponse>): StreamObserver<FileChunkUploadRequest> {
                return object : StreamObserver<FileChunkUploadRequest> {
                    private var path: String = ""
                    private var bytes: Long = 0

                    override fun onNext(value: FileChunkUploadRequest) {
                        path = value.relativePath
                        bytes += value.contentBytes.size().toLong()
                    }

                    override fun onError(t: Throwable) = Unit

                    override fun onCompleted() {
                        responseObserver.onNext(
                            FileChunkUploadResponse.newBuilder()
                                .setSyncId("sync-1")
                                .setRelativePath(path)
                                .setReceivedBytes(bytes)
                                .setReceivedChunks(1)
                                .setSha256Verified(true)
                                .setMessage("ok")
                                .build(),
                        )
                        responseObserver.onCompleted()
                    }
                }
            }

            override fun commitSync(
                request: SyncCommitRequest,
                responseObserver: StreamObserver<SyncCommitResponse>,
            ) {
                responseObserver.onNext(
                    SyncCommitResponse.newBuilder()
                        .setSyncId(request.syncId)
                        .setAcceptedCount(1)
                        .setIndexedCount(0)
                        .setRefreshStarted(true)
                        .setMessage("refresh started")
                        .build(),
                )
                responseObserver.onCompleted()
            }

            override fun getSyncStatus(
                request: SyncStatusRequest,
                responseObserver: StreamObserver<SyncStatusResponse>,
            ) {
                statusRequests += request.syncId
                responseObserver.onNext(
                    SyncStatusResponse.newBuilder()
                        .setSyncId(request.syncId)
                        .setState(me.eternalblue.agent4minecraft.proto.SyncState.SYNC_STATE_INDEXING)
                        .setRefreshStarted(true)
                        .setMessage("refresh running")
                        .setRequiredFileCount(1)
                        .setUploadedFileCount(1)
                        .setTotalUploadBytes(12L)
                        .setUploadedBytes(12L)
                        .setRefreshTotalBundles(1)
                        .setRefreshCompletedBundles(0)
                        .setRefreshFailedBundles(0)
                        .setCurrentRefreshBundle("lobby-1/TestPlugin")
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
        val tracker = SyncStatusTracker()
        val syncService = SyncService(
            serverId = "lobby-1",
            serverInstanceId = "instance-1",
            backendEndpointLabel = "in-process",
            backendClient = client,
            uploadPreparer = SyncUploadPreparer(tempDir, ChecksumCache()),
            tracker = tracker,
            messages = testMessages(),
            logger = Logger.getLogger("SyncServiceTest"),
        )

        try {
            syncService.startManualSync()
            val report = syncService.describeStatus()

            assertEquals(listOf("sync-1"), statusRequests.toList())
            val remoteStatus = assertNotNull(report.remoteStatus)
            assertEquals(1, remoteStatus.requiredFileCount)
            assertEquals(1, remoteStatus.uploadedFileCount)
            assertEquals("lobby-1/TestPlugin", remoteStatus.currentRefreshBundle)
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

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
