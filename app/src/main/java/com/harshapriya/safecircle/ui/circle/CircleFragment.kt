package com.harshapriya.safecircle.ui.circle

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
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
import com.harshapriya.safecircle.guardian.GuardianJourneyActivity
import com.harshapriya.safecircle.guardian.GuardianJourneyStatus
import com.harshapriya.safecircle.guardian.GuardianRepository
import com.harshapriya.safecircle.guardian.GuardianTimelineCoordinator
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
    private lateinit var guardianRepo: GuardianRepository
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunning = false
    private val refreshGuardianActivity = object : Runnable {
        override fun run() {
            refreshActivity()
            handler.postDelayed(this, 3_000L)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_circle, container, false)
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]
        familyRepo = FamilyCircleRepository(requireContext())
        inviteService = GuardianInviteService(requireContext())
        guardianRepo = GuardianRepository(requireContext())

        renderGuardians()
        renderFamily()
        renderSession()
        renderShareLink(inviteService.lastUrl())

        root.findViewById<MaterialButton>(R.id.addGuardianButton).setOnClickListener {
            val button = it as MaterialButton
            val name = root.findViewById<EditText>(R.id.guardianNameInput).text.toString().trim()
            if (name.length < 2) {
                Toast.makeText(requireContext(), "Enter the Guardian's name first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            button.isEnabled = false
            button.text = "Creating signed link…"
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    GuardianInviteCoordinator(requireContext()).createForActiveSession(ttlMinutes = 24 * 60)
                }.onSuccess { invite ->
                    guardianRepo.savePrimary(name)
                    inviteService.rememberUrl(invite.url)
                    renderShareLink(invite.url)
                    renderGuardians()
                    renderActivity(GuardianJourneyActivity(GuardianJourneyStatus.PENDING))
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
        handler.removeCallbacks(refreshGuardianActivity)
        handler.post(refreshGuardianActivity)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshGuardianActivity)
        super.onPause()
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
        val guardians = vm.guardians()
        if (guardians.isEmpty()) {
            list.addView(TextView(requireContext()).apply {
                text = "No Guardian saved yet. Add a real trusted contact before sharing this journey."
            })
            return
        }
        guardians.forEach { guardian ->
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

    private fun refreshActivity() {
        val session = vm.session.value ?: return renderActivity(GuardianJourneyActivity(GuardianJourneyStatus.NOT_SHARED))
        val hasInvite = !inviteService.lastUrl().isNullOrBlank()
        if (!hasInvite) {
            return renderActivity(
                GuardianJourneyActivity(if (session.resolved) GuardianJourneyStatus.RESOLVED else GuardianJourneyStatus.NOT_SHARED)
            )
        }
        if (refreshRunning) return
        refreshRunning = true
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                GuardianTimelineCoordinator(requireContext()).activity(session.id, session.resolved, hasInvite)
            }.onSuccess(::renderActivity)
                .onFailure { renderActivity(GuardianJourneyActivity(GuardianJourneyStatus.SYNC_FAILED)) }
            refreshRunning = false
        }
    }

    private fun renderActivity(activity: GuardianJourneyActivity) {
        val time = activity.occurredAt?.let {
            SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(it))
        }
        root.findViewById<TextView>(R.id.guardianActivityStatus).text = when (activity.status) {
            GuardianJourneyStatus.NOT_SHARED -> "Not shared · Create a signed link when your journey is active."
            GuardianJourneyStatus.PENDING -> "Pending · Waiting for your Guardian to open the link."
            GuardianJourneyStatus.ACKNOWLEDGED -> "Acknowledged${time?.let { " · $it" }.orEmpty()} · Your Guardian is watching."
            GuardianJourneyStatus.CHECK_IN_REQUESTED -> "Check-in requested${time?.let { " · $it" }.orEmpty()} · Respond from Today."
            GuardianJourneyStatus.RESOLVED -> "Resolved · Monitoring and Guardian actions have ended."
            GuardianJourneyStatus.SYNC_FAILED -> "Sync failed · Status was not confirmed. Check your connection and retry."
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
