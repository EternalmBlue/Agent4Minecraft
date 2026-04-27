package me.eternalblue.agent4minecraft.i18n

import me.eternalblue.agent4minecraft.config.BackendSettings
import me.eternalblue.agent4minecraft.domain.ProbeResult
import me.eternalblue.agent4minecraft.domain.RemoteSyncState
import me.eternalblue.agent4minecraft.transfer.LocalSyncPhase

class PluginMessages(
    private val templates: Map<String, String>,
    private val fallbackTemplates: Map<String, String> = emptyMap(),
) {
    fun noPermission(): String = msg("common.no_permission")
    fun askUsage(label: String): String = msg("ask.usage", "label" to label)
    fun adminUsage(label: String): String = msg("admin.usage", "label" to label)
    fun unknownSubcommand(label: String): String = msg("admin.unknown_subcommand", "label" to label)
    fun askQueued(): String = msg("ask.queued")
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

    fun backendActionProbe(): String = msg("backend.action.probe")
    fun backendActionAsk(): String = msg("backend.action.ask")
    fun backendActionSyncPrepare(): String = msg("backend.action.sync_prepare")
    fun backendActionUpload(relativePath: String): String = msg("backend.action.upload", "path" to relativePath)
    fun backendActionSyncCommit(): String = msg("backend.action.sync_commit")
    fun backendActionSyncStatus(): String = msg("backend.action.sync_status")

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

    fun startupProbeAckFalse(): String = msg("startup.probe_ack_false")
    fun startupProtocolMismatch(): String = msg("startup.protocol_mismatch")
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

    private fun msg(
        key: String,
        vararg variables: Pair<String, Any?>,
    ): String {
        val template = templates[key] ?: fallbackTemplates[key] ?: key
        return render(template, variables.toMap())
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
