package me.eternalblue.agent4minecraft.skill

import me.eternalblue.agent4minecraft.domain.SkillCreationStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillCreationSessionStoreTest {
    @Test
    fun `stores pending and ready player sessions`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val store = SkillCreationSessionStore(ttlMillis = 1_000L)

        val token = store.startPending(playerId, commandLabel = "agent", nowMillis = 100L)

        val pending = assertIs<SkillCreationSessionLookup.Active>(store.lookup(playerId, nowMillis = 200L)).session
        assertEquals(true, pending.isPendingStart)
        assertEquals(false, pending.isDraftReady)
        assertEquals(true, pending.inFlight)
        assertEquals("agent", pending.commandLabel)
        assertEquals(100L, pending.startedAtEpochMillis)

        assertTrue(store.completeRequest(
            token = token,
            draftId = "draft-1",
            status = SkillCreationStatus.DRAFT_READY,
            nowMillis = 300L,
        ))

        val ready = assertIs<SkillCreationSessionLookup.Active>(store.lookup(playerId, nowMillis = 400L)).session
        assertEquals("draft-1", ready.draftId)
        assertEquals(true, ready.isDraftReady)
        assertEquals(false, ready.inFlight)
        assertEquals("agent", ready.commandLabel)
        assertEquals(100L, ready.startedAtEpochMillis)
        assertEquals(300L, ready.updatedAtEpochMillis)
    }

    @Test
    fun `request lease blocks duplicate in-flight requests`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val store = SkillCreationSessionStore(ttlMillis = 1_000L)

        val startToken = store.startPending(playerId, nowMillis = 100L)
        assertIs<SkillCreationRequestStart.AlreadyInFlight>(store.tryBeginRequest(playerId, nowMillis = 150L))
        assertTrue(store.completeRequest(startToken, "draft-1", SkillCreationStatus.NEEDS_CLARIFICATION, nowMillis = 200L))

        val request = assertIs<SkillCreationRequestStart.Started>(store.tryBeginRequest(playerId, nowMillis = 300L))

        assertEquals("draft-1", request.session.draftId)
        assertEquals(true, request.session.inFlight)
        assertIs<SkillCreationRequestStart.AlreadyInFlight>(store.tryBeginRequest(playerId, nowMillis = 350L))
        assertTrue(store.failRequest(request.token, clearSession = false, nowMillis = 400L))
        assertEquals(false, store.get(playerId, nowMillis = 450L)?.inFlight)
    }

    @Test
    fun `stale completion after removal is ignored`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000006")
        val store = SkillCreationSessionStore(ttlMillis = 1_000L)
        val token = store.startPending(playerId, nowMillis = 100L)

        store.remove(playerId)

        assertFalse(store.completeRequest(token, "draft-1", SkillCreationStatus.NEEDS_CLARIFICATION, nowMillis = 200L))
        assertIs<SkillCreationSessionLookup.Missing>(store.lookup(playerId, nowMillis = 300L))
    }

    @Test
    fun `expires stale sessions and removes them`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val store = SkillCreationSessionStore(ttlMillis = 1_000L)

        store.upsert(
            playerId = playerId,
            draftId = "draft-1",
            status = SkillCreationStatus.NEEDS_CLARIFICATION,
            nowMillis = 100L,
        )

        assertIs<SkillCreationSessionLookup.Expired>(store.lookup(playerId, nowMillis = 1_101L))
        assertNull(store.get(playerId, nowMillis = 1_102L))
        assertIs<SkillCreationSessionLookup.Missing>(store.lookup(playerId, nowMillis = 1_103L))
    }

    @Test
    fun `remove and clear discard active sessions`() {
        val firstPlayerId = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val secondPlayerId = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val store = SkillCreationSessionStore()

        store.upsert(firstPlayerId, "draft-1", SkillCreationStatus.NEEDS_CLARIFICATION)
        store.upsert(secondPlayerId, "draft-2", SkillCreationStatus.DRAFT_READY)
        store.remove(firstPlayerId)

        assertIs<SkillCreationSessionLookup.Missing>(store.lookup(firstPlayerId))
        assertIs<SkillCreationSessionLookup.Active>(store.lookup(secondPlayerId))

        store.clear()

        assertIs<SkillCreationSessionLookup.Missing>(store.lookup(secondPlayerId))
    }
}
