package me.eternalblue.agent4minecraft.backend

import me.eternalblue.agent4minecraft.domain.AskCommandRequest
import me.eternalblue.agent4minecraft.domain.AskProgress
import me.eternalblue.agent4minecraft.domain.AskResult
import me.eternalblue.agent4minecraft.domain.ManifestFile
import me.eternalblue.agent4minecraft.domain.ProbeCommand
import me.eternalblue.agent4minecraft.domain.ProbeResult
import me.eternalblue.agent4minecraft.domain.RemoteSyncStatus
import me.eternalblue.agent4minecraft.domain.SkillCreationResult
import me.eternalblue.agent4minecraft.domain.SkillDeleteResult
import me.eternalblue.agent4minecraft.domain.SkillDetail
import me.eternalblue.agent4minecraft.domain.SkillSummary
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

    fun listSkills(
        serverId: String,
        serverInstanceId: String,
    ): List<SkillSummary>

    fun getSkill(
        serverId: String,
        serverInstanceId: String,
        skillName: String,
    ): SkillDetail

    fun deleteSkill(
        serverId: String,
        serverInstanceId: String,
        skillName: String,
    ): SkillDeleteResult

    fun startSkillCreation(
        serverId: String,
        serverInstanceId: String,
        initialRequirement: String,
    ): SkillCreationResult

    fun continueSkillCreation(
        serverId: String,
        serverInstanceId: String,
        draftId: String,
        userMessage: String,
    ): SkillCreationResult

    fun confirmSkillCreation(
        serverId: String,
        serverInstanceId: String,
        draftId: String,
    ): SkillCreationResult

    override fun close() = Unit
}
