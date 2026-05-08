package me.eternalblue.agent4minecraft.i18n

import me.eternalblue.agent4minecraft.config.BackendSettings
import me.eternalblue.agent4minecraft.domain.AskProgress
import me.eternalblue.agent4minecraft.domain.ProbeResult
import me.eternalblue.agent4minecraft.domain.RemoteSyncState
import me.eternalblue.agent4minecraft.domain.SkillScope
import me.eternalblue.agent4minecraft.domain.SkillSummary
import me.eternalblue.agent4minecraft.transfer.LocalSyncPhase

class PluginMessages(
    private val templates: Map<String, String>,
    private val fallbackTemplates: Map<String, String> = emptyMap(),
) {
    fun noPermission(): String = msg("common.no_permission")
    fun askUsage(label: String): String = msg("ask.usage", "label" to label)
    fun adminUsage(label: String): String = msg("admin.usage", "label" to label)
    fun unknownSubcommand(label: String): String = msg("admin.unknown_subcommand", "label" to label)
    fun skillUsage(label: String): String = msg("skill.usage", "label" to label)
    fun skillCreateUsage(label: String): String = msg("skill.create_usage", "label" to label)
    fun askQueued(): String = msg("ask.queued")
    fun askProgress(progress: AskProgress, elapsedSeconds: Long): String {
        val stage = msgOrDefault(
            key = "ask.progress.stage.${progress.stage}",
            defaultValue = progress.message.ifBlank { progress.stage },
        )
        return msg(
            "ask.progress.running",
            "elapsed_seconds" to elapsedSeconds,
            "stage" to stage,
        )
    }

    fun askProgressWaiting(elapsedSeconds: Long): String {
        return msg("ask.progress.waiting", "elapsed_seconds" to elapsedSeconds)
    }

    fun askFailedFallback(): String = msg("ask.failed_fallback")
    fun answerHeader(): String = msg("answer.header")
    fun emptyAnswer(): String = msg("answer.empty")
    fun questionBlank(): String = msg("ask.question_blank")
    fun askAlreadyRunning(): String = msg("ask.already_running")
    fun askCooldown(seconds: Int): String = msg("ask.cooldown", "seconds" to seconds)
    fun syncAlreadyRunning(): String = msg("sync.already_running")
    fun syncStarting(): String = msg("sync.starting")
    fun syncFailedFallback(): String = msg("sync.failed_fallback")
    fun statusQueryQueued(): String = msg("status.query_queued")
    fun statusQueryFailedFallback(): String = msg("status.query_failed_fallback")
    fun syncScanningFiles(): String = msg("sync.phase.scanning")
    fun syncSendingManifest(): String = msg("sync.phase.preparing")
    fun syncCommitting(): String = msg("sync.phase.committing")

    fun syncUploading(uploaded: Int, total: Int): String {
        return msg("sync.phase.uploading", "uploaded" to uploaded, "total" to total)
    }

    fun syncFinished(syncId: String): String = msg("sync.finished", "sync_id" to syncId)

    fun syncSummary(scanned: Int, requiredUploads: Int, uploaded: Int): String {
        return msg(
            "sync.summary",
            "scanned" to scanned,
            "required_uploads" to requiredUploads,
            "uploaded" to uploaded,
        )
    }

    fun syncCommitSummary(accepted: Int, indexed: Int, refreshStarted: Boolean): String {
        return msg(
            "sync.commit_summary",
            "accepted" to accepted,
            "indexed" to indexed,
            "refresh_started" to refreshStarted,
        )
    }

    fun backendMessage(message: String): String {
        return msg("backend.message", "message" to message.ifBlank { emptyValue() })
    }

    fun backendEndpoint(endpoint: String): String = msg("status.backend_endpoint", "endpoint" to endpoint)
    fun localSyncIdle(): String = msg("status.local_sync_idle")

    fun localSync(syncId: String?, phase: LocalSyncPhase): String {
        return msg(
            "status.local_sync",
            "sync_id" to (syncId ?: pendingSyncId()),
            "phase" to localSyncPhase(phase),
        )
    }

    fun localMessage(message: String): String = msg("status.local_message", "message" to message)
    fun localScannedFiles(count: Int): String = msg("status.local_scanned_files", "count" to count)

    fun localUploadProgress(
        uploadedFiles: Int,
        requiredFiles: Int,
        uploadedBytes: String,
        totalBytes: String,
    ): String {
        return msg(
            "status.local_upload_progress",
            "uploaded_files" to uploadedFiles,
            "required_files" to requiredFiles,
            "uploaded_bytes" to uploadedBytes,
            "total_bytes" to totalBytes,
        )
    }

    fun startedAt(timestamp: String): String = msg("status.started_at", "timestamp" to timestamp)

    fun remoteState(
        state: RemoteSyncState,
        accepted: Int,
        indexed: Int,
        refreshStarted: Boolean,
    ): String {
        return msg(
            "status.remote_state",
            "state" to remoteSyncState(state),
            "accepted" to accepted,
            "indexed" to indexed,
            "refresh_started" to refreshStarted,
        )
    }

    fun remoteUploadProgress(
        uploadedFiles: Int,
        requiredFiles: Int,
        uploadedBytes: String,
        totalBytes: String,
    ): String {
        return msg(
            "status.remote_upload_progress",
            "uploaded_files" to uploadedFiles,
            "required_files" to requiredFiles,
            "uploaded_bytes" to uploadedBytes,
            "total_bytes" to totalBytes,
        )
    }

    fun remoteCurrentUpload(path: String): String = msg("status.remote_current_upload", "path" to path)
    fun remoteRefreshProgress(completed: Int, total: Int, failed: Int): String {
        return msg(
            "status.remote_refresh_progress",
            "completed" to completed,
            "total" to total,
            "failed" to failed,
        )
    }

    fun remoteCurrentRefresh(bundle: String, phase: String?): String {
        return msg("status.remote_current_refresh", "bundle" to bundle, "phase" to (phase ?: unknown()))
    }

    fun remoteMessage(message: String): String = msg("status.remote_message", "message" to message)
    fun remoteStatusQueryFailed(error: String): String = msg("status.remote_query_failed", "error" to error)

    fun lastSync(syncId: String, scanned: Int, uploaded: Int): String {
        return msg("status.last_sync", "sync_id" to syncId, "scanned" to scanned, "uploaded" to uploaded)
    }

    fun lastCommit(accepted: Int, indexed: Int, refreshStarted: Boolean): String {
        return msg(
            "status.last_commit",
            "accepted" to accepted,
            "indexed" to indexed,
            "refresh_started" to refreshStarted,
        )
    }

    fun lastCompleted(timestamp: String): String = msg("status.last_completed", "timestamp" to timestamp)
    fun lastFailure(message: String): String = msg("status.last_failure", "message" to message)

    fun skillListQueued(): String = msg("skill.list_queued")
    fun skillListHeader(count: Int): String = msg("skill.list_header", "count" to count)
    fun skillListEmpty(): String = msg("skill.list_empty")
    fun skillListItem(skill: SkillSummary): String {
        return msg(
            "skill.list_item",
            "scope" to skillScope(skill.scope),
            "name" to skill.name,
            "description" to skill.description.ifBlank { emptyValue() },
            "valid" to skill.valid,
            "readonly" to skill.readonly,
            "deletable" to skill.deletable,
            "diagnostics" to skill.diagnostics.joinToString("; ").ifBlank { emptyValue() },
        )
    }

    fun skillViewQueued(skillName: String): String = msg("skill.view_queued", "name" to skillName)
    fun skillViewHeader(skill: SkillSummary): String {
        return msg(
            "skill.view_header",
            "scope" to skillScope(skill.scope),
            "name" to skill.name,
            "valid" to skill.valid,
        )
    }

    fun skillViewDescription(description: String): String {
        return msg("skill.view_description", "description" to description.ifBlank { emptyValue() })
    }

    fun skillViewDiagnostics(diagnostics: String): String {
        return msg("skill.view_diagnostics", "diagnostics" to diagnostics.ifBlank { emptyValue() })
    }

    fun skillViewContentHeader(): String = msg("skill.view_content_header")
    fun skillDeleteQueued(skillName: String): String = msg("skill.delete_queued", "name" to skillName)
    fun skillDeleteResult(skillName: String, message: String, archivedPath: String?): String {
        return msg(
            "skill.delete_result",
            "name" to skillName,
            "message" to message.ifBlank { emptyValue() },
            "archived_path" to (archivedPath ?: emptyValue()),
        )
    }

    fun skillCreateQueued(): String = msg("skill.create_queued")
    fun skillCreateRequirementBlank(): String = msg("skill.create_requirement_blank")
    fun skillCreateResponseBlank(): String = msg("skill.create_response_blank")
    fun skillNameBlank(): String = msg("skill.name_blank")
    fun skillNoActiveDraft(): String = msg("skill.no_active_draft")
    fun skillDraftCancelled(): String = msg("skill.draft_cancelled")
    fun skillDraftAlreadyActive(label: String): String = msg("skill.draft_already_active", "label" to label)
    fun skillDraftCancelHint(label: String): String = msg("skill.draft_cancel_hint", "label" to label)
    fun skillChatModeEntered(): String = msg("skill.chat_mode_entered")
    fun skillChatModeExited(): String = msg("skill.chat_mode_exited")
    fun skillChatPrivateNotice(): String = msg("skill.chat_private_notice")
    fun skillChatContinueHint(label: String = "a4m"): String = msg("skill.chat_continue_hint", "label" to label)
    fun skillChatEmptyResponse(): String = msg("skill.chat_empty_response")
    fun skillChatStartPending(): String = msg("skill.chat_start_pending")
    fun skillChatRequestInFlight(): String = msg("skill.chat_request_in_flight")
    fun skillConfirmNotReady(): String = msg("skill.confirm_not_ready")
    fun skillSessionExpired(): String = msg("skill.session_expired")
    fun skillStatusHeader(): String = msg("skill.status_header")
    fun skillStatusNoActive(): String = msg("skill.status_no_active")
    fun skillStatusLine(status: String, inFlight: Boolean, draftId: String): String {
        return msg(
            "skill.status_line",
            "status" to status,
            "in_flight" to inFlight,
            "draft_id" to draftId.ifBlank { emptyValue() },
        )
    }

    fun skillCreationMessage(message: String): String = msg("skill.creation_message", "message" to message)
    fun skillCreationQuestionsHeader(): String = msg("skill.creation_questions_header")
    fun skillCreationQuestion(index: Int, question: String): String {
        return msg("skill.creation_question", "index" to index, "question" to question)
    }

    fun skillCreationDraftReady(skillName: String): String = msg("skill.creation_draft_ready", "name" to skillName)
    fun skillCreationInstalled(skillName: String): String = msg("skill.creation_installed", "name" to skillName)
    fun skillCreationContinueHint(label: String): String = msg("skill.creation_continue_hint", "label" to label)
    fun skillCreationConfirmHint(label: String): String = msg("skill.creation_confirm_hint", "label" to label)
    fun skillCreationDiagnosticsHeader(): String = msg("skill.creation_diagnostics_header")
    fun skillOperationFailedFallback(): String = msg("skill.failed_fallback")

    fun backendActionProbe(): String = msg("backend.action.probe")
    fun backendActionAsk(): String = msg("backend.action.ask")
    fun backendActionSyncPrepare(): String = msg("backend.action.sync_prepare")
    fun backendActionUpload(relativePath: String): String = msg("backend.action.upload", "path" to relativePath)
    fun backendActionSyncCommit(): String = msg("backend.action.sync_commit")
    fun backendActionSyncStatus(): String = msg("backend.action.sync_status")
    fun backendActionSkillList(): String = msg("backend.action.skill_list")
    fun backendActionSkillView(skillName: String): String = msg("backend.action.skill_view", "name" to skillName)
    fun backendActionSkillDelete(skillName: String): String = msg("backend.action.skill_delete", "name" to skillName)
    fun backendActionSkillCreate(): String = msg("backend.action.skill_create")

    fun backendTransportFailure(action: String, detail: String): String {
        return msg("backend.error.transport_failure", "action" to action, "detail" to detail)
    }

    fun backendAuthenticationFailed(): String = msg("backend.error.authentication_failed")
    fun backendPermissionDenied(action: String): String = msg("backend.error.permission_denied", "action" to action)
    fun backendTimedOut(action: String): String = msg("backend.error.timed_out", "action" to action)
    fun backendUnavailable(): String = msg("backend.error.unavailable")

    fun backendRequestRejected(action: String, detail: String): String {
        return msg("backend.error.request_rejected", "action" to action, "detail" to detail)
    }

    fun backendUploadTimedOut(relativePath: String): String = msg("backend.error.upload_timed_out", "path" to relativePath)
    fun backendFileReadFailed(relativePath: String): String = msg("backend.error.file_read_failed", "path" to relativePath)
    fun backendUploadInterrupted(relativePath: String): String = msg("backend.error.upload_interrupted", "path" to relativePath)
    fun backendNoUploadResult(): String = msg("backend.error.no_upload_result")
    fun backendNoAskResult(): String = msg("backend.error.no_ask_result")

    fun startupProbeAckFalse(): String = msg("startup.probe_ack_false")
    fun startupProtocolMismatch(): String = msg("startup.protocol_mismatch")
    fun startupSkillsUnsupported(capability: String): String {
        return msg("startup.skills_unsupported", "capability" to capability)
    }

    fun startupProbeFailedDefault(): String = msg("startup.probe_failed_default")

    fun startupFailureLines(
        backendSettings: BackendSettings,
        serverId: String,
        serverInstanceId: String,
        pluginName: String,
        pluginVersion: String,
        bridgeProtocolVersion: Int,
        detail: String,
        probe: ProbeResult?,
        backendEntrypoint: String,
    ): List<String> {
        val backendSummary = if (probe == null) {
            msg("startup.backend_unavailable")
        } else {
            msg(
                "startup.backend_response",
                "backend_name" to probe.backendName.ifBlank { unknown() },
                "backend_version" to (probe.backendVersion ?: unknown()),
                "protocol_version" to probe.protocolVersion,
                "message" to probe.message.ifBlank { emptyValue() },
            )
        }

        return listOf(
            msg("startup.failed"),
            msg("startup.attempted_endpoint", "endpoint" to backendSettings.endpointLabel),
            msg("startup.server_identity", "server_id" to serverId, "server_instance_id" to serverInstanceId),
            msg("startup.plugin_identity", "plugin_name" to pluginName, "plugin_version" to pluginVersion),
            msg("startup.plugin_protocol", "protocol_version" to bridgeProtocolVersion),
            msg("startup.failure_detail", "detail" to detail),
            backendSummary,
            msg("startup.check_plugin_config"),
            msg("startup.check_backend_config"),
            msg("startup.confirm_backend_running"),
            msg("startup.backend_entrypoint", "entrypoint" to backendEntrypoint),
            msg("startup.restart_server"),
        )
    }

    fun invalidRequest(): String = msg("common.invalid_request")
    fun unknownError(): String = msg("common.unknown_error")
    fun unknown(): String = msg("common.unknown")
    fun emptyValue(): String = msg("common.empty")
    fun pendingSyncId(): String = msg("status.pending_sync_id")

    fun localSyncPhase(phase: LocalSyncPhase): String {
        return msg("status.phase.${phase.name.lowercase()}")
    }

    fun remoteSyncState(state: RemoteSyncState): String {
        return msg("status.remote_state_value.${state.name.lowercase()}")
    }

    fun skillScope(scope: SkillScope): String {
        return msg("skill.scope.${scope.name.lowercase()}")
    }

    private fun msg(
        key: String,
        vararg variables: Pair<String, Any?>,
    ): String {
        val template = templates[key] ?: fallbackTemplates[key] ?: key
        return render(template, variables.toMap())
    }

    private fun msgOrDefault(
        key: String,
        defaultValue: String,
    ): String {
        return templates[key] ?: fallbackTemplates[key] ?: defaultValue
    }

    private fun render(
        template: String,
        variables: Map<String, Any?>,
    ): String {
        var rendered = template
        variables.forEach { (name, value) ->
            rendered = rendered.replace("{$name}", value?.toString().orEmpty())
        }
        return rendered
    }
}
