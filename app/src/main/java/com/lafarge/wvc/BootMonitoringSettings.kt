package com.lafarge.wvc

import android.content.Context
import android.os.UserManager
import android.util.Log
import com.google.gson.Gson

/** Only the selected profile and enable intent are available before the first unlock. */
object BootMonitoringSettings {
    // Call with the component's normal context; the application keeps credential storage as its default.
    fun storageContext(context: Context): Context =
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) context
        else context.createDeviceProtectedStorageContext()

    fun sync(context: Context) {
        // Never open credential-protected preferences from a locked-boot component.
        if (context.isDeviceProtectedStorage || !context.getSystemService(UserManager::class.java).isUserUnlocked) return
        val source = MonitoringSettings.prefs(context)
        val active = ProfileStorageManager(context).getActiveProfile()
        val snapshot = MonitoringSettings.prefs(context.createDeviceProtectedStorageContext())
        val editor = snapshot.edit()
            .putBoolean(MonitoringSettings.ENABLED, source.getBoolean(MonitoringSettings.ENABLED, false))
            .putString("VOLUME_PROFILES", Gson().toJson(listOfNotNull(active)))
            .putString("ACTIVE_PROFILE_NAME", active?.name.orEmpty())
            .putString("HOME_SSID", active?.ssid ?: source.getString("HOME_SSID", "").orEmpty())
        VolumeProfile.defaultVolumeMap().forEach { (key, default) ->
            editor.putInt("${key}_VOLUME", active?.volumes?.get(key) ?: source.getInt("${key}_VOLUME", default))
        }
        // A small durable snapshot also ensures Stop survives a subsequent reboot.
        if (!editor.commit()) Log.w("WvcMonitoring", "Early-boot settings could not be saved")
    }
}
