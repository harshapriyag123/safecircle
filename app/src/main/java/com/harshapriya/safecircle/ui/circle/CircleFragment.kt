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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.ui.shared.SafetyViewModel

class CircleFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_circle, container, false)
        val vm = ViewModelProvider(requireActivity())[SafetyViewModel::class.java]
        val list = root.findViewById<LinearLayout>(R.id.guardianList)

        vm.guardians().forEach { guardian ->
            val card = MaterialCardView(requireContext()).apply {
                radius = 24f
                cardElevation = 0f
                setContentPadding(28, 24, 28, 24)
                val text = TextView(context).apply {
                    text = "${if (guardian.primary) "★ " else ""}${guardian.name}\n${guardian.relation} · ${guardian.channel}"
                    textSize = 16f
                }
                addView(text)
            }
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })
        }

        root.findViewById<MaterialButton>(R.id.addGuardianButton).setOnClickListener {
            Toast.makeText(requireContext(), "Hackathon demo: connect invite flow/backend next.", Toast.LENGTH_LONG).show()
        }
        return root
    }
}
