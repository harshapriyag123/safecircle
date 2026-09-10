package com.harshapriya.safecircle.ui.automations

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.harshapriya.safecircle.MainActivity
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.billing.SubscriptionManager
import com.harshapriya.safecircle.automation.AutomationRepository
import com.harshapriya.safecircle.automation.AutomationRule
import com.harshapriya.safecircle.background.RecurringCommuteScheduler
import com.harshapriya.safecircle.domain.PhraseSignal
import com.harshapriya.safecircle.domain.SafePhraseConfig
import com.harshapriya.safecircle.domain.SafePhraseEngine
import com.harshapriya.safecircle.model.EscalationAction
import com.harshapriya.safecircle.model.TriggerType
import com.harshapriya.safecircle.privacy.SafetyPreferencesRepository
import java.util.Calendar

class AutomationsFragment : Fragment() {
    private lateinit var root: View
    private lateinit var repo: AutomationRepository
    private lateinit var prefs: SafetyPreferencesRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_automations, container, false)
        repo = AutomationRepository(requireContext())
        prefs = SafetyPreferencesRepository(requireContext())

        SubscriptionManager.refresh()
        root.findViewById<MaterialButton>(R.id.addAutomationButton).setOnClickListener { requirePro { addRule() } }
        root.findViewById<MaterialButton>(R.id.scheduleCommuteButton).setOnClickListener { requirePro { scheduleCommute() } }
        root.findViewById<MaterialButton>(R.id.configureSafePhraseButton).setOnClickListener { requirePro { configureSafePhrase() } }
        root.findViewById<MaterialButton>(R.id.testSafePhraseButton).setOnClickListener { testSafePhrase() }

        render()
        return root
    }

    private fun requirePro(action: () -> Unit) {
        if (SubscriptionManager.isPro.value == true) {
            action()
        } else {
            (activity as? MainActivity)?.showProPaywall()
        }
    }

    private fun render() {
        val list = root.findViewById<LinearLayout>(R.id.automationList)
        list.removeAllViews()
        val rules = repo.all().ifEmpty {
            val default = AutomationRule(
                name = "Progressive overdue escalation",
                trigger = TriggerType.ETA_OVERDUE,
                thresholdMinutes = 5,
                actions = listOf(
                    EscalationAction.PROMPT_USER,
                    EscalationAction.NOTIFY_PRIMARY_GUARDIAN,
                    EscalationAction.NOTIFY_BACKUP_GUARDIAN,
                    EscalationAction.SHARE_SAFETY_CAPSULE
                )
            )
            repo.upsert(default)
            listOf(default)
        }

        rules.forEach { rule ->
            val card = MaterialCardView(requireContext()).apply {
                radius = 26f
                cardElevation = 0f
                setContentPadding(28, 24, 28, 24)
                addView(TextView(context).apply {
                    text = "⚡ " + rule.name + "\n" +
                        rule.trigger.name.replace('_', ' ') + " · " + rule.thresholdMinutes + " min\n" +
                        rule.actions.joinToString(" → ") { it.name.replace('_', ' ') }
                    textSize = 15f
                })
                setOnLongClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete automation?")
                        .setMessage(rule.name)
                        .setPositiveButton("Delete") { _, _ -> repo.delete(rule.id); render() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 })
        }

        val phrase = prefs.safePhrase()
        root.findViewById<TextView>(R.id.safePhraseSummary).text =
            "Yellow · " + phrase.yellowPhrase + "\nRed · " + phrase.redPhrase
    }

    private fun addRule() {
        val input = EditText(requireContext()).apply {
            hint = "Automation name"
            setText("Low battery guardian check")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Create Safety Automation")
            .setView(input)
            .setMessage("Trigger: LOW BATTERY\nAction: notify primary Guardian")
            .setPositiveButton("Create") { _, _ ->
                repo.upsert(
                    AutomationRule(
                        name = input.text.toString().ifBlank { "Low battery guardian check" },
                        trigger = TriggerType.LOW_BATTERY,
                        thresholdMinutes = 0,
                        actions = listOf(EscalationAction.NOTIFY_PRIMARY_GUARDIAN)
                    )
                )
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleCommute() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                RecurringCommuteScheduler(requireContext()).scheduleNext(hour, minute)
                Toast.makeText(requireContext(), "Commute reminder scheduled", Toast.LENGTH_SHORT).show()
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun testSafePhrase() {
        val input = EditText(requireContext()).apply { hint = "Enter one of your SafePhrases" }
        AlertDialog.Builder(requireContext())
            .setTitle("Local SafePhrase test")
            .setView(input)
            .setPositiveButton("Evaluate") { _, _ ->
                val signal = SafePhraseEngine.evaluate(input.text.toString(), prefs.safePhrase())
                val message = when (signal) {
                    PhraseSignal.NONE -> "No safety signal detected."
                    PhraseSignal.CHECK_ON_ME -> "Yellow signal: request a Guardian check-in."
                    PhraseSignal.SILENT_ESCALATION -> "Red signal: silent escalation workflow would start."
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun configureSafePhrase() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 8)
        }
        val current = prefs.safePhrase()
        val yellow = EditText(requireContext()).apply { hint = "Yellow phrase"; setText(current.yellowPhrase) }
        val red = EditText(requireContext()).apply { hint = "Red phrase"; setText(current.redPhrase) }
        container.addView(yellow)
        container.addView(red)

        AlertDialog.Builder(requireContext())
            .setTitle("SafePhrase")
            .setMessage("Choose ordinary phrases you can remember. Red is intended for silent escalation.")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                prefs.saveSafePhrase(SafePhraseConfig(yellow.text.toString(), red.text.toString()))
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
