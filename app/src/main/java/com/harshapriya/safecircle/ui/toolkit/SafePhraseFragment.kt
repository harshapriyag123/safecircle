package com.harshapriya.safecircle.ui.toolkit

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R

class SafePhraseFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_safephrase, container, false)
        val prefs = requireContext().getSharedPreferences("safecircle_safephrase", Context.MODE_PRIVATE)
        val phrase = root.findViewById<EditText>(R.id.safePhraseInput)
        val test = root.findViewById<EditText>(R.id.safePhraseTestInput)
        val status = root.findViewById<TextView>(R.id.safePhraseStatus)
        phrase.setText(prefs.getString("phrase", "blue notebook"))

        root.findViewById<MaterialButton>(R.id.saveSafePhraseButton).setOnClickListener {
            val value = phrase.text.toString().trim()
            if (value.length < 4) {
                status.text = "Use at least 4 characters so accidental activation is less likely."
            } else {
                prefs.edit().putString("phrase", value).apply()
                status.text = "SafePhrase saved on this device. It is never displayed to Guardians."
                Toast.makeText(requireContext(), "SafePhrase saved", Toast.LENGTH_SHORT).show()
            }
        }

        root.findViewById<MaterialButton>(R.id.testSafePhraseButton).setOnClickListener {
            val saved = prefs.getString("phrase", "blue notebook") ?: "blue notebook"
            val matched = test.text.toString().trim().equals(saved, ignoreCase = true)
            status.text = if (matched) {
                "MATCHED ✓ Trusted-circle escalation would activate. This test does not contact anyone."
            } else {
                "No match. No escalation action was triggered."
            }
        }
        return root
    }
}
