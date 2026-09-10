package com.harshapriya.safecircle.ui.toolkit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R

class SafetyToolkitFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_safety_toolkit, container, false)
        root.findViewById<MaterialButton>(R.id.toolkitSafePhrase).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_toolkit_to_safephrase)
        }
        root.findViewById<MaterialButton>(R.id.toolkitFakeCall).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_toolkit_to_fake_call)
        }
        root.findViewById<MaterialButton>(R.id.toolkitNearby).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_toolkit_to_nearby)
        }
        root.findViewById<MaterialButton>(R.id.toolkitReceipt).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_toolkit_to_receipt)
        }
        root.findViewById<MaterialButton>(R.id.toolkitJudge).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_toolkit_to_judge)
        }
        return root
    }
}
