package com.harshapriya.safecircle.guardian

import android.content.Context
import com.harshapriya.safecircle.model.Guardian
import org.json.JSONArray
import org.json.JSONObject

class GuardianRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_guardians", Context.MODE_PRIVATE)

    fun guardians(): List<Guardian> {
        val stored = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until stored.length()).mapNotNull { index ->
            val item = stored.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            if (name.isBlank()) return@mapNotNull null
            Guardian(
                name = name,
                relation = item.optString("relation", "Trusted contact"),
                channel = item.optString("channel", "Signed Guardian link"),
                primary = item.optBoolean("primary", index == 0),
            )
        }
    }

    fun savePrimary(name: String): Guardian {
        val cleanName = name.trim()
        require(cleanName.length >= 2) { "Enter the Guardian's name." }
        val guardian = Guardian(cleanName, "Trusted contact", "Signed Guardian link", primary = true)
        val existing = guardians().filterNot { it.name.equals(cleanName, ignoreCase = true) }
        val all = listOf(guardian) + existing.map { it.copy(primary = false) }
        val json = JSONArray().apply {
            all.forEach { item ->
                put(JSONObject().apply {
                    put("name", item.name)
                    put("relation", item.relation)
                    put("channel", item.channel)
                    put("primary", item.primary)
                })
            }
        }
        prefs.edit().putString(KEY, json.toString()).apply()
        return guardian
    }

    companion object { private const val KEY = "guardians" }
}
