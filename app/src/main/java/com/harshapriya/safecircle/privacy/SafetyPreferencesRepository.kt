package com.harshapriya.safecircle.privacy

import android.content.Context
import com.harshapriya.safecircle.domain.SafePhraseConfig

enum class LocationPrivacyMode {
    STATUS_ONLY,
    APPROXIMATE,
    PRECISE_ON_ESCALATION
}

data class PrivacyPreferences(
    val locationMode: LocationPrivacyMode = LocationPrivacyMode.PRECISE_ON_ESCALATION,
    val capsuleRetentionHours: Int = 24,
    val shareBatteryOnEscalation: Boolean = true,
    val shareDestinationOnEscalation: Boolean = true
)

class SafetyPreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_preferences", Context.MODE_PRIVATE)

    fun safePhrase(): SafePhraseConfig = SafePhraseConfig(
        yellowPhrase = prefs.getString("yellow_phrase", "Did the package arrive?") ?: "Did the package arrive?",
        redPhrase = prefs.getString("red_phrase", "Did you feed the cat?") ?: "Did you feed the cat?"
    )

    fun saveSafePhrase(config: SafePhraseConfig) {
        prefs.edit()
            .putString("yellow_phrase", config.yellowPhrase)
            .putString("red_phrase", config.redPhrase)
            .apply()
    }

    fun privacy(): PrivacyPreferences {
        val mode = runCatching {
            LocationPrivacyMode.valueOf(
                prefs.getString("location_mode", LocationPrivacyMode.PRECISE_ON_ESCALATION.name)
                    ?: LocationPrivacyMode.PRECISE_ON_ESCALATION.name
            )
        }.getOrDefault(LocationPrivacyMode.PRECISE_ON_ESCALATION)

        return PrivacyPreferences(
            locationMode = mode,
            capsuleRetentionHours = prefs.getInt("retention_hours", 24).coerceIn(1, 168),
            shareBatteryOnEscalation = prefs.getBoolean("share_battery", true),
            shareDestinationOnEscalation = prefs.getBoolean("share_destination", true)
        )
    }

    fun savePrivacy(value: PrivacyPreferences) {
        prefs.edit()
            .putString("location_mode", value.locationMode.name)
            .putInt("retention_hours", value.capsuleRetentionHours.coerceIn(1, 168))
            .putBoolean("share_battery", value.shareBatteryOnEscalation)
            .putBoolean("share_destination", value.shareDestinationOnEscalation)
            .apply()
    }
}
