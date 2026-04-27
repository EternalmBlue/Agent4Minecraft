package me.eternalblue.agent4minecraft.bootstrap

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerInstanceIdStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `load or create persists a stable server instance id`() {
        val first = ServerInstanceIdStore.loadOrCreate(tempDir)
        val second = ServerInstanceIdStore.loadOrCreate(tempDir)

        assertEquals(first, second)
        assertTrue(Files.isRegularFile(tempDir.resolve("server-instance-id.txt")))
    }
}
