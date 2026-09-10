package com.harshapriya.safecircle.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumFeatureGateTest {
    @Test
    fun advancedAutomationRequiresPro() {
        assertFalse(PremiumFeatureGate.isAvailable(PremiumFeature.ADVANCED_AUTOMATIONS, false))
        assertTrue(PremiumFeatureGate.isAvailable(PremiumFeature.ADVANCED_AUTOMATIONS, true))
    }
}
