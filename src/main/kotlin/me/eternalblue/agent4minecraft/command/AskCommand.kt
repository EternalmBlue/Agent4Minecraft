package me.eternalblue.agent4minecraft.command

import me.eternalblue.agent4minecraft.Agent4MinecraftPlugin
import me.eternalblue.agent4minecraft.config.QaProgressSettings
import me.eternalblue.agent4minecraft.domain.AskProgress
import me.eternalblue.agent4minecraft.i18n.PluginMessages
import me.eternalblue.agent4minecraft.qa.AnswerRenderer
import me.eternalblue.agent4minecraft.qa.QuestionContext
import me.eternalblue.agent4minecraft.qa.QuestionRequestLimiter
import me.eternalblue.agent4minecraft.qa.QuestionService
import me.eternalblue.agent4minecraft.qa.RateLimitDecision
import me.eternalblue.agent4minecraft.qa.ServerPluginInventory
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

class AskCommand(
    private val plugin: Agent4MinecraftPlugin,
    private val executor: ExecutorService,
    private val questionService: QuestionService,
    private val limiter: QuestionRequestLimiter,
    private val progressSettings: QaProgressSettings,
    private val messages: PluginMessages,
) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (!sender.hasPermission(Permissions.ASK)) {
            AnswerRenderer.sendFailure(sender, messages.noPermission())
            return true
        }

        val question = args.joinToString(" ").trim()
        if (question.isEmpty()) {
            AnswerRenderer.sendFailure(sender, messages.askUsage(label))
            return true
        }

        val context = QuestionContext(
            senderId = senderId(sender),
            senderName = sender.name,
            question = question,
            installedPlugins = ServerPluginInventory.snapshot(plugin.server.pluginManager.plugins),
        )

        when (val decision = limiter.tryAcquire(context.senderId)) {
            is RateLimitDecision.Rejected -> {
                AnswerRenderer.sendFailure(sender, decision.message)
                return true
            }

            RateLimitDecision.Allowed -> Unit
        }

        AnswerRenderer.sendQueued(sender, messages.askQueued())
        val latestProgress = AtomicReference<AskProgress?>()
        val progressTask = AtomicReference<BukkitTask?>()
        val startedAtEpochMillis = System.currentTimeMillis()
        if (progressSettings.enabled) {
            progressTask.set(
                scheduleProgressMessages(
                    sender = sender,
                    latestProgress = latestProgress,
                    startedAtEpochMillis = startedAtEpochMillis,
                )
            )
        }

        CompletableFuture
            .supplyAsync(
                {
                    questionService.ask(context) { progress ->
                        latestProgress.set(progress)
                    }
                },
                executor,
            )
            .whenComplete { result, throwable ->
                limiter.release(context.senderId)
                val task = progressTask.getAndSet(null)
                if (!plugin.isEnabled) {
                    task?.cancel()
                    return@whenComplete
                }

                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        task?.cancel()
                        if (sender is Player && !sender.isOnline) {
                            return@Runnable
                        }
                        val failure = AsyncFailureUnwrapper.unwrap(throwable)
                        if (failure != null) {
                            AnswerRenderer.sendFailure(
                                sender,
                                failure.message ?: messages.askFailedFallback(),
                            )
                        } else if (result != null) {
                            AnswerRenderer.sendAnswer(sender, result, messages)
                        }
                    },
                )
            }

        return true
    }

    private fun scheduleProgressMessages(
        sender: CommandSender,
        latestProgress: AtomicReference<AskProgress?>,
        startedAtEpochMillis: Long,
    ): BukkitTask {
        val intervalTicks = progressSettings.intervalSeconds * 20L
        val taskRef = AtomicReference<BukkitTask?>()
        val task = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable {
                if (sender is Player && !sender.isOnline) {
                    taskRef.get()?.cancel()
                    return@Runnable
                }
                val elapsedSeconds = ((System.currentTimeMillis() - startedAtEpochMillis) / 1000L)
                    .coerceAtLeast(0L)
                val progress = latestProgress.get()
                val message = if (progress == null) {
                    messages.askProgressWaiting(elapsedSeconds)
                } else {
                    messages.askProgress(progress, elapsedSeconds)
                }
                AnswerRenderer.sendProgress(sender, message)
            },
            intervalTicks,
            intervalTicks,
        )
        taskRef.set(task)
        return task
    }

    private fun senderId(sender: CommandSender): String {
        return when (sender) {
            is Player -> sender.uniqueId.toString()
            else -> "sender:${sender.name.lowercase()}"
        }
    }

}
