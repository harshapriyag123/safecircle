package com.harshapriya.safecircle.ui.session

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.model.SessionMode
import com.harshapriya.safecircle.privacy.SafetyPreferencesRepository
import com.harshapriya.safecircle.ui.shared.SafetyViewModel

class SessionSetupFragment : Fragment() {
    private lateinit var vm: SafetyViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_session_setup, container, false)
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]

        val modeSpinner = root.findViewById<Spinner>(R.id.modeSpinner)
        val duration = root.findViewById<EditText>(R.id.durationInput)
        val destination = root.findViewById<EditText>(R.id.destinationInput)
        val privacy = SafetyPreferencesRepository(requireContext()).privacy()

        val modes = SessionMode.values()
        modeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            modes.map { it.label }
        )

        duration.setText(modes.first().minutes.toString())
        modeSpinner.setSelection(0)

        root.findViewById<TextView>(R.id.setupPrivacySummary).text =
            "Location privacy: " + privacy.locationMode.name.replace('_', ' ') +
                "\nCapsule retention: " + privacy.capsuleRetentionHours + "h"

        root.findViewById<MaterialButton>(R.id.startConfiguredSessionButton).setOnClickListener {
            val mode = modes[modeSpinner.selectedItemPosition]
            val minutes = duration.text.toString().toIntOrNull()
            if (minutes == null || minutes < 5) {
                Toast.makeText(requireContext(), "Choose at least 5 minutes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.start(
                mode = mode,
                durationMinutes = minutes,
                destinationLabel = destination.text.toString().trim().ifBlank { null }
            )
            Toast.makeText(requireContext(), "Safety Session started", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        root.findViewById<MaterialButton>(R.id.cancelSessionSetupButton).setOnClickListener {
            findNavController().popBackStack()
        }

        return root
    }
}
