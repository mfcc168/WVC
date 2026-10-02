package com.lafarge.wvc

import android.media.AudioManager

/** Notification permission does not grant notification-policy (Do Not Disturb) access. */
object SoundControlAccess {
    const val REQUIRED_MESSAGE = "Sound-control access needed: allow Do Not Disturb access for WVC in App setup. Notification permission is separate."

    fun targetVolume(audio: AudioManager, stream: Int, percent: Int): Int =
        (audio.getStreamMaxVolume(stream) * percent.coerceIn(0, 100) / 100.0).toInt()

    fun needsPolicyAccess(audio: AudioManager, ring: Int, alerts: Int): Boolean =
        audio.ringerMode == AudioManager.RINGER_MODE_SILENT ||
            targetVolume(audio, AudioManager.STREAM_RING, ring) == 0 ||
            targetVolume(audio, AudioManager.STREAM_NOTIFICATION, alerts) == 0

    fun profileNeedsPolicyAccess(audio: AudioManager, profile: VolumeProfile?): Boolean {
        if (profile == null) return false
        return needsPolicyAccess(audio,
            profile.volumes[VolumeProfile.RINGTONE_INDOOR] ?: 50,
            profile.volumes[VolumeProfile.NOTIFICATION_INDOOR] ?: 50) ||
            needsPolicyAccess(audio,
                profile.volumes[VolumeProfile.RINGTONE_OUTDOOR] ?: 100,
                profile.volumes[VolumeProfile.NOTIFICATION_OUTDOOR] ?: 100)
    }
}
