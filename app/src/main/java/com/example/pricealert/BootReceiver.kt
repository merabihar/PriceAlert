package com.example.pricealert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val active = c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("active", false)
        if (active) try { c.startForegroundService(Intent(c, AlertService::class.java)) } catch (e: Exception) {}
    }
}
