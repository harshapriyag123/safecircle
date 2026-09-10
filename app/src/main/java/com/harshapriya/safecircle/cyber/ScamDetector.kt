package com.harshapriya.safecircle.cyber

import java.net.URI
import java.util.Locale

data class ScamSignal(val label: String, val weight: Int)
data class ScamAssessment(
    val riskScore: Int,
    val riskLevel: String,
    val verdict: String,
    val signals: List<ScamSignal>
)

/**
 * Explainable on-device message risk scanner. It does not identify a sender or
 * guarantee that a message is genuine; it highlights common scam indicators.
 */
object ScamDetector {
    private val urlRegex = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly")

    fun analyze(raw: String): ScamAssessment {
        val text = raw.trim()
        if (text.isBlank()) {
            return ScamAssessment(0, "low", "Paste a message to analyze.", emptyList())
        }

        val lower = text.lowercase(Locale.US)
        val signals = mutableListOf<ScamSignal>()

        fun add(condition: Boolean, label: String, weight: Int) {
            if (condition) signals += ScamSignal(label, weight)
        }

        add(listOf("urgent", "immediately", "act now", "final warning", "within 24 hours").any(lower::contains),
            "Urgency or pressure language", 18)
        add(listOf("gift card", "bitcoin", "crypto", "wire transfer", "zelle", "cash app", "western union").any(lower::contains),
            "Unusual or irreversible payment request", 30)
        add(listOf("otp", "one-time password", "verification code", "password", "pin number", "security code").any(lower::contains),
            "Request for authentication credentials or codes", 24)
        add(listOf("account suspended", "account locked", "verify your account", "confirm your identity", "unauthorized transaction").any(lower::contains),
            "Account-threat or impersonation language", 18)
        add(listOf("you won", "prize", "lottery", "refund waiting", "claim reward").any(lower::contains),
            "Unexpected reward or refund claim", 18)
        add(listOf("do not tell", "keep this confidential", "secret transaction").any(lower::contains),
            "Secrecy request", 20)
        add(Regex("\\b\\d{3}[-. ]?\\d{3}[-. ]?\\d{4}\\b").containsMatchIn(text) && lower.contains("call"),
            "Unsolicited call-back number", 8)

        val urls = urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '}') }.toList()
        if (urls.isNotEmpty()) signals += ScamSignal("Contains a clickable link", 8)
        urls.forEach { url ->
            val host = runCatching { URI(url).host?.lowercase(Locale.US) }.getOrNull().orEmpty()
            if (host in shorteners) signals += ScamSignal("Uses a shortened link ($host)", 20)
            if (host.contains("xn--")) signals += ScamSignal("Internationalized/punycode domain", 15)
            if (host.count { it == '-' } >= 3) signals += ScamSignal("Unusually complex link domain", 8)
        }

        val exclamationCount = text.count { it == '!' }
        add(exclamationCount >= 3, "Excessive urgency punctuation", 6)

        val score = signals.sumOf { it.weight }.coerceIn(0, 100)
        val level = when {
            score >= 60 -> "high"
            score >= 30 -> "medium"
            else -> "low"
        }
        val verdict = when (level) {
            "high" -> "High risk: do not click links, send money, or share codes. Verify through an official channel."
            "medium" -> "Caution: verify the sender and request through a separate trusted channel."
            else -> "No common scam signals were detected, but this does not prove the message is genuine."
        }
        return ScamAssessment(score, level, verdict, signals.distinctBy { it.label })
    }
}
