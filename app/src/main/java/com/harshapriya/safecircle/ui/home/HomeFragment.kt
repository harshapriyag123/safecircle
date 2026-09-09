package com.harshapriya.safecircle.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.model.SessionMode
import com.harshapriya.safecircle.ui.shared.SafetyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {
    private lateinit var vm: SafetyViewModel
    private lateinit var root: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_home, container, false)
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]

        root.findViewById<Button>(R.id.startSessionButton).setOnClickListener { chooseMode() }
        root.findViewById<Button>(R.id.checkInButton).setOnClickListener { vm.checkIn() }
        root.findViewById<Button>(R.id.safeButton).setOnClickListener { vm.markSafe() }
        root.findViewById<Button>(R.id.simulateButton).setOnClickListener { vm.simulateConcern() }

        vm.session.observe(viewLifecycleOwner) { render() }
        vm.snapshot.observe(viewLifecycleOwner) { render() }
        return root
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun chooseMode() {
        val modes = SessionMode.values()
        AlertDialog.Builder(requireContext())
            .setTitle("Start a Safety Session")
            .setItems(modes.map { "${it.label} · ${it.minutes} min" }.toTypedArray()) { _, which ->
                vm.start(modes[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun render() {
        val session = vm.session.value
        val snapshot = vm.snapshot.value ?: return
        root.findViewById<TextView>(R.id.readinessScore).text = "${snapshot.score}/100"
        root.findViewById<TextView>(R.id.safetyState).text = snapshot.state.name.replace('_', ' ')
        root.findViewById<TextView>(R.id.signalSummary).text = snapshot.reasons.joinToString("  •  ")

        if (session == null || session.resolved) {
            root.findViewById<TextView>(R.id.activeSessionTitle).text = "No active session"
            root.findViewById<TextView>(R.id.activeSessionMeta).text = "Start before a walk, rideshare, meetup, or time alone."
        } else {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.expectedEndAt))
            root.findViewById<TextView>(R.id.activeSessionTitle).text = session.mode.label
            root.findViewById<TextView>(R.id.activeSessionMeta).text = "Expected safe by $time · ${session.batteryPercent}% battery"
        }
    }
}
