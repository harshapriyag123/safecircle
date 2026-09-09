package com.harshapriya.safecircle.domain

enum class PhraseSignal {
    NONE,
    CHECK_ON_ME,
    SILENT_ESCALATION
}

data class SafePhraseConfig(
    val yellowPhrase: String,
    val redPhrase: String,
)

object SafePhraseEngine {
    fun evaluate(input: String, config: SafePhraseConfig): PhraseSignal {
        val normalized = input.trim().lowercase()
        return when {
            normalized == config.redPhrase.trim().lowercase() -> PhraseSignal.SILENT_ESCALATION
            normalized == config.yellowPhrase.trim().lowercase() -> PhraseSignal.CHECK_ON_ME
            else -> PhraseSignal.NONE
        }
    }
}
