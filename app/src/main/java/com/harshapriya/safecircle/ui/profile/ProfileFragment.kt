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
import com.harshapriya.safecircle.auth.AuthRepository
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

        renderAccount(account)

        SubscriptionManager.status.observe(viewLifecycleOwner) { status.text = it }
        SubscriptionManager.refresh { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }

        root.findViewById<Button>(R.id.accountAuthButton).setOnClickListener {
            showAuthDialog(account)
        }

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

    private fun renderAccount(account: TextView) {
        val auth = AuthRepository(requireContext()).state()
        val stableId = AccountRepository(requireContext()).stableUserId()
        account.text = if (auth == null) {
            "Local account ID: " + stableId.take(18) + "…"
        } else {
            "Signed in: " + auth.email + "\nUser ID: " + auth.userId.take(18) + "…"
        }
        root.findViewById<Button>(R.id.accountAuthButton).text =
            if (auth == null) "Sign in / Create account" else "Sign out"
    }

    private fun showAuthDialog(account: TextView) {
        val repo = AuthRepository(requireContext())
        val current = repo.state()
        if (current != null) {
            repo.signOut()
            renderAccount(account)
            Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
            return
        }

        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val email = EditText(requireContext()).apply { hint = "Email"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val password = EditText(requireContext()).apply {
            hint = "Password (10+ characters)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(email)
        box.addView(password)

        AlertDialog.Builder(requireContext())
            .setTitle("SafeCircle account")
            .setMessage("Sign in for multi-device session sync and Guardian links.")
            .setView(box)
            .setPositiveButton("Sign in", null)
            .setNeutralButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    fun runAuth(register: Boolean) {
                        val e = email.text.toString().trim()
                        val p = password.text.toString()
                        if (e.isBlank() || p.length < 10) {
                            Toast.makeText(requireContext(), "Enter a valid email and 10+ character password.", Toast.LENGTH_LONG).show()
                            return
                        }
                        Thread {
                            runCatching {
                                if (register) repo.register(e, p) else repo.login(e, p)
                            }.onSuccess {
                                requireActivity().runOnUiThread {
                                    renderAccount(account)
                                    Toast.makeText(requireContext(), if (register) "Account created" else "Signed in", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            }.onFailure { error ->
                                requireActivity().runOnUiThread {
                                    Toast.makeText(requireContext(), error.message ?: "Authentication failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.start()
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { runAuth(false) }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { runAuth(true) }
                }
                dialog.show()
            }
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
