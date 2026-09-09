package com.harshapriya.safecircle.billing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.harshapriya.safecircle.data.Constants
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.restorePurchasesWith

object SubscriptionManager {
    private val _isPro = MutableLiveData(false)
    val isPro: LiveData<Boolean> = _isPro

    private val _status = MutableLiveData("Free")
    val status: LiveData<String> = _status

    fun update(info: CustomerInfo) {
        val active = info.entitlements.active[Constants.ENTITLEMENT_ID] != null
        _isPro.postValue(active)
        _status.postValue(if (active) "SafeCircle+ active" else "Free plan")
    }

    fun refresh(onError: (String) -> Unit = {}) {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { onError(it.message) },
            onSuccess =(::update),
        )
    }

    fun restore(onDone: (Boolean) -> Unit, onError: (String) -> Unit) {
        if (!Purchases.isConfigured) {
            onError("RevenueCat is not configured. Add REVENUECAT_API_KEY to local.properties.")
            return
        }
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { onError(it.message) },
            onSuccess = {
                update(it)
                onDone(it.entitlements.active[Constants.ENTITLEMENT_ID] != null)
            },
        )
    }
}
