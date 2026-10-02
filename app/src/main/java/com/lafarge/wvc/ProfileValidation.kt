package com.lafarge.wvc

object ProfileValidation {
    fun nameError(name: String, profiles: List<VolumeProfile>, originalName: String?): String? = when {
        name.isBlank() -> "Give this profile a name."
        name.trim().length > 40 -> "Use 40 characters or fewer."
        profiles.any { it.name != originalName && it.name.equals(name.trim(), ignoreCase = true) } ->
            "A profile with this name already exists."
        else -> null
    }

    fun ssidError(ssid: String): String? = when {
        ssid.isBlank() -> "Enter the Wi-Fi name exactly as it appears in Settings."
        ssid.toByteArray(Charsets.UTF_8).size > 32 -> "Wi-Fi names can contain up to 32 UTF-8 bytes."
        else -> null
    }
}
