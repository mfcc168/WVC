package com.lafarge.wvc

data class VolumeProfile(
    val name: String,
    val ssid: String,
    val volumes: Map<String, Int>
) {
    companion object {
        // Keys used in the volumes map
//        const val MEDIA_INDOOR = "MEDIA_INDOOR"
//        const val MEDIA_OUTDOOR = "MEDIA_OUTDOOR"
        const val RINGTONE_INDOOR = "RINGTONE_INDOOR"
        const val RINGTONE_OUTDOOR = "RINGTONE_OUTDOOR"
        const val NOTIFICATION_INDOOR = "NOTIFICATION_INDOOR"
        const val NOTIFICATION_OUTDOOR = "NOTIFICATION_OUTDOOR"
//        const val SYSTEM_INDOOR = "SYSTEM_INDOOR"
//        const val SYSTEM_OUTDOOR = "SYSTEM_OUTDOOR"
//        const val CALL_INDOOR = "CALL_INDOOR"
//        const val CALL_OUTDOOR = "CALL_OUTDOOR"
//        const val ALARM_INDOOR = "ALARM_INDOOR"
//        const val ALARM_OUTDOOR = "ALARM_OUTDOOR"

        // Default volume map for new profiles
        fun defaultVolumeMap(): Map<String, Int> = mapOf(
//            MEDIA_INDOOR to 50,
//            MEDIA_OUTDOOR to 100,
            RINGTONE_INDOOR to 50,
            RINGTONE_OUTDOOR to 100,
            NOTIFICATION_INDOOR to 50,
            NOTIFICATION_OUTDOOR to 100,
//            SYSTEM_INDOOR to 50,
//            SYSTEM_OUTDOOR to 100,
//            CALL_INDOOR to 50,
//            CALL_OUTDOOR to 100,
//            ALARM_INDOOR to 50,
//            ALARM_OUTDOOR to 100
        )
    }
}
