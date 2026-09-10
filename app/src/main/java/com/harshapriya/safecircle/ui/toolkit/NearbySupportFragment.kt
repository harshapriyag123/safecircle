package com.harshapriya.safecircle.ui.toolkit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R

class NearbySupportFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_nearby_support, container, false)
        val status = root.findViewById<TextView>(R.id.nearbyStatus)
        root.findViewById<MaterialButton>(R.id.refreshNearbyButton).setOnClickListener {
            val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            status.text = if (granted) {
                "Location permission is available. Demo support results are shown below; production lookup can replace them without storing coordinates."
            } else {
                "Location is not available. The screen remains usable with demo results and does not silently request continuous tracking."
            }
        }
        return root
    }
}
