package com.harshapriya.safecircle.profile

import android.content.Context

data class EmergencyProfile(
    val displayName: String = "",
    val primaryContact: String = "",
    val preferredLanguage: String = "English",
    val emergencyNotes: String = "",
    val guardianInstruction: String = "Call me first. If I do not answer, contact my backup Guardian."
)

class EmergencyProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_emergency_profile", Context.MODE_PRIVATE)

    fun load(): EmergencyProfile = EmergencyProfile(
        displayName = prefs.getString("display_name", "") ?: "",
        primaryContact = prefs.getString("primary_contact", "") ?: "",
        preferredLanguage = prefs.getString("preferred_language", "English") ?: "English",
        emergencyNotes = prefs.getString("emergency_notes", "") ?: "",
        guardianInstruction = prefs.getString(
            "guardian_instruction",
            "Call me first. If I do not answer, contact my backup Guardian."
        ) ?: "Call me first. If I do not answer, contact my backup Guardian."
    )

    fun save(profile: EmergencyProfile) {
        prefs.edit()
            .putString("display_name", profile.displayName)
            .putString("primary_contact", profile.primaryContact)
            .putString("preferred_language", profile.preferredLanguage)
            .putString("emergency_notes", profile.emergencyNotes)
            .putString("guardian_instruction", profile.guardianInstruction)
            .apply()
    }
}
