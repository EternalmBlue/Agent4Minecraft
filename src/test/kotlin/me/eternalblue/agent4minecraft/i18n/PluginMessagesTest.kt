package me.eternalblue.agent4minecraft.i18n

import me.eternalblue.agent4minecraft.domain.AskProgress
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
    }
}
