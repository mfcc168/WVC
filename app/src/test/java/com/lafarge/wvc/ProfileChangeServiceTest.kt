package com.lafarge.wvc

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.location.LocationManager
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileChangeServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    @Before fun prepare() {
        MonitoringSettings.prefs(context).edit().clear().putBoolean(MonitoringSettings.ENABLED, true).commit()
        ProfileStorageManager(context).saveProfile(VolumeProfile("Home", "Home_WiFi", VolumeProfile.defaultVolumeMap()), null)
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        wifi.isWifiEnabled = true
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        shadowOf(wifi).setScanResults(listOf(ScanResult().apply { SSID = "Home_WiFi"; timestamp = SystemClock.elapsedRealtime() * 1000 }))
        manager.cancelAll()
    }
    private fun consume(service: WiFiScanService) {
        WiFiScanService::class.java.getDeclaredMethod("consumeResults").apply { isAccessible = true }.invoke(service)
    }
    @Test fun confirmedApplicationNotifiesOnceAndRenamedProfileNotifiesAgain() {
        val service = Robolectric.buildService(WiFiScanService::class.java).create()
        try {
            consume(service.get())
            val first = shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID)
            assertNotNull(first)
            assertEquals("Home · In Wi-Fi range", first!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            manager.cancel(ProfileChangeNotifier.NOTIFICATION_ID)
            consume(service.get())
            assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
            ProfileStorageManager(context).saveProfile(VolumeProfile("Quiet home", "Home_WiFi", VolumeProfile.defaultVolumeMap()), "Home")
            consume(service.get())
            assertEquals("Quiet home · In Wi-Fi range", shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID)!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        } finally { service.destroy() }
    }
    @Test fun dndDeferredApplicationDoesNotAnnounceSuccess() {
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        val service = Robolectric.buildService(WiFiScanService::class.java).create()
        try {
            consume(service.get())
            assertNull(shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID))
            assertTrue(MonitoringSettings.prefs(context).getString(MonitoringSettings.STATUS, "")!!.contains("deferred"))
        } finally { service.destroy() }
    }
}
