package com.harshapriya.safecircle.billing

enum class PremiumFeature {
    UNLIMITED_SESSIONS,
    MULTIPLE_GUARDIANS,
    ADVANCED_AUTOMATIONS,
    SAFE_PHRASE,
    SAFETY_CAPSULE_CONTROLS,
    FAMILY_CIRCLES,
    EXTENDED_HISTORY
}

object PremiumFeatureGate {
    fun isAvailable(feature: PremiumFeature, isPro: Boolean): Boolean {
        return when (feature) {
            PremiumFeature.UNLIMITED_SESSIONS,
            PremiumFeature.MULTIPLE_GUARDIANS,
            PremiumFeature.ADVANCED_AUTOMATIONS,
            PremiumFeature.SAFE_PHRASE,
            PremiumFeature.SAFETY_CAPSULE_CONTROLS,
            PremiumFeature.FAMILY_CIRCLES,
            PremiumFeature.EXTENDED_HISTORY -> isPro
        }
    }
}
