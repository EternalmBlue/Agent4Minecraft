package me.eternalblue.agent4minecraft.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginMessagesTest {
    @Test
    fun `loads english templates from yaml`() {
        val messages = PluginMessageLoader.loadFromFiles(File("src/main/resources/lang/en_US.yml"))

        assertEquals("Usage: /askmc <question>", messages.askUsage("askmc"))
        assertEquals("Uploading 1/3 required files.", messages.syncUploading(uploaded = 1, total = 3))
    }

    @Test
    fun `loads simplified chinese templates from yaml`() {
        val messages = PluginMessageLoader.loadFromFiles(
            selectedFile = File("src/main/resources/lang/zh_CN.yml"),
            fallbackFile = File("src/main/resources/lang/en_US.yml"),
        )

        assertEquals("用法: /askmc <问题>", messages.askUsage("askmc"))
        assertEquals("正在上传 1/3 个需要同步的文件。", messages.syncUploading(uploaded = 1, total = 3))
    }
}
