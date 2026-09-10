package com.harshapriya.safecircle.ui.automations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.automation.AutomationRepository
import com.harshapriya.safecircle.automation.AutomationRule
import com.harshapriya.safecircle.model.EscalationAction
import com.harshapriya.safecircle.model.TriggerType

class AutomationBuilderFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_automation_builder, container, false)
        val repo = AutomationRepository(requireContext())

        val name = root.findViewById<EditText>(R.id.ruleNameInput)
        val trigger = root.findViewById<Spinner>(R.id.triggerSpinner)
        val threshold = root.findViewById<EditText>(R.id.thresholdInput)

        val triggers = TriggerType.values()
        trigger.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            triggers.map { it.name.replace('_', ' ') }
        )

        root.findViewById<MaterialButton>(R.id.saveAutomationButton).setOnClickListener {
            val actions = buildList {
                if (root.findViewById<CheckBox>(R.id.actionPromptUser).isChecked) add(EscalationAction.PROMPT_USER)
                if (root.findViewById<CheckBox>(R.id.actionPrimaryGuardian).isChecked) add(EscalationAction.NOTIFY_PRIMARY_GUARDIAN)
                if (root.findViewById<CheckBox>(R.id.actionBackupGuardian).isChecked) add(EscalationAction.NOTIFY_BACKUP_GUARDIAN)
                if (root.findViewById<CheckBox>(R.id.actionSafetyCapsule).isChecked) add(EscalationAction.SHARE_SAFETY_CAPSULE)
            }

            val title = name.text.toString().trim()
            val minutes = threshold.text.toString().toIntOrNull() ?: 0
            if (title.isBlank()) {
                Toast.makeText(requireContext(), "Give the automation a name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (actions.isEmpty()) {
                Toast.makeText(requireContext(), "Choose at least one action.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            repo.upsert(
                AutomationRule(
                    name = title,
                    trigger = triggers[trigger.selectedItemPosition],
                    thresholdMinutes = minutes.coerceAtLeast(0),
                    actions = actions
                )
            )
            Toast.makeText(requireContext(), "Safety Automation saved", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        root.findViewById<MaterialButton>(R.id.cancelAutomationButton).setOnClickListener {
            findNavController().popBackStack()
        }

        return root
    }
}
