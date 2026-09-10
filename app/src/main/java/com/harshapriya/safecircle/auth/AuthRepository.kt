package com.harshapriya.safecircle.auth

import android.content.Context
import com.harshapriya.safecircle.sync.NetworkConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthState(
    val userId: String,
    val accessToken: String,
    val email: String
)

class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_auth", Context.MODE_PRIVATE)

    fun state(): AuthState? {
        val userId = prefs.getString("user_id", null) ?: return null
        val token = prefs.getString("access_token", null) ?: return null
        val email = prefs.getString("email", "") ?: ""
        return AuthState(userId, token, email)
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    fun register(email: String, password: String): AuthState =
        authenticate("/v1/auth/register", email, password)

    fun login(email: String, password: String): AuthState =
        authenticate("/v1/auth/login", email, password)

    private fun authenticate(path: String, email: String, password: String): AuthState {
        require(NetworkConfig.baseUrl.isNotBlank()) { "Configure SAFECIRCLE_API_BASE_URL first." }
        val connection = URL(NetworkConfig.baseUrl + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Authentication failed: " + raw.take(180))
            val json = JSONObject(raw)
            val state = AuthState(
                userId = json.getString("user_id"),
                accessToken = json.getString("access_token"),
                email = email.trim()
            )
            prefs.edit()
                .putString("user_id", state.userId)
                .putString("access_token", state.accessToken)
                .putString("email", state.email)
                .apply()
            state
        } finally {
            connection.disconnect()
        }
    }
}
