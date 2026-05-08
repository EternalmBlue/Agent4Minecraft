package me.eternalblue.agent4minecraft.skill

import io.papermc.paper.event.player.AsyncChatEvent
import me.eternalblue.agent4minecraft.Agent4MinecraftPlugin
import me.eternalblue.agent4minecraft.command.SkillCommandHandler
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

class SkillCreationChatListener(
    private val plugin: Agent4MinecraftPlugin,
    private val sessionStore: SkillCreationSessionStore,
    private val skillCommandHandler: SkillCommandHandler,
) : Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        when (sessionStore.lookup(player.uniqueId)) {
            is SkillCreationSessionLookup.Active -> {
                event.isCancelled = true
                val message = PlainTextComponentSerializer.plainText().serialize(event.message())
                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        skillCommandHandler.handlePrivateChat(player, message)
                    },
                )
            }

            SkillCreationSessionLookup.Expired -> {
                event.isCancelled = true
                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        skillCommandHandler.expirePlayerSession(player)
                    },
                )
            }

            SkillCreationSessionLookup.Missing -> Unit
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        skillCommandHandler.removePlayerSession(event.player)
    }
}
