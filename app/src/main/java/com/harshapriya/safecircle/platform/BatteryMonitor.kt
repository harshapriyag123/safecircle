package com.harshapriya.safecircle.platform

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryMonitor(private val context: Context) {
    fun currentPercent(): Int {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            return ((level * 100f) / scale).toInt().coerceIn(0, 100)
        }

        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (capacity in 0..100) capacity else 0
    }
}
