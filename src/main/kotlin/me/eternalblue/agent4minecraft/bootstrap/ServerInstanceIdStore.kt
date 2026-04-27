package me.eternalblue.agent4minecraft.bootstrap

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

object ServerInstanceIdStore {
    private const val FILE_NAME = "server-instance-id.txt"

    fun loadOrCreate(dataFolder: Path): String {
        val normalizedDataFolder = dataFolder.toAbsolutePath().normalize()
        val identityPath = normalizedDataFolder.resolve(FILE_NAME)

        if (Files.isRegularFile(identityPath)) {
            val existing = Files.readString(identityPath, StandardCharsets.UTF_8).trim()
            if (existing.isNotBlank()) {
                return existing
            }
        }

        Files.createDirectories(normalizedDataFolder)
        val generated = UUID.randomUUID().toString()
        try {
            Files.writeString(identityPath, "$generated\n", StandardCharsets.UTF_8)
        } catch (exception: IOException) {
            throw IOException("Could not persist server instance id at $identityPath", exception)
        }
        return generated
    }
}
