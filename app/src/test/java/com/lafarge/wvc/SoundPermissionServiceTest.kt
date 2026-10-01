package com.lafarge.wvc

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.shadows.ShadowWifiManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [SoundPermissionServiceTest.DeniedAudio::class, SoundPermissionServiceTest.DeniedWifi::class])
class SoundPermissionServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = MonitoringSettings.prefs(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)

    @Before fun prepare() {
        DeniedAudio.deniedStream = -1
        DeniedWifi.denyResults = false
        prefs.edit().clear().putBoolean(MonitoringSettings.ENABLED, true).commit()
        profile(50, 50)
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        wifi.isWifiEnabled = true
        shadowOf(wifi).setScanResults(listOf(ScanResult().apply { SSID = "Home_WiFi"; timestamp = SystemClock.elapsedRealtime() * 1000 }))
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        shadowOf(manager).setNotificationPolicyAccessGranted(false)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audio.setStreamVolume(AudioManager.STREAM_RING, 4, 0)
        audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 4, 0)
        manager.cancelAll()
    }

    private fun profile(ring: Int, alerts: Int) {
        ProfileStorageManager(context).saveProfile(VolumeProfile("Home", "Home_WiFi", VolumeProfile.defaultVolumeMap() +
            mapOf(VolumeProfile.RINGTONE_INDOOR to ring, VolumeProfile.NOTIFICATION_INDOOR to alerts)),
            if (ProfileStorageManager(context).getActiveProfile() == null) null else "Home")
    }
    private fun consume(service: WiFiScanService) {
        WiFiScanService::class.java.getDeclaredMethod("consumeResults").apply { isAccessible = true }.invoke(service)
    }
    private fun withService(test: (WiFiScanService) -> Unit) {
        val controller = Robolectric.buildService(WiFiScanService::class.java).create()
        try { test(controller.get()) } finally { controller.destroy() }
    }
    private fun assertNoSuccess() = assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))

    @Test fun notificationPermissionDoesNotAllowSilentVolumesAndNeitherStreamIsChanged() {
        profile(0, 0)
        withService { service ->
            consume(service)
            assertEquals("dnd", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertEquals(SoundControlAccess.REQUIRED_MESSAGE, prefs.getString(MonitoringSettings.STATUS, null))
            assertEquals(4, audio.getStreamVolume(AudioManager.STREAM_RING))
            assertEquals(4, audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
            assertNoSuccess()
        }
    }

    @Test fun normalVolumesStillWorkWithoutSpecialAccess() {
        withService { service ->
            consume(service)
            assertNotNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
            assertNull(prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
        }
    }

    @Test fun tinyNonzeroVolumesWhichRoundToZeroRequireSpecialAccess() {
        profile(50, 1)
        assertTrue(SoundControlAccess.profileNeedsPolicyAccess(audio, ProfileStorageManager(context).getActiveProfile()))
        withService { service ->
            consume(service)
            assertEquals("dnd", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertEquals(4, audio.getStreamVolume(AudioManager.STREAM_RING))
            assertNoSuccess()
        }
    }

    @Test fun leavingSilentModeAlsoRequiresSpecialAccess() {
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        withService { service ->
            consume(service)
            assertEquals("dnd", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertNoSuccess()
        }
    }

    @Test fun grantingAccessAllowsRetryAndClearsRecoveryAction() {
        profile(0, 0)
        withService { service ->
            consume(service)
            assertNoSuccess()
            shadowOf(manager).setNotificationPolicyAccessGranted(true)
            consume(service)
            assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_RING))
            assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
            assertNull(prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertNotNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        }
    }

    @Test fun audioSecurityExceptionIsNotReportedAsWifiFailureAndRemainsRetryable() {
        DeniedAudio.deniedStream = AudioManager.STREAM_NOTIFICATION
        withService { service ->
            consume(service)
            assertEquals("dnd", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertFalse(prefs.getString(MonitoringSettings.STATUS, "")!!.contains("Wi-Fi"))
            assertNoSuccess()
            DeniedAudio.deniedStream = -1
            shadowOf(manager).setNotificationPolicyAccessGranted(true)
            consume(service)
            assertNotNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
        }
    }

    @Test fun audioDenialWithAccessAlreadyGrantedDoesNotAskForItAgain() {
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        DeniedAudio.deniedStream = AudioManager.STREAM_RING
        withService { service ->
            consume(service)
            assertTrue(prefs.getString(MonitoringSettings.STATUS, "")!!.contains("Android denied sound control"))
            assertEquals("resume", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertNoSuccess()
        }
    }

    @Test fun wifiDenialDoesNotAskForSoundAccessAndKeepsCurrentVolumes() {
        DeniedWifi.denyResults = true
        withService { service ->
            consume(service)
            assertEquals("location", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertTrue(prefs.getString(MonitoringSettings.STATUS, "")!!.contains("Wi-Fi scan access denied"))
            assertEquals(4, audio.getStreamVolume(AudioManager.STREAM_RING))
            assertNoSuccess()
        }
    }

    @Test fun emptyScanDoesNotEraseUnresolvedSoundPermissionWarning() {
        profile(0, 0)
        withService { service ->
            consume(service)
            shadowOf(wifi).setScanResults(emptyList())
            consume(service)
            assertEquals("dnd", prefs.getString(MonitoringSettings.RECOVERY_ACTION, null))
            assertEquals(SoundControlAccess.REQUIRED_MESSAGE, prefs.getString(MonitoringSettings.STATUS, null))
        }
    }

    @Implements(AudioManager::class)
    class DeniedAudio : ShadowAudioManager() {
        companion object { var deniedStream = -1 }
        @Implementation override fun setStreamVolume(streamType: Int, index: Int, flags: Int) {
            if (streamType == deniedStream) throw SecurityException("Sound control denied by test device")
            super.setStreamVolume(streamType, index, flags)
        }
    }
    @Implements(WifiManager::class)
    class DeniedWifi : ShadowWifiManager() {
        companion object { var denyResults = false }
        @Implementation override fun getScanResults(): List<ScanResult> {
            if (denyResults) throw SecurityException("Location app-op denied by test device")
            return super.getScanResults()
        }
    }
}
