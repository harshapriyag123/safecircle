package com.harshapriya.safecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SafePhraseEngineTest {
    private val config = SafePhraseConfig(
        yellowPhrase = "Did the package arrive?",
        redPhrase = "Did you feed the cat?"
    )

    @Test
    fun redPhraseTriggersSilentEscalation() {
        assertEquals(
            PhraseSignal.SILENT_ESCALATION,
            SafePhraseEngine.evaluate("Did you feed the cat?", config)
        )
    }

    @Test
    fun unknownPhraseDoesNothing() {
        assertEquals(PhraseSignal.NONE, SafePhraseEngine.evaluate("hello", config))
    }
}
