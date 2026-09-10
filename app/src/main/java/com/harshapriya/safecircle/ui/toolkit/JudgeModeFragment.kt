package com.harshapriya.safecircle.ui.toolkit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.demo.DemoScenarioManager

class JudgeModeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_judge_mode, container, false)
        val demo = DemoScenarioManager(requireContext())
        val status = root.findViewById<TextView>(R.id.judgeStatus)

        fun refresh() {
            val session = SafetyRepository(requireContext()).currentSession()
            status.text = when {
                session == null -> "Ready. Seed the judge demo to create a Walk Home session."
                session.resolved -> "RESOLVED ✓ Session closed and temporary sharing ended."
                else -> "${session.state.name.replace('_', ' ')} · ${session.mode.label} · ${session.batteryPercent}% battery"
            }
        }

        root.findViewById<MaterialButton>(R.id.judgeSeedButton).setOnClickListener {
            demo.seedJudgeDemo(); refresh()
        }
        root.findViewById<MaterialButton>(R.id.judgeConcernButton).setOnClickListener {
            demo.simulateConcern(); refresh()
        }
        root.findViewById<MaterialButton>(R.id.judgeResolveButton).setOnClickListener {
            demo.resolveDemo(); refresh()
        }
        refresh()
        return root
    }
}
