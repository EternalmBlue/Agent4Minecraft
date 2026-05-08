package me.eternalblue.agent4minecraft.command

import me.eternalblue.agent4minecraft.domain.SkillCreationResult
import me.eternalblue.agent4minecraft.domain.SkillCreationStatus
import me.eternalblue.agent4minecraft.domain.SkillDetail
import me.eternalblue.agent4minecraft.domain.SkillSummary
import me.eternalblue.agent4minecraft.i18n.PluginMessages
import me.eternalblue.agent4minecraft.qa.AnswerRenderer
import me.eternalblue.agent4minecraft.skill.SkillCreationRequestStart
import me.eternalblue.agent4minecraft.skill.SkillCreationRequestToken
import me.eternalblue.agent4minecraft.skill.SkillCreationSessionLookup
import me.eternalblue.agent4minecraft.skill.SkillCreationSessionStore
import me.eternalblue.agent4minecraft.skill.SkillService
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class SkillCommandHandler(
    private val skillService: SkillService,
    private val messages: PluginMessages,
    private val asyncRunner: AsyncCommandRunner,
    private val sessionStore: SkillCreationSessionStore,
) {
    private val consoleDrafts = ConcurrentHashMap<String, ConsoleSkillDraft>()

    fun handle(
        sender: CommandSender,
        label: String,
        args: List<String>,
    ) {
        if (args.isEmpty()) {
            AnswerRenderer.sendFailure(sender, messages.skillUsage(label))
            return
        }

        when (args[0].lowercase()) {
            "list" -> listSkills(sender)
            "view" -> viewSkill(sender, args.drop(1))
            "delete" -> deleteSkill(sender, args.drop(1))
            "create" -> createOrContinueSkill(sender, label, args.drop(1))
            "confirm" -> confirmSkill(sender, label)
            "cancel" -> cancelDraft(sender)
            "status" -> showDraftStatus(sender, label)
            else -> AnswerRenderer.sendFailure(sender, messages.skillUsage(label))
        }
    }

    fun handlePrivateChat(
        player: Player,
        message: String,
        label: String = "a4m",
    ) {
        if (!player.isOnline) {
            sessionStore.remove(player.uniqueId)
            return
        }
        val session = sessionStore.get(player.uniqueId)
        if (session == null) {
            AnswerRenderer.sendFailure(player, messages.skillSessionExpired())
            return
        }
        val commandLabel = session.commandLabel.ifBlank { label }
        if (!player.hasPermission(Permissions.ADMIN)) {
            sessionStore.remove(player.uniqueId)
            AnswerRenderer.sendFailure(player, messages.noPermission())
            return
        }
        if (session.inFlight) {
            AnswerRenderer.sendQueued(
                player,
                if (session.isPendingStart) messages.skillChatStartPending() else messages.skillChatRequestInFlight(),
            )
            return
        }
        if (session.isPendingStart) {
            AnswerRenderer.sendQueued(player, messages.skillChatStartPending())
            return
        }
        if (session.isDraftReady) {
            AnswerRenderer.sendInfo(
                player,
                listOf(
                    messages.skillCreationConfirmHint(commandLabel),
                    messages.skillDraftCancelHint(commandLabel),
                ),
            )
            return
        }

        val answer = message.trim()
        if (answer.isBlank()) {
            AnswerRenderer.sendFailure(player, messages.skillChatEmptyResponse())
            AnswerRenderer.sendInfo(player, listOf(messages.skillChatContinueHint(commandLabel)))
            return
        }

        continuePlayerSession(
            player = player,
            label = commandLabel,
            message = answer,
        )
    }

    fun expirePlayerSession(player: Player) {
        sessionStore.remove(player.uniqueId)
        AnswerRenderer.sendFailure(player, messages.skillSessionExpired())
    }

    fun removePlayerSession(player: Player) {
        sessionStore.remove(player.uniqueId)
    }

    fun tabComplete(partial: String): MutableList<String> {
        return listOf("list", "view", "delete", "create", "confirm", "cancel", "status")
            .filter { option -> option.startsWith(partial, ignoreCase = true) }
            .toMutableList()
    }

    private fun listSkills(sender: CommandSender) {
        AnswerRenderer.sendQueued(sender, messages.skillListQueued())
        asyncRunner.run(
            sender = sender,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.listSkills() },
        ) { skills ->
            AnswerRenderer.sendInfo(sender, renderSkillList(skills))
        }
    }

    private fun viewSkill(
        sender: CommandSender,
        args: List<String>,
    ) {
        val skillName = args.firstOrNull()?.trim().orEmpty()
        if (skillName.isBlank()) {
            AnswerRenderer.sendFailure(sender, messages.skillNameBlank())
            return
        }
        AnswerRenderer.sendQueued(sender, messages.skillViewQueued(skillName))
        asyncRunner.run(
            sender = sender,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.getSkill(skillName) },
        ) { detail ->
            AnswerRenderer.sendInfo(sender, renderSkillDetail(detail))
        }
    }

    private fun deleteSkill(
        sender: CommandSender,
        args: List<String>,
    ) {
        val skillName = args.firstOrNull()?.trim().orEmpty()
        if (skillName.isBlank()) {
            AnswerRenderer.sendFailure(sender, messages.skillNameBlank())
            return
        }
        AnswerRenderer.sendQueued(sender, messages.skillDeleteQueued(skillName))
        asyncRunner.run(
            sender = sender,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.deleteSkill(skillName) },
        ) { result ->
            AnswerRenderer.sendInfo(
                sender,
                listOf(messages.skillDeleteResult(skillName, result.message, result.archivedPath)),
            )
        }
    }

    private fun createOrContinueSkill(
        sender: CommandSender,
        label: String,
        args: List<String>,
    ) {
        val message = args.joinToString(" ").trim()
        when (sender) {
            is Player -> createOrContinuePlayerSkill(sender, label, message)
            else -> createOrContinueConsoleSkill(sender, label, message)
        }
    }

    private fun createOrContinuePlayerSkill(
        player: Player,
        label: String,
        message: String,
    ) {
        val activeSession = sessionStore.get(player.uniqueId)
        if (activeSession == null) {
            if (message.isBlank()) {
                AnswerRenderer.sendFailure(player, messages.skillCreateUsage(label))
                return
            }
            startPlayerSession(player, label, message)
            return
        }

        if (activeSession.inFlight) {
            AnswerRenderer.sendQueued(
                player,
                if (activeSession.isPendingStart) messages.skillChatStartPending() else messages.skillChatRequestInFlight(),
            )
            return
        }
        if (activeSession.isPendingStart) {
            AnswerRenderer.sendQueued(player, messages.skillChatStartPending())
            return
        }
        if (activeSession.isDraftReady) {
            val commandLabel = activeSession.commandLabel.ifBlank { label }
            AnswerRenderer.sendInfo(
                player,
                listOf(
                    messages.skillCreationConfirmHint(commandLabel),
                    messages.skillDraftCancelHint(commandLabel),
                ),
            )
            return
        }

        val commandLabel = activeSession.commandLabel.ifBlank { label }
        AnswerRenderer.sendInfo(
            player,
            listOf(
                messages.skillDraftAlreadyActive(commandLabel),
                messages.skillChatContinueHint(commandLabel),
                messages.skillDraftCancelHint(commandLabel),
            ),
        )
    }

    private fun startPlayerSession(
        player: Player,
        label: String,
        requirement: String,
    ) {
        val token = sessionStore.startPending(player.uniqueId, commandLabel = label)
        AnswerRenderer.sendInfo(
            player,
            listOf(
                messages.skillChatModeEntered(),
                messages.skillChatPrivateNotice(),
            ),
        )
        AnswerRenderer.sendQueued(player, messages.skillCreateQueued())
        asyncRunner.run(
            sender = player,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.startCreation(requirement) },
            onFailure = {
                !sessionStore.failRequest(token, clearSession = true)
            },
        ) { result ->
            if (rememberPlayerSession(result, token = token)) {
                AnswerRenderer.sendInfo(player, renderSkillCreation(result, label, chatMode = true))
            }
        }
    }

    private fun continuePlayerSession(
        player: Player,
        label: String,
        message: String,
    ) {
        val request = when (val start = sessionStore.tryBeginRequest(player.uniqueId)) {
            is SkillCreationRequestStart.Started -> start
            is SkillCreationRequestStart.AlreadyInFlight -> {
                AnswerRenderer.sendQueued(player, messages.skillChatRequestInFlight())
                return
            }

            SkillCreationRequestStart.Expired,
            SkillCreationRequestStart.Missing,
            -> {
                AnswerRenderer.sendFailure(player, messages.skillSessionExpired())
                return
            }
        }
        val session = request.session
        if (session.isPendingStart) {
            sessionStore.failRequest(request.token, clearSession = false)
            AnswerRenderer.sendQueued(player, messages.skillChatStartPending())
            return
        }
        if (session.isDraftReady) {
            sessionStore.failRequest(request.token, clearSession = false)
            AnswerRenderer.sendInfo(
                player,
                listOf(
                    messages.skillCreationConfirmHint(label),
                    messages.skillDraftCancelHint(label),
                ),
            )
            return
        }

        AnswerRenderer.sendQueued(player, messages.skillCreateQueued())
        asyncRunner.run(
            sender = player,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.continueCreation(session.draftId, message) },
            onFailure = { failure ->
                if (isMissingDraftFailure(failure)) {
                    if (sessionStore.failRequest(request.token, clearSession = true)) {
                        AnswerRenderer.sendFailure(player, messages.skillSessionExpired())
                    }
                    true
                } else {
                    !sessionStore.failRequest(request.token, clearSession = false)
                }
            },
        ) { result ->
            if (rememberPlayerSession(result, session.draftId, request.token)) {
                AnswerRenderer.sendInfo(player, renderSkillCreation(result, label, chatMode = true))
            }
        }
    }

    private fun createOrContinueConsoleSkill(
        sender: CommandSender,
        label: String,
        message: String,
    ) {
        val senderKey = sender.name.lowercase()
        val activeDraft = consoleDrafts[senderKey]
        if (activeDraft == null) {
            if (message.isBlank()) {
                AnswerRenderer.sendFailure(sender, messages.skillCreateUsage(label))
                return
            }
            val pendingDraft = ConsoleSkillDraft("", SkillCreationStatus.UNKNOWN, inFlight = true)
            consoleDrafts[senderKey] = pendingDraft
            AnswerRenderer.sendQueued(sender, messages.skillCreateQueued())
            asyncRunner.run(
                sender = sender,
                fallbackMessage = messages.skillOperationFailedFallback(),
                task = { skillService.startCreation(message) },
                onFailure = {
                    !failConsoleDraft(senderKey, pendingDraft, clearDraft = true)
                },
            ) { result ->
                if (rememberConsoleDraft(senderKey, result, expectedDraft = pendingDraft)) {
                    AnswerRenderer.sendInfo(sender, renderSkillCreation(result, label, chatMode = false))
                }
            }
            return
        }

        if (activeDraft.inFlight) {
            AnswerRenderer.sendQueued(sender, messages.skillChatRequestInFlight())
            return
        }
        if (message.isBlank()) {
            AnswerRenderer.sendInfo(
                sender,
                listOf(
                    messages.skillDraftAlreadyActive(label),
                    messages.skillCreationContinueHint(label),
                    messages.skillCreationConfirmHint(label),
                ),
            )
            return
        }
        if (activeDraft.status == SkillCreationStatus.DRAFT_READY) {
            AnswerRenderer.sendInfo(
                sender,
                listOf(
                    messages.skillCreationConfirmHint(label),
                    messages.skillDraftCancelHint(label),
                ),
            )
            return
        }

        val requestDraft = activeDraft.copy(inFlight = true)
        if (!consoleDrafts.replace(senderKey, activeDraft, requestDraft)) {
            AnswerRenderer.sendQueued(sender, messages.skillChatRequestInFlight())
            return
        }
        AnswerRenderer.sendQueued(sender, messages.skillCreateQueued())
        asyncRunner.run(
            sender = sender,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.continueCreation(activeDraft.draftId, message) },
            onFailure = { failure ->
                if (isMissingDraftFailure(failure)) {
                    if (failConsoleDraft(senderKey, requestDraft, clearDraft = true)) {
                        AnswerRenderer.sendFailure(sender, messages.skillSessionExpired())
                    }
                    true
                } else {
                    !failConsoleDraft(senderKey, requestDraft, clearDraft = false)
                }
            },
        ) { result ->
            if (rememberConsoleDraft(senderKey, result, activeDraft.draftId, requestDraft)) {
                AnswerRenderer.sendInfo(sender, renderSkillCreation(result, label, chatMode = false))
            }
        }
    }

    private fun confirmSkill(
        sender: CommandSender,
        label: String,
    ) {
        when (sender) {
            is Player -> confirmPlayerSkill(sender, label)
            else -> confirmConsoleSkill(sender, label)
        }
    }

    private fun confirmPlayerSkill(
        player: Player,
        label: String,
    ) {
        val session = sessionStore.get(player.uniqueId)
        if (session == null) {
            AnswerRenderer.sendFailure(player, messages.skillNoActiveDraft())
            AnswerRenderer.sendInfo(player, listOf(messages.skillCreateUsage(label)))
            return
        }
        if (session.inFlight) {
            AnswerRenderer.sendQueued(
                player,
                if (session.isPendingStart) messages.skillChatStartPending() else messages.skillChatRequestInFlight(),
            )
            return
        }
        if (session.isPendingStart) {
            AnswerRenderer.sendQueued(player, messages.skillChatStartPending())
            return
        }
        if (!session.isDraftReady) {
            AnswerRenderer.sendFailure(player, messages.skillConfirmNotReady())
            AnswerRenderer.sendInfo(
                player,
                listOf(messages.skillChatContinueHint(session.commandLabel.ifBlank { label })),
            )
            return
        }

        val request = when (val start = sessionStore.tryBeginRequest(player.uniqueId)) {
            is SkillCreationRequestStart.Started -> start
            is SkillCreationRequestStart.AlreadyInFlight -> {
                AnswerRenderer.sendQueued(player, messages.skillChatRequestInFlight())
                return
            }

            SkillCreationRequestStart.Expired,
            SkillCreationRequestStart.Missing,
            -> {
                AnswerRenderer.sendFailure(player, messages.skillSessionExpired())
                return
            }
        }
        val requestSession = request.session
        if (!requestSession.isDraftReady) {
            sessionStore.failRequest(request.token, clearSession = false)
            AnswerRenderer.sendFailure(player, messages.skillConfirmNotReady())
            AnswerRenderer.sendInfo(
                player,
                listOf(messages.skillChatContinueHint(requestSession.commandLabel.ifBlank { label })),
            )
            return
        }

        AnswerRenderer.sendQueued(player, messages.skillCreateQueued())
        asyncRunner.run(
            sender = player,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.confirmCreation(requestSession.draftId) },
            onFailure = { failure ->
                if (isMissingDraftFailure(failure)) {
                    if (sessionStore.failRequest(request.token, clearSession = true)) {
                        AnswerRenderer.sendFailure(player, messages.skillSessionExpired())
                    }
                    true
                } else {
                    !sessionStore.failRequest(request.token, clearSession = false)
                }
            },
        ) { result ->
            if (rememberPlayerSession(result, requestSession.draftId, request.token)) {
                AnswerRenderer.sendInfo(player, renderSkillCreation(result, label, chatMode = true))
            }
        }
    }

    private fun confirmConsoleSkill(
        sender: CommandSender,
        label: String,
    ) {
        val senderKey = sender.name.lowercase()
        val activeDraft = consoleDrafts[senderKey]
        if (activeDraft == null) {
            AnswerRenderer.sendFailure(sender, messages.skillNoActiveDraft())
            AnswerRenderer.sendInfo(sender, listOf(messages.skillCreateUsage(label)))
            return
        }
        if (activeDraft.inFlight) {
            AnswerRenderer.sendQueued(sender, messages.skillChatRequestInFlight())
            return
        }
        if (activeDraft.status != SkillCreationStatus.DRAFT_READY) {
            AnswerRenderer.sendFailure(sender, messages.skillConfirmNotReady())
            AnswerRenderer.sendInfo(sender, listOf(messages.skillCreationContinueHint(label)))
            return
        }

        val requestDraft = activeDraft.copy(inFlight = true)
        if (!consoleDrafts.replace(senderKey, activeDraft, requestDraft)) {
            AnswerRenderer.sendQueued(sender, messages.skillChatRequestInFlight())
            return
        }
        AnswerRenderer.sendQueued(sender, messages.skillCreateQueued())
        asyncRunner.run(
            sender = sender,
            fallbackMessage = messages.skillOperationFailedFallback(),
            task = { skillService.confirmCreation(activeDraft.draftId) },
            onFailure = { failure ->
                if (isMissingDraftFailure(failure)) {
                    if (failConsoleDraft(senderKey, requestDraft, clearDraft = true)) {
                        AnswerRenderer.sendFailure(sender, messages.skillSessionExpired())
                    }
                    true
                } else {
                    !failConsoleDraft(senderKey, requestDraft, clearDraft = false)
                }
            },
        ) { result ->
            if (rememberConsoleDraft(senderKey, result, activeDraft.draftId, requestDraft)) {
                AnswerRenderer.sendInfo(sender, renderSkillCreation(result, label, chatMode = false))
            }
        }
    }

    private fun cancelDraft(sender: CommandSender) {
        when (sender) {
            is Player -> sessionStore.remove(sender.uniqueId)
            else -> consoleDrafts.remove(sender.name.lowercase())
        }
        AnswerRenderer.sendInfo(sender, listOf(messages.skillDraftCancelled()))
    }

    private fun showDraftStatus(
        sender: CommandSender,
        label: String,
    ) {
        when (sender) {
            is Player -> {
                when (val lookup = sessionStore.lookup(sender.uniqueId)) {
                    is SkillCreationSessionLookup.Active -> {
                        AnswerRenderer.sendInfo(
                            sender,
                            renderDraftStatus(
                                draftId = lookup.session.draftId,
                                status = lookup.session.status,
                                inFlight = lookup.session.inFlight,
                                label = lookup.session.commandLabel.ifBlank { label },
                                chatMode = true,
                            ),
                        )
                    }

                    SkillCreationSessionLookup.Expired -> {
                        sessionStore.remove(sender.uniqueId)
                        AnswerRenderer.sendFailure(sender, messages.skillSessionExpired())
                    }

                    SkillCreationSessionLookup.Missing -> {
                        AnswerRenderer.sendInfo(sender, listOf(messages.skillStatusNoActive()))
                    }
                }
            }

            else -> {
                val draft = consoleDrafts[sender.name.lowercase()]
                if (draft == null) {
                    AnswerRenderer.sendInfo(sender, listOf(messages.skillStatusNoActive()))
                    return
                }
                AnswerRenderer.sendInfo(
                    sender,
                    renderDraftStatus(
                        draftId = draft.draftId,
                        status = draft.status,
                        inFlight = draft.inFlight,
                        label = label,
                        chatMode = false,
                    ),
                )
            }
        }
    }

    private fun renderDraftStatus(
        draftId: String,
        status: SkillCreationStatus,
        inFlight: Boolean,
        label: String,
        chatMode: Boolean,
    ): List<String> {
        return buildList {
            add(messages.skillStatusHeader())
            add(messages.skillStatusLine(status.name.lowercase(), inFlight, draftId))
            when {
                inFlight -> add(messages.skillChatRequestInFlight())
                chatMode && status == SkillCreationStatus.NEEDS_CLARIFICATION -> {
                    add(messages.skillChatContinueHint(label))
                    add(messages.skillDraftCancelHint(label))
                }

                !chatMode && status == SkillCreationStatus.NEEDS_CLARIFICATION -> {
                    add(messages.skillCreationContinueHint(label))
                    add(messages.skillDraftCancelHint(label))
                }

                status == SkillCreationStatus.DRAFT_READY -> {
                    add(messages.skillCreationConfirmHint(label))
                    add(messages.skillDraftCancelHint(label))
                }

                else -> add(messages.skillCreateUsage(label))
            }
        }
    }

    private fun renderSkillList(skills: List<SkillSummary>): List<String> {
        if (skills.isEmpty()) {
            return listOf(messages.skillListEmpty())
        }
        return buildList {
            add(messages.skillListHeader(skills.size))
            skills.forEach { skill ->
                add(messages.skillListItem(skill))
            }
        }
    }

    private fun renderSkillDetail(detail: SkillDetail): List<String> {
        return buildList {
            add(messages.skillViewHeader(detail.summary))
            add(messages.skillViewDescription(detail.summary.description))
            if (detail.summary.diagnostics.isNotEmpty()) {
                add(messages.skillViewDiagnostics(detail.summary.diagnostics.joinToString("; ")))
            }
            add(messages.skillViewContentHeader())
            addAll(chunkChatText(detail.content))
        }
    }

    private fun renderSkillCreation(
        result: SkillCreationResult,
        label: String,
        chatMode: Boolean,
    ): List<String> {
        return buildList {
            if (!(chatMode && result.status == SkillCreationStatus.NEEDS_CLARIFICATION)) {
                result.message.takeIf(String::isNotBlank)?.let { message ->
                    add(messages.skillCreationMessage(message))
                }
            }

            when (result.status) {
                SkillCreationStatus.NEEDS_CLARIFICATION -> {
                    add(messages.skillCreationQuestionsHeader())
                    result.questions.forEachIndexed { index, question ->
                        add(messages.skillCreationQuestion(index + 1, question))
                    }
                    add(if (chatMode) messages.skillChatContinueHint(label) else messages.skillCreationContinueHint(label))
                }

                SkillCreationStatus.DRAFT_READY -> {
                    val skillName = result.skill?.name ?: result.draftId
                    add(messages.skillCreationDraftReady(skillName))
                    result.skill?.let { skill ->
                        add(messages.skillListItem(skill))
                    }
                    if (result.content.isNotBlank()) {
                        add(messages.skillViewContentHeader())
                        addAll(chunkChatText(result.content))
                    }
                    add(messages.skillCreationConfirmHint(label))
                    add(messages.skillDraftCancelHint(label))
                }

                SkillCreationStatus.INSTALLED -> {
                    val skillName = result.skill?.name ?: result.draftId
                    add(messages.skillCreationInstalled(skillName))
                    if (chatMode) {
                        add(messages.skillChatModeExited())
                    }
                }

                SkillCreationStatus.UNKNOWN -> {
                    add(messages.skillCreationMessage(result.rawStatus.ifBlank { messages.unknown() }))
                }
            }

            if (result.diagnostics.isNotEmpty()) {
                add(messages.skillCreationDiagnosticsHeader())
                result.diagnostics.forEach { diagnostic -> add(diagnostic) }
            }
        }.ifEmpty {
            listOf(messages.skillCreationMessage(messages.emptyValue()))
        }
    }

    private fun rememberPlayerSession(
        result: SkillCreationResult,
        fallbackDraftId: String? = null,
        token: SkillCreationRequestToken,
    ): Boolean {
        return when (result.status) {
            SkillCreationStatus.NEEDS_CLARIFICATION,
            SkillCreationStatus.DRAFT_READY,
            SkillCreationStatus.UNKNOWN,
            -> {
                val draftId = result.draftId.ifBlank { fallbackDraftId.orEmpty() }
                if (draftId.isBlank()) {
                    sessionStore.failRequest(token, clearSession = true)
                } else {
                    sessionStore.completeRequest(token, draftId, result.status)
                }
            }

            SkillCreationStatus.INSTALLED -> sessionStore.completeRequest(token, result.draftId, result.status)
        }
    }

    private fun rememberConsoleDraft(
        senderKey: String,
        result: SkillCreationResult,
        fallbackDraftId: String? = null,
        expectedDraft: ConsoleSkillDraft,
    ): Boolean {
        if (consoleDrafts[senderKey] != expectedDraft) {
            return false
        }
        return when (result.status) {
            SkillCreationStatus.NEEDS_CLARIFICATION,
            SkillCreationStatus.DRAFT_READY,
            SkillCreationStatus.UNKNOWN,
            -> {
                val draftId = result.draftId.ifBlank { fallbackDraftId.orEmpty() }
                if (draftId.isBlank()) {
                    consoleDrafts.remove(senderKey, expectedDraft)
                } else {
                    consoleDrafts.replace(
                        senderKey,
                        expectedDraft,
                        expectedDraft.copy(
                            draftId = draftId,
                            status = result.status,
                            inFlight = false,
                        ),
                    )
                }
            }

            SkillCreationStatus.INSTALLED -> consoleDrafts.remove(senderKey, expectedDraft)
        }
    }

    private fun failConsoleDraft(
        senderKey: String,
        expectedDraft: ConsoleSkillDraft,
        clearDraft: Boolean,
    ): Boolean {
        if (consoleDrafts[senderKey] != expectedDraft) {
            return false
        }
        if (clearDraft) {
            return consoleDrafts.remove(senderKey, expectedDraft)
        }
        return consoleDrafts.replace(senderKey, expectedDraft, expectedDraft.copy(inFlight = false))
    }

    private fun isMissingDraftFailure(failure: Throwable): Boolean {
        val message = failure.message?.lowercase().orEmpty()
        return "draft" in message && ("not found" in message || "expired" in message)
    }

    private fun chunkChatText(
        text: String,
        maxChunkLength: Int = 240,
    ): List<String> {
        return text.lineSequence()
            .flatMap { line -> splitLine(line.trimEnd(), maxChunkLength).asSequence() }
            .filter(String::isNotBlank)
            .toList()
            .ifEmpty { listOf(messages.emptyValue()) }
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

    private data class ConsoleSkillDraft(
        val draftId: String,
        val status: SkillCreationStatus,
        val inFlight: Boolean = false,
        val nonce: Long = System.nanoTime(),
    )
}
