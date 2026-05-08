package me.eternalblue.agent4minecraft.skill

import me.eternalblue.agent4minecraft.domain.SkillCreationStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SkillCreationSessionStore(
    private val ttlMillis: Long = TimeUnit.MINUTES.toMillis(30),
) {
    private val sessions = ConcurrentHashMap<UUID, SkillCreationSession>()

    fun lookup(
        playerId: UUID,
        nowMillis: Long = System.currentTimeMillis(),
    ): SkillCreationSessionLookup {
        val session = sessions[playerId] ?: return SkillCreationSessionLookup.Missing
        if (isExpired(session, nowMillis)) {
            sessions.remove(playerId, session)
            return SkillCreationSessionLookup.Expired
        }
        return SkillCreationSessionLookup.Active(session)
    }

    fun get(
        playerId: UUID,
        nowMillis: Long = System.currentTimeMillis(),
    ): SkillCreationSession? {
        return when (val lookup = lookup(playerId, nowMillis)) {
            is SkillCreationSessionLookup.Active -> lookup.session
            SkillCreationSessionLookup.Expired,
            SkillCreationSessionLookup.Missing,
            -> null
        }
    }

    fun startPending(
        playerId: UUID,
        commandLabel: String = "a4m",
        nowMillis: Long = System.currentTimeMillis(),
    ): SkillCreationRequestToken {
        val session = SkillCreationSession(
            playerId = playerId,
            draftId = "",
            status = SkillCreationStatus.UNKNOWN,
            commandLabel = commandLabel.ifBlank { "a4m" },
            inFlight = true,
            version = 1L,
            startedAtEpochMillis = nowMillis,
            updatedAtEpochMillis = nowMillis,
        )
        sessions[playerId] = session
        return SkillCreationRequestToken(playerId, session.version)
    }

    fun upsert(
        playerId: UUID,
        draftId: String,
        status: SkillCreationStatus,
        commandLabel: String = "a4m",
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val existing = sessions[playerId]
        sessions[playerId] = SkillCreationSession(
            playerId = playerId,
            draftId = draftId,
            status = status,
            commandLabel = existing?.commandLabel ?: commandLabel.ifBlank { "a4m" },
            inFlight = false,
            version = (existing?.version ?: 0L) + 1L,
            startedAtEpochMillis = existing?.startedAtEpochMillis ?: nowMillis,
            updatedAtEpochMillis = nowMillis,
        )
    }

    fun tryBeginRequest(
        playerId: UUID,
        nowMillis: Long = System.currentTimeMillis(),
    ): SkillCreationRequestStart {
        while (true) {
            val session = sessions[playerId] ?: return SkillCreationRequestStart.Missing
            if (isExpired(session, nowMillis)) {
                sessions.remove(playerId, session)
                return SkillCreationRequestStart.Expired
            }
            if (session.inFlight) {
                return SkillCreationRequestStart.AlreadyInFlight(session)
            }

            val next = session.copy(
                inFlight = true,
                version = session.version + 1L,
                updatedAtEpochMillis = nowMillis,
            )
            if (sessions.replace(playerId, session, next)) {
                return SkillCreationRequestStart.Started(
                    session = next,
                    token = SkillCreationRequestToken(playerId, next.version),
                )
            }
        }
    }

    fun completeRequest(
        token: SkillCreationRequestToken,
        draftId: String,
        status: SkillCreationStatus,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        while (true) {
            val session = sessions[token.playerId] ?: return false
            if (isExpired(session, nowMillis)) {
                sessions.remove(token.playerId, session)
                return false
            }
            if (!session.matches(token)) {
                return false
            }
            if (status == SkillCreationStatus.INSTALLED) {
                return sessions.remove(token.playerId, session)
            }

            val resolvedDraftId = draftId.ifBlank { session.draftId }
            if (resolvedDraftId.isBlank()) {
                return sessions.remove(token.playerId, session)
            }

            val next = session.copy(
                draftId = resolvedDraftId,
                status = status,
                inFlight = false,
                version = session.version + 1L,
                updatedAtEpochMillis = nowMillis,
            )
            if (sessions.replace(token.playerId, session, next)) {
                return true
            }
        }
    }

    fun failRequest(
        token: SkillCreationRequestToken,
        clearSession: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        while (true) {
            val session = sessions[token.playerId] ?: return false
            if (!session.matches(token)) {
                return false
            }
            if (clearSession || isExpired(session, nowMillis)) {
                return sessions.remove(token.playerId, session)
            }

            val next = session.copy(
                inFlight = false,
                version = session.version + 1L,
                updatedAtEpochMillis = nowMillis,
            )
            if (sessions.replace(token.playerId, session, next)) {
                return true
            }
        }
    }

    fun remove(playerId: UUID) {
        sessions.remove(playerId)
    }

    fun clear() {
        sessions.clear()
    }

    private fun isExpired(
        session: SkillCreationSession,
        nowMillis: Long,
    ): Boolean {
        return ttlMillis > 0L && nowMillis - session.updatedAtEpochMillis > ttlMillis
    }
}

data class SkillCreationSession(
    val playerId: UUID,
    val draftId: String,
    val status: SkillCreationStatus,
    val commandLabel: String,
    val inFlight: Boolean,
    val version: Long,
    val startedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val isPendingStart: Boolean
        get() = draftId.isBlank()

    val isDraftReady: Boolean
        get() = status == SkillCreationStatus.DRAFT_READY

    fun matches(token: SkillCreationRequestToken): Boolean {
        return playerId == token.playerId && version == token.version && inFlight
    }
}

data class SkillCreationRequestToken(
    val playerId: UUID,
    val version: Long,
)

sealed class SkillCreationSessionLookup {
    data class Active(
        val session: SkillCreationSession,
    ) : SkillCreationSessionLookup()

    data object Expired : SkillCreationSessionLookup()

    data object Missing : SkillCreationSessionLookup()
}

sealed class SkillCreationRequestStart {
    data class Started(
        val session: SkillCreationSession,
        val token: SkillCreationRequestToken,
    ) : SkillCreationRequestStart()

    data class AlreadyInFlight(
        val session: SkillCreationSession,
    ) : SkillCreationRequestStart()

    data object Expired : SkillCreationRequestStart()

    data object Missing : SkillCreationRequestStart()
}
