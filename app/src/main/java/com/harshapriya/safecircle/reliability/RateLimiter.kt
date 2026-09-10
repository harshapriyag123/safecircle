package com.harshapriya.safecircle.reliability

import android.content.Context

class RateLimiter(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_rate_limit", Context.MODE_PRIVATE)

    fun allow(key: String, minIntervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < minIntervalMs) return false
        prefs.edit().putLong(key, now).apply()
        return true
    }
}
