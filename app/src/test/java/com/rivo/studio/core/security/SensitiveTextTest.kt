package com.rivo.studio.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextTest {
    @Test
    fun redactsCommonTokenAndKeyAssignments() {
        val source = "api_key='sk_test_abcdefghijklmnop' bot_token=123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"
        val safe = SensitiveText.redact(source)
        assertFalse(safe.contains("sk_test_abcdefghijklmnop"))
        assertFalse(safe.contains("123456:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"))
        assertTrue(safe.contains("[redacted]"))
        assertTrue(safe.contains("[Telegram token redacted]"))
    }

    @Test
    fun redactsExplicitProviderSecretsEvenWithoutRecognizedSyntax() {
        assertFalse(SensitiveText.redact("value: plain-secret-value", listOf("plain-secret-value")).contains("plain-secret-value"))
    }
}
