package com.harshapriya.safecircle.data

import com.harshapriya.safecircle.BuildConfig

object Constants {
    const val ENTITLEMENT_ID = "safecircle_pro"
    const val OFFERING_ID = "default"
    const val MONTHLY_PACKAGE = "monthly"
    const val YEARLY_PACKAGE = "yearly"
    val revenueCatApiKey: String get() = BuildConfig.REVENUECAT_API_KEY
}
