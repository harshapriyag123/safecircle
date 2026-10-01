package com.harshapriya.safecircle.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumFeatureGateTest {
    @Test
    fun essentialSafetyRemainsFree() {
        assertTrue(PremiumFeatureGate.isAvailable(PremiumFeature.UNLIMITED_SESSIONS, false))
        assertTrue(PremiumFeatureGate.isAvailable(PremiumFeature.MULTIPLE_GUARDIANS, false))
        assertTrue(PremiumFeatureGate.isAvailable(PremiumFeature.SAFETY_CAPSULE_CONTROLS, false))
    }

    @Test
    fun advancedAutomationRequiresPro() {
        assertFalse(PremiumFeatureGate.isAvailable(PremiumFeature.ADVANCED_AUTOMATIONS, false))
        assertTrue(PremiumFeatureGate.isAvailable(PremiumFeature.ADVANCED_AUTOMATIONS, true))
    }
}
