package me.eternalblue.agent4minecraft.transfer

import java.util.concurrent.atomic.AtomicReference

class SyncStatusTracker {
    private val activeSync = AtomicReference<ActiveSync?>(null)
    private val lastSummary = AtomicReference<SyncSummary?>(null)
    private val lastFailureMessage = AtomicReference<String?>(null)

    fun begin(): Boolean {
        return activeSync.compareAndSet(
            null,
            ActiveSync(
                syncId = null,
                phase = LocalSyncPhase.SCANNING,
                message = "Scanning files to sync.",
                startedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun update(
        phase: LocalSyncPhase,
        message: String,
        syncId: String? = activeSync.get()?.syncId,
        progress: LocalSyncProgress? = null,
    ) {
        val current = activeSync.get() ?: return
        activeSync.set(
            current.copy(
                syncId = syncId ?: current.syncId,
                phase = phase,
                message = message,
                progress = progress ?: current.progress,
            ),
        )
    }

    fun complete(summary: SyncSummary) {
        lastSummary.set(summary)
        lastFailureMessage.set(null)
        activeSync.set(null)
    }

    fun fail(message: String) {
        lastFailureMessage.set(message)
        activeSync.set(null)
    }

    fun snapshot(): SyncStatusSnapshot {
        return SyncStatusSnapshot(
            activeSync = activeSync.get(),
            lastSummary = lastSummary.get(),
            lastFailureMessage = lastFailureMessage.get(),
        )
    }
}
