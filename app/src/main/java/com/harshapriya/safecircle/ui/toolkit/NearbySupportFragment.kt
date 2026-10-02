package com.harshapriya.safecircle.ui.toolkit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.harshapriya.safecircle.R

class NearbySupportFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_nearby_support, container, false)
        val status = root.findViewById<TextView>(R.id.nearbyStatus)
        val choices = listOf(R.id.refreshNearbyButton to "hospital near me", R.id.nearbyPolice to "police station near me", R.id.nearbyPharmacy to "pharmacy near me", R.id.nearbyClinic to "urgent care near me")
        choices.forEach { (id, query) ->
            root.findViewById<MaterialButton>(id).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
                try {
                    startActivity(intent)
                    status.text = "Opened your maps app. Check current listings, hours and directions there. SafeCircle did not collect your coordinates."
                } catch (_: android.content.ActivityNotFoundException) {
                    status.text = "No maps app is installed. Install a maps app or contact local support directly."
                }
            }
        }
        return root
    }
}
