package com.harshapriya.safecircle.auth

import android.content.Context
import java.util.UUID

class AccountRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_account", Context.MODE_PRIVATE)

    fun stableUserId(): String {
        val existing = prefs.getString("user_id", null)
        if (existing != null) return existing
        val created = "sc_" + UUID.randomUUID().toString()
        prefs.edit().putString("user_id", created).apply()
        return created
    }
}
