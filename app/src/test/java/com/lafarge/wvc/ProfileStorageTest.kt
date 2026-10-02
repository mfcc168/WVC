package com.lafarge.wvc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = MonitoringSettings.prefs(context)
    private val store = ProfileStorageManager(context)
    private fun profile(name: String, ssid: String = "WiFi") = VolumeProfile(name, ssid, VolumeProfile.defaultVolumeMap())
    @Before fun clear() { prefs.edit().clear().commit() }

    @Test fun firstProfileIsSelectedButSavingAnotherDoesNotSwitch() {
        assertNull(store.saveProfile(profile("Home"), null))
        assertNull(store.saveProfile(profile("Office"), null))
        assertEquals("Home", store.getActiveProfileName())
    }
    @Test fun renamePreservesSelectionAndUpdatesLegacySnapshot() {
        store.saveProfile(profile("Home"), null)
        assertNull(store.saveProfile(profile("Apartment", "New WiFi"), "Home"))
        assertEquals("Apartment", store.getActiveProfileName())
        assertEquals("New WiFi", prefs.getString("HOME_SSID", null))
        assertEquals(1, store.loadProfiles().size)
    }
    @Test fun deletingActiveProfileStopsAndCannotResurrectLegacySettings() {
        store.saveProfile(profile("Home"), null)
        prefs.edit().putBoolean(MonitoringSettings.ENABLED, true).commit()
        store.deleteProfile("Home")
        store.migrateLegacyProfile()
        assertFalse(prefs.getBoolean(MonitoringSettings.ENABLED, true))
        assertNull(store.getActiveProfile())
        assertTrue(store.loadProfiles().isEmpty())
        assertEquals("", prefs.getString("HOME_SSID", null))
    }
    @Test fun deletingOtherProfileKeepsActiveProfileAndMonitoring() {
        store.saveProfile(profile("Home"), null)
        store.saveProfile(profile("Office"), null)
        prefs.edit().putBoolean(MonitoringSettings.ENABLED, true).commit()
        store.deleteProfile("Office")
        assertEquals("Home", store.getActiveProfileName())
        assertTrue(prefs.getBoolean(MonitoringSettings.ENABLED, false))
    }
    @Test fun legacyMigrationPreservesExactSsidAndVolumes() {
        prefs.edit().putString("HOME_SSID", " Home WiFi ").putInt("RINGTONE_INDOOR_VOLUME", 25).commit()
        store.migrateLegacyProfile()
        store.migrateLegacyProfile()
        assertEquals(1, store.loadProfiles().size)
        assertEquals(" Home WiFi ", store.getActiveProfile()?.ssid)
        assertEquals(25, store.getActiveProfile()?.volumes?.get(VolumeProfile.RINGTONE_INDOOR))
    }
    @Test fun duplicateNamesAndInvalidSsidDoNotOverwriteProfiles() {
        store.saveProfile(profile("Home"), null)
        assertNotNull(store.saveProfile(profile(" home "), null))
        assertNotNull(store.saveProfile(profile("Office", ""), null))
        assertNotNull(ProfileValidation.ssidError("家".repeat(11)))
        assertNull(ProfileValidation.ssidError("a".repeat(32)))
        assertEquals(1, store.loadProfiles().size)
    }
}
