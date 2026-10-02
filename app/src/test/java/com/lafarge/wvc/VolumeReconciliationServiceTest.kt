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
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.shadows.ShadowWifiManager
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [VolumeReconciliationServiceTest.RecordingAudio::class, VolumeReconciliationServiceTest.ThrottledWifi::class])
@LooperMode(LooperMode.Mode.PAUSED)
class VolumeReconciliationServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = MonitoringSettings.prefs(context)
    private val profiles = ProfileStorageManager(context)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val main = shadowOf(Looper.getMainLooper())
    private lateinit var controller: ServiceController<WiFiScanService>
    private var destroyed = false
    private val home = VolumeProfile("Home", "Home_WiFi", VolumeProfile.defaultVolumeMap() +
        mapOf(VolumeProfile.RINGTONE_INDOOR to 25, VolumeProfile.NOTIFICATION_INDOOR to 50))

    @Before fun prepare() {
        RecordingAudio.writes = 0
        RecordingAudio.linked = false
        RecordingAudio.ignoredStream = -1
        ThrottledWifi.requests = 0
        prefs.edit().clear().putBoolean(MonitoringSettings.ENABLED, true).commit()
        profiles.saveProfile(home, null)
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        wifi.isWifiEnabled = true
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        manager.cancelAll()
        controller = Robolectric.buildService(WiFiScanService::class.java).create()
        controller.get().onStartCommand(Intent(), 0, 1)
        main.idle()
        RecordingAudio.writes = 0
    }

    @After fun finish() { if (!destroyed) controller.destroy() }

    private fun scan(ssid: String = home.ssid) {
        shadowOf(wifi).setScanResults(listOf(ScanResult().apply {
            SSID = ssid
            timestamp = SystemClock.elapsedRealtime() * 1000
        }))
        context.sendBroadcast(Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true))
        main.idle()
    }

    private fun tick() = main.idleFor(Duration.ofMillis(WiFiScanService.VOLUME_CHECK_INTERVAL_MS))
    private fun target(stream: Int, percent: Int) = SoundControlAccess.targetVolume(audio, stream, percent)
    private fun assertLevels(ring: Int = 25, alerts: Int = 50) {
        assertEquals(target(AudioManager.STREAM_RING, ring), audio.getStreamVolume(AudioManager.STREAM_RING))
        assertEquals(target(AudioManager.STREAM_NOTIFICATION, alerts), audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
    }
    private fun manualChange() {
        audio.setStreamVolume(AudioManager.STREAM_RING, audio.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
        audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0)
        RecordingAudio.writes = 0
        manager.cancel(ProfileChangeNotifier.NOTIFICATION_ID)
    }

    @Test fun manualChangesAreRestoredOnTimerEvenWhileWifiScansAreThrottled() {
        scan()
        assertLevels()
        val requests = ThrottledWifi.requests
        manualChange()
        tick()
        assertLevels()
        assertEquals(2, RecordingAudio.writes)
        assertEquals(requests, ThrottledWifi.requests)
        assertNotNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        manager.cancel(ProfileChangeNotifier.NOTIFICATION_ID)
        tick()
        assertEquals(2, RecordingAudio.writes)
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
    }

    @Test fun notificationOnlyChangeDoesNotRewriteMatchingRingtone() {
        scan()
        audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0)
        RecordingAudio.writes = 0
        tick()
        assertLevels()
        assertEquals(1, RecordingAudio.writes)
    }

    @Test fun scanAlsoRepairsDriftWhenProfileSignatureIsUnchanged() {
        scan()
        manualChange()
        scan()
        assertLevels()
        assertNotNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
    }

    @Test fun editingLevelsForConfirmedNetworkAppliesWithoutAnotherScan() {
        scan()
        profiles.saveProfile(home.copy(volumes = home.volumes + mapOf(
            VolumeProfile.RINGTONE_INDOOR to 75, VolumeProfile.NOTIFICATION_INDOOR to 75)), home.name)
        val requests = ThrottledWifi.requests
        tick()
        assertLevels(75, 75)
        assertEquals(requests, ThrottledWifi.requests)
    }

    @Test fun choosingDifferentNetworkWaitsForItsOwnFreshObservation() {
        scan()
        val office = VolumeProfile("Office", "Office_WiFi", home.volumes + mapOf(
            VolumeProfile.RINGTONE_INDOOR to 75, VolumeProfile.NOTIFICATION_INDOOR to 75))
        profiles.saveProfile(office, null)
        profiles.selectProfile(office.name)
        manualChange()
        tick()
        assertEquals(0, RecordingAudio.writes)
        assertLevels(100, 100)
        scan(office.ssid)
        assertLevels(75, 75)
    }

    @Test fun timerDoesNotInventAnAreaBeforeFirstSuccessfulApplication() {
        manualChange()
        tick()
        tick()
        assertLevels(100, 100)
        assertEquals(0, RecordingAudio.writes)
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
    }

    @Test fun correctionIsDeferredDuringDndAndRetriedAfterItEnds() {
        scan()
        manualChange()
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        tick()
        assertLevels(100, 100)
        assertEquals(0, RecordingAudio.writes)
        assertTrue(prefs.getString(MonitoringSettings.STATUS, "")!!.contains("deferred"))
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        tick()
        assertLevels()
    }

    @Test fun revokedSoundAccessBlocksBothStreamsUntilAccessReturns() {
        scan()
        manualChange()
        shadowOf(manager).setNotificationPolicyAccessGranted(false)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        tick()
        assertLevels(100, 100)
        assertEquals(0, RecordingAudio.writes)
        assertEquals("dnd", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        tick()
        assertLevels()
        assertNull(prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
    }

    @Test fun disabledMonitoringLeavesManualLevelsAlone() {
        scan()
        prefs.edit().putBoolean(MonitoringSettings.ENABLED, false).commit()
        manualChange()
        tick()
        tick()
        assertLevels(100, 100)
        assertEquals(0, RecordingAudio.writes)
    }

    @Test fun serviceDestructionCancelsVolumeChecks() {
        scan()
        controller.destroy()
        destroyed = true
        manualChange()
        tick()
        tick()
        assertLevels(100, 100)
        assertEquals(0, RecordingAudio.writes)
    }

    @Test fun disablingWifiDiscardsCorrectionTargetUntilAreaIsConfirmedAgain() {
        scan()
        wifi.isWifiEnabled = false
        manualChange()
        tick()
        assertLevels(100, 100)
        wifi.isWifiEnabled = true
        tick()
        assertEquals(0, RecordingAudio.writes)
        scan()
        assertLevels()
    }

    @Test fun silentlyIgnoredCorrectionOffersResumeInsteadOfRepeatedWrites() {
        scan()
        manualChange()
        RecordingAudio.ignoredStream = AudioManager.STREAM_RING
        tick()
        assertLevels(100, 100)
        assertEquals("resume", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        val attempts = RecordingAudio.writes
        RecordingAudio.ignoredStream = -1
        tick()
        assertEquals(attempts, RecordingAudio.writes)
    }

    @Test fun linkedStreamsConflictDoesNotCreateRepeatedCorrectionLoop() {
        RecordingAudio.linked = true
        scan()
        assertTrue(prefs.getString(MonitoringSettings.STATUS, "")!!.contains("links sound volumes"))
        assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        RecordingAudio.writes = 0
        tick()
        tick()
        assertEquals(0, RecordingAudio.writes)
    }

    @Test fun correctionsUseOutdoorLevelsAfterTwoFreshMisses() {
        scan()
        main.idleFor(Duration.ofMillis(1))
        scan("Other_WiFi")
        assertLevels()
        main.idleFor(Duration.ofMillis(1))
        scan("Other_WiFi")
        assertLevels(100, 100)
        audio.setStreamVolume(AudioManager.STREAM_RING, 1, 0)
        audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 1, 0)
        tick()
        assertLevels(100, 100)
    }

    @Implements(AudioManager::class)
    class RecordingAudio : ShadowAudioManager() {
        companion object {
            var writes = 0
            var linked = false
            var ignoredStream = -1
        }
        @Implementation override fun setStreamVolume(streamType: Int, index: Int, flags: Int) {
            writes++
            if (streamType == ignoredStream) return
            super.setStreamVolume(streamType, index, flags)
            if (linked && (streamType == AudioManager.STREAM_RING || streamType == AudioManager.STREAM_NOTIFICATION)) {
                super.setStreamVolume(if (streamType == AudioManager.STREAM_RING) AudioManager.STREAM_NOTIFICATION else AudioManager.STREAM_RING, index, flags)
            }
        }
    }

    @Implements(WifiManager::class)
    class ThrottledWifi : ShadowWifiManager() {
        companion object { var requests = 0 }
        @Implementation override fun startScan(): Boolean { requests++; return false }
    }
}
