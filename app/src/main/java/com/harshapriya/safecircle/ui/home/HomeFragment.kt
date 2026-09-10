package com.harshapriya.safecircle.ui.home

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.ui.shared.SafetyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {
    private lateinit var vm: SafetyViewModel
    private lateinit var root: View
    private var countdown: CountDownTimer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val liveRefresh = object : Runnable {
        override fun run() {
            if (isAdded) vm.refresh()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_home, container, false)
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]

        root.findViewById<Button>(R.id.startSessionButton).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_session_setup)
        }
        root.findViewById<Button>(R.id.toolkitButton).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_toolkit)
        }
        root.findViewById<Button>(R.id.checkInButton).setOnClickListener { vm.checkIn() }
        root.findViewById<Button>(R.id.safeButton).setOnClickListener { vm.markSafe() }
        root.findViewById<Button>(R.id.extend5Button).setOnClickListener { vm.extendEta(5) }
        root.findViewById<Button>(R.id.extend15Button).setOnClickListener { vm.extendEta(15) }
        root.findViewById<Button>(R.id.simulateButton).visibility = View.GONE

        vm.session.observe(viewLifecycleOwner) { render() }
        vm.snapshot.observe(viewLifecycleOwner) { render() }
        return root
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        handler.removeCallbacks(liveRefresh)
        handler.postDelayed(liveRefresh, 30_000L)
    }

    override fun onPause() {
        handler.removeCallbacks(liveRefresh)
        super.onPause()
    }

    override fun onDestroyView() {
        countdown?.cancel()
        handler.removeCallbacks(liveRefresh)
        super.onDestroyView()
    }

    private fun render() {
        val session = vm.session.value
        val snapshot = vm.snapshot.value ?: return
        val readiness = root.findViewById<TextView>(R.id.readinessScore)
        val stateView = root.findViewById<TextView>(R.id.safetyState)
        val summary = root.findViewById<TextView>(R.id.signalSummary)
        val extend5 = root.findViewById<Button>(R.id.extend5Button)
        val extend15 = root.findViewById<Button>(R.id.extend15Button)
        val checkIn = root.findViewById<Button>(R.id.checkInButton)
        val safe = root.findViewById<Button>(R.id.safeButton)

        if (session == null) {
            readiness.text = "—"
            stateView.text = "NO ACTIVE SESSION"
            summary.text = "Start a Safety Session to calculate readiness from live session conditions."
            renderInactive("No active session", "Start before a walk, rideshare, meetup, or time alone.")
            return
        }
        if (session.resolved) {
            countdown?.cancel()
            readiness.text = "—"
            stateView.text = "RESOLVED"
            val resolvedText = session.resolvedAt?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it)) } ?: "recently"
            summary.text = "Monitoring ended at $resolvedText. This session is read-only and remains in Safety History."
            renderInactive("Last session resolved", "${session.mode.label}${session.destinationLabel?.let { " · $it" } ?: ""} · resolved $resolvedText")
            root.findViewById<TextView>(R.id.sessionCountdown).text = "Monitoring ended"
            return
        }

        readiness.text = snapshot.score.toString() + "/100"
        stateView.text = snapshot.state.name.replace('_', ' ')
        summary.text = snapshot.reasons.joinToString("  •  ")
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.expectedEndAt))
        root.findViewById<TextView>(R.id.activeSessionTitle).text = session.mode.label
        val destination = session.destinationLabel?.let { " · $it" } ?: ""
        root.findViewById<TextView>(R.id.activeSessionMeta).text = "Expected safe by $time · ${session.batteryPercent}% native battery$destination"
        val location = vm.lastLocationLabel()
        root.findViewById<TextView>(R.id.locationStatus).text = if (location == null) "Location: unavailable or not permitted" else "Location snapshot available · visibility follows session privacy policy"
        extend5.isEnabled = true; extend15.isEnabled = true; checkIn.isEnabled = true; safe.isEnabled = true
        startCountdown(session.expectedEndAt)
    }

    private fun renderInactive(title: String, meta: String) {
        countdown?.cancel()
        root.findViewById<TextView>(R.id.activeSessionTitle).text = title
        root.findViewById<TextView>(R.id.activeSessionMeta).text = meta
        root.findViewById<TextView>(R.id.sessionCountdown).text = "--:--"
        root.findViewById<TextView>(R.id.locationStatus).text = "No session-scoped location sharing is active"
        root.findViewById<Button>(R.id.extend5Button).isEnabled = false
        root.findViewById<Button>(R.id.extend15Button).isEnabled = false
        root.findViewById<Button>(R.id.checkInButton).isEnabled = false
        root.findViewById<Button>(R.id.safeButton).isEnabled = false
    }

    private fun startCountdown(expectedEndAt: Long) {
        countdown?.cancel()
        val remaining = expectedEndAt - System.currentTimeMillis()
        val countdownView = root.findViewById<TextView>(R.id.sessionCountdown)
        if (remaining <= 0L) {
            val overdueMinutes = ((-remaining) / 60_000L).coerceAtLeast(0L)
            countdownView.text = if (overdueMinutes == 0L) "Check-in due now" else "$overdueMinutes min overdue"
            return
        }
        countdown = object : CountDownTimer(remaining, 1_000L) {
            override fun onTick(ms: Long) {
                val totalSeconds = ms / 1_000L
                val minutes = totalSeconds / 60L
                val seconds = totalSeconds % 60L
                countdownView.text = String.format(Locale.getDefault(), "%02d:%02d remaining", minutes, seconds)
            }
            override fun onFinish() { countdownView.text = "Check-in due now"; vm.refresh() }
        }.start()
    }
}
