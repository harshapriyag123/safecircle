package com.harshapriya.safecircle.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.harshapriya.safecircle.R

class NotificationService(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "safecircle_safety"
    }

    init {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Safety alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun show(id: Int, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_safe_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    fun showCheckIn(id: Int, title: String, body: String, sessionId: String) {
        val checkInIntent = Intent(context, SafetyActionReceiver::class.java).apply {
            action = SafetyActionReceiver.ACTION_CHECK_IN
            putExtra(SafetyActionReceiver.EXTRA_SESSION_ID, sessionId)
        }
        val safeIntent = Intent(context, SafetyActionReceiver::class.java).apply {
            action = SafetyActionReceiver.ACTION_MARK_SAFE
            putExtra(SafetyActionReceiver.EXTRA_SESSION_ID, sessionId)
        }

        val checkInPending = PendingIntent.getBroadcast(
            context,
            id + 1,
            checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val safePending = PendingIntent.getBroadcast(
            context,
            id + 2,
            safeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_safe_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Check in", checkInPending)
            .addAction(0, "I'm safe", safePending)
            .build()
        manager.notify(id, notification)
    }
}
