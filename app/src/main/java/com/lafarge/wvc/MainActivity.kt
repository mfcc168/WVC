package com.lafarge.wvc

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lafarge.wvc.ui.*
import com.lafarge.wvc.ui.theme.WVCTheme

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var profiles: ProfileStorageManager
    private var state by mutableStateOf(WvcState())
    private var message by mutableStateOf<String?>(null)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    private val locationRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        message = if (MonitoringSettings.hasLocation(this)) "Location access is ready. You can start monitoring." else "Precise location is needed to detect your Wi-Fi area."
    }
    private val notificationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        prefs = MonitoringSettings.prefs(this)
        profiles = ProfileStorageManager(this)
        profiles.migrateLegacyProfile()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        refresh()
        setContent {
            WVCTheme {
                WvcApp(
                    state = state, message = message, onMessageShown = { message = null },
                    onStart = ::startMonitoring, onStop = ::stopMonitoring,
                    onSetupAction = ::setupAction,
                    onSelect = { profiles.selectProfile(it); configurationChanged(); message = "Profile selected. Changes apply after a fresh scan." },
                    onSave = { profile, original ->
                        val error = profiles.saveProfile(profile, original)
                        if (error == null) {
                            if (profiles.getActiveProfileName() == profile.name.trim()) configurationChanged()
                            message = "Profile saved"
                        }
                        error
                    },
                    onDelete = { name ->
                        if (profiles.getActiveProfileName() == name) stopMonitoring()
                        profiles.deleteProfile(name)
                        message = "Profile deleted"
                    }
                )
            }
        }
    }

    override fun onResume() { super.onResume(); if (::prefs.isInitialized) refresh() }
    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    private fun refresh() {
        val notifications = getSystemService(NotificationManager::class.java)
        val channelVisible = notifications.getNotificationChannel(WiFiScanService.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        val items = mutableListOf(
            SetupItem("location", "Precise location", "Android requires this to read nearby Wi-Fi names. WVC does not store your coordinates.", MonitoringSettings.hasLocation(this), true, "Allow access"),
            SetupItem("locationServices", "Location services", "Keep the phone’s Location setting on so Wi-Fi scans can work.", MonitoringSettings.locationEnabled(this), true, "Open settings"),
            SetupItem("wifi", "Wi-Fi", "Wi-Fi must be on. You do not need to connect to the selected network.", applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled, true, "Open Wi-Fi"),
            SetupItem("notifications", "Monitoring notifications", "See scan status and recovery prompts without opening WVC.", NotificationManagerCompat.from(this).areNotificationsEnabled() && channelVisible, false, "Allow notifications"),
            SetupItem("dnd", "Silent-volume access", "Allow Do Not Disturb access for volume changes that enter or leave silent mode. WVC respects an active Do Not Disturb mode.", notifications.isNotificationPolicyAccessGranted, false, "Review access")
        )
        if (Build.VERSION.SDK_INT in 29..36) items.add(SetupItem("background", "Restart after reboot", "Choose “Allow all the time” for location so monitoring can restart when your phone reboots.", MonitoringSettings.hasBackgroundLocation(this), false, "Allow background access"))
        state = WvcState(profiles.loadProfiles(), profiles.getActiveProfileName(), prefs.getBoolean(MonitoringSettings.ENABLED, false),
            prefs.getString(MonitoringSettings.STATUS, "Choose a profile, then start monitoring.").orEmpty(), items, Build.VERSION.SDK_INT >= 37)
    }

    private fun configurationChanged() {
        if (prefs.getBoolean(MonitoringSettings.ENABLED, false)) MonitoringSettings.status(this, "Profile updated; waiting for a fresh Wi-Fi scan")
        refresh()
    }

    private fun startMonitoring() {
        if (profiles.getActiveProfile() == null) { message = "Choose a profile first."; return }
        if (!MonitoringSettings.hasLocation(this)) { requestLocation(); return }
        if (!MonitoringSettings.locationEnabled(this)) { setupAction("locationServices"); return }
        if (!applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled) { setupAction("wifi"); return }
        prefs.edit().putBoolean(MonitoringSettings.ENABLED, true).apply()
        try {
            stopService(Intent(this, WiFiScanService::class.java))
            getSystemService(NotificationManager::class.java).cancel(2)
            ContextCompat.startForegroundService(this, Intent(this, WiFiScanService::class.java))
            MonitoringSettings.status(this, "Starting; waiting for a fresh Wi-Fi scan")
        } catch (e: SecurityException) {
            prefs.edit().putBoolean(MonitoringSettings.ENABLED, false).apply()
            MonitoringSettings.status(this, "Startup denied; check precise location permission")
        } catch (e: IllegalStateException) {
            prefs.edit().putBoolean(MonitoringSettings.ENABLED, false).apply()
            MonitoringSettings.status(this, "Android blocked startup; tap Resume while WVC is open")
        }
    }

    private fun stopMonitoring() {
        prefs.edit().putBoolean(MonitoringSettings.ENABLED, false).apply()
        stopService(Intent(this, WiFiScanService::class.java))
        getSystemService(NotificationManager::class.java).cancel(2)
        MonitoringSettings.status(this, "Monitoring stopped. Your current volume is unchanged.")
    }

    private fun requestLocation() {
        if (prefs.getBoolean("LOCATION_REQUESTED", false) && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
        } else {
            prefs.edit().putBoolean("LOCATION_REQUESTED", true).apply()
            locationRequest.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun setupAction(id: String) {
        when (id) {
            "location" -> if (MonitoringSettings.hasLocation(this)) openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) else requestLocation()
            "locationServices" -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "wifi" -> openSettings(Settings.ACTION_WIFI_SETTINGS)
            "notifications" -> if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("NOTIFICATIONS_REQUESTED", false)) {
                prefs.edit().putBoolean("NOTIFICATIONS_REQUESTED", true).apply()
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            "dnd" -> openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "background" -> if (Build.VERSION.SDK_INT == 29 && MonitoringSettings.hasLocation(this) && !MonitoringSettings.hasBackgroundLocation(this)) {
                locationRequest.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            } else openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
            "battery" -> openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
        }
    }

    private fun openSettings(action: String, appDetails: Boolean = false) {
        val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (appDetails) intent.data = Uri.parse("package:$packageName")
        try { startActivity(intent) } catch (e: ActivityNotFoundException) { message = "Open Android Settings, then select WVC." }
    }
}
