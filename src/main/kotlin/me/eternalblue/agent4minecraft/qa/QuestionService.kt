package me.eternalblue.agent4minecraft.qa

import me.eternalblue.agent4minecraft.backend.BackendClient
import me.eternalblue.agent4minecraft.domain.AskCommandRequest
import me.eternalblue.agent4minecraft.domain.AskProgress
import me.eternalblue.agent4minecraft.domain.AskResult
import me.eternalblue.agent4minecraft.domain.ServerPluginInfo
import me.eternalblue.agent4minecraft.i18n.PluginMessages
import java.util.UUID
import java.util.logging.Logger

data class QuestionContext(
    val senderId: String,
    val senderName: String,
    val question: String,
    val installedPlugins: List<ServerPluginInfo>,
)

class QuestionService(
    private val backendClient: BackendClient,
    private val serverId: String,
    private val serverInstanceId: String,
    private val messages: PluginMessages,
    private val debugLogging: Boolean,
    private val logger: Logger,
) {
    fun ask(
        context: QuestionContext,
        onProgress: (AskProgress) -> Unit = {},
    ): AskResult {
        val normalizedQuestion = context.question.trim()
        require(normalizedQuestion.isNotEmpty()) {
            messages.questionBlank()
        }

        val request = AskCommandRequest(
            serverId = serverId,
            serverInstanceId = serverInstanceId,
            playerId = context.senderId,
            playerName = context.senderName,
            question = normalizedQuestion,
            requestId = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            installedPlugins = context.installedPlugins,
        )

        val result = backendClient.ask(request, onProgress)
        if (debugLogging) {
            logger.info(
                buildString {
                    append("Ask request completed: requestId=${request.requestId}")
                    result.backendTraceId?.takeIf(String::isNotBlank)?.let { traceId ->
                        append(", traceId=$traceId")
                    }
                    result.citationsSummary?.takeIf(String::isNotBlank)?.let { citations ->
                        append(", citations=").append(citations)
                    }
                },
            )
        }
        return result
    }
}
