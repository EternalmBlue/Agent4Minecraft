package me.eternalblue.agent4minecraft.i18n

import me.eternalblue.agent4minecraft.domain.AskProgress
import me.eternalblue.agent4minecraft.domain.SkillScope
import me.eternalblue.agent4minecraft.domain.SkillSummary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginMessagesTest {
    @Test
    fun `loads english templates from yaml`() {
        val messages = PluginMessageLoader.loadFromFiles(File("src/main/resources/lang/en_US.yml"))

        assertEquals("Usage: /askmc <question>", messages.askUsage("askmc"))
        assertEquals("Uploading 1/3 required files.", messages.syncUploading(uploaded = 1, total = 3))
        assertEquals(
            "Building the answer (12s), current step: searching plugin documentation",
            messages.askProgress(
                AskProgress("req-1", "retrieval", "Searching docs", 1_000L, 1),
                elapsedSeconds = 12,
            ),
        )
        assertEquals("Backend did not return an ask result.", messages.backendNoAskResult())
        assertEquals("Usage: /a4m skill <list|view|delete|create|confirm|cancel|status>", messages.skillUsage("a4m"))
        assertEquals("Skill creation session:", messages.skillStatusHeader())
        assertEquals("Entered private skill creation mode.", messages.skillChatModeEntered())
        assertEquals(
            "Reply directly in chat. Your messages will not be shown to other players.",
            messages.skillChatPrivateNotice(),
        )
        assertEquals(
            "Your previous skill creation reply is still being processed. Please wait.",
            messages.skillChatRequestInFlight(),
        )
        assertEquals(
            "Backend does not advertise required capability skills_v1. Upgrade AgentForMc before using this plugin build.",
            messages.startupSkillsUnsupported("skills_v1"),
        )
        assertEquals(
            "[server] economy-helper - Answers economy questions | valid=true, readonly=false, " +
                "deletable=true, diagnostics=<empty>",
            messages.skillListItem(
                SkillSummary(
                    scope = SkillScope.SERVER,
                    name = "economy-helper",
                    description = "Answers economy questions",
                    valid = true,
                    readonly = false,
                    deletable = true,
                ),
            ),
        )
    }

    @Test
    fun `loads simplified chinese templates from yaml`() {
        val messages = PluginMessageLoader.loadFromFiles(
            selectedFile = File("src/main/resources/lang/zh_CN.yml"),
            fallbackFile = File("src/main/resources/lang/en_US.yml"),
        )

        assertEquals("用法: /askmc <问题>", messages.askUsage("askmc"))
        assertEquals("正在上传 1/3 个需要同步的文件。", messages.syncUploading(uploaded = 1, total = 3))
        assertEquals(
            "正在构建回答（12s），当前步骤：检索插件文档",
            messages.askProgress(
                AskProgress("req-1", "retrieval", "正在检索文档", 1_000L, 1),
                elapsedSeconds = 12,
            ),
        )
        assertEquals("用法: /a4m skill <list|view|delete|create|confirm|cancel|status>", messages.skillUsage("a4m"))
        assertEquals("Skill 创建会话:", messages.skillStatusHeader())
        assertEquals("本服", messages.skillScope(SkillScope.SERVER))
        assertEquals("已进入私有 Skill 创建模式。", messages.skillChatModeEntered())
        assertEquals("请直接在聊天栏回答。你的消息不会展示给其他玩家。", messages.skillChatPrivateNotice())
        assertEquals("上一条 Skill 创建回复正在处理，请稍等。", messages.skillChatRequestInFlight())
        assertEquals(
            "后端未声明必需能力 skills_v1。请先升级 AgentForMc 后端。",
            messages.startupSkillsUnsupported("skills_v1"),
        )
    }
}
