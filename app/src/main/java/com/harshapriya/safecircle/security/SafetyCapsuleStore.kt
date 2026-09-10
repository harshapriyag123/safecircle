package com.harshapriya.safecircle.security

import android.content.Context
import android.util.Base64

class SafetyCapsuleStore(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_capsules", Context.MODE_PRIVATE)
    private val crypto = CryptoBox()

    fun put(sessionId: String, json: String, expiresAt: Long) {
        val encrypted = crypto.encrypt(json.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("data_$sessionId", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putLong("exp_$sessionId", expiresAt)
            .apply()
    }

    fun get(sessionId: String): String? {
        val expires = prefs.getLong("exp_$sessionId", 0L)
        if (expires == 0L || System.currentTimeMillis() > expires) {
            delete(sessionId)
            return null
        }
        val raw = prefs.getString("data_$sessionId", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            crypto.decrypt(bytes).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun delete(sessionId: String) {
        prefs.edit().remove("data_$sessionId").remove("exp_$sessionId").apply()
    }

    fun purgeExpired() {
        val ids = prefs.all.keys.filter { it.startsWith("exp_") }
        ids.forEach { key ->
            val expires = prefs.getLong(key, 0L)
            if (expires > 0L && System.currentTimeMillis() > expires) {
                delete(key.removePrefix("exp_"))
            }
        }
    }
}
