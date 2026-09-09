package com.harshapriya.safecircle

import android.app.Application
import android.util.Log
import com.harshapriya.safecircle.billing.SubscriptionManager
import com.harshapriya.safecircle.data.Constants
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Purchases.logLevel = LogLevel.DEBUG

        val key = Constants.revenueCatApiKey
        if (key.isBlank()) {
            Log.w("SafeCircle", "RevenueCat key missing. Add REVENUECAT_API_KEY to local.properties.")
            return
        }

        Purchases.configure(
            PurchasesConfiguration.Builder(this, key).build()
        )
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener {
            SubscriptionManager.update(it)
        }
        SubscriptionManager.refresh()
    }
}
