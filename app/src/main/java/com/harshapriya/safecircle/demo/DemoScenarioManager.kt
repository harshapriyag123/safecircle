package com.harshapriya.safecircle.demo

import android.content.Context
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.family.FamilyCircleRepository
import com.harshapriya.safecircle.model.SessionMode
import com.harshapriya.safecircle.privacy.LocationPrivacyMode
import com.harshapriya.safecircle.privacy.PrivacyPreferences
import com.harshapriya.safecircle.privacy.SafetyPreferencesRepository
import com.harshapriya.safecircle.profile.EmergencyProfile
import com.harshapriya.safecircle.profile.EmergencyProfileRepository
import com.harshapriya.safecircle.reliability.AuditEvent
import com.harshapriya.safecircle.reliability.AuditLog

class DemoScenarioManager(private val context: Context) {
    private val safetyRepo = SafetyRepository(context)

    fun seedJudgeDemo() {
        SafetyPreferencesRepository(context).savePrivacy(
            PrivacyPreferences(
                locationMode = LocationPrivacyMode.PRECISE_ON_ESCALATION,
                capsuleRetentionHours = 24,
                shareBatteryOnEscalation = true,
                shareDestinationOnEscalation = true
            )
        )

        EmergencyProfileRepository(context).save(
            EmergencyProfile(
                displayName = "Demo User",
                primaryContact = "+1 555 010 2400",
                preferredLanguage = "English",
                emergencyNotes = "No medical notes configured for this demo.",
                guardianInstruction = "Call me first. If I do not answer, notify my backup Guardian."
            )
        )

        val family = FamilyCircleRepository(context)
        val circle = family.circles().firstOrNull() ?: family.create("Demo Family")
        if (circle.members.isEmpty()) {
            family.addMember(circle.id, "Primary Guardian", "Guardian")
            family.addMember(circle.id, "Backup Guardian", "Backup Guardian")
        }

        safetyRepo.startSession(
            mode = SessionMode.WALK_HOME,
            durationMinutes = 12,
            destinationLabel = "Home · Demo destination"
        )

        AuditLog(context).append(
            AuditEvent(
                System.currentTimeMillis(),
                "DEMO_SCENARIO_SEEDED",
                safetyRepo.currentSession()?.id,
                "judge demo data created"
            )
        )
    }

    fun simulateConcern() {
        safetyRepo.simulateConcern()
        AuditLog(context).append(
            AuditEvent(
                System.currentTimeMillis(),
                "DEMO_CONCERN_SIMULATED",
                safetyRepo.currentSession()?.id,
                "missed check-in + route deviation + low battery"
            )
        )
    }

    fun resolveDemo() {
        safetyRepo.markSafe()
    }
}
