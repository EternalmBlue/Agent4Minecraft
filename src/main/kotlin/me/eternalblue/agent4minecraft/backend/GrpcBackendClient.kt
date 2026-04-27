package me.eternalblue.agent4minecraft.backend

import com.google.protobuf.ByteString
import io.grpc.Channel
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import me.eternalblue.agent4minecraft.config.BackendSettings
import me.eternalblue.agent4minecraft.domain.AskCommandRequest
import me.eternalblue.agent4minecraft.domain.AskProgress
import me.eternalblue.agent4minecraft.domain.AskResult
import me.eternalblue.agent4minecraft.domain.ManifestFile
import me.eternalblue.agent4minecraft.domain.ProbeCommand
import me.eternalblue.agent4minecraft.domain.ProbeResult
import me.eternalblue.agent4minecraft.domain.RejectedPath
import me.eternalblue.agent4minecraft.domain.RemoteSyncState
import me.eternalblue.agent4minecraft.domain.RemoteSyncStatus
import me.eternalblue.agent4minecraft.domain.ServerPluginInfo
import me.eternalblue.agent4minecraft.domain.SyncCommitResult
import me.eternalblue.agent4minecraft.domain.SyncPrepareResult
import me.eternalblue.agent4minecraft.domain.UploadFile
import me.eternalblue.agent4minecraft.domain.UploadReceipt
import me.eternalblue.agent4minecraft.i18n.PluginMessages
import me.eternalblue.agent4minecraft.proto.AgentBridgeServiceGrpc
import me.eternalblue.agent4minecraft.proto.AskEvent
import me.eternalblue.agent4minecraft.proto.AskProgress as ProtoAskProgress
import me.eternalblue.agent4minecraft.proto.AskRequest
import me.eternalblue.agent4minecraft.proto.AskResponse
import me.eternalblue.agent4minecraft.proto.FileChunkUploadRequest
import me.eternalblue.agent4minecraft.proto.FileManifestEntry
import me.eternalblue.agent4minecraft.proto.ProbeRequest
import me.eternalblue.agent4minecraft.proto.RejectedPath as ProtoRejectedPath
import me.eternalblue.agent4minecraft.proto.ServerPlugin
import me.eternalblue.agent4minecraft.proto.SyncCommitRequest
import me.eternalblue.agent4minecraft.proto.SyncPrepareRequest
import me.eternalblue.agent4minecraft.proto.SyncState
import me.eternalblue.agent4minecraft.proto.SyncStatusRequest
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class GrpcBackendClient private constructor(
    private val settings: BackendSettings,
    private val messages: PluginMessages,
    private val channel: ManagedChannel,
) : BackendClient {
    private val probeBlockingStub: AgentBridgeServiceGrpc.AgentBridgeServiceBlockingStub =
        AgentBridgeServiceGrpc.newBlockingStub(channel)
    private val interceptedChannel: Channel =
        ClientInterceptors.intercept(channel, AuthTokenClientInterceptor(settings.authToken))
    private val blockingStub: AgentBridgeServiceGrpc.AgentBridgeServiceBlockingStub =
        AgentBridgeServiceGrpc.newBlockingStub(interceptedChannel)
    private val asyncStub: AgentBridgeServiceGrpc.AgentBridgeServiceStub =
        AgentBridgeServiceGrpc.newStub(interceptedChannel)

    constructor(
        settings: BackendSettings,
        messages: PluginMessages,
    ) : this(settings, messages, createChannel(settings))

    override fun probe(request: ProbeCommand): ProbeResult {
        return try {
            val response = probeBlockingStub
                .withDeadlineAfter(settings.probeTimeoutMillis, TimeUnit.MILLISECONDS)
                .probe(
                    ProbeRequest.newBuilder()
                        .setServerId(request.serverId)
                        .setServerInstanceId(request.serverInstanceId)
                        .setPluginName(request.pluginName)
                        .setPluginVersion(request.pluginVersion)
                        .setProtocolVersion(request.protocolVersion)
                        .build(),
                )
            ProbeResult(
                ack = response.ack,
                message = response.message,
                backendName = response.backendName,
                backendVersion = response.backendVersion.takeUnless(String::isBlank),
                protocolVersion = response.protocolVersion,
                capabilities = response.capabilitiesList.toSet(),
            )
        } catch (throwable: Throwable) {
            throw GrpcErrorMapper.map(throwable, messages.backendActionProbe(), messages)
        }
    }

    override fun ask(request: AskCommandRequest): AskResult {
        return ask(request) {}
    }

    override fun ask(
        request: AskCommandRequest,
        onProgress: (AskProgress) -> Unit,
    ): AskResult {
        return try {
            val events = blockingStub
                .withDeadlineAfter(settings.askDeadlineMillis, TimeUnit.MILLISECONDS)
                .askStream(request.toProto())
            var result: AskResult? = null
            events.forEachRemaining { event ->
                when (event.eventCase) {
                    AskEvent.EventCase.PROGRESS -> onProgress(event.progress.toDomain())
                    AskEvent.EventCase.RESPONSE -> result = event.response.toDomain(request.requestId)
                    AskEvent.EventCase.EVENT_NOT_SET,
                    -> Unit
                }
            }
            result ?: throw BackendTransportException(messages.backendNoAskResult())
        } catch (throwable: Throwable) {
            if (Status.fromThrowable(throwable).code == Status.Code.UNIMPLEMENTED) {
                return askUnary(request)
            }
            throw GrpcErrorMapper.map(throwable, messages.backendActionAsk(), messages)
        }
    }

    private fun askUnary(request: AskCommandRequest): AskResult {
        return try {
            val response = blockingStub
                .withDeadlineAfter(settings.askDeadlineMillis, TimeUnit.MILLISECONDS)
                .ask(request.toProto())
            response.toDomain(request.requestId)
        } catch (throwable: Throwable) {
            throw GrpcErrorMapper.map(throwable, messages.backendActionAsk(), messages)
        }
    }

    override fun prepareSync(
        serverId: String,
        serverInstanceId: String,
        manifest: List<ManifestFile>,
    ): SyncPrepareResult {
        return try {
            val response = blockingStub
                .withDeadlineAfter(settings.syncDeadlineMillis, TimeUnit.MILLISECONDS)
                .prepareSync(
                    SyncPrepareRequest.newBuilder()
                        .setServerId(serverId)
                        .setServerInstanceId(serverInstanceId)
                        .addAllManifest(manifest.map { it.toProto() })
                        .build(),
                )
            SyncPrepareResult(
                syncId = response.syncId,
                requiredPaths = response.requiredPathsList.toSet(),
                rejectedPaths = response.rejectedPathsList.map { protoRejectedPath ->
                    protoRejectedPath.toDomain()
                },
            )
        } catch (throwable: Throwable) {
            throw GrpcErrorMapper.map(throwable, messages.backendActionSyncPrepare(), messages)
        }
    }

    override fun uploadFile(
        syncId: String,
        file: UploadFile,
    ): UploadReceipt {
        val responseFuture = CompletableFuture<UploadReceipt>()
        val requestObserver = asyncStub
            .withDeadlineAfter(settings.syncDeadlineMillis, TimeUnit.MILLISECONDS)
            .uploadFileChunk(
                object : StreamObserver<me.eternalblue.agent4minecraft.proto.FileChunkUploadResponse> {
                    override fun onNext(value: me.eternalblue.agent4minecraft.proto.FileChunkUploadResponse) {
                        responseFuture.complete(
                            UploadReceipt(
                                syncId = value.syncId,
                                relativePath = value.relativePath,
                                receivedBytes = value.receivedBytes,
                                receivedChunks = value.receivedChunks,
                                sha256Verified = value.sha256Verified,
                                message = value.message,
                            ),
                        )
                    }

                    override fun onError(throwable: Throwable) {
                        responseFuture.completeExceptionally(throwable)
                    }

                    override fun onCompleted() {
                        if (!responseFuture.isDone) {
                            responseFuture.completeExceptionally(
                                IllegalStateException(messages.backendNoUploadResult()),
                            )
                        }
                    }
                },
            )

        try {
            streamFile(syncId, file, requestObserver)
            return responseFuture.get(settings.syncDeadlineMillis + 1_000L, TimeUnit.MILLISECONDS)
        } catch (exception: TimeoutException) {
            throw BackendTimeoutException(messages.backendUploadTimedOut(file.relativePath), exception)
        } catch (exception: ExecutionException) {
            throw GrpcErrorMapper.map(
                exception,
                messages.backendActionUpload(file.relativePath),
                messages,
            )
        } catch (exception: BackendClientException) {
            throw exception
        } catch (exception: IOException) {
            throw BackendTransportException(messages.backendFileReadFailed(file.relativePath), exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BackendTimeoutException(messages.backendUploadInterrupted(file.relativePath), exception)
        } catch (exception: Throwable) {
            throw GrpcErrorMapper.map(
                exception,
                messages.backendActionUpload(file.relativePath),
                messages,
            )
        }
    }

    override fun commitSync(
        syncId: String,
        serverId: String,
        serverInstanceId: String,
        uploadedPaths: List<String>,
    ): SyncCommitResult {
        return try {
            val response = blockingStub
                .withDeadlineAfter(settings.syncDeadlineMillis, TimeUnit.MILLISECONDS)
                .commitSync(
                    SyncCommitRequest.newBuilder()
                        .setSyncId(syncId)
                        .setServerId(serverId)
                        .setServerInstanceId(serverInstanceId)
                        .addAllUploadedPaths(uploadedPaths)
                        .build(),
                )
            SyncCommitResult(
                syncId = response.syncId,
                acceptedCount = response.acceptedCount,
                indexedCount = response.indexedCount,
                refreshStarted = response.refreshStarted,
                message = response.message,
            )
        } catch (throwable: Throwable) {
            throw GrpcErrorMapper.map(throwable, messages.backendActionSyncCommit(), messages)
        }
    }

    override fun getSyncStatus(syncId: String): RemoteSyncStatus {
        return try {
            val response = blockingStub
                .withDeadlineAfter(settings.syncDeadlineMillis, TimeUnit.MILLISECONDS)
                .getSyncStatus(
                    SyncStatusRequest.newBuilder()
                        .setSyncId(syncId)
                        .build(),
                )
            RemoteSyncStatus(
                syncId = response.syncId,
                state = response.state.toDomain(),
                acceptedCount = response.acceptedCount,
                indexedCount = response.indexedCount,
                refreshStarted = response.refreshStarted,
                message = response.message,
                updatedAtEpochMillis = response.updatedAtEpochMs,
                requiredFileCount = response.requiredFileCount,
                uploadedFileCount = response.uploadedFileCount,
                totalUploadBytes = response.totalUploadBytes,
                uploadedBytes = response.uploadedBytes,
                currentUploadPath = response.currentUploadPath.takeUnless(String::isBlank),
                refreshTotalBundles = response.refreshTotalBundles,
                refreshCompletedBundles = response.refreshCompletedBundles,
                refreshFailedBundles = response.refreshFailedBundles,
                currentRefreshBundle = response.currentRefreshBundle.takeUnless(String::isBlank),
                currentRefreshPhase = response.currentRefreshPhase.takeUnless(String::isBlank),
            )
        } catch (throwable: Throwable) {
            throw GrpcErrorMapper.map(throwable, messages.backendActionSyncStatus(), messages)
        }
    }

    override fun close() {
        channel.shutdownNow()
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow()
            channel.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun streamFile(
        syncId: String,
        file: UploadFile,
        requestObserver: StreamObserver<FileChunkUploadRequest>,
    ) {
        val totalChunks = totalChunkCount(file.uploadSize, settings.maxChunkBytes)
        Files.newInputStream(file.uploadSourcePath).use { input ->
            val buffer = ByteArray(settings.maxChunkBytes)
            if (file.uploadSize == 0L) {
                requestObserver.onNext(
                    buildChunkRequest(
                        syncId = syncId,
                        file = file,
                        chunkIndex = 0,
                        totalChunks = totalChunks,
                        bytes = ByteArray(0),
                    ),
                )
            } else {
                var chunkIndex = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    requestObserver.onNext(
                        buildChunkRequest(
                            syncId = syncId,
                            file = file,
                            chunkIndex = chunkIndex,
                            totalChunks = totalChunks,
                            bytes = buffer.copyOf(read),
                        ),
                    )
                    chunkIndex++
                }
            }
        }
        requestObserver.onCompleted()
    }

    private fun buildChunkRequest(
        syncId: String,
        file: UploadFile,
        chunkIndex: Int,
        totalChunks: Int,
        bytes: ByteArray,
    ): FileChunkUploadRequest {
        return FileChunkUploadRequest.newBuilder()
            .setSyncId(syncId)
            .setRelativePath(file.relativePath)
            .setChunkIndex(chunkIndex)
            .setTotalChunks(totalChunks)
            .setContentBytes(ByteString.copyFrom(bytes))
            .setSha256(file.uploadSha256)
            .build()
    }

    private fun totalChunkCount(
        size: Long,
        chunkSize: Int,
    ): Int {
        if (size <= 0L) {
            return 1
        }
        return ((size + chunkSize - 1) / chunkSize).toInt()
    }

    private fun ManifestFile.toProto(): FileManifestEntry {
        return FileManifestEntry.newBuilder()
            .setRelativePath(relativePath)
            .setSize(size)
            .setSha256(sha256)
            .setLastModifiedEpochMs(lastModifiedEpochMillis)
            .build()
    }

    private fun ProtoRejectedPath.toDomain(): RejectedPath {
        return RejectedPath(
            relativePath = relativePath,
            reason = reason,
        )
    }

    private fun ServerPluginInfo.toProto(): ServerPlugin {
        return ServerPlugin.newBuilder()
            .setName(name)
            .setVersion(version)
            .setEnabled(enabled)
            .build()
    }

    private fun AskCommandRequest.toProto(): AskRequest {
        return AskRequest.newBuilder()
            .setServerId(serverId)
            .setServerInstanceId(serverInstanceId)
            .setPlayerId(playerId)
            .setPlayerName(playerName)
            .setQuestion(question)
            .setRequestId(requestId)
            .setTimestamp(timestampMillis)
            .addAllInstalledPlugins(installedPlugins.map { it.toProto() })
            .build()
    }

    private fun AskResponse.toDomain(fallbackRequestId: String): AskResult {
        return AskResult(
            requestId = requestId.ifBlank { fallbackRequestId },
            answer = answer,
            citationsSummary = citationsSummary.takeUnless(String::isBlank),
            backendTraceId = backendTraceId.takeUnless(String::isBlank),
        )
    }

    private fun ProtoAskProgress.toDomain(): AskProgress {
        return AskProgress(
            requestId = requestId,
            stage = stage,
            message = message,
            elapsedMillis = elapsedMs,
            sequence = sequence,
        )
    }

    private fun SyncState.toDomain(): RemoteSyncState {
        return when (this) {
            SyncState.SYNC_STATE_PENDING -> RemoteSyncState.PENDING
            SyncState.SYNC_STATE_UPLOADING -> RemoteSyncState.UPLOADING
            SyncState.SYNC_STATE_INDEXING -> RemoteSyncState.INDEXING
            SyncState.SYNC_STATE_COMPLETED -> RemoteSyncState.COMPLETED
            SyncState.SYNC_STATE_FAILED -> RemoteSyncState.FAILED
            SyncState.UNRECOGNIZED,
            SyncState.SYNC_STATE_UNSPECIFIED,
            -> RemoteSyncState.UNKNOWN
        }
    }

    companion object {
        internal fun forChannel(
            settings: BackendSettings,
            channel: ManagedChannel,
            messages: PluginMessages = PluginMessages(emptyMap()),
        ): GrpcBackendClient {
            return GrpcBackendClient(settings, messages, channel)
        }

        private fun createChannel(settings: BackendSettings): ManagedChannel {
            val builder = OkHttpChannelBuilder.forAddress(settings.host, settings.port)
            if (settings.useTls) {
                builder.useTransportSecurity()
            } else {
                builder.usePlaintext()
            }
            return builder.build()
        }
    }
}
