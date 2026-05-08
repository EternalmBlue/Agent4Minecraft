package me.eternalblue.agent4minecraft.skill

import me.eternalblue.agent4minecraft.backend.BackendClient
import me.eternalblue.agent4minecraft.domain.SkillCreationResult
import me.eternalblue.agent4minecraft.domain.SkillDeleteResult
import me.eternalblue.agent4minecraft.domain.SkillDetail
import me.eternalblue.agent4minecraft.domain.SkillScope
import me.eternalblue.agent4minecraft.domain.SkillSummary
import me.eternalblue.agent4minecraft.i18n.PluginMessages

class SkillService(
    private val backendClient: BackendClient,
    private val serverId: String,
    private val serverInstanceId: String,
    private val messages: PluginMessages,
) {
    fun listSkills(): List<SkillSummary> {
        return backendClient.listSkills(serverId, serverInstanceId)
            .sortedWith(compareBy<SkillSummary>({ scopeSortOrder(it.scope) }, { it.name.lowercase() }))
    }

    fun getSkill(skillName: String): SkillDetail {
        return backendClient.getSkill(
            serverId = serverId,
            serverInstanceId = serverInstanceId,
            skillName = normalizeSkillName(skillName),
        )
    }

    fun deleteSkill(skillName: String): SkillDeleteResult {
        return backendClient.deleteSkill(
            serverId = serverId,
            serverInstanceId = serverInstanceId,
            skillName = normalizeSkillName(skillName),
        )
    }

    fun startCreation(initialRequirement: String): SkillCreationResult {
        val requirement = initialRequirement.trim()
        require(requirement.isNotEmpty()) {
            messages.skillCreateRequirementBlank()
        }
        return backendClient.startSkillCreation(
            serverId = serverId,
            serverInstanceId = serverInstanceId,
            initialRequirement = requirement,
        )
    }

    fun continueCreation(
        draftId: String,
        userMessage: String,
    ): SkillCreationResult {
        val message = userMessage.trim()
        require(draftId.isNotBlank()) {
            messages.skillNoActiveDraft()
        }
        require(message.isNotEmpty()) {
            messages.skillCreateResponseBlank()
        }
        return backendClient.continueSkillCreation(
            serverId = serverId,
            serverInstanceId = serverInstanceId,
            draftId = draftId,
            userMessage = message,
        )
    }

    fun confirmCreation(draftId: String): SkillCreationResult {
        require(draftId.isNotBlank()) {
            messages.skillNoActiveDraft()
        }
        return backendClient.confirmSkillCreation(
            serverId = serverId,
            serverInstanceId = serverInstanceId,
            draftId = draftId,
        )
    }

    private fun normalizeSkillName(skillName: String): String {
        return skillName.trim().also { normalized ->
            require(normalized.isNotEmpty()) {
                messages.skillNameBlank()
            }
        }
    }

    private fun scopeSortOrder(scope: SkillScope): Int {
        return when (scope) {
            SkillScope.OFFICIAL -> 0
            SkillScope.GLOBAL -> 1
            SkillScope.SERVER -> 2
            SkillScope.UNKNOWN -> 3
        }
    }
}
