package me.eternalblue.agent4minecraft.transfer

import me.eternalblue.agent4minecraft.domain.RemoteSyncStatus

enum class LocalSyncPhase {
    SCANNING,
    PREPARING,
    UPLOADING,
    COMMITTING,
}

data class LocalSyncProgress(
    val scannedFileCount: Int = 0,
    val requiredUploadCount: Int = 0,
    val uploadedFileCount: Int = 0,
    val totalUploadBytes: Long = 0,
    val uploadedBytes: Long = 0,
)

data class ActiveSync(
    val syncId: String?,
    val phase: LocalSyncPhase,
    val message: String,
    val startedAtEpochMillis: Long,
    val progress: LocalSyncProgress = LocalSyncProgress(),
)

data class SyncSummary(
    val syncId: String,
    val scannedFileCount: Int,
    val requiredUploadCount: Int,
    val uploadedFileCount: Int,
    val rejectedPathCount: Int,
    val acceptedCount: Int,
    val indexedCount: Int,
    val refreshStarted: Boolean,
    val message: String,
    val completedAtEpochMillis: Long,
)

data class SyncStatusSnapshot(
    val activeSync: ActiveSync?,
    val lastSummary: SyncSummary?,
    val lastFailureMessage: String?,
)

data class SyncStatusReport(
    val backendEndpointLabel: String,
    val snapshot: SyncStatusSnapshot,
    val remoteStatus: RemoteSyncStatus?,
    val remoteError: String?,
)
