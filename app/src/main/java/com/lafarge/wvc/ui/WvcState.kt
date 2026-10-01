package com.lafarge.wvc.ui

import com.lafarge.wvc.VolumeProfile

data class SetupItem(
    val id: String,
    val title: String,
    val detail: String,
    val ready: Boolean,
    val required: Boolean = false,
    val action: String
)

data class WvcState(
    val profiles: List<VolumeProfile> = emptyList(),
    val activeName: String = "",
    val enabled: Boolean = false,
    val status: String = "Choose a profile to get started.",
    val setup: List<SetupItem> = emptyList(),
    val needsRebootResume: Boolean = false,
    val recoveryAction: String? = null
) {
    val activeProfile: VolumeProfile? get() = profiles.find { it.name == activeName }
    val requiredMissing: Int get() = setup.count { it.required && !it.ready }
    // Saved monitoring intent can survive a reboot even when Android requires a fresh start.
    val monitoringOn: Boolean get() = enabled && recoveryAction != "resume"
}
