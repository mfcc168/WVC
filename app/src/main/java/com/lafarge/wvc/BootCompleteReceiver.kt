package com.lafarge.wvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!MonitoringSettings.prefs(context).getBoolean(MonitoringSettings.ENABLED, false)) return
        if (android.os.Build.VERSION.SDK_INT >= 37) {
            val message = "Android 17 requires you to open WVC and tap Resume after reboot or update"
            MonitoringSettings.status(context, message)
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
        if (!MonitoringSettings.hasLocation(context) || !MonitoringSettings.hasBackgroundLocation(context) ||
            !MonitoringSettings.locationEnabled(context)) {
            MonitoringSettings.status(context, "Open WVC: precise and always-allowed location are needed after reboot")
            return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, WiFiScanService::class.java))
        } catch (e: SecurityException) {
            MonitoringSettings.status(context, "Open WVC to resume: permission unavailable at startup")
        } catch (e: IllegalStateException) {
            MonitoringSettings.status(context, "Android blocked automatic startup; open WVC to resume")
        }
    }
}
