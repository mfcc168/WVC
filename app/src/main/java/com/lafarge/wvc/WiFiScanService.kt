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
        homeSSID = intent?.getStringExtra("HOME_SSID") ?: ""
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    private fun setupReceivers() {
        // Receiver for scan results
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

        // Receiver for Wi-Fi state changes
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                        startWifiScan() // Wi-Fi turned on, start scan
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
                // You could retry or just wait for next scan
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleScanResults(results: List<ScanResult>) {
        val isIndoor = results.any { it.SSID == homeSSID }

        val indoorVolumePercent = prefs.getInt("INDOOR_VOLUME", 50)
        val outdoorVolumePercent = prefs.getInt("OUTDOOR_VOLUME", 100)

        val targetVolumePercent = if (isIndoor) indoorVolumePercent else outdoorVolumePercent

        setVolume(targetVolumePercent)
    }

    private fun setVolume(percent: Int) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (percent / 100.0 * maxVolume).toInt()

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
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
            e.printStackTrace() // In case receiver was already unregistered
        }
    }
}
