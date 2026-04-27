package me.eternalblue.agent4minecraft.transfer

data class RedactionResult(
    val content: String,
    val redacted: Boolean,
)

class SensitiveConfigRedactor {
    fun redact(content: String): RedactionResult {
        val pemResult = redactPemBlocks(content)
        val lines = pemResult.content.split('\n')
        val hasTrailingNewline = pemResult.content.endsWith('\n')
        val effectiveLines = if (hasTrailingNewline) {
            lines.dropLast(1)
        } else {
            lines
        }

        var redacted = pemResult.redacted
        val rendered = effectiveLines.joinToString("\n") { line ->
            val lineResult = redactLine(line)
            redacted = redacted || lineResult.redacted
            lineResult.content
        } + if (hasTrailingNewline) "\n" else ""

        return RedactionResult(rendered, redacted)
    }

    private fun redactLine(rawLine: String): RedactionResult {
        val line = rawLine.removeSuffix("\r")
        val suffix = if (rawLine.endsWith("\r")) "\r" else ""
        if (isCommentLine(line)) {
            return RedactionResult(rawLine, redacted = false)
        }

        var redacted = false
        var current = line

        current = redactJsonFields(current).also { result ->
            redacted = redacted || result.redacted
        }.content

        current = redactAssignment(current).also { result ->
            redacted = redacted || result.redacted
        }.content

        current = replaceRegex(current, URL_CREDENTIAL_REGEX, "$1[REDACTED]$3").also { result ->
            redacted = redacted || result.redacted
        }.content

        current = replaceRegex(current, QUERY_PASSWORD_REGEX, "$1").also { result ->
            redacted = redacted || result.redacted
        }.content

        current = replaceRegex(current, BEARER_TOKEN_REGEX, "Bearer [REDACTED]").also { result ->
            redacted = redacted || result.redacted
        }.content

        current = replaceRegex(current, JWT_REGEX, "[REDACTED_JWT]").also { result ->
            redacted = redacted || result.redacted
        }.content

        return RedactionResult(current + suffix, redacted)
    }

    private fun redactJsonFields(line: String): RedactionResult {
        var changed = false
        val rendered = JSON_FIELD_REGEX.replace(line) { match ->
            val key = match.groupValues[2]
            if (!isSensitiveKey(key)) {
                match.value
            } else {
                changed = true
                match.groupValues[1] + "\"\"" + match.groupValues[4]
            }
        }
        return RedactionResult(rendered, changed)
    }

    private fun redactAssignment(line: String): RedactionResult {
        val match = ASSIGNMENT_REGEX.matchEntire(line) ?: return RedactionResult(line, false)
        val key = match.groupValues[3]
        if (!isSensitiveKey(key)) {
            return RedactionResult(line, false)
        }

        val separator = match.groupValues[6]
        val value = match.groupValues[8]
        val inlineComment = value.indexOfInlineComment()?.let(value::substring).orEmpty()
        val replacementValue = if (separator == "=") "" else "\"\""
        return RedactionResult(
            content = match.groupValues[1] +
                match.groupValues[2] +
                key +
                match.groupValues[4] +
                match.groupValues[5] +
                separator +
                match.groupValues[7] +
                replacementValue +
                inlineComment,
            redacted = true,
        )
    }

    private fun redactPemBlocks(content: String): RedactionResult {
        val lines = content.split('\n')
        val hasTrailingNewline = content.endsWith('\n')
        val effectiveLines = if (hasTrailingNewline) {
            lines.dropLast(1)
        } else {
            lines
        }

        val rendered = mutableListOf<String>()
        var inSensitiveBlock = false
        var blockRedacted = false
        var redacted = false

        for (line in effectiveLines) {
            if (!inSensitiveBlock) {
                val beginMatch = PEM_BEGIN_REGEX.find(line)
                if (beginMatch == null) {
                    rendered += line
                    continue
                }
                rendered += line
                rendered += "[REDACTED]"
                inSensitiveBlock = true
                blockRedacted = true
                redacted = true
                continue
            }

            if (PEM_END_REGEX.containsMatchIn(line)) {
                rendered += line
                inSensitiveBlock = false
                blockRedacted = false
            } else if (!blockRedacted) {
                rendered += "[REDACTED]"
                blockRedacted = true
            }
        }

        return RedactionResult(
            content = rendered.joinToString("\n") + if (hasTrailingNewline) "\n" else "",
            redacted = redacted,
        )
    }

    private fun isSensitiveKey(rawKey: String): Boolean {
        val key = rawKey
            .trim()
            .trim('"', '\'')
            .replace("[", "")
            .replace("]", "")
        return key.equals("auth", ignoreCase = true) || SENSITIVE_KEY_REGEX.containsMatchIn(key)
    }

    private fun isCommentLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith(";")
    }

    private fun replaceRegex(
        line: String,
        regex: Regex,
        replacement: String,
    ): RedactionResult {
        val rendered = regex.replace(line, replacement)
        return RedactionResult(rendered, rendered != line)
    }

    private fun String.indexOfInlineComment(): Int? {
        val hashIndex = indexOf(" #")
        if (hashIndex >= 0) {
            return hashIndex
        }
        val tabHashIndex = indexOf("\t#")
        if (tabHashIndex >= 0) {
            return tabHashIndex
        }
        return null
    }

    companion object {
        private val SENSITIVE_KEY_REGEX = Regex(
            pattern = "(^|[._-])(" +
                "password|passwd|pwd|secret|token|apikey|api[_-]?key|" +
                "access[_-]?key|private[_-]?key|credential|credentials|" +
                "authorization|auth[_-]?(token|key|header|secret|password)|mysql[_-]?password|" +
                "database[_-]?password|db[_-]?(password|pass|pwd)|passphrase" +
                ")($|[._-])",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        private val JSON_FIELD_REGEX = Regex(
            """("([^"\\]*(?:\\.[^"\\]*)*)"\s*:\s*)("(?:[^"\\]|\\.)*"|[^,\r\n}\]]+)(\s*,?)""",
        )
        private val ASSIGNMENT_REGEX = Regex(
            """^(\s*)(["']?)([A-Za-z0-9_.\-\[\]]+)(["']?)(\s*)([:=])(\s*)(.*)$""",
        )
        private val URL_CREDENTIAL_REGEX = Regex(
            """\b([A-Za-z][A-Za-z0-9+.-]*://[^:\s/@]+:)([^@\s/]+)(@)""",
        )
        private val QUERY_PASSWORD_REGEX = Regex(
            """(?i)([?&](?:password|passwd|pwd)=)[^&\s"'<>]+""",
        )
        private val BEARER_TOKEN_REGEX = Regex(
            """(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""",
        )
        private val JWT_REGEX = Regex(
            """\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\b""",
        )
        private val PEM_BEGIN_REGEX = Regex(
            """-----BEGIN [A-Z0-9 ]*(PRIVATE KEY|CERTIFICATE)[A-Z0-9 ]*-----""",
        )
        private val PEM_END_REGEX = Regex(
            """-----END [A-Z0-9 ]*(PRIVATE KEY|CERTIFICATE)[A-Z0-9 ]*-----""",
        )
    }
}
