package com.harshapriya.safecircle.ui.circle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.harshapriya.safecircle.MainActivity
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.billing.SubscriptionManager
import com.harshapriya.safecircle.family.FamilyCircleRepository
import com.harshapriya.safecircle.guardian.GuardianInviteService
import com.harshapriya.safecircle.ui.shared.SafetyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CircleFragment : Fragment() {
    private lateinit var root: View
    private lateinit var vm: SafetyViewModel
    private lateinit var familyRepo: FamilyCircleRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_circle, container, false)
        vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]
        familyRepo = FamilyCircleRepository(requireContext())

        renderGuardians()
        renderFamily()
        renderSession()

        root.findViewById<MaterialButton>(R.id.addGuardianButton).setOnClickListener {
            val service = GuardianInviteService(requireContext())
            val invite = service.create()
            service.share(invite)
        }

        SubscriptionManager.refresh()
        root.findViewById<MaterialButton>(R.id.createFamilyCircleButton).setOnClickListener {
            requirePro {
                findNavController().navigate(R.id.action_navigation_circle_to_family)
            }
        }

        vm.session.observe(viewLifecycleOwner) { renderSession() }
        return root
    }

    private fun requirePro(action: () -> Unit) {
        if (SubscriptionManager.isPro.value == true) {
            action()
        } else {
            (activity as? MainActivity)?.showProPaywall()
        }
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
            summary.text = "No active Safety Session. Start one from Today to create temporary Guardian visibility."
            return
        }
        val eta = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(session.expectedEndAt))
        summary.text =
            session.mode.label + "\nExpected safe: " + eta +
                "\nState: " + session.state.name.replace('_', ' ') +
                "\nBattery: " + session.batteryPercent + "%" +
                "\n\nGuardian view is designed to expose status first and precise location only according to your privacy rule."
    }

    private fun renderFamily() {
        val circles = familyRepo.circles()
        val summary = root.findViewById<TextView>(R.id.familySummary)
        if (circles.isEmpty()) {
            summary.text = "No Family Circle yet. Create one for shared routines and multi-Guardian protection."
        } else {
            val circle = circles.first()
            summary.text = circle.name + " · " + circle.members.size + " member(s)" +
                if (circle.members.isEmpty()) "\nTap below to add your first member." else
                    "\n" + circle.members.joinToString("\n") { "• " + it.displayName + " · " + it.role }
        }
    }

    private fun showAddFamilyMember(circleId: String) {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val name = EditText(requireContext()).apply { hint = "Member name" }
        val role = EditText(requireContext()).apply { hint = "Role"; setText("Guardian") }
        box.addView(name)
        box.addView(role)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Family Circle member")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val displayName = name.text.toString().trim()
                if (displayName.isBlank()) {
                    Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
                } else {
                    familyRepo.addMember(circleId, displayName, role.text.toString().ifBlank { "Guardian" })
                    renderFamily()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
