package com.harshapriya.safecircle.ui.diagnostics

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.auth.AccountRepository
import com.harshapriya.safecircle.reliability.AuditLog
import com.harshapriya.safecircle.reliability.OfflineEventQueue
import com.revenuecat.purchases.Purchases

class DiagnosticsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_diagnostics, container, false)

        val locationGranted =
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        val notificationsGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        val queueCount = OfflineEventQueue(requireContext()).all().size
        val auditCount = AuditLog(requireContext()).recent(250).size
        val userId = AccountRepository(requireContext()).stableUserId()

        root.findViewById<TextView>(R.id.diagnosticsSummary).text =
            "RevenueCat configured: " + yesNo(Purchases.isConfigured) +
                "\nLocation permission: " + yesNo(locationGranted) +
                "\nNotification permission: " + yesNo(notificationsGranted) +
                "\nOffline events queued: " + queueCount +
                "\nAudit events retained: " + auditCount +
                "\nStable account ID: " + userId.take(20) + "…"

        root.findViewById<MaterialButton>(R.id.diagnosticsBackButton).setOnClickListener {
            findNavController().popBackStack()
        }
        return root
    }

    private fun yesNo(value: Boolean) = if (value) "READY ✓" else "NEEDS ATTENTION"
}
