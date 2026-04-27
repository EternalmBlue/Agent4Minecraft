package me.eternalblue.agent4minecraft.backend

import me.eternalblue.agent4minecraft.domain.AskCommandRequest
import me.eternalblue.agent4minecraft.domain.AskProgress
import me.eternalblue.agent4minecraft.domain.AskResult
import me.eternalblue.agent4minecraft.domain.ManifestFile
import me.eternalblue.agent4minecraft.domain.ProbeCommand
import me.eternalblue.agent4minecraft.domain.ProbeResult
import me.eternalblue.agent4minecraft.domain.RemoteSyncStatus
import me.eternalblue.agent4minecraft.domain.SyncCommitResult
import me.eternalblue.agent4minecraft.domain.SyncPrepareResult
import me.eternalblue.agent4minecraft.domain.UploadFile
import me.eternalblue.agent4minecraft.domain.UploadReceipt

interface BackendClient : AutoCloseable {
    fun probe(request: ProbeCommand): ProbeResult

    fun ask(request: AskCommandRequest): AskResult

    fun ask(
        request: AskCommandRequest,
        onProgress: (AskProgress) -> Unit,
    ): AskResult = ask(request)

    fun prepareSync(
        serverId: String,
        serverInstanceId: String,
        manifest: List<ManifestFile>,
    ): SyncPrepareResult

    fun uploadFile(
        syncId: String,
        file: UploadFile,
    ): UploadReceipt

    fun commitSync(
        syncId: String,
        serverId: String,
        serverInstanceId: String,
        uploadedPaths: List<String>,
    ): SyncCommitResult

    fun getSyncStatus(syncId: String): RemoteSyncStatus

    override fun close() = Unit
}
