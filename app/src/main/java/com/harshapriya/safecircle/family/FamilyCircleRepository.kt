package com.harshapriya.safecircle.family

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CircleMember(
    val id: String,
    val displayName: String,
    val role: String,
)

data class FamilyCircle(
    val id: String,
    val name: String,
    val members: List<CircleMember>,
)

class FamilyCircleRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_family", Context.MODE_PRIVATE)

    fun circles(): List<FamilyCircle> {
        val arr = JSONArray(prefs.getString("circles", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val membersJson = o.getJSONArray("members")
            val members = (0 until membersJson.length()).map { j ->
                val m = membersJson.getJSONObject(j)
                CircleMember(m.getString("id"), m.getString("name"), m.getString("role"))
            }
            FamilyCircle(o.getString("id"), o.getString("name"), members)
        }
    }

    fun create(name: String): FamilyCircle {
        val circle = FamilyCircle(UUID.randomUUID().toString(), name, emptyList())
        save(circles() + circle)
        return circle
    }

    fun addMember(circleId: String, displayName: String, role: String): FamilyCircle? {
        var updated: FamilyCircle? = null
        val all = circles().map { c ->
            if (c.id != circleId) c else c.copy(
                members = c.members + CircleMember(UUID.randomUUID().toString(), displayName, role)
            ).also { updated = it }
        }
        save(all)
        return updated
    }

    private fun save(circles: List<FamilyCircle>) {
        val arr = JSONArray()
        circles.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("members", JSONArray().apply {
                    c.members.forEach { m ->
                        put(JSONObject().apply {
                            put("id", m.id)
                            put("name", m.displayName)
                            put("role", m.role)
                        })
                    }
                })
            })
        }
        prefs.edit().putString("circles", arr.toString()).apply()
    }
}
