package me.eternalblue.agent4minecraft.transfer

import me.eternalblue.agent4minecraft.backend.BackendClient
import me.eternalblue.agent4minecraft.backend.BackendClientException
import me.eternalblue.agent4minecraft.domain.UploadFile
import me.eternalblue.agent4minecraft.i18n.PluginMessages
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class SyncService(
    private val serverId: String,
    private val serverInstanceId: String,
    private val backendEndpointLabel: String,
    private val backendClient: BackendClient,
    private val uploadPreparer: SyncUploadPreparer,
    private val tracker: SyncStatusTracker,
    private val messages: PluginMessages,
    private val logger: Logger,
) {
    private val running = AtomicBoolean(false)

    fun startManualSync(): SyncSummary {
        if (!running.compareAndSet(false, true) || !tracker.begin()) {
            throw IllegalStateException(messages.syncAlreadyRunning())
        }

        try {
            tracker.update(
                phase = LocalSyncPhase.SCANNING,
                message = messages.syncScanningFiles(),
            )
            val localFiles = uploadPreparer.prepare()
            val redactedFileCount = localFiles.count(UploadFile::redacted)
            if (redactedFileCount > 0) {
                logger.info("Redacted sensitive values in $redactedFileCount file(s) before config sync upload.")
            }
            val scannedProgress = LocalSyncProgress(scannedFileCount = localFiles.size)

            tracker.update(
                phase = LocalSyncPhase.PREPARING,
                message = messages.syncSendingManifest(),
                progress = scannedProgress,
            )
            val prepareResult = backendClient.prepareSync(
                serverId = serverId,
                serverInstanceId = serverInstanceId,
                manifest = localFiles.map(UploadFile::toManifestFile),
            )
            val syncId = prepareResult.syncId
            val requiredFiles = localFiles.filter { file ->
                prepareResult.requiredPaths.contains(file.relativePath)
            }
            val requiredUploadBytes = requiredFiles.sumOf(UploadFile::uploadSize)

            if (prepareResult.rejectedPaths.isNotEmpty()) {
                logger.info(
                    "Backend rejected these paths: " +
                        prepareResult.rejectedPaths.joinToString { rejected ->
                            "${rejected.relativePath}(${rejected.reason})"
                        },
                )
            }

            var uploadProgress = LocalSyncProgress(
                scannedFileCount = localFiles.size,
                requiredUploadCount = requiredFiles.size,
                uploadedFileCount = 0,
                totalUploadBytes = requiredUploadBytes,
                uploadedBytes = 0L,
            )
            tracker.update(
                phase = LocalSyncPhase.UPLOADING,
                message = messages.syncUploading(uploaded = 0, total = requiredFiles.size),
                syncId = syncId,
                progress = uploadProgress,
            )

            requiredFiles.forEach { file ->
                val receipt = backendClient.uploadFile(syncId, file)
                uploadProgress = uploadProgress.copy(
                    uploadedFileCount = uploadProgress.uploadedFileCount + 1,
                    uploadedBytes = uploadProgress.uploadedBytes + receipt.receivedBytes,
                )
                tracker.update(
                    phase = LocalSyncPhase.UPLOADING,
                    message = messages.syncUploading(
                        uploaded = uploadProgress.uploadedFileCount,
                        total = requiredFiles.size,
                    ),
                    syncId = syncId,
                    progress = uploadProgress,
                )
            }

            tracker.update(
                phase = LocalSyncPhase.COMMITTING,
                message = messages.syncCommitting(),
                syncId = syncId,
                progress = uploadProgress,
            )
            val commitResult = backendClient.commitSync(
                syncId = syncId,
                serverId = serverId,
                serverInstanceId = serverInstanceId,
                uploadedPaths = requiredFiles.map(UploadFile::relativePath),
            )

            val summary = SyncSummary(
                syncId = syncId,
                scannedFileCount = localFiles.size,
                requiredUploadCount = requiredFiles.size,
                uploadedFileCount = requiredFiles.size,
                rejectedPathCount = prepareResult.rejectedPaths.size,
                acceptedCount = commitResult.acceptedCount,
                indexedCount = commitResult.indexedCount,
                refreshStarted = commitResult.refreshStarted,
                message = commitResult.message,
                completedAtEpochMillis = System.currentTimeMillis(),
            )
            tracker.complete(summary)
            return summary
        } catch (exception: Exception) {
            val message = exception.message ?: messages.syncFailedFallback()
            logger.warning("Agent4Minecraft sync failed: $message")
            tracker.fail(message)
            throw exception
        } finally {
            uploadPreparer.cleanupTemporaryFiles()
            running.set(false)
        }
    }

    fun describeStatus(): SyncStatusReport {
        val snapshot = tracker.snapshot()
        val latestSyncId = snapshot.activeSync?.syncId ?: snapshot.lastSummary?.syncId
        val (remoteStatus, remoteError) = if (latestSyncId.isNullOrBlank()) {
            null to null
        } else {
            try {
                backendClient.getSyncStatus(latestSyncId) to null
            } catch (exception: BackendClientException) {
                null to exception.message
            }
        }

        return SyncStatusReport(
            backendEndpointLabel = backendEndpointLabel,
            snapshot = snapshot,
            remoteStatus = remoteStatus,
            remoteError = remoteError,
        )
    }
}
