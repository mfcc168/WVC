package com.lafarge.wvc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

class WiFiScanService : Service() {

    private lateinit var wifiManager: WifiManager
    private lateinit var audioManager: AudioManager
    private lateinit var scanReceiver: BroadcastReceiver
    private var isScanning = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var homeSSID: String = ""


    private val scanRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                try {
                    wifiManager.startScan()
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
                handler.postDelayed(this, 10000) // scan every 10 seconds
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        homeSSID = intent?.getStringExtra("HOME_SSID") ?: ""
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createNotificationChannel()
        setupReceiver()

        startForeground(1, createNotification("Scanning Wi-Fi..."))

        isScanning = true
        handler.post(scanRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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
            .setContentTitle("Wi-Fi Scan Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun setupReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val results: List<ScanResult> = try {
                    if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        wifiManager.scanResults
                    } else {
                        emptyList()
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    emptyList()
                }

                val isIndoor = results.any { it.SSID == homeSSID }

                val volumePercent = if (isIndoor) 50 else 100
                setVolume(volumePercent)

                val message = if (isIndoor)
                    "You are indoor, setting volume to $volumePercent%"
                else
                    "You are outdoor, setting volume to $volumePercent%"

                showNotification(message)
            }
        }

        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    private fun setVolume(percent: Int) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (percent / 100.0 * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }

    private fun showNotification(message: String) {
        val notification = NotificationCompat.Builder(this, "wifi_volume_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WiFi Volume Control")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        unregisterReceiver(scanReceiver)
        handler.removeCallbacks(scanRunnable)
    }
}
