package com.harshapriya.safecircle.ui.home

import android.os.Bundle
import android.os.CountDownTimer
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
    private var countdown: CountDownTimer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_home, container, false)
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]

        root.findViewById<Button>(R.id.startSessionButton).setOnClickListener { chooseMode() }
        root.findViewById<Button>(R.id.checkInButton).setOnClickListener { vm.checkIn() }
        root.findViewById<Button>(R.id.safeButton).setOnClickListener { vm.markSafe() }
        root.findViewById<Button>(R.id.simulateButton).setOnClickListener { vm.simulateConcern() }
        root.findViewById<Button>(R.id.extend5Button).setOnClickListener { vm.extendEta(5) }
        root.findViewById<Button>(R.id.extend15Button).setOnClickListener { vm.extendEta(15) }

        vm.session.observe(viewLifecycleOwner) { render() }
        vm.snapshot.observe(viewLifecycleOwner) { render() }
        return root
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onDestroyView() {
        countdown?.cancel()
        super.onDestroyView()
    }

    private fun chooseMode() {
        val modes = SessionMode.values()
        AlertDialog.Builder(requireContext())
            .setTitle("Start a Safety Session")
            .setItems(modes.map { it.label + " · " + it.minutes + " min" }.toTypedArray()) { _, which ->
                vm.start(modes[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun render() {
        val session = vm.session.value
        val snapshot = vm.snapshot.value ?: return

        root.findViewById<TextView>(R.id.readinessScore).text = snapshot.score.toString() + "/100"
        root.findViewById<TextView>(R.id.safetyState).text = snapshot.state.name.replace('_', ' ')
        root.findViewById<TextView>(R.id.signalSummary).text = snapshot.reasons.joinToString("  •  ")

        val extend5 = root.findViewById<Button>(R.id.extend5Button)
        val extend15 = root.findViewById<Button>(R.id.extend15Button)
        val checkIn = root.findViewById<Button>(R.id.checkInButton)
        val safe = root.findViewById<Button>(R.id.safeButton)

        if (session == null || session.resolved) {
            countdown?.cancel()
            root.findViewById<TextView>(R.id.activeSessionTitle).text = "No active session"
            root.findViewById<TextView>(R.id.activeSessionMeta).text =
                "Start before a walk, rideshare, meetup, or time alone."
            root.findViewById<TextView>(R.id.sessionCountdown).text = "--:-- remaining"
            root.findViewById<TextView>(R.id.locationStatus).text =
                "Location: shared only according to your session privacy settings"
            extend5.isEnabled = false
            extend15.isEnabled = false
            checkIn.isEnabled = false
            safe.isEnabled = false
        } else {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.expectedEndAt))
            root.findViewById<TextView>(R.id.activeSessionTitle).text = session.mode.label
            root.findViewById<TextView>(R.id.activeSessionMeta).text =
                "Expected safe by " + time + " · " + session.batteryPercent + "% battery"
            val location = vm.lastLocationLabel()
            root.findViewById<TextView>(R.id.locationStatus).text =
                if (location == null) "Location: unavailable or not permitted"
                else "Location snapshot: available · protected by privacy mode"
            extend5.isEnabled = true
            extend15.isEnabled = true
            checkIn.isEnabled = true
            safe.isEnabled = true
            startCountdown(session.expectedEndAt)
        }
    }

    private fun startCountdown(expectedEndAt: Long) {
        countdown?.cancel()
        val remaining = (expectedEndAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val countdownView = root.findViewById<TextView>(R.id.sessionCountdown)
        countdown = object : CountDownTimer(remaining, 1_000L) {
            override fun onTick(ms: Long) {
                val totalSeconds = ms / 1_000L
                val minutes = totalSeconds / 60L
                val seconds = totalSeconds % 60L
                countdownView.text = String.format(Locale.getDefault(), "%02d:%02d remaining", minutes, seconds)
            }

            override fun onFinish() {
                countdownView.text = "Check-in due now"
                vm.refresh()
            }
        }.start()
    }
}
