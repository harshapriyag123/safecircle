package com.harshapriya.safecircle.automation

import android.content.Context
import com.harshapriya.safecircle.model.EscalationAction
import com.harshapriya.safecircle.model.TriggerType
import org.json.JSONArray
import org.json.JSONObject

class AutomationRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_automations", Context.MODE_PRIVATE)

    fun all(): List<AutomationRule> {
        val arr = JSONArray(prefs.getString("rules", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val actionsJson = o.getJSONArray("actions")
            AutomationRule(
                id = o.getString("id"),
                name = o.getString("name"),
                trigger = TriggerType.valueOf(o.getString("trigger")),
                thresholdMinutes = o.getInt("threshold"),
                actions = (0 until actionsJson.length()).map { j ->
                    EscalationAction.valueOf(actionsJson.getString(j))
                },
                enabled = o.getBoolean("enabled")
            )
        }
    }

    fun upsert(rule: AutomationRule) {
        save(all().filterNot { it.id == rule.id } + rule)
    }

    fun delete(id: String) = save(all().filterNot { it.id == id })

    private fun save(rules: List<AutomationRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("trigger", r.trigger.name)
                put("threshold", r.thresholdMinutes)
                put("enabled", r.enabled)
                put("actions", JSONArray().apply { r.actions.forEach { put(it.name) } })
            })
        }
        prefs.edit().putString("rules", arr.toString()).apply()
    }
}
