package com.harshapriya.safecircle

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.harshapriya.safecircle.data.Constants
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.ui.revenuecatui.ExperimentalPreviewRevenueCatUIPurchasesAPI
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallActivityLauncher
import com.revenuecat.purchases.ui.revenuecatui.customercenter.ShowCustomerCenter

@OptIn(ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
class MainActivity : AppCompatActivity() {
    private val paywallLauncher by lazy { PaywallActivityLauncher(this) }
    private val customerCenterLauncher = registerForActivityResult(ShowCustomerCenter()) { }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        navView.setupWithNavController(navController)

        requestSafetyPermissions()
    }

    private fun requestSafetyPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    fun showProPaywall() {
        if (!Purchases.isConfigured) {
            Toast.makeText(this, "Add REVENUECAT_API_KEY to local.properties first.", Toast.LENGTH_LONG).show()
            return
        }
        paywallLauncher.launchIfNeeded(requiredEntitlementIdentifier = Constants.ENTITLEMENT_ID)
    }

    fun showCustomerCenter() {
        if (!Purchases.isConfigured) {
            Toast.makeText(this, "RevenueCat is not configured yet.", Toast.LENGTH_LONG).show()
            return
        }
        customerCenterLauncher.launch(Unit)
    }
}
