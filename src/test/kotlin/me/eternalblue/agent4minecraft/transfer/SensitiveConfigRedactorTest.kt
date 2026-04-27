package me.eternalblue.agent4minecraft.transfer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveConfigRedactorTest {
    private val redactor = SensitiveConfigRedactor()

    @Test
    fun `redacts sensitive yaml properties and json fields`() {
        val result = redactor.redact(
            """
            password: hunter2 # keep comment
            username: steve
            api-key=abc123
            {"token": "secret-token", "enabled": true}
            # password: documented only
            """.trimIndent(),
        )

        assertTrue(result.redacted)
        assertContains(result.content, "password: \"\" # keep comment")
        assertContains(result.content, "username: steve")
        assertContains(result.content, "api-key=")
        assertContains(result.content, "\"token\": \"\"")
        assertContains(result.content, "# password: documented only")
        assertFalse(result.content.contains("hunter2"))
        assertFalse(result.content.contains("abc123"))
        assertFalse(result.content.contains("secret-token"))
    }

    @Test
    fun `redacts high risk inline tokens urls and pem blocks`() {
        val result = redactor.redact(
            """
            jdbc-url: jdbc:mysql://root:dbSecret@localhost:3306/minecraft?password=querySecret&useSSL=false
            auth-header: Bearer live-access-token
            copied-value: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwbGF5ZXIifQ.signature12345
            key: -----BEGIN PRIVATE KEY-----
            super-secret-key-material
            -----END PRIVATE KEY-----
            """.trimIndent(),
        )

        assertTrue(result.redacted)
        assertContains(result.content, "jdbc:mysql://root:[REDACTED]@localhost")
        assertContains(result.content, "?password=&useSSL=false")
        assertContains(result.content, "auth-header: \"\"")
        assertContains(result.content, "[REDACTED_JWT]")
        assertContains(result.content, "[REDACTED]")
        assertFalse(result.content.contains("dbSecret"))
        assertFalse(result.content.contains("querySecret"))
        assertFalse(result.content.contains("live-access-token"))
        assertFalse(result.content.contains("super-secret-key-material"))
    }

    @Test
    fun `leaves ordinary configuration unchanged`() {
        val input = """
            enabled: true
            max-homes=3
            motd: Welcome to the server
            uuid: 123e4567-e89b-12d3-a456-426614174000
        """.trimIndent()

        val result = redactor.redact(input)

        assertFalse(result.redacted)
        assertContains(result.content, "enabled: true")
        assertContains(result.content, "uuid: 123e4567-e89b-12d3-a456-426614174000")
    }
}
