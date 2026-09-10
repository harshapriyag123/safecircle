package com.harshapriya.safecircle.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.harshapriya.safecircle.MainActivity
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.auth.AccountRepository
import com.harshapriya.safecircle.billing.SubscriptionManager
import com.harshapriya.safecircle.profile.EmergencyProfile
import com.harshapriya.safecircle.profile.EmergencyProfileRepository

class ProfileFragment : Fragment() {
    private lateinit var root: View
    private lateinit var emergencyRepo: EmergencyProfileRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_profile, container, false)
        emergencyRepo = EmergencyProfileRepository(requireContext())

        val status = root.findViewById<TextView>(R.id.planStatus)
        val account = root.findViewById<TextView>(R.id.accountId)

        val stableId = AccountRepository(requireContext()).stableUserId()
        account.text = "Account ID: " + stableId.take(18) + "…"

        SubscriptionManager.status.observe(viewLifecycleOwner) { status.text = it }
        SubscriptionManager.refresh { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }

        root.findViewById<Button>(R.id.upgradeButton).setOnClickListener {
            (activity as? MainActivity)?.showProPaywall()
        }
        root.findViewById<Button>(R.id.manageButton).setOnClickListener {
            (activity as? MainActivity)?.showCustomerCenter()
        }
        root.findViewById<Button>(R.id.restoreButton).setOnClickListener {
            SubscriptionManager.restore(
                onDone = { active ->
                    Toast.makeText(
                        requireContext(),
                        if (active) "SafeCircle+ restored" else "No active purchase found",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onError = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
            )
        }
        root.findViewById<Button>(R.id.editEmergencyProfileButton).setOnClickListener {
            editEmergencyProfile()
        }
        root.findViewById<Button>(R.id.systemHealthButton).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_profile_to_diagnostics)
        }

        renderEmergencyProfile()
        return root
    }

    private fun renderEmergencyProfile() {
        val profile = emergencyRepo.load()
        val summary = root.findViewById<TextView>(R.id.emergencyProfileSummary)
        summary.text =
            if (profile.displayName.isBlank() && profile.primaryContact.isBlank()) {
                "Optional emergency information is private until you configure sharing."
            } else {
                "Name: " + profile.displayName.ifBlank { "Not set" } +
                    "\nPrimary contact: " + profile.primaryContact.ifBlank { "Not set" } +
                    "\nLanguage: " + profile.preferredLanguage +
                    "\nInstruction: " + profile.guardianInstruction
            }
    }

    private fun editEmergencyProfile() {
        val current = emergencyRepo.load()
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }

        val name = EditText(requireContext()).apply {
            hint = "Display name"
            setText(current.displayName)
        }
        val contact = EditText(requireContext()).apply {
            hint = "Primary contact"
            setText(current.primaryContact)
        }
        val language = EditText(requireContext()).apply {
            hint = "Preferred language"
            setText(current.preferredLanguage)
        }
        val notes = EditText(requireContext()).apply {
            hint = "Emergency notes (optional)"
            setText(current.emergencyNotes)
        }
        val instruction = EditText(requireContext()).apply {
            hint = "Guardian instruction"
            setText(current.guardianInstruction)
        }

        box.addView(name)
        box.addView(contact)
        box.addView(language)
        box.addView(notes)
        box.addView(instruction)

        AlertDialog.Builder(requireContext())
            .setTitle("Emergency Profile")
            .setMessage("Store only information you intentionally want available to your safety workflow.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                emergencyRepo.save(
                    EmergencyProfile(
                        displayName = name.text.toString().trim(),
                        primaryContact = contact.text.toString().trim(),
                        preferredLanguage = language.text.toString().ifBlank { "English" },
                        emergencyNotes = notes.text.toString().trim(),
                        guardianInstruction = instruction.text.toString().ifBlank {
                            "Call me first. If I do not answer, contact my backup Guardian."
                        }
                    )
                )
                renderEmergencyProfile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
