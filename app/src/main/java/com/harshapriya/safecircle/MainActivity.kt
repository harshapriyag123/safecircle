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
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallResult
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallResultHandler
import com.revenuecat.purchases.ui.revenuecatui.customercenter.ShowCustomerCenter

@OptIn(ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
class MainActivity : AppCompatActivity(), PaywallResultHandler {
    private lateinit var paywallLauncher: PaywallActivityLauncher
    private val customerCenterLauncher = registerForActivityResult(ShowCustomerCenter()) { }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paywallLauncher = PaywallActivityLauncher(this, this)

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
        paywallLauncher.launchIfNeeded(
            requiredEntitlementIdentifier = Constants.ENTITLEMENT_ID
        )
    }

    override fun onActivityResult(result: PaywallResult) {
        when (result) {
            is PaywallResult.Purchased -> Toast.makeText(this, "SafeCircle+ activated", Toast.LENGTH_SHORT).show()
            is PaywallResult.Restored -> Toast.makeText(this, "Purchase restored", Toast.LENGTH_SHORT).show()
            is PaywallResult.Error -> Toast.makeText(this, "Paywall error: ${result.error.message}", Toast.LENGTH_LONG).show()
            is PaywallResult.Cancelled -> Unit
        }
    }

    fun showCustomerCenter() {
        if (!Purchases.isConfigured) {
            Toast.makeText(this, "RevenueCat is not configured yet.", Toast.LENGTH_LONG).show()
            return
        }
        customerCenterLauncher.launch(Unit)
    }
}
