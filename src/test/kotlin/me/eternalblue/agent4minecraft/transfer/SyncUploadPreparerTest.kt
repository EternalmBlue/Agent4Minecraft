package me.eternalblue.agent4minecraft.transfer

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncUploadPreparerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scans plugin text configs and core server configs only`() {
        val pluginDir = Files.createDirectories(tempDir.resolve("plugins/TestPlugin"))
        Files.writeString(pluginDir.resolve("config.yml"), "enabled: true")
        Files.writeString(pluginDir.resolve("menus.json"), """{"menu":"main"}""")
        Files.writeString(pluginDir.resolve("notes.txt"), "hello")
        Files.write(pluginDir.resolve("plugin.jar"), byteArrayOf(0x01, 0x02))
        Files.writeString(tempDir.resolve("server.properties"), "motd=Agent4Minecraft")
        Files.writeString(tempDir.resolve("paper-global.yml"), "verbose: false")
        Files.write(tempDir.resolve("world.db"), byteArrayOf(0x03, 0x04))

        val uploadPreparer = SyncUploadPreparer(tempDir, ChecksumCache())
        val results = uploadPreparer.prepare()

        assertEquals(
            setOf(
                "paper-global.yml",
                "plugins/TestPlugin/config.yml",
                "plugins/TestPlugin/menus.json",
                "plugins/TestPlugin/notes.txt",
                "server.properties",
            ),
            results.map { it.relativePath }.toSet(),
        )
        assertTrue(results.all { it.uploadSha256.isNotBlank() })
        assertTrue(results.none { it.relativePath.endsWith(".jar") })
        assertTrue(results.none { it.relativePath.endsWith(".db") })
    }

    @Test
    fun `manifest metadata is based on redacted upload content`() {
        val pluginDir = Files.createDirectories(tempDir.resolve("plugins/TestPlugin"))
        val configPath = pluginDir.resolve("config.yml")
        Files.writeString(
            configPath,
            """
            username: steve
            password: hunter2
            """.trimIndent(),
        )

        val uploadPreparer = SyncUploadPreparer(
            serverRoot = tempDir,
            checksumCache = ChecksumCache(),
            redactor = SensitiveConfigRedactor(),
            redactedUploadRoot = tempDir.resolve("plugins/Agent4Minecraft/sync-cache/redacted"),
        )
        val result = uploadPreparer.prepare().single()
        val uploadedContent = Files.readString(result.uploadSourcePath)

        assertTrue(result.redacted)
        assertEquals(configPath.toAbsolutePath().normalize(), result.sourcePath)
        assertFalse(result.uploadSourcePath == result.sourcePath)
        assertEquals("username: steve\npassword: \"\"", uploadedContent)
        assertEquals(uploadedContent.toByteArray().size.toLong(), result.uploadSize)
        assertEquals(sha256(uploadedContent.toByteArray()), result.uploadSha256)
        assertTrue(Files.readString(configPath).contains("hunter2"))
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
