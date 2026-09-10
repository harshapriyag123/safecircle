package com.harshapriya.safecircle.ui.toolkit

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.cyber.ScamDetector
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.model.SessionMode
import com.harshapriya.safecircle.reliability.AuditEvent
import com.harshapriya.safecircle.reliability.AuditLog

class SafePhraseFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_safephrase, container, false)
        val prefs = requireContext().getSharedPreferences("safecircle_safephrase", Context.MODE_PRIVATE)
        val phrase = root.findViewById<EditText>(R.id.safePhraseInput)
        val test = root.findViewById<EditText>(R.id.safePhraseTestInput)
        val status = root.findViewById<TextView>(R.id.safePhraseStatus)
        val messageInput = root.findViewById<EditText>(R.id.messageScanInput)
        val messageResult = root.findViewById<TextView>(R.id.messageScanResult)
        val safety = SafetyRepository(requireContext())
        val audit = AuditLog(requireContext())
        phrase.setText(prefs.getString("phrase", "blue notebook"))

        root.findViewById<MaterialButton>(R.id.saveSafePhraseButton).setOnClickListener {
            val value = phrase.text.toString().trim()
            if (value.length < 4) {
                status.text = "Use at least 4 characters so accidental activation is less likely."
            } else {
                prefs.edit().putString("phrase", value).apply()
                status.text = "SafePhrase saved on this device. It is never displayed to Guardians."
                audit.append(AuditEvent(System.currentTimeMillis(), "SAFEPHRASE_CONFIGURED", safety.currentSession()?.id, "local phrase updated"))
                Toast.makeText(requireContext(), "SafePhrase saved", Toast.LENGTH_SHORT).show()
            }
        }

        root.findViewById<MaterialButton>(R.id.testSafePhraseButton).setOnClickListener {
            val matched = phraseMatches(prefs, test.text.toString())
            status.text = if (matched) {
                "MATCHED ✓ Private test passed. No Guardian was contacted and no session state changed."
            } else {
                "No match. No escalation action was triggered."
            }
        }

        root.findViewById<MaterialButton>(R.id.activateSafePhraseButton).setOnClickListener {
            if (!phraseMatches(prefs, test.text.toString())) {
                status.text = "Phrase did not match. Nothing was activated."
                return@setOnClickListener
            }

            var session = safety.currentSession()
            if (session == null || session.resolved) {
                session = safety.startSession(
                    mode = SessionMode.STAY_WITH_ME,
                    durationMinutes = 30,
                    destinationLabel = "SafePhrase protection",
                    originLabel = "Discreet activation"
                )
            }
            safety.simulateConcern()
            audit.append(AuditEvent(System.currentTimeMillis(), "SAFEPHRASE_ACTIVATED", session.id, "trusted-circle workflow activated"))
            status.text = "ACTIVATED ✓ SafeCircle moved the active Safety Session into CONCERN and queued normal sync/escalation handling."
            Toast.makeText(requireContext(), "SafePhrase activated", Toast.LENGTH_LONG).show()
        }

        root.findViewById<MaterialButton>(R.id.scanMessageButton).setOnClickListener {
            val assessment = ScamDetector.analyze(messageInput.text.toString())
            val signals = if (assessment.signals.isEmpty()) {
                "• No common scam indicators found"
            } else {
                assessment.signals.joinToString("\n") { "• ${it.label}" }
            }
            messageResult.text =
                "${assessment.riskLevel.uppercase()} RISK · ${assessment.riskScore}/100\n\n${assessment.verdict}\n\n$signals\n\nSafeCircle cannot prove a sender or message is genuine. Verify through an official phone number, app, or website before acting."
            audit.append(
                AuditEvent(
                    System.currentTimeMillis(),
                    "MESSAGE_RISK_SCAN",
                    safety.currentSession()?.id,
                    "level=${assessment.riskLevel};score=${assessment.riskScore}"
                )
            )
        }

        root.findViewById<MaterialButton>(R.id.useScamExampleButton).setOnClickListener {
            messageInput.setText("URGENT: Your account will be suspended. Send the verification code and pay with gift cards now: https://bit.ly/demo")
        }
        root.findViewById<MaterialButton>(R.id.useSafeExampleButton).setOnClickListener {
            messageInput.setText("Hi, your parcel is expected tomorrow between 2–4 PM. You can view updates in the official carrier app.")
        }

        return root
    }

    private fun phraseMatches(prefs: android.content.SharedPreferences, entered: String): Boolean {
        val saved = prefs.getString("phrase", "blue notebook") ?: "blue notebook"
        return entered.trim().equals(saved.trim(), ignoreCase = true)
    }
}
