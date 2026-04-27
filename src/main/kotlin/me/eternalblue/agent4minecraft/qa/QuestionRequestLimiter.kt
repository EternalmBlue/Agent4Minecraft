package me.eternalblue.agent4minecraft.qa

import me.eternalblue.agent4minecraft.i18n.PluginMessages
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

class QuestionRequestLimiter(
    rateLimitSeconds: Long,
    private val messages: PluginMessages,
) {
    private val inflightSenders = ConcurrentHashMap.newKeySet<String>()
    private val lastCompletedAt = ConcurrentHashMap<String, Long>()
    private val cooldownMillis = rateLimitSeconds.coerceAtLeast(0) * 1_000L

    fun tryAcquire(
        senderId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): RateLimitDecision {
        if (inflightSenders.contains(senderId)) {
            return RateLimitDecision.Rejected(messages.askAlreadyRunning())
        }

        val completedAt = lastCompletedAt[senderId]
        if (completedAt != null && cooldownMillis > 0) {
            val remaining = cooldownMillis - (nowMillis - completedAt)
            if (remaining > 0) {
                val seconds = ceil(remaining / 1_000.0).toInt()
                return RateLimitDecision.Rejected(messages.askCooldown(seconds))
            }
        }

        return if (inflightSenders.add(senderId)) {
            RateLimitDecision.Allowed
        } else {
            RateLimitDecision.Rejected(messages.askAlreadyRunning())
        }
    }

    fun release(
        senderId: String,
        completedAtMillis: Long = System.currentTimeMillis(),
    ) {
        inflightSenders.remove(senderId)
        lastCompletedAt[senderId] = completedAtMillis
    }
}

sealed interface RateLimitDecision {
    data object Allowed : RateLimitDecision

    data class Rejected(
        val message: String,
    ) : RateLimitDecision
}
