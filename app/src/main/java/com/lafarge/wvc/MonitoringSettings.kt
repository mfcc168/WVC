package com.lafarge.wvc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.location.LocationManagerCompat

object MonitoringSettings {
    const val PREFS = "wifi_volume_prefs"
    const val ENABLED = "MONITORING_ENABLED"
    const val STATUS = "MONITORING_STATUS"
    const val RECOVERY_ACTION = "MONITORING_RECOVERY_ACTION"
    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, enabled).apply()
        BootMonitoringSettings.sync(context)
    }
    fun hasLocation(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackgroundLocation(context: Context) = Build.VERSION.SDK_INT < 29 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun locationEnabled(context: Context) = LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))
    fun status(context: Context, message: String, recoveryAction: String? = null) {
        prefs(context).edit().putString(STATUS, message).putString(RECOVERY_ACTION, recoveryAction).apply()
    }
}
