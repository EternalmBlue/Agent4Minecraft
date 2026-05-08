package me.eternalblue.agent4minecraft.command

import me.eternalblue.agent4minecraft.Agent4MinecraftPlugin
import me.eternalblue.agent4minecraft.qa.AnswerRenderer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class AsyncCommandRunner(
    private val plugin: Agent4MinecraftPlugin,
    private val executor: ExecutorService,
) {
    fun <T> run(
        sender: CommandSender,
        fallbackMessage: String,
        task: () -> T,
        onFailure: (Throwable) -> Boolean = { false },
        onSuccess: (T) -> Unit,
    ) {
        CompletableFuture
            .supplyAsync({ task() }, executor)
            .whenComplete { result, throwable ->
                if (!plugin.isEnabled) {
                    return@whenComplete
                }

                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        if (sender is Player && !sender.isOnline) {
                            return@Runnable
                        }
                        val failure = AsyncFailureUnwrapper.unwrap(throwable)
                        if (failure != null) {
                            val handled = onFailure(failure)
                            if (!handled) {
                                AnswerRenderer.sendFailure(
                                    sender,
                                    failure.message ?: fallbackMessage,
                                )
                            }
                        } else if (result != null) {
                            onSuccess(result)
                        }
                    },
                )
            }
    }
}
