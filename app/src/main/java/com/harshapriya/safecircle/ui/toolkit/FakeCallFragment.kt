package com.harshapriya.safecircle.ui.toolkit

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.reliability.AuditEvent
import com.harshapriya.safecircle.reliability.AuditLog

class FakeCallFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())
    private var scheduled: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_fake_call, container, false)
        val setup = root.findViewById<View>(R.id.fakeCallSetup)
        val incoming = root.findViewById<View>(R.id.fakeCallIncoming)
        val callerInput = root.findViewById<EditText>(R.id.fakeCallerInput)
        val countdown = root.findViewById<TextView>(R.id.fakeCallCountdown)
        val callerName = root.findViewById<TextView>(R.id.fakeCallCallerName)
        val audit = AuditLog(requireContext())

        fun stopVibration() {
            vibrator()?.cancel()
        }

        fun closeIncoming(eventType: String) {
            stopVibration()
            incoming.isVisible = false
            setup.isVisible = true
            countdown.text = "No simulated call is scheduled."
            audit.append(AuditEvent(System.currentTimeMillis(), eventType, null, "simulated exit-aid call"))
        }

        root.findViewById<MaterialButton>(R.id.fakeCallScheduleButton).setOnClickListener {
            val caller = callerInput.text.toString().trim().ifBlank { "Trusted contact" }
            scheduled?.let(handler::removeCallbacks)
            countdown.text = "Simulated call scheduled in 5 seconds…"
            val task = Runnable {
                callerName.text = caller
                setup.isVisible = false
                incoming.isVisible = true
                vibratePattern()
                audit.append(AuditEvent(System.currentTimeMillis(), "FAKE_CALL_PRESENTED", null, caller))
            }
            scheduled = task
            handler.postDelayed(task, 5_000L)
            Toast.makeText(requireContext(), "Fake call scheduled", Toast.LENGTH_SHORT).show()
        }

        root.findViewById<MaterialButton>(R.id.fakeCallCancelScheduleButton).setOnClickListener {
            scheduled?.let(handler::removeCallbacks)
            scheduled = null
            countdown.text = "Schedule cancelled."
            audit.append(AuditEvent(System.currentTimeMillis(), "FAKE_CALL_CANCELLED", null, "before presentation"))
        }

        root.findViewById<MaterialButton>(R.id.fakeCallDeclineButton).setOnClickListener {
            closeIncoming("FAKE_CALL_DECLINED")
        }

        root.findViewById<MaterialButton>(R.id.fakeCallAnswerButton).setOnClickListener {
            closeIncoming("FAKE_CALL_ANSWERED")
            Toast.makeText(requireContext(), "Simulated call answered · exit-aid mode", Toast.LENGTH_LONG).show()
        }

        return root
    }

    override fun onDestroyView() {
        scheduled?.let(handler::removeCallbacks)
        vibrator()?.cancel()
        super.onDestroyView()
    }

    private fun vibrator(): Vibrator? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requireContext().getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibratePattern() {
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 450, 250, 450, 500, 800)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }
}
