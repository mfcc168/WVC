package com.lafarge.wvc

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProfileStorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wifi_volume_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<VolumeProfile>>() {}.type

    fun saveProfiles(profiles: List<VolumeProfile>) {
        val json = gson.toJson(profiles)
        prefs.edit().putString("VOLUME_PROFILES", json).apply()
    }

    fun deleteProfile(name: String) {
        val updatedProfiles = loadProfiles().filter { it.name != name }
        saveProfiles(updatedProfiles)

        if (getActiveProfileName() == name) {
            setActiveProfileName("")
        }
    }
    fun loadProfiles(): List<VolumeProfile> {
        val json = prefs.getString("VOLUME_PROFILES", null)
        return if (json.isNullOrBlank()) {
            emptyList()
        } else {
            gson.fromJson(json, typeToken)
        }
    }

    fun setActiveProfileName(name: String) {
        prefs.edit().putString("ACTIVE_PROFILE_NAME", name).apply()
    }

    fun getActiveProfileName(): String {
        return prefs.getString("ACTIVE_PROFILE_NAME", "") ?: ""
    }

    fun getActiveProfile(): VolumeProfile? {
        val profiles = loadProfiles()
        val name = getActiveProfileName()
        return profiles.find { it.name == name }
    }
}
