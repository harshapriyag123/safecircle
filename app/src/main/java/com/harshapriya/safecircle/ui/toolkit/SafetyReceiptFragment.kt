package com.harshapriya.safecircle.ui.toolkit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.data.SafetyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafetyReceiptFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_safety_receipt, container, false)
        val session = SafetyRepository(requireContext()).currentSession()
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val summary = if (session == null) {
            "No active session receipt is available yet. Start a Safety Session to generate one."
        } else {
            val destination = session.destinationLabel ?: "Not shared"
            val ended = if (session.resolved) "Sharing ended" else "Sharing active for this session"
            "Session ${session.id.take(8).uppercase(Locale.getDefault())}\n" +
                "Mode: ${session.mode.label}\n" +
                "Started: ${fmt.format(Date(session.startedAt))}\n" +
                "Expected safe: ${fmt.format(Date(session.expectedEndAt))}\n" +
                "Destination: $destination\n" +
                "Guardian scope: session only\n" +
                "Precise location: escalation policy controlled\n" +
                "Status: $ended"
        }
        root.findViewById<TextView>(R.id.receiptSummary).text = summary
        return root
    }
}
