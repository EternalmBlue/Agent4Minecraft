package me.eternalblue.agent4minecraft.config

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginSettingsLoaderTest {
    @Test
    fun `loads defaults from base deadline when specific deadlines are absent`() {
        val configuration = YamlConfiguration().apply {
            set("plugin.debug", true)
            set("qa.rateLimitSeconds", 5)
            set("backend.port", 50051)
            set("backend.authToken", "secret")
            set("backend.useTls", false)
            set("backend.deadlineMillis", 12_000L)
            set("backend.maxChunkBytes", 131_072)
        }

        val settings = PluginSettingsLoader.load(configuration, Path.of("F:/servers/lobby-1"))

        assertEquals("lobby-1", settings.serverId)
        assertEquals(true, settings.debugLogging)
        assertEquals("zh_CN", settings.language.configValue)
        assertEquals(5L, settings.qa.rateLimitSeconds)
        assertEquals("127.0.0.1", settings.backend.host)
        assertEquals("secret", settings.backend.authToken)
        assertEquals(12_000L, settings.backend.deadlineMillis)
        assertEquals(120_000L, settings.backend.askDeadlineMillis)
        assertEquals(12_000L, settings.backend.syncDeadlineMillis)
        assertEquals(131_072, settings.backend.maxChunkBytes)
    }

    @Test
    fun `uses explicit ask deadline when configured`() {
        val configuration = YamlConfiguration().apply {
            set("backend.authToken", "secret")
            set("backend.deadlineMillis", 12_000L)
            set("backend.askDeadlineMillis", 45_000L)
        }

        val settings = PluginSettingsLoader.load(configuration, Path.of("F:/servers/lobby-1"))

        assertEquals(45_000L, settings.backend.askDeadlineMillis)
    }

    @Test
    fun `loads explicit english language`() {
        val configuration = YamlConfiguration().apply {
            set("plugin.language", "en_US")
            set("backend.authToken", "secret")
        }

        val settings = PluginSettingsLoader.load(configuration, Path.of("F:/servers/lobby-1"))

        assertEquals("en_US", settings.language.configValue)
    }

    @Test
    fun `rejects blank backend auth token`() {
        val configuration = YamlConfiguration().apply {
            set("backend.port", 50051)
            set("backend.authToken", "   ")
            set("backend.deadlineMillis", 10_000L)
            set("backend.maxChunkBytes", 1024)
        }

        assertFailsWith<IllegalArgumentException> {
            PluginSettingsLoader.load(configuration, Path.of("F:/servers/lobby-1"))
        }
    }

    @Test
    fun `uses explicit server id when configured`() {
        val configuration = YamlConfiguration().apply {
            set("server.id", "production-lobby")
            set("backend.authToken", "secret")
        }

        val settings = PluginSettingsLoader.load(configuration, Path.of("F:/servers/lobby-1"))

        assertEquals("production-lobby", settings.serverId)
    }
}
