package com.lafarge.wvc

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** User-enabled location foreground service. Scanning remains subject to Android throttling. */
class WiFiScanService : Service() {
    private lateinit var wifi: WifiManager
    private lateinit var audio: AudioManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var tracker = PresenceTracker()
    private var targetSsid = ""
    private var applied: AppliedSoundProfile? = null
    private var lastRequestMs = -SCAN_INTERVAL_MS
    private var lastStatus = ""

    private val scanTask = object : Runnable {
        override fun run() {
            requestScan()
            handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) consumeResults()
                else scanStatus("Scan unavailable; keeping current volume")
            } else {
                // Turning Wi-Fi/location off is not proof that the user left the area.
                requestScan()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = MonitoringSettings.prefs(this)
        wifi = applicationContext.getSystemService(WifiManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Wi-Fi monitoring", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.getBoolean(MonitoringSettings.ENABLED, false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, 1, notification("Waiting for a fresh Wi-Fi scan"),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (e: SecurityException) {
            MonitoringSettings.status(this, "Location permission required; open WVC to resume")
            stopSelf()
            return START_NOT_STICKY
        } catch (e: IllegalStateException) {
            MonitoringSettings.status(this, "Android blocked startup; open WVC to resume")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered) {
            // These are system-only broadcasts; no custom/exported app command receiver.
            ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(android.location.LocationManager.MODE_CHANGED_ACTION)
            }, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        handler.removeCallbacks(scanTask)
        handler.post(scanTask)
        return START_STICKY
    }

    private fun requestScan() {
        if (!prefs.getBoolean(MonitoringSettings.ENABLED, false)) return
        if (!MonitoringSettings.hasLocation(this) || !MonitoringSettings.locationEnabled(this)) {
            status("Paused: enable precise location and Location services", "location")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastRequestMs < SCAN_INTERVAL_MS) return
        lastRequestMs = now
        try {
            if (!wifi.isWifiEnabled) {
                status("Paused: turn on Wi-Fi")
                return
            }
            if (!wifi.startScan()) scanStatus("Scan throttled; waiting for Android, keeping current volume")
        } catch (e: SecurityException) {
            Log.w(TAG, "Wi-Fi scan request denied", e)
            status("Wi-Fi scan access denied; allow Precise location for WVC and keep Location on", "location")
        }
    }

    @Suppress("DEPRECATION")
    private fun consumeResults() {
        if (!prefs.getBoolean(MonitoringSettings.ENABLED, false)) return
        if (!MonitoringSettings.hasLocation(this) || !MonitoringSettings.locationEnabled(this)) return
        // Keep Wi-Fi permission failures separate from failures to change sound settings.
        val scanResults = try {
            if (!wifi.isWifiEnabled) return
            wifi.scanResults
        } catch (e: SecurityException) {
            Log.w(TAG, "Reading Wi-Fi scan results denied", e)
            status("Wi-Fi scan access denied; allow Precise location for WVC and keep Location on", "location")
            return
        }
        try {
            val profile = ProfileStorageManager(this).getActiveProfile()
            val ssid = profile?.ssid ?: prefs.getString("HOME_SSID", "").orEmpty()
            if (ssid.isBlank()) {
                status("Paused: select a profile with a Wi-Fi name")
                return
            }
            if (targetSsid != ssid) {
                targetSsid = ssid
                tracker = PresenceTracker()
                applied = null
            }
            val nowUs = SystemClock.elapsedRealtime() * 1000
            val results = scanResults.filter { nowUs - it.timestamp in 0..MAX_SCAN_AGE_US }
            // An empty result has no timestamp with which to establish freshness.
            if (results.isEmpty()) {
                scanStatus("No fresh Wi-Fi observations; keeping current volume")
                return
            }
            val indoor = tracker.observe(results.any { it.SSID == ssid }, results.maxOf { it.timestamp })
            if (indoor == null) {
                scanStatus("Confirming Wi-Fi area; keeping current volume")
                return
            }
            val ringKey = if (indoor) VolumeProfile.RINGTONE_INDOOR else VolumeProfile.RINGTONE_OUTDOOR
            val notificationKey = if (indoor) VolumeProfile.NOTIFICATION_INDOOR else VolumeProfile.NOTIFICATION_OUTDOOR
            val default = if (indoor) 50 else 100
            val ring = (profile?.volumes?.get(ringKey) ?: prefs.getInt("${ringKey}_VOLUME", default)).coerceIn(0, 100)
            val alerts = (profile?.volumes?.get(notificationKey) ?: prefs.getInt("${notificationKey}_VOLUME", default)).coerceIn(0, 100)
            val signature = AppliedSoundProfile(profile?.name ?: "Home", ssid, indoor, ring, alerts)
            if (signature != applied) {
                // Do not override a user's global DND mode. Android 15+ composes DND rules.
                val manager = getSystemService(NotificationManager::class.java)
                if (audio.isVolumeFixed) {
                    status("This phone does not allow apps to change sound volumes")
                    return
                }
                // Check both streams before changing either. Some phones link the streams;
                // even a low nonzero percentage can round down to a silent volume step.
                if (!manager.isNotificationPolicyAccessGranted && SoundControlAccess.needsPolicyAccess(audio, ring, alerts)) {
                    status(SoundControlAccess.REQUIRED_MESSAGE, "dnd")
                    return
                }
                if (manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                    status("Do Not Disturb is active; volume change deferred")
                    return
                }
                val beforeRing = audio.getStreamVolume(AudioManager.STREAM_RING)
                val beforeAlerts = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                setVolume(AudioManager.STREAM_RING, ring)
                setVolume(AudioManager.STREAM_NOTIFICATION, alerts)
                // Some devices link the streams: do not announce success if the second
                // write undid the first. Matching levels are required on those phones.
                if (audio.getStreamVolume(AudioManager.STREAM_RING) != targetVolume(AudioManager.STREAM_RING, ring)) {
                    status("Phone links sound volumes; use matching ringtone and notification levels")
                    return
                }
                applied = signature
                ProfileChangeNotifier.applied(this, signature,
                    beforeRing != audio.getStreamVolume(AudioManager.STREAM_RING) ||
                        beforeAlerts != audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
            }
            status(if (indoor) "Inside Wi-Fi area: $ssid" else "Outside Wi-Fi area: $ssid")
        } catch (e: SecurityException) {
            Log.w(TAG, "Changing sound settings denied", e)
            if (!getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
                status(SoundControlAccess.REQUIRED_MESSAGE, "dnd")
            } else {
                status("Android denied sound control; tap Resume monitoring. If it continues, check the phone's sound restrictions.")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Sound settings were not applied", e)
            status("Android blocked the volume change; open WVC and tap Resume")
        }
    }

    private fun targetVolume(stream: Int, percent: Int) = SoundControlAccess.targetVolume(audio, stream, percent)

    private fun setVolume(stream: Int, percent: Int) {
        val target = targetVolume(stream, percent)
        audio.setStreamVolume(stream, target, 0)
        // Android 17 may silently reject audio calls after a background service restart.
        check(audio.getStreamVolume(stream) == target) { "Volume change was not applied" }
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Wi-Fi Volume Control")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
    }
    private fun scanStatus(message: String) {
        // A throttled scan must not hide the permission action the user still needs.
        if (prefs.getString(MonitoringSettings.RECOVERY_ACTION, null) == null) status(message)
    }
    private fun status(message: String, recoveryAction: String? = null) {
        if (message == lastStatus && prefs.getString(MonitoringSettings.STATUS, null) == message &&
            prefs.getString(MonitoringSettings.RECOVERY_ACTION, null) == recoveryAction) return
        lastStatus = message
        MonitoringSettings.status(this, message, recoveryAction)
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                getSystemService(NotificationManager::class.java).notify(1, notification(message))
            } catch (e: SecurityException) {
                // Notification permission may be revoked between the check and notify.
                Log.w(TAG, "Monitoring notification denied", e)
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (registered) unregisterReceiver(receiver)
        super.onDestroy()
    }
    companion object {
        private const val TAG = "WvcMonitoring"
        const val CHANNEL = "wifi_monitoring_v2"
        // Android can throttle further; passive system scan broadcasts are also consumed.
        const val SCAN_INTERVAL_MS = 120_000L
        const val MAX_SCAN_AGE_US = 30_000_000L
    }
}
