package com.lafarge.wvc

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Profile edits and selection are committed together; the service never sees a half-edited profile. */
class ProfileStorageManager(private val context: Context) {
    private val prefs = MonitoringSettings.prefs(context)
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<VolumeProfile>>() {}.type

    fun loadProfiles(): List<VolumeProfile> {
        val json = prefs.getString("VOLUME_PROFILES", null)
        return if (json.isNullOrBlank()) emptyList() else gson.fromJson<List<VolumeProfile>>(json, typeToken) ?: emptyList()
    }
    fun getActiveProfileName(): String = prefs.getString("ACTIVE_PROFILE_NAME", "").orEmpty()
    fun getActiveProfile(): VolumeProfile? = loadProfiles().find { it.name == getActiveProfileName() }

    fun saveProfile(profile: VolumeProfile, originalName: String?): String? {
        val profiles = loadProfiles()
        ProfileValidation.nameError(profile.name, profiles, originalName)?.let { return it }
        ProfileValidation.ssidError(profile.ssid)?.let { return it }
        if (originalName != null && profiles.none { it.name == originalName }) return "This profile was removed. Create a new profile instead."
        val normalized = profile.copy(name = profile.name.trim(), volumes = profile.volumes.mapValues { it.value.coerceIn(0, 100) })
        val updated = if (originalName == null) profiles + normalized else profiles.map { if (it.name == originalName) normalized else it }
        val activeName = if (profiles.isEmpty() || getActiveProfileName() == originalName) normalized.name else getActiveProfileName()
        write(updated, activeName)
        return null
    }

    fun selectProfile(name: String) {
        val profiles = loadProfiles()
        if (profiles.any { it.name == name }) write(profiles, name)
    }

    fun deleteProfile(name: String) {
        val active = getActiveProfileName()
        val editor = prefs.edit()
        if (active == name) editor.putBoolean(MonitoringSettings.ENABLED, false)
        write(loadProfiles().filterNot { it.name == name }, if (active == name) "" else active, editor)
    }

    /** Import the original single-profile settings without changing existing named profiles. */
    fun migrateLegacyProfile() {
        if (loadProfiles().isNotEmpty()) return
        val ssid = prefs.getString("HOME_SSID", "").orEmpty()
        if (ssid.isBlank()) return
        val volumes = VolumeProfile.defaultVolumeMap().mapValues { (key, default) ->
            prefs.getInt("${key}_VOLUME", default).coerceIn(0, 100)
        }
        write(listOf(VolumeProfile("Home", ssid, volumes)), "Home")
    }

    private fun write(profiles: List<VolumeProfile>, activeName: String, editor: SharedPreferences.Editor = prefs.edit()) {
        editor.putString("VOLUME_PROFILES", gson.toJson(profiles)).putString("ACTIVE_PROFILE_NAME", activeName)
        // Keep the legacy service snapshot in sync; deleting an active profile must not resurrect it.
        val active = profiles.find { it.name == activeName }
        editor.putString("HOME_SSID", active?.ssid.orEmpty())
        active?.volumes?.forEach { (key, volume) -> editor.putInt("${key}_VOLUME", volume) }
        editor.apply()
        BootMonitoringSettings.sync(context)
    }
}
