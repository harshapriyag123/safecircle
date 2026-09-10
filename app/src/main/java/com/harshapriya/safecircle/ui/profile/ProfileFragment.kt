package com.harshapriya.safecircle.ui.profile

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
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
            showAccountActions(account)
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
                    Toast.makeText(requireContext(), if (active) "SafeCircle+ restored" else "No active purchase found", Toast.LENGTH_LONG).show()
                },
                onError = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
            )
        }
        root.findViewById<Button>(R.id.editEmergencyProfileButton).setOnClickListener { editEmergencyProfile() }
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
            "Not signed in\nLocal account ID: ${stableId.take(18)}…\nSign in is required for real Guardian links and multi-device sync."
        } else {
            val name = auth.displayName.ifBlank { "SafeCircle user" }
            "$name\n${auth.email}\nUser ID: ${auth.userId.take(18)}…\nBackend session: authenticated"
        }
        root.findViewById<Button>(R.id.accountAuthButton).text = if (auth == null) "Sign in / Create account" else "Account options"
    }

    private fun showAccountActions(account: TextView) {
        val repo = AuthRepository(requireContext())
        if (repo.state() != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("SafeCircle account")
                .setItems(arrayOf("Stay signed in", "Sign out")) { dialog, which ->
                    if (which == 1) {
                        repo.signOut()
                        renderAccount(account)
                        Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
                .show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("SafeCircle account")
            .setMessage("Use a real account for Guardian links and multi-device session sync.")
            .setPositiveButton("Sign in") { _, _ -> showLoginDialog(account) }
            .setNeutralButton("Create account") { _, _ -> showRegisterDialog(account) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoginDialog(account: TextView) {
        val repo = AuthRepository(requireContext())
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val email = EditText(requireContext()).apply {
            hint = "Email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = EditText(requireContext()).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        box.addView(email)
        box.addView(password)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Welcome back")
            .setMessage("Sign in to sync Safety Sessions and create signed Guardian links.")
            .setView(box)
            .setPositiveButton("Sign in", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runAuth(dialog, account, "Signing in…") {
                    repo.login(email.text.toString(), password.text.toString())
                }
            }
        }
        dialog.show()
    }

    private fun showRegisterDialog(account: TextView) {
        val repo = AuthRepository(requireContext())
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val name = EditText(requireContext()).apply { hint = "Preferred name" }
        val email = EditText(requireContext()).apply {
            hint = "Email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = EditText(requireContext()).apply {
            hint = "Password (10+ chars, upper/lower/number)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirm = EditText(requireContext()).apply {
            hint = "Confirm password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val terms = CheckBox(requireContext()).apply { text = "I agree to the Terms and Privacy Policy." }
        box.addView(name)
        box.addView(email)
        box.addView(password)
        box.addView(confirm)
        box.addView(terms)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Create SafeCircle account")
            .setMessage("Your account enables signed Guardian links. Safety sharing remains session-scoped.")
            .setView(box)
            .setPositiveButton("Create account", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                runAuth(dialog, account, "Creating account…") {
                    repo.register(
                        displayName = name.text.toString(),
                        email = email.text.toString(),
                        password = password.text.toString(),
                        confirmPassword = confirm.text.toString(),
                        acceptedTerms = terms.isChecked
                    )
                }
            }
        }
        dialog.show()
    }

    private fun runAuth(
        dialog: AlertDialog,
        account: TextView,
        busyText: String,
        action: () -> Any
    ) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = busyText
        Thread {
            runCatching(action).onSuccess {
                requireActivity().runOnUiThread {
                    renderAccount(account)
                    Toast.makeText(requireContext(), "Account authenticated", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }.onFailure { error ->
                requireActivity().runOnUiThread {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = if (busyText.startsWith("Creating")) "Create account" else "Sign in"
                    Toast.makeText(requireContext(), error.message ?: "Authentication failed", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun renderEmergencyProfile() {
        val profile = emergencyRepo.load()
        val summary = root.findViewById<TextView>(R.id.emergencyProfileSummary)
        summary.text = if (profile.displayName.isBlank() && profile.primaryContact.isBlank()) {
            "Optional emergency information is private until you configure sharing."
        } else {
            "Name: ${profile.displayName.ifBlank { "Not set" }}\nPrimary contact: ${profile.primaryContact.ifBlank { "Not set" }}\nLanguage: ${profile.preferredLanguage}\nInstruction: ${profile.guardianInstruction}"
        }
    }

    private fun editEmergencyProfile() {
        val current = emergencyRepo.load()
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val name = EditText(requireContext()).apply { hint = "Display name"; setText(current.displayName) }
        val contact = EditText(requireContext()).apply { hint = "Primary contact"; setText(current.primaryContact) }
        val language = EditText(requireContext()).apply { hint = "Preferred language"; setText(current.preferredLanguage) }
        val notes = EditText(requireContext()).apply { hint = "Emergency notes (optional)"; setText(current.emergencyNotes) }
        val instruction = EditText(requireContext()).apply { hint = "Guardian instruction"; setText(current.guardianInstruction) }
        box.addView(name); box.addView(contact); box.addView(language); box.addView(notes); box.addView(instruction)

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
                        guardianInstruction = instruction.text.toString().ifBlank { "Call me first. If I do not answer, contact my backup Guardian." }
                    )
                )
                renderEmergencyProfile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
