package com.harshapriya.safecircle.ui.family

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
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.family.FamilyCircle
import com.harshapriya.safecircle.family.FamilyCircleRepository

class FamilyCircleFragment : Fragment() {
    private lateinit var root: View
    private lateinit var repo: FamilyCircleRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        root = inflater.inflate(R.layout.fragment_family_circle, container, false)
        repo = FamilyCircleRepository(requireContext())

        root.findViewById<MaterialButton>(R.id.createCircleButton).setOnClickListener { createCircle() }
        root.findViewById<MaterialButton>(R.id.backFromFamilyButton).setOnClickListener {
            findNavController().popBackStack()
        }

        render()
        return root
    }

    private fun render() {
        val list = root.findViewById<LinearLayout>(R.id.familyCircleList)
        list.removeAllViews()
        val circles = repo.circles()

        root.findViewById<TextView>(R.id.familyEmptyState).visibility =
            if (circles.isEmpty()) View.VISIBLE else View.GONE

        circles.forEach { circle ->
            list.addView(circleCard(circle), LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 16
            })
        }
    }

    private fun circleCard(circle: FamilyCircle): View {
        return MaterialCardView(requireContext()).apply {
            radius = 28f
            cardElevation = 0f
            setContentPadding(28, 24, 28, 24)

            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            box.addView(TextView(context).apply {
                text = circle.name
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            box.addView(TextView(context).apply {
                text = if (circle.members.isEmpty()) {
                    "No members yet."
                } else {
                    circle.members.joinToString("\n") { "• " + it.displayName + " · " + it.role }
                }
                textSize = 15f
                setPadding(0, 14, 0, 14)
            })

            box.addView(MaterialButton(context).apply {
                text = "Add member"
                setOnClickListener { addMember(circle.id) }
            })

            addView(box)
        }
    }

    private fun createCircle() {
        val input = EditText(requireContext()).apply {
            hint = "Circle name"
            setText("My Family")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Create Family Circle")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "Circle name is required", Toast.LENGTH_SHORT).show()
                } else {
                    repo.create(name)
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addMember(circleId: String) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val name = EditText(requireContext()).apply { hint = "Member name" }
        val role = EditText(requireContext()).apply { hint = "Role"; setText("Guardian") }
        container.addView(name)
        container.addView(role)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Family member")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val displayName = name.text.toString().trim()
                if (displayName.isBlank()) {
                    Toast.makeText(requireContext(), "Member name is required", Toast.LENGTH_SHORT).show()
                } else {
                    repo.addMember(circleId, displayName, role.text.toString().ifBlank { "Guardian" })
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
