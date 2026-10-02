package com.lafarge.wvc

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A confirmed profile application, separate from merely selecting a saved profile. */
data class AppliedSoundProfile(
    val name: String,
    val ssid: String,
    val indoor: Boolean,
    val ringtone: Int,
    val notifications: Int
) {
    val area get() = if (indoor) "In Wi-Fi range" else "Out of Wi-Fi range"
    val summary get() = "Ringtone $ringtone% · Notifications $notifications%"
}

object ProfileChangeNotifier {
    const val CHANNEL = "sound_profile_changes_v1"
    const val NOTIFICATION_ID = 3
    private const val LAST_APPLICATION = "LAST_NOTIFIED_SOUND_APPLICATION"
    private val changes = MutableSharedFlow<AppliedSoundProfile>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    // No replay: opening the app must not replay a stale background change.
    val events = changes.asSharedFlow()

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Sound profile changes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "On-screen updates when WVC applies a sound profile. Quiet by default."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    /** Called only after the service has successfully written and checked both volume levels. */
    fun applied(context: Context, profile: AppliedSoundProfile, volumeChanged: Boolean) {
        val prefs = MonitoringSettings.prefs(context)
        val signature = Gson().toJson(profile)
        if (!volumeChanged && prefs.getString(LAST_APPLICATION, null) == signature) return
        prefs.edit().putString(LAST_APPLICATION, signature).apply()
        if (changes.subscriptionCount.value > 0) {
            changes.tryEmit(profile)
            return // One presentation: an in-app popup OR an Android notification.
        }
        showSystemNotification(context, profile)
    }

    private fun showSystemNotification(context: Context, profile: AppliedSoundProfile) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_sound_profile)
            .setColor(Color.rgb(79, 108, 94))
            .setContentTitle("${profile.name} · ${profile.area}")
            .setContentText(profile.summary)
            .setSubText("Sound profile applied")
            .setStyle(NotificationCompat.BigTextStyle().bigText(profile.summary))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sound_profile).setContentTitle("Sound profile updated").build())
            .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(60_000L)
            .build()
        // Notification access can be revoked between the permission check and notify().
        try { manager.notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { }
    }
}
