package com.harshapriya.safecircle.ui.circle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.harshapriya.safecircle.MainActivity
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.billing.SubscriptionManager
import com.harshapriya.safecircle.family.FamilyCircleRepository
import com.harshapriya.safecircle.guardian.GuardianInviteCoordinator
import com.harshapriya.safecircle.guardian.GuardianInviteService
import com.harshapriya.safecircle.ui.shared.SafetyViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CircleFragment : Fragment() {
    private lateinit var root: View
    private lateinit var vm: SafetyViewModel
    private lateinit var familyRepo: FamilyCircleRepository
    private lateinit var inviteService: GuardianInviteService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_circle, container, false)
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]
        familyRepo = FamilyCircleRepository(requireContext())
        inviteService = GuardianInviteService(requireContext())

        renderGuardians()
        renderFamily()
        renderSession()
        renderShareLink(inviteService.lastUrl())

        root.findViewById<MaterialButton>(R.id.addGuardianButton).setOnClickListener {
            val button = it as MaterialButton
            button.isEnabled = false
            button.text = "Creating signed link…"
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    GuardianInviteCoordinator(requireContext()).createForActiveSession(ttlMinutes = 24 * 60)
                }.onSuccess { invite ->
                    inviteService.rememberUrl(invite.url)
                    renderShareLink(invite.url)
                    button.text = "Create another signed link"
                    Toast.makeText(requireContext(), "Guardian link created. Copy, share, or open it below.", Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    button.text = "Create signed Guardian link"
                    Toast.makeText(requireContext(), error.message ?: "Unable to create Guardian invite", Toast.LENGTH_LONG).show()
                }
                button.isEnabled = true
            }
        }

        root.findViewById<MaterialButton>(R.id.copyGuardianLinkButton).setOnClickListener {
            inviteService.lastUrl()?.let { url ->
                inviteService.copyUrl(url)
                Toast.makeText(requireContext(), "Guardian link copied", Toast.LENGTH_SHORT).show()
            }
        }
        root.findViewById<MaterialButton>(R.id.shareGuardianLinkButton).setOnClickListener {
            inviteService.lastUrl()?.let(inviteService::shareUrl)
        }

        SubscriptionManager.refresh()
        root.findViewById<MaterialButton>(R.id.createFamilyCircleButton).setOnClickListener {
            requirePro { findNavController().navigate(R.id.action_navigation_circle_to_family) }
        }

        vm.session.observe(viewLifecycleOwner) {
            renderSession()
            if (it == null || it.resolved) {
                inviteService.clearRememberedUrl()
                renderShareLink(null)
            }
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        renderShareLink(inviteService.lastUrl())
    }

    private fun requirePro(action: () -> Unit) {
        if (SubscriptionManager.isPro.value == true) action() else (activity as? MainActivity)?.showProPaywall()
    }

    private fun renderShareLink(url: String?) {
        val card = root.findViewById<View>(R.id.shareLinkCard)
        val text = root.findViewById<TextView>(R.id.shareLinkText)
        card.visibility = if (url.isNullOrBlank()) View.GONE else View.VISIBLE
        text.text = url.orEmpty()
    }

    private fun renderGuardians() {
        val list = root.findViewById<LinearLayout>(R.id.guardianList)
        list.removeAllViews()
        vm.guardians().forEach { guardian ->
            val card = MaterialCardView(requireContext()).apply {
                radius = 24f
                cardElevation = 0f
                setContentPadding(28, 24, 28, 24)
                addView(TextView(context).apply {
                    text = (if (guardian.primary) "★ " else "") + guardian.name +
                        "\n" + guardian.relation + " · " + guardian.channel
                    textSize = 16f
                })
            }
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })
        }
    }

    private fun renderSession() {
        val session = vm.session.value
        val summary = root.findViewById<TextView>(R.id.guardianSessionSummary)
        if (session == null || session.resolved) {
            summary.text = "No active Safety Session. Start one from Today, then sign in and create a signed Guardian link."
            return
        }
        val eta = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.expectedEndAt))
        summary.text =
            session.mode.label + "\nExpected safe: " + eta +
                "\nState: " + session.state.name.replace('_', ' ') +
                "\nBattery: " + session.batteryPercent + "% (native device)" +
                "\n\nA real Guardian link is server-signed and expires automatically. Precise location follows your privacy rule."
    }

    private fun renderFamily() {
        val circles = familyRepo.circles()
        val summary = root.findViewById<TextView>(R.id.familySummary)
        if (circles.isEmpty()) {
            summary.text = "No Family Circle yet. Create one for shared routines and multi-Guardian protection."
        } else {
            val circle = circles.first()
            summary.text = circle.name + " · " + circle.members.size + " member(s)" +
                if (circle.members.isEmpty()) "\nOpen the circle to add your first member." else
                    "\n" + circle.members.joinToString("\n") { "• " + it.displayName + " · " + it.role }
        }
    }
}
