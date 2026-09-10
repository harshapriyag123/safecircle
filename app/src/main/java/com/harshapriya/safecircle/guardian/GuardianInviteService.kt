package com.harshapriya.safecircle.guardian

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

    fun create(role: String = "guardian", validHours: Int = 24): GuardianInvite {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..8).joinToString("") { alphabet[random.nextInt(alphabet.length)].toString() }
        return GuardianInvite(
            code = code,
            expiresAt = System.currentTimeMillis() + validHours * 60L * 60L * 1000L,
            role = role,
        )
    }

    fun share(invite: GuardianInvite) {
        val deepLink = "https://safecircle.app/join?code=${invite.code}"
        shareUrl(deepLink)
    }

    fun shareUrl(url: String) {
        val text = "Join my SafeCircle as a trusted Guardian.\n" + url +
            "\n\nThis session-scoped invite expires automatically."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SafeCircle Guardian invite")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startActivity(
            context,
            Intent.createChooser(intent, "Invite Guardian").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            null
        )
    }
}
