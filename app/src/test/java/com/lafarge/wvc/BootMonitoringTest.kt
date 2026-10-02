package com.lafarge.wvc

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootMonitoringTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val deviceContext = context.createDeviceProtectedStorageContext()
    private val prefs = MonitoringSettings.prefs(context)
    private val snapshot = MonitoringSettings.prefs(deviceContext)
    private val profiles = ProfileStorageManager(context)
    private val user = shadowOf(context.getSystemService(UserManager::class.java))
    private val home = VolumeProfile("Home", "Home_WiFi", VolumeProfile.defaultVolumeMap())
    private val office = home.copy(name = "Office", ssid = "Office_WiFi")

    @Before fun prepare() {
        user.setUserUnlocked(true)
        prefs.edit().clear().commit()
        snapshot.edit().clear().commit()
        profiles.saveProfile(home, null)
        profiles.saveProfile(office, null)
        MonitoringSettings.setEnabled(context, true)
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
    }

    @Test fun bootSnapshotContainsOnlyActiveProfileAndDoesNotMoveFullProfileList() {
        prefs.edit().putString("UI_ONLY_PREFERENCE", "private").commit()
        BootMonitoringSettings.sync(context)
        assertEquals(listOf(home), ProfileStorageManager(deviceContext).loadProfiles())
        assertEquals(listOf(home, office), profiles.loadProfiles())
        assertFalse(snapshot.contains("UI_ONLY_PREFERENCE"))
        assertTrue(snapshot.getBoolean(MonitoringSettings.ENABLED, false))
    }

    @Test fun editsAndSelectionUpdateTheEarlyBootProfile() {
        profiles.selectProfile(office.name)
        val updated = office.copy(name = "Quiet office", volumes = office.volumes +
            (VolumeProfile.RINGTONE_INDOOR to 25))
        profiles.saveProfile(updated, office.name)
        assertEquals(updated, ProfileStorageManager(deviceContext).getActiveProfile())
        assertEquals(listOf(updated), ProfileStorageManager(deviceContext).loadProfiles())
        assertEquals(2, profiles.loadProfiles().size)
    }

    @Test fun stoppingDisablesBothLockedAndNormalBoot() {
        MonitoringSettings.setEnabled(context, false)
        user.setUserUnlocked(false)
        BootCompleteReceiver().onReceive(guardedContext(), Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertNull(shadowOf(context).nextStartedService)
        user.setUserUnlocked(true)
        BootCompleteReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(context).nextStartedService)
        assertFalse(snapshot.getBoolean(MonitoringSettings.ENABLED, true))
    }

    @Test fun deletingActiveProfileClearsBootProfileAndDisablesStartup() {
        profiles.deleteProfile(home.name)
        assertFalse(snapshot.getBoolean(MonitoringSettings.ENABLED, true))
        assertNull(ProfileStorageManager(deviceContext).getActiveProfile())
        assertEquals("", snapshot.getString("HOME_SSID", null))
        assertEquals(listOf(office), profiles.loadProfiles())
    }

    @Test fun lockedBootStartsFromSnapshotWithoutReadingCredentialPreferences() {
        user.setUserUnlocked(false)
        BootCompleteReceiver().onReceive(guardedContext(), Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(WiFiScanService::class.java.name, shadowOf(context).nextStartedService?.component?.className)
        assertTrue(BootMonitoringSettings.storageContext(context).isDeviceProtectedStorage)
    }

    @Test fun lockedBootWithoutSnapshotDoesNotStartOrAccessCredentialPreferences() {
        snapshot.edit().clear().commit()
        user.setUserUnlocked(false)
        BootCompleteReceiver().onReceive(guardedContext(), Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun bootStartsPausedWhileLocationSwitchIsStillOff() {
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(false)
        user.setUserUnlocked(false)
        BootCompleteReceiver().onReceive(guardedContext(), Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(WiFiScanService::class.java.name, shadowOf(context).nextStartedService?.component?.className)
    }

    @Test fun missingBackgroundLocationRequiresResumeRatherThanStarting() {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        user.setUserUnlocked(false)
        BootCompleteReceiver().onReceive(guardedContext(), Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertNull(shadowOf(context).nextStartedService)
        assertEquals("resume", snapshot.getString(MonitoringSettings.RECOVERY_ACTION, null))
        assertFalse(prefs.contains(MonitoringSettings.RECOVERY_ACTION))
    }

    @Test fun normalBootSeedsSnapshotForExistingInstallAndStarts() {
        snapshot.edit().clear().commit()
        BootCompleteReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(WiFiScanService::class.java.name, shadowOf(context).nextStartedService?.component?.className)
        assertEquals(home, ProfileStorageManager(deviceContext).getActiveProfile())
    }

    @Test @Config(sdk = [26]) fun earlyBootOnAndroid8DoesNotRequireBackgroundLocationPermission() {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        user.setUserUnlocked(false)
        BootCompleteReceiver().onReceive(guardedContext(), Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(WiFiScanService::class.java.name, shadowOf(context).nextStartedService?.component?.className)
    }

    @Test fun unrelatedBroadcastDoesNotStartMonitoring() {
        BootCompleteReceiver().onReceive(context, Intent(Intent.ACTION_POWER_CONNECTED))
        assertNull(shadowOf(context).nextStartedService)
    }

    private fun guardedContext() = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            error("Credential-protected preferences are unavailable before unlock")
    }
}
