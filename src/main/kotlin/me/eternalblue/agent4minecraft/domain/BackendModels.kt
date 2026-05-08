package me.eternalblue.agent4minecraft.domain

import java.nio.file.Path

data class AskCommandRequest(
    val serverId: String,
    val serverInstanceId: String,
    val playerId: String,
    val playerName: String,
    val question: String,
    val requestId: String,
    val timestampMillis: Long,
    val installedPlugins: List<ServerPluginInfo> = emptyList(),
)

data class ServerPluginInfo(
    val name: String,
    val version: String,
    val enabled: Boolean,
)

data class ProbeCommand(
    val serverId: String,
    val serverInstanceId: String,
    val pluginName: String,
    val pluginVersion: String,
    val protocolVersion: Int,
)

data class ProbeResult(
    val ack: Boolean,
    val message: String,
    val backendName: String,
    val backendVersion: String?,
    val protocolVersion: Int,
    val capabilities: Set<String> = emptySet(),
)

data class AskResult(
    val requestId: String,
    val answer: String,
    val citationsSummary: String? = null,
    val backendTraceId: String? = null,
)

data class AskProgress(
    val requestId: String,
    val stage: String,
    val message: String,
    val elapsedMillis: Long,
    val sequence: Int,
)

data class ManifestFile(
    val relativePath: String,
    val size: Long,
    val sha256: String,
    val lastModifiedEpochMillis: Long,
)

data class UploadFile(
    val relativePath: String,
    val sourcePath: Path,
    val uploadSourcePath: Path,
    val uploadSize: Long,
    val uploadSha256: String,
    val sourceLastModifiedEpochMillis: Long,
    val redacted: Boolean = false,
) {
    fun toManifestFile(): ManifestFile {
        return ManifestFile(
            relativePath = relativePath,
            size = uploadSize,
            sha256 = uploadSha256,
            lastModifiedEpochMillis = sourceLastModifiedEpochMillis,
        )
    }
}

data class RejectedPath(
    val relativePath: String,
    val reason: String,
)

data class SyncPrepareResult(
    val syncId: String,
    val requiredPaths: Set<String>,
    val rejectedPaths: List<RejectedPath>,
)

data class UploadReceipt(
    val syncId: String,
    val relativePath: String,
    val receivedBytes: Long,
    val receivedChunks: Int,
    val sha256Verified: Boolean,
    val message: String,
)

data class SyncCommitResult(
    val syncId: String,
    val acceptedCount: Int,
    val indexedCount: Int,
    val refreshStarted: Boolean,
    val message: String,
)

enum class RemoteSyncState {
    UNKNOWN,
    PENDING,
    UPLOADING,
    INDEXING,
    COMPLETED,
    FAILED,
}

data class RemoteSyncStatus(
    val syncId: String,
    val state: RemoteSyncState,
    val acceptedCount: Int,
    val indexedCount: Int,
    val refreshStarted: Boolean,
    val message: String,
    val updatedAtEpochMillis: Long,
    val requiredFileCount: Int,
    val uploadedFileCount: Int,
    val totalUploadBytes: Long,
    val uploadedBytes: Long,
    val currentUploadPath: String?,
    val refreshTotalBundles: Int,
    val refreshCompletedBundles: Int,
    val refreshFailedBundles: Int,
    val currentRefreshBundle: String?,
    val currentRefreshPhase: String?,
)

enum class SkillScope {
    UNKNOWN,
    OFFICIAL,
    GLOBAL,
    SERVER,
}

data class SkillSummary(
    val scope: SkillScope,
    val name: String,
    val description: String,
    val valid: Boolean,
    val readonly: Boolean,
    val deletable: Boolean,
    val diagnostics: List<String> = emptyList(),
)

data class SkillDetail(
    val summary: SkillSummary,
    val content: String,
)

data class SkillDeleteResult(
    val deleted: Boolean,
    val message: String,
    val archivedPath: String?,
)

enum class SkillCreationStatus {
    UNKNOWN,
    NEEDS_CLARIFICATION,
    DRAFT_READY,
    INSTALLED,
}

data class SkillCreationResult(
    val draftId: String,
    val status: SkillCreationStatus,
    val rawStatus: String,
    val message: String,
    val questions: List<String>,
    val skill: SkillSummary?,
    val content: String,
    val diagnostics: List<String> = emptyList(),
)
