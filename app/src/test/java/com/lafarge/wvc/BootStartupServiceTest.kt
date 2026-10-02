package com.lafarge.wvc

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [VolumeReconciliationServiceTest.RecordingAudio::class,
    VolumeReconciliationServiceTest.ThrottledWifi::class])
@LooperMode(LooperMode.Mode.PAUSED)
class BootStartupServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val deviceContext = context.createDeviceProtectedStorageContext()
    private val prefs = MonitoringSettings.prefs(context)
    private val snapshot = MonitoringSettings.prefs(deviceContext)
    private val user = shadowOf(context.getSystemService(UserManager::class.java))
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val main = shadowOf(Looper.getMainLooper())
    private var controller: ServiceController<WiFiScanService>? = null
    private val home = VolumeProfile("Home", "Home_WiFi", VolumeProfile.defaultVolumeMap() +
        mapOf(VolumeProfile.RINGTONE_INDOOR to 25, VolumeProfile.NOTIFICATION_INDOOR to 50))

    @Before fun prepare() {
        user.setUserUnlocked(true)
        prefs.edit().clear().commit()
        snapshot.edit().clear().commit()
        ProfileStorageManager(context).saveProfile(home, null)
        MonitoringSettings.setEnabled(context, true)
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        wifi.isWifiEnabled = true
        shadowOf(wifi).setScanResults(emptyList())
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        VolumeReconciliationServiceTest.RecordingAudio.linked = false
        VolumeReconciliationServiceTest.RecordingAudio.ignoredStream = -1
        manualLevels()
        VolumeReconciliationServiceTest.ThrottledWifi.requests = 0
        manager.cancelAll()
        main.idleFor(Duration.ofSeconds(60))
    }

    @After fun finish() { controller?.destroy() }

    private fun start() {
        controller = Robolectric.buildService(WiFiScanService::class.java).create()
        controller!!.get().onStartCommand(Intent(), 0, 1)
        main.idle()
    }

    private fun observations(ssid: String = home.ssid, ageMs: Long = 0) {
        shadowOf(wifi).setScanResults(listOf(ScanResult().apply {
            SSID = ssid
            timestamp = (SystemClock.elapsedRealtime() - ageMs) * 1000
        }))
    }

    private fun broadcastScan() {
        context.sendBroadcast(Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true))
        main.idle()
    }

    private fun manualLevels() {
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
        audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0)
        VolumeReconciliationServiceTest.RecordingAudio.writes = 0
    }

    private fun assertLevels(ring: Int = 25, alerts: Int = 50) {
        assertEquals(SoundControlAccess.targetVolume(audio, AudioManager.STREAM_RING, ring), audio.getStreamVolume(AudioManager.STREAM_RING))
        assertEquals(SoundControlAccess.targetVolume(audio, AudioManager.STREAM_NOTIFICATION, alerts), audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
    }

    @Test fun recentMatchingObservationAppliesImmediatelyWithoutScanBroadcast() {
        observations(ageMs = 1_000)
        start()
        assertLevels()
        assertEquals(2, VolumeReconciliationServiceTest.RecordingAudio.writes)
        assertNotNull(shadowOf(manager).getNotification(1))
        assertNotNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        assertEquals(1, VolumeReconciliationServiceTest.ThrottledWifi.requests)
    }

    @Test fun staleMatchingObservationWaitsForFreshSuccessfulScan() {
        observations(ageMs = WiFiScanService.MAX_SCAN_AGE_US / 1000 + 1)
        start()
        assertEquals(0, VolumeReconciliationServiceTest.RecordingAudio.writes)
        assertLevels(100, 100)
        assertNotNull(shadowOf(manager).getNotification(1))
        observations()
        broadcastScan()
        assertLevels()
    }

    @Test fun cachedMissDoesNotCountTowardsOutdoorConfirmation() {
        observations("Other_WiFi")
        start()
        assertEquals(0, VolumeReconciliationServiceTest.RecordingAudio.writes)
        // One subsequent genuine miss must still leave the area unknown.
        main.idleFor(Duration.ofMillis(1))
        observations("Other_WiFi")
        broadcastScan()
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        main.idleFor(Duration.ofMillis(1))
        observations("Other_WiFi")
        broadcastScan()
        assertNotNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        assertTrue(prefs.getString(MonitoringSettings.STATUS, "")!!.startsWith("Outside"))
    }

    @Test fun lockedServiceUsesSnapshotAndCorrectsManualVolumeChanges() {
        user.setUserUnlocked(false)
        observations()
        start()
        assertLevels()
        assertTrue(snapshot.getString(MonitoringSettings.STATUS, "")!!.startsWith("Inside"))
        assertFalse(prefs.contains(MonitoringSettings.STATUS))
        manualLevels()
        main.idleFor(Duration.ofMillis(WiFiScanService.VOLUME_CHECK_INTERVAL_MS))
        assertLevels()
    }

    @Test fun unlockUsesFullCredentialConfigurationWithoutReplacingSavedProfiles() {
        val updated = home.copy(volumes = home.volumes + mapOf(
            VolumeProfile.RINGTONE_INDOOR to 75, VolumeProfile.NOTIFICATION_INDOOR to 75))
        val office = home.copy(name = "Office", ssid = "Office_WiFi")
        // Deliberately leave an older boot snapshot; unlock must read the authoritative list.
        prefs.edit().putString("VOLUME_PROFILES", Gson().toJson(listOf(updated, office))).commit()
        user.setUserUnlocked(false)
        observations()
        start()
        assertLevels()
        user.setUserUnlocked(true)
        context.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        main.idle()
        assertLevels(75, 75)
        assertEquals(listOf(updated, office), ProfileStorageManager(context).loadProfiles())
        assertTrue(prefs.getString(MonitoringSettings.STATUS, "")!!.startsWith("Inside"))
    }

    @Test fun disabledCredentialSettingsAtUnlockAreNeverReenabledByBootSnapshot() {
        prefs.edit().putBoolean(MonitoringSettings.ENABLED, false).commit()
        user.setUserUnlocked(false)
        observations()
        start()
        assertLevels()
        manualLevels()
        user.setUserUnlocked(true)
        context.sendBroadcast(Intent(Intent.ACTION_USER_UNLOCKED))
        main.idle()
        main.idleFor(Duration.ofSeconds(10))
        assertEquals(0, VolumeReconciliationServiceTest.RecordingAudio.writes)
        assertFalse(prefs.getBoolean(MonitoringSettings.ENABLED, true))
        assertTrue(shadowOf(controller!!.get()).isStoppedBySelf)
    }

    @Test fun normalBootAfterLockedBootKeepsOneSetOfTimersAndReceivers() {
        user.setUserUnlocked(false)
        observations()
        start()
        user.setUserUnlocked(true)
        controller!!.get().onStartCommand(Intent(), 0, 2)
        main.idle()
        assertEquals(1, VolumeReconciliationServiceTest.ThrottledWifi.requests)
        manualLevels()
        main.idleFor(Duration.ofMillis(WiFiScanService.VOLUME_CHECK_INTERVAL_MS))
        assertLevels()
        assertEquals(2, VolumeReconciliationServiceTest.RecordingAudio.writes)
        main.idleFor(Duration.ofMillis(WiFiScanService.SCAN_INTERVAL_MS))
        assertEquals(2, VolumeReconciliationServiceTest.ThrottledWifi.requests)
    }

    @Test fun wifiReadinessBroadcastStartsFirstScanWithoutWaitingForPeriodicTimer() {
        wifi.isWifiEnabled = false
        start()
        assertNotNull(shadowOf(manager).getNotification(1))
        assertEquals(0, VolumeReconciliationServiceTest.ThrottledWifi.requests)
        wifi.isWifiEnabled = true
        context.sendBroadcast(Intent(WifiManager.WIFI_STATE_CHANGED_ACTION))
        main.idle()
        assertEquals(1, VolumeReconciliationServiceTest.ThrottledWifi.requests)
        observations()
        broadcastScan()
        assertLevels()
    }

    @Test fun cachedMatchStillRespectsDnd() {
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        observations()
        start()
        assertEquals(0, VolumeReconciliationServiceTest.RecordingAudio.writes)
        assertLevels(100, 100)
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        assertTrue(prefs.getString(MonitoringSettings.STATUS, "")!!.contains("deferred"))
    }
}
