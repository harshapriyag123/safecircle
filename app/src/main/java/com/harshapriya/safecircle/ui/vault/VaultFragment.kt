package com.harshapriya.safecircle.ui.vault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.history.SafetyHistoryRepository
import com.harshapriya.safecircle.model.SafetyCapsuleFactory
import com.harshapriya.safecircle.privacy.LocationPrivacyMode
import com.harshapriya.safecircle.privacy.SafetyPreferencesRepository
import com.harshapriya.safecircle.reliability.AuditLog
import com.harshapriya.safecircle.security.SafetyCapsuleStore
import com.harshapriya.safecircle.ui.shared.SafetyViewModel
import org.json.JSONObject

class VaultFragment : Fragment() {
    private lateinit var root: View
    private lateinit var prefs: SafetyPreferencesRepository
    private lateinit var store: SafetyCapsuleStore
    private lateinit var vm: SafetyViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_vault, container, false)
        prefs = SafetyPreferencesRepository(requireContext())
        store = SafetyCapsuleStore(requireContext())
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]

        root.findViewById<MaterialButton>(R.id.createCapsuleButton).setOnClickListener { createCapsule() }
        root.findViewById<MaterialButton>(R.id.privacyModeButton).setOnClickListener { choosePrivacy() }
        root.findViewById<MaterialButton>(R.id.purgeCapsuleButton).setOnClickListener {
            store.purgeExpired()
            Toast.makeText(requireContext(), "Expired capsules purged", Toast.LENGTH_SHORT).show()
            render()
        }

        vm.session.observe(viewLifecycleOwner) { render() }
        render()
        return root
    }

    private fun createCapsule() {
        val session = vm.session.value
        if (session == null || session.resolved) {
            Toast.makeText(requireContext(), "Start a Safety Session first.", Toast.LENGTH_LONG).show()
            return
        }
        val config = prefs.privacy()
        val base = SafetyCapsuleFactory.fromSession(
            session = session,
            destinationLabel = "Expected destination",
            lastKnownLocationLabel = vm.lastLocationLabel(),
            guardianInstructions = "Call me first; escalate only if I do not respond."
        )
        val expires = System.currentTimeMillis() + config.capsuleRetentionHours * 60L * 60L * 1000L
        val json = JSONObject().apply {
            put("sessionId", base.sessionId)
            put("mode", session.mode.name)
            put("battery", if (config.shareBatteryOnEscalation) session.batteryPercent else JSONObject.NULL)
            put("location", if (config.locationMode == LocationPrivacyMode.STATUS_ONLY) JSONObject.NULL else base.lastKnownLocationLabel)
            put("destination", if (config.shareDestinationOnEscalation) base.destinationLabel else JSONObject.NULL)
            put("instructions", base.guardianInstructions)
            put("privacyMode", config.locationMode.name)
            put("expiresAt", expires)
        }.toString()
        store.put(session.id, json, expires)
        Toast.makeText(requireContext(), "Encrypted Safety Capsule created", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun choosePrivacy() {
        val modes = arrayOf("Status only", "Approximate", "Precise only on escalation")
        val current = prefs.privacy()
        val checked = when (current.locationMode) {
            LocationPrivacyMode.STATUS_ONLY -> 0
            LocationPrivacyMode.APPROXIMATE -> 1
            LocationPrivacyMode.PRECISE_ON_ESCALATION -> 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Location privacy")
            .setSingleChoiceItems(modes, checked) { dialog, which ->
                val mode = when (which) {
                    0 -> LocationPrivacyMode.STATUS_ONLY
                    1 -> LocationPrivacyMode.APPROXIMATE
                    else -> LocationPrivacyMode.PRECISE_ON_ESCALATION
                }
                prefs.savePrivacy(current.copy(locationMode = mode))
                dialog.dismiss()
                render()
            }
            .show()
    }

    private fun render() {
        val config = prefs.privacy()
        root.findViewById<TextView>(R.id.vaultStatus).text =
            "Android Keystore · AES-GCM\nRetention: " + config.capsuleRetentionHours +
                "h\nLocation mode: " + config.locationMode.name.replace('_', ' ')

        val session = vm.session.value
        val capsule = if (session == null) null else store.get(session.id)
        root.findViewById<TextView>(R.id.capsulePreview).text =
            if (capsule == null) "No encrypted capsule for the active session."
            else "Encrypted capsule available ✓\n" + capsule.take(220)

        val audit = AuditLog(requireContext()).recent(6)
        root.findViewById<TextView>(R.id.auditPreview).text =
            if (audit.isEmpty()) "No safety events yet."
            else audit.joinToString("\n") { "• " + it.type + " · " + it.details }

        val history = SafetyHistoryRepository(requireContext()).all().take(6)
        root.findViewById<TextView>(R.id.historyPreview).text =
            if (history.isEmpty()) "No Safety Session history yet."
            else history.joinToString("\n") {
                "• " + it.mode.replace('_', ' ') + " · " + it.outcome
            }
    }
}
