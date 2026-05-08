package me.eternalblue.agent4minecraft.command

import me.eternalblue.agent4minecraft.i18n.PluginMessages
import me.eternalblue.agent4minecraft.qa.AnswerRenderer
import me.eternalblue.agent4minecraft.transfer.ActiveSync
import me.eternalblue.agent4minecraft.transfer.LocalSyncPhase
import me.eternalblue.agent4minecraft.transfer.SyncService
import me.eternalblue.agent4minecraft.transfer.SyncStatusReport
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class A4mCommand(
    private val asyncRunner: AsyncCommandRunner,
    private val syncService: SyncService,
    private val skillCommandHandler: SkillCommandHandler,
    private val messages: PluginMessages,
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            AnswerRenderer.sendFailure(sender, messages.noPermission())
            return true
        }

        if (args.isEmpty()) {
            AnswerRenderer.sendFailure(sender, messages.adminUsage(label))
            return true
        }

        return when (args[0].lowercase()) {
            "sync" -> {
                triggerSync(sender)
                true
            }

            "status" -> {
                showStatus(sender)
                true
            }

            "skill" -> {
                skillCommandHandler.handle(sender, label, args.drop(1))
                true
            }

            else -> {
                AnswerRenderer.sendFailure(sender, messages.unknownSubcommand(label))
                true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        if (args.size == 1) {
            return listOf("sync", "status", "skill")
                .filter { option -> option.startsWith(args[0], ignoreCase = true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].equals("skill", ignoreCase = true)) {
            return skillCommandHandler.tabComplete(args[1])
        }
        return mutableListOf()
    }

    private fun triggerSync(sender: CommandSender) {
        AnswerRenderer.sendQueued(sender, messages.syncStarting())
        asyncRunner.run(
            sender = sender,
            fallbackMessage = messages.syncFailedFallback(),
            task = { syncService.startManualSync() },
        ) { summary ->
            AnswerRenderer.sendInfo(
                sender,
                listOf(
                    messages.syncFinished(summary.syncId),
                    messages.syncSummary(
                        scanned = summary.scannedFileCount,
                        requiredUploads = summary.requiredUploadCount,
                        uploaded = summary.uploadedFileCount,
                    ),
                    messages.syncCommitSummary(
                        accepted = summary.acceptedCount,
                        indexed = summary.indexedCount,
                        refreshStarted = summary.refreshStarted,
                    ),
                    messages.backendMessage(summary.message),
                ),
            )
        }
    }

    private fun showStatus(sender: CommandSender) {
        AnswerRenderer.sendQueued(sender, messages.statusQueryQueued())
        asyncRunner.run(
            sender = sender,
            fallbackMessage = messages.statusQueryFailedFallback(),
            task = { syncService.describeStatus() },
        ) { report ->
            AnswerRenderer.sendInfo(sender, renderStatus(report))
        }
    }

    private fun renderStatus(report: SyncStatusReport): List<String> {
        val lines = mutableListOf<String>()
        lines += messages.backendEndpoint(report.backendEndpointLabel)

        report.snapshot.activeSync?.let { activeSync ->
            lines += renderLocalActiveSync(activeSync)
        } ?: run {
            lines += messages.localSyncIdle()
        }

        report.remoteStatus?.let { remote ->
            lines += messages.remoteState(
                state = remote.state,
                accepted = remote.acceptedCount,
                indexed = remote.indexedCount,
                refreshStarted = remote.refreshStarted,
            )
            lines += messages.remoteUploadProgress(
                uploadedFiles = remote.uploadedFileCount,
                requiredFiles = remote.requiredFileCount,
                uploadedBytes = formatBytes(remote.uploadedBytes),
                totalBytes = formatBytes(remote.totalUploadBytes),
            )
            remote.currentUploadPath?.let { path ->
                lines += messages.remoteCurrentUpload(path)
            }
            lines += messages.remoteRefreshProgress(
                completed = remote.refreshCompletedBundles,
                total = remote.refreshTotalBundles,
                failed = remote.refreshFailedBundles,
            )
            remote.currentRefreshBundle?.let { bundle ->
                lines += messages.remoteCurrentRefresh(bundle, remote.currentRefreshPhase)
            }
            if (remote.message.isNotBlank()) {
                lines += messages.remoteMessage(remote.message)
            }
        }

        report.remoteError?.takeIf(String::isNotBlank)?.let { error ->
            lines += messages.remoteStatusQueryFailed(error)
        }

        report.snapshot.lastSummary?.let { summary ->
            lines += messages.lastSync(
                syncId = summary.syncId,
                scanned = summary.scannedFileCount,
                uploaded = summary.uploadedFileCount,
            )
            lines += messages.lastCommit(
                accepted = summary.acceptedCount,
                indexed = summary.indexedCount,
                refreshStarted = summary.refreshStarted,
            )
            lines += messages.lastCompleted(formatTimestamp(summary.completedAtEpochMillis))
        }

        report.snapshot.lastFailureMessage?.takeIf(String::isNotBlank)?.let { message ->
            lines += messages.lastFailure(message)
        }

        return lines
    }

    private fun renderLocalActiveSync(activeSync: ActiveSync): List<String> {
        val lines = mutableListOf<String>()
        lines += messages.localSync(activeSync.syncId, activeSync.phase)
        lines += messages.localMessage(activeSync.message)
        lines += messages.localScannedFiles(activeSync.progress.scannedFileCount)
        if (activeSync.phase == LocalSyncPhase.UPLOADING || activeSync.progress.requiredUploadCount > 0) {
            lines += messages.localUploadProgress(
                uploadedFiles = activeSync.progress.uploadedFileCount,
                requiredFiles = activeSync.progress.requiredUploadCount,
                uploadedBytes = formatBytes(activeSync.progress.uploadedBytes),
                totalBytes = formatBytes(activeSync.progress.totalUploadBytes),
            )
        }
        lines += messages.startedAt(formatTimestamp(activeSync.startedAtEpochMillis))
        return lines
    }

    private fun formatTimestamp(timestamp: Long): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return "0 B"
        }
        val units = listOf("B", "KiB", "MiB", "GiB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format("%.1f %s", value, units[index])
    }
}
