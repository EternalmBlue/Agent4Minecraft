package me.eternalblue.agent4minecraft.transfer

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class ChecksumCache {
    private val entries = ConcurrentHashMap<String, ChecksumEntry>()

    fun sha256(
        path: Path,
        size: Long,
        lastModifiedEpochMillis: Long,
    ): String {
        val key = path.toAbsolutePath().normalize().toString()
        val cached = entries[key]
        if (cached != null &&
            cached.size == size &&
            cached.lastModifiedEpochMillis == lastModifiedEpochMillis
        ) {
            return cached.sha256
        }

        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }

        val sha256 = digest.digest().joinToString("") { byte ->
            "%02x".format(byte)
        }
        entries[key] = ChecksumEntry(size, lastModifiedEpochMillis, sha256)
        return sha256
    }

    private data class ChecksumEntry(
        val size: Long,
        val lastModifiedEpochMillis: Long,
        val sha256: String,
    )
}
