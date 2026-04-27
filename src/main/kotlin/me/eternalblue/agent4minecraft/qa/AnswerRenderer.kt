package me.eternalblue.agent4minecraft.qa

import me.eternalblue.agent4minecraft.domain.AskResult
import me.eternalblue.agent4minecraft.i18n.PluginMessages
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import kotlin.math.min

object AnswerRenderer {
    fun sendQueued(sender: CommandSender, message: String) {
        sender.sendMessage(Component.text(message, NamedTextColor.YELLOW))
    }

    fun sendProgress(sender: CommandSender, message: String) {
        sender.sendMessage(Component.text(message, NamedTextColor.YELLOW))
    }

    fun sendAnswer(
        sender: CommandSender,
        result: AskResult,
        messages: PluginMessages,
    ) {
        sender.sendMessage(Component.text(messages.answerHeader(), NamedTextColor.GREEN))
        chunkText(result.answer, messages).forEach { chunk ->
            sender.sendMessage(Component.text(chunk, NamedTextColor.WHITE))
        }
    }

    fun sendFailure(
        sender: CommandSender,
        message: String,
    ) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED))
    }

    fun sendInfo(
        sender: CommandSender,
        lines: List<String>,
    ) {
        lines.forEach { line ->
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY))
        }
    }

    private fun chunkText(
        text: String,
        messages: PluginMessages,
        maxChunkLength: Int = 240,
    ): List<String> {
        return text.lineSequence()
            .flatMap { line ->
                splitLine(line.trimEnd(), maxChunkLength).asSequence()
            }
            .filter(String::isNotBlank)
            .toList()
            .ifEmpty { listOf(messages.emptyAnswer()) }
    }

    private fun splitLine(
        line: String,
        maxChunkLength: Int,
    ): List<String> {
        if (line.isEmpty()) {
            return listOf("")
        }
        if (line.length <= maxChunkLength) {
            return listOf(line)
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < line.length) {
            var end = min(start + maxChunkLength, line.length)
            if (end < line.length) {
                val spaceIndex = line.lastIndexOf(' ', end - 1)
                if (spaceIndex > start + (maxChunkLength / 2)) {
                    end = spaceIndex
                }
            }
            val chunk = line.substring(start, end).trim()
            if (chunk.isNotEmpty()) {
                chunks += chunk
            }
            start = end
            while (start < line.length && line[start].isWhitespace()) {
                start++
            }
        }
        return chunks
    }
}
