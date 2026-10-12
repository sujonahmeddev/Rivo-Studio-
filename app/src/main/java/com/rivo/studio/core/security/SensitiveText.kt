package com.rivo.studio.core.security

object SensitiveText {
    private val telegramToken = Regex("\\b\\d{6,}:[A-Za-z0-9_-]{20,}\\b")
    private val assignmentSecret = Regex(
        "(?i)(api[_-]?key|access[_-]?token|bot[_-]?token|password|secret)(\\s*[:=]\\s*[\\\"']?)[A-Za-z0-9._-]{8,}",
    )

    fun redact(text: String, exactSecrets: Collection<String> = emptyList()): String {
        var result = text
        exactSecrets.filter(String::isNotBlank).sortedByDescending(String::length).forEach { secret ->
            result = result.replace(secret, "[credential redacted]")
        }
        result = telegramToken.replace(result, "[Telegram token redacted]")
        result = assignmentSecret.replace(result, "$1$2[redacted]")
        return result
    }
}
