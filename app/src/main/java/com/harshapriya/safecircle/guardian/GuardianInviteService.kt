package com.harshapriya.safecircle.guardian

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.security.SecureRandom

data class GuardianInvite(
    val code: String,
    val expiresAt: Long,
    val role: String,
)

class GuardianInviteService(private val context: Context) {
    private val random = SecureRandom()
    private val prefs = context.getSharedPreferences("safecircle_guardian_share", Context.MODE_PRIVATE)

    fun create(role: String = "guardian", validHours: Int = 24): GuardianInvite {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..8).joinToString("") { alphabet[random.nextInt(alphabet.length)].toString() }
        return GuardianInvite(
            code = code,
            expiresAt = System.currentTimeMillis() + validHours * 60L * 60L * 1000L,
            role = role,
        )
    }

    fun rememberUrl(url: String) {
        prefs.edit().putString("last_url", url).apply()
    }

    fun lastUrl(): String? = prefs.getString("last_url", null)

    fun clearRememberedUrl() {
        prefs.edit().remove("last_url").apply()
    }

    fun share(invite: GuardianInvite) {
        val deepLink = "https://safecircle.app/join?code=${invite.code}"
        shareUrl(deepLink)
    }

    fun copyUrl(url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SafeCircle Guardian invite", url))
    }

    fun shareUrl(url: String) {
        rememberUrl(url)
        val text = "You’re invited to be my temporary SafeCircle Guardian.\n$url\n\nThis signed, session-scoped invitation expires automatically."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SafeCircle Guardian invitation")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startActivity(
            context,
            Intent.createChooser(intent, "Share SafeCircle Guardian invitation").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            null
        )
    }
}
