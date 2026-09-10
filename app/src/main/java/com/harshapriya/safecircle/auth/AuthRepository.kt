package com.harshapriya.safecircle.auth

import android.content.Context
import android.util.Patterns
import com.harshapriya.safecircle.sync.NetworkConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthState(
    val userId: String,
    val accessToken: String,
    val email: String,
    val displayName: String = ""
)

class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("safecircle_auth", Context.MODE_PRIVATE)

    fun state(): AuthState? {
        val userId = prefs.getString("user_id", null) ?: return null
        val token = prefs.getString("access_token", null) ?: return null
        val email = prefs.getString("email", "") ?: ""
        val displayName = prefs.getString("display_name", "") ?: ""
        return AuthState(userId, token, email, displayName)
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    fun register(
        displayName: String,
        email: String,
        password: String,
        confirmPassword: String,
        acceptedTerms: Boolean
    ): AuthState {
        validateEmail(email)
        validatePassword(password)
        require(displayName.trim().length >= 2) { "Enter your preferred name." }
        require(password == confirmPassword) { "Passwords do not match." }
        require(acceptedTerms) { "Accept the Terms and Privacy Policy to continue." }
        return authenticate("/v1/auth/register", email, password, displayName.trim())
    }

    fun login(email: String, password: String): AuthState {
        validateEmail(email)
        require(password.isNotBlank()) { "Enter your password." }
        return authenticate("/v1/auth/login", email, password, null)
    }

    private fun validateEmail(email: String) {
        require(Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
    }

    private fun validatePassword(password: String) {
        require(password.length >= 10) { "Password must be at least 10 characters." }
        require(password.any(Char::isUpperCase)) { "Password needs an uppercase letter." }
        require(password.any(Char::isLowerCase)) { "Password needs a lowercase letter." }
        require(password.any(Char::isDigit)) { "Password needs a number." }
    }

    private fun authenticate(path: String, email: String, password: String, displayName: String?): AuthState {
        require(NetworkConfig.baseUrl.isNotBlank()) { "SafeCircle backend is not configured." }
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
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull().orEmpty()
                throw IllegalStateException(detail.ifBlank { "Authentication failed (HTTP $code)" })
            }
            val json = JSONObject(raw)
            val savedName = displayName ?: prefs.getString("display_name", "").orEmpty()
            val auth = AuthState(
                userId = json.getString("user_id"),
                accessToken = json.getString("access_token"),
                email = email.trim(),
                displayName = savedName
            )
            prefs.edit()
                .putString("user_id", auth.userId)
                .putString("access_token", auth.accessToken)
                .putString("email", auth.email)
                .putString("display_name", auth.displayName)
                .apply()
            auth
        } finally {
            connection.disconnect()
        }
    }
}
