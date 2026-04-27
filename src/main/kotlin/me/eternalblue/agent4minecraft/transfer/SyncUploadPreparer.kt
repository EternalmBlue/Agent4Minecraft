package me.eternalblue.agent4minecraft.transfer

import me.eternalblue.agent4minecraft.domain.UploadFile
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Comparator

class SyncUploadPreparer(
    serverRoot: Path,
    private val checksumCache: ChecksumCache,
    private val redactor: SensitiveConfigRedactor,
    redactedUploadRoot: Path,
) {
    private val normalizedServerRoot: Path = serverRoot.toAbsolutePath().normalize()
    private val normalizedRedactedUploadRoot: Path = redactedUploadRoot.toAbsolutePath().normalize()

    constructor(
        serverRoot: Path,
        checksumCache: ChecksumCache,
    ) : this(
        serverRoot = serverRoot,
        checksumCache = checksumCache,
        redactor = SensitiveConfigRedactor(),
        redactedUploadRoot = serverRoot.resolve(".agent4minecraft-sync-cache").resolve("redacted"),
    )

    fun prepare(): List<UploadFile> {
        cleanupTemporaryFiles()
        val files = mutableListOf<UploadFile>()
        files += scanPluginConfigs()
        files += scanCoreServerConfigs()
        return files.distinctBy(UploadFile::relativePath).sortedBy(UploadFile::relativePath)
    }

    fun cleanupTemporaryFiles() {
        if (!Files.exists(normalizedRedactedUploadRoot)) {
            return
        }
        Files.walk(normalizedRedactedUploadRoot).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { path ->
                    Files.deleteIfExists(path)
                }
        }
    }

    private fun scanPluginConfigs(): List<UploadFile> {
        val pluginRoot = normalizedServerRoot.resolve("plugins")
        if (!Files.isDirectory(pluginRoot)) {
            return emptyList()
        }

        val files = mutableListOf<UploadFile>()
        Files.walk(pluginRoot).use { stream ->
            stream
                .filter { path ->
                    val normalizedPath = path.toAbsolutePath().normalize()
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isSymbolicLink(path) &&
                        !normalizedPath.startsWith(normalizedRedactedUploadRoot) &&
                        ALLOWED_PLUGIN_EXTENSIONS.contains(path.extensionLowercase())
                }
                .sorted()
                .forEach { path ->
                    files += buildUploadFile(path)
                }
        }
        return files
    }

    private fun scanCoreServerConfigs(): List<UploadFile> {
        if (!Files.isDirectory(normalizedServerRoot)) {
            return emptyList()
        }

        val files = mutableListOf<UploadFile>()
        Files.list(normalizedServerRoot).use { stream ->
            stream
                .filter { path ->
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isSymbolicLink(path) &&
                        isCoreServerConfig(path.fileName.toString())
                }
                .sorted()
                .forEach { path ->
                    files += buildUploadFile(path)
                }
        }
        return files
    }

    private fun buildUploadFile(path: Path): UploadFile {
        val normalizedPath = path.toAbsolutePath().normalize()
        val sourceLastModified = Files.getLastModifiedTime(normalizedPath, LinkOption.NOFOLLOW_LINKS).toMillis()
        val relativePath = normalizedServerRoot.relativize(normalizedPath).toString().replace(File.separatorChar, '/')
        val redactionResult = redactor.redact(readUtf8Lenient(normalizedPath))
        val uploadSourcePath = if (redactionResult.redacted) {
            writeRedactedCopy(relativePath, redactionResult.content)
        } else {
            normalizedPath
        }
        val uploadSize = Files.size(uploadSourcePath)
        val uploadLastModified = Files.getLastModifiedTime(uploadSourcePath, LinkOption.NOFOLLOW_LINKS).toMillis()
        return UploadFile(
            relativePath = relativePath,
            sourcePath = normalizedPath,
            uploadSourcePath = uploadSourcePath,
            uploadSize = uploadSize,
            uploadSha256 = checksumCache.sha256(uploadSourcePath, uploadSize, uploadLastModified),
            sourceLastModifiedEpochMillis = sourceLastModified,
            redacted = redactionResult.redacted,
        )
    }

    private fun readUtf8Lenient(path: Path): String {
        return String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    }

    private fun writeRedactedCopy(
        relativePath: String,
        content: String,
    ): Path {
        val target = normalizedRedactedUploadRoot.resolve(relativePath.replace('/', File.separatorChar)).normalize()
        require(target.startsWith(normalizedRedactedUploadRoot)) {
            "Redacted upload path escapes the configured cache directory."
        }
        Files.createDirectories(target.parent)
        Files.writeString(target, content, StandardCharsets.UTF_8)
        return target
    }

    private fun isCoreServerConfig(fileName: String): Boolean {
        val normalized = fileName.lowercase()
        return normalized in CORE_SERVER_CONFIGS || normalized.matches(PAPER_CONFIG_REGEX)
    }

    private fun Path.extensionLowercase(): String {
        val fileName = fileName.toString()
        val separator = fileName.lastIndexOf('.')
        if (separator < 0) {
            return ""
        }
        return fileName.substring(separator).lowercase()
    }

    companion object {
        private val ALLOWED_PLUGIN_EXTENSIONS = setOf(
            ".yml",
            ".yaml",
            ".json",
            ".properties",
            ".txt",
            ".md",
        )

        private val CORE_SERVER_CONFIGS = setOf(
            "server.properties",
            "bukkit.yml",
            "spigot.yml",
        )

        private val PAPER_CONFIG_REGEX = Regex("^paper.*\\.yml$")
    }
}
