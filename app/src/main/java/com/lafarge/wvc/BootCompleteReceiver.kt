package com.lafarge.wvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("wifi_volume_prefs", Context.MODE_PRIVATE)
            val ssid = prefs.getString("HOME_SSID", "")
            if (!ssid.isNullOrEmpty()) {
                val serviceIntent = Intent(context, WiFiScanService::class.java).apply {
                    putExtra("HOME_SSID", ssid)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}