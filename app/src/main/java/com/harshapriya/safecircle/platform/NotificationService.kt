package com.harshapriya.safecircle.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
}
