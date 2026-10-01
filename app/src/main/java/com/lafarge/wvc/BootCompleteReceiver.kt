package com.lafarge.wvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val storage = BootMonitoringSettings.storageContext(context)
        BootMonitoringSettings.sync(storage)
        if (!MonitoringSettings.prefs(storage).getBoolean(MonitoringSettings.ENABLED, false)) return
        android.util.Log.i("WvcMonitoring", "Boot event ${intent?.action} at ${android.os.SystemClock.elapsedRealtime()} ms")
        if (android.os.Build.VERSION.SDK_INT >= 37) {
            val message = "Open WVC and tap the Wi-Fi button to enable monitoring after reboot or update"
            MonitoringSettings.status(storage, message, "resume")
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(android.app.NotificationChannel(
                WiFiScanService.CHANNEL, "Wi-Fi monitoring", android.app.NotificationManager.IMPORTANCE_LOW))
            val open = android.app.PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.notify(2, androidx.core.app.NotificationCompat.Builder(context, WiFiScanService.CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Resume Wi-Fi volume control")
                    .setContentText(message).setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(open).setAutoCancel(true).build())
            }
            return
        }
        if (!MonitoringSettings.hasLocation(context) || !MonitoringSettings.hasBackgroundLocation(context)) {
            MonitoringSettings.status(storage, "Open WVC: precise and always-allowed location are needed after reboot", "resume")
            return
        }
        try {
            // Location/Wi-Fi switches may still be initializing; the service waits for their system broadcasts.
            ContextCompat.startForegroundService(storage, Intent(context, WiFiScanService::class.java))
        } catch (e: SecurityException) {
            MonitoringSettings.status(storage, "Open WVC and tap the Wi-Fi button: permission unavailable at startup", "resume")
        } catch (e: IllegalStateException) {
            MonitoringSettings.status(storage, "Android blocked automatic startup; open WVC and tap the Wi-Fi button", "resume")
        }
    }
}
