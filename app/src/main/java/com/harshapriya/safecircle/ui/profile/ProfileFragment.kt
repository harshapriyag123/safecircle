package com.harshapriya.safecircle.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.harshapriya.safecircle.MainActivity
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.auth.AccountRepository
import com.harshapriya.safecircle.billing.SubscriptionManager

class ProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_profile, container, false)
        val status = root.findViewById<TextView>(R.id.planStatus)
        val account = root.findViewById<TextView>(R.id.accountId)

        val stableId = AccountRepository(requireContext()).stableUserId()
        account.text = "Account ID: " + stableId.take(18) + "…"

        SubscriptionManager.status.observe(viewLifecycleOwner) { status.text = it }
        SubscriptionManager.refresh { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }

        root.findViewById<Button>(R.id.upgradeButton).setOnClickListener {
            (activity as? MainActivity)?.showProPaywall()
        }
        root.findViewById<Button>(R.id.manageButton).setOnClickListener {
            (activity as? MainActivity)?.showCustomerCenter()
        }
        root.findViewById<Button>(R.id.restoreButton).setOnClickListener {
            SubscriptionManager.restore(
                onDone = { active ->
                    Toast.makeText(
                        requireContext(),
                        if (active) "SafeCircle+ restored" else "No active purchase found",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onError = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
            )
        }
        return root
    }
}
