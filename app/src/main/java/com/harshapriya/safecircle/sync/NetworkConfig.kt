package com.harshapriya.safecircle.sync

import com.harshapriya.safecircle.BuildConfig

object NetworkConfig {
    val baseUrl: String get() = BuildConfig.SAFECIRCLE_API_BASE_URL.trim().trimEnd('/')
    val demoToken: String get() = BuildConfig.SAFECIRCLE_DEMO_API_TOKEN.trim()
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && demoToken.isNotBlank()
}
