package com.lafarge.wvc

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class WiFiScanService : Service() {

    private lateinit var wifiManager: WifiManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var homeSSID: String = ""
    private var isScanning = false

    private lateinit var scanReceiver: BroadcastReceiver
    private lateinit var wifiStateReceiver: BroadcastReceiver

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                startWifiScan()
                handler.postDelayed(this, 10000) // every 10 seconds
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        prefs = getSharedPreferences("wifi_volume_prefs", MODE_PRIVATE)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createNotificationChannel()
        startForeground(1, createNotification("Scanning Wi-Fi..."))

        setupReceivers()

        isScanning = true
        startWifiScan()
        handler.post(scanRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        homeSSID = intent?.getStringExtra("HOME_SSID") ?:
                prefs.getString("HOME_SSID", "") ?: ""
        return START_REDELIVER_INTENT
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(this, WiFiScanService::class.java).apply {
            putExtra("HOME_SSID", homeSSID)
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getService(
                this,
                0,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                0,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000, // 1 second delay
            pendingIntent
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "wifi_volume_channel",
            "WiFi Volume Control",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, "wifi_volume_channel")
            .setContentTitle("Wi-Fi Volume Control Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun setupReceivers() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val results = wifiManager.scanResults
                        handleScanResults(results)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                        startWifiScan()
                    }
                }
            }
        }
        registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
    }

    private fun startWifiScan() {
        try {
            val success = wifiManager.startScan()
            if (!success) {
                // Scan failed (could be throttled or Wi-Fi off)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleScanResults(results: List<ScanResult>) {
        val profileManager = ProfileStorageManager(this)
        val activeProfile = profileManager.getActiveProfile()
        if (activeProfile == null) {
            // Fallback to legacy behavior
            handleLegacyScanResults(results)
            return
        }
        val isIndoor = results.any { it.SSID == activeProfile.ssid }
        val volumes = activeProfile.volumes

        if (isIndoor) {
            // Indoor volumes
//            setStreamVolume(AudioManager.STREAM_MUSIC, volumes[VolumeProfile.MEDIA_INDOOR] ?: 50)
            setStreamVolume(AudioManager.STREAM_RING, volumes[VolumeProfile.RINGTONE_INDOOR] ?: 50)
            setStreamVolume(AudioManager.STREAM_NOTIFICATION, volumes[VolumeProfile.NOTIFICATION_INDOOR] ?: 50)
//            setStreamVolume(AudioManager.STREAM_SYSTEM, volumes[VolumeProfile.SYSTEM_INDOOR] ?: 50)
//            setStreamVolume(AudioManager.STREAM_VOICE_CALL, volumes[VolumeProfile.CALL_INDOOR] ?: 50)
//            setStreamVolume(AudioManager.STREAM_ALARM, volumes[VolumeProfile.ALARM_INDOOR] ?: 50)
        } else {
            // Outdoor volumes
//            setStreamVolume(AudioManager.STREAM_MUSIC, volumes[VolumeProfile.MEDIA_OUTDOOR] ?: 100)
            setStreamVolume(AudioManager.STREAM_RING, volumes[VolumeProfile.RINGTONE_OUTDOOR] ?: 100)
            setStreamVolume(AudioManager.STREAM_NOTIFICATION, volumes[VolumeProfile.NOTIFICATION_OUTDOOR] ?: 100)
//            setStreamVolume(AudioManager.STREAM_SYSTEM, volumes[VolumeProfile.SYSTEM_OUTDOOR] ?: 100)
//            setStreamVolume(AudioManager.STREAM_VOICE_CALL, volumes[VolumeProfile.CALL_OUTDOOR] ?: 100)
//            setStreamVolume(AudioManager.STREAM_ALARM, volumes[VolumeProfile.ALARM_OUTDOOR] ?: 100)
        }
    }

    private fun handleLegacyScanResults(results: List<ScanResult>) {
        val isIndoor = results.any { it.SSID == homeSSID }
        val prefs = getSharedPreferences("wifi_volume_prefs", MODE_PRIVATE)

        if (isIndoor) {
//            setStreamVolume(AudioManager.STREAM_MUSIC, prefs.getInt("MEDIA_INDOOR_VOLUME", 50))
            setStreamVolume(AudioManager.STREAM_RING, prefs.getInt("RINGTONE_INDOOR_VOLUME", 50))
            setStreamVolume(AudioManager.STREAM_NOTIFICATION, prefs.getInt("NOTIFICATION_INDOOR_VOLUME", 50))
//            setStreamVolume(AudioManager.STREAM_SYSTEM, prefs.getInt("SYSTEM_INDOOR_VOLUME", 50))
//            setStreamVolume(AudioManager.STREAM_VOICE_CALL, prefs.getInt("CALL_INDOOR_VOLUME", 50))
//            setStreamVolume(AudioManager.STREAM_ALARM, prefs.getInt("ALARM_INDOOR_VOLUME", 50))
        } else {
//            setStreamVolume(AudioManager.STREAM_MUSIC, prefs.getInt("MEDIA_OUTDOOR_VOLUME", 100))
            setStreamVolume(AudioManager.STREAM_RING, prefs.getInt("RINGTONE_OUTDOOR_VOLUME", 100))
            setStreamVolume(AudioManager.STREAM_NOTIFICATION, prefs.getInt("NOTIFICATION_OUTDOOR_VOLUME", 100))
//            setStreamVolume(AudioManager.STREAM_SYSTEM, prefs.getInt("SYSTEM_OUTDOOR_VOLUME", 100))
//            setStreamVolume(AudioManager.STREAM_VOICE_CALL, prefs.getInt("CALL_OUTDOOR_VOLUME", 100))
//            setStreamVolume(AudioManager.STREAM_ALARM, prefs.getInt("ALARM_OUTDOOR_VOLUME", 100))
        }
    }

    private fun setStreamVolume(streamType: Int, percent: Int) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            val targetVolume = (percent / 100.0 * maxVolume).toInt()
            audioManager.setStreamVolume(streamType, targetVolume, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        handler.removeCallbacks(scanRunnable)

        try {
            unregisterReceiver(scanReceiver)
            unregisterReceiver(wifiStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}