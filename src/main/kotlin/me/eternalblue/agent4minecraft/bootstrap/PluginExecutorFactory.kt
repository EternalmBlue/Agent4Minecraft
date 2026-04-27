package me.eternalblue.agent4minecraft.bootstrap

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

object PluginExecutorFactory {
    fun create(prefix: String): ExecutorService {
        val threadCount = max(2, min(Runtime.getRuntime().availableProcessors(), 4))
        return Executors.newFixedThreadPool(threadCount, NamedThreadFactory(prefix))
    }

    private class NamedThreadFactory(
        private val prefix: String,
    ) : ThreadFactory {
        private val counter = AtomicInteger(1)

        override fun newThread(runnable: Runnable): Thread {
            return Thread(runnable, "$prefix-${counter.getAndIncrement()}").apply {
                isDaemon = false
            }
        }
    }
}
