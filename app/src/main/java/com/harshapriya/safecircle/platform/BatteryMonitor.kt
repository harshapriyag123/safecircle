package com.harshapriya.safecircle.platform

import android.content.Context
import android.os.BatteryManager

class BatteryMonitor(private val context: Context) {
    fun currentPercent(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
}
