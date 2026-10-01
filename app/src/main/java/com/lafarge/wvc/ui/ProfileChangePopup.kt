package com.lafarge.wvc.ui

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lafarge.wvc.AppliedSoundProfile
import kotlinx.coroutines.delay

data class ProfileNotice(
    val name: String,
    val detail: String,
    val applied: AppliedSoundProfile? = null,
    val id: Long = SystemClock.elapsedRealtimeNanos()
) {
    companion object {
        fun applied(profile: AppliedSoundProfile) = ProfileNotice(profile.name, profile.area, profile)
        fun selected(name: String, monitoring: Boolean) = ProfileNotice(name,
            if (monitoring) "Changes apply after a fresh Wi-Fi scan." else "Start monitoring to use these sound settings.")
    }
}

@Composable
fun ProfileChangePopup(notice: ProfileNotice?, onDismiss: () -> Unit) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = notice != null
    var lastNotice by remember { mutableStateOf(notice) }
    SideEffect { if (notice != null) lastNotice = notice }
    val dismiss by rememberUpdatedState(onDismiss)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(notice?.id, accessibility) {
        if (notice != null) {
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(6_000L,
                containsIcons = true, containsText = true, containsControls = true) ?: 6_000L
            delay(timeout)
            dismiss()
        }
    }
    if (visibility.currentState || visibility.targetState) {
        // An app-owned, non-focusable window also sits above the profile editor.
        // It needs no overlay permission and never steals input from the page.
        Box(Modifier.fillMaxSize()) {
            Popup(alignment = Alignment.TopCenter,
                properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
                AnimatedVisibility(visibleState = visibility,
                    enter = slideInVertically(tween(260)) { -it / 2 } + fadeIn(tween(220)),
                    exit = slideOutVertically(tween(180)) { -it / 3 } + fadeOut(tween(160))) {
                    (notice ?: lastNotice)?.let { item ->
                        ProfileNoticeCard(item, dismiss, Modifier.widthIn(max = 520.dp).fillMaxWidth()
                            .safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileNoticeCard(notice: ProfileNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(modifier.testTag("profile-change-popup").semantics { liveRegion = LiveRegionMode.Polite }, animateSize = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionLabel(if (notice.applied != null) "SOUND UPDATED" else "PROFILE SELECTED")
                Text(notice.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, "Dismiss profile notification", Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(notice.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        notice.applied?.let { applied ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NoticeVolume("Ringtone", applied.ringtone, Modifier.weight(1f))
                NoticeVolume("Notifications", applied.notifications, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NoticeVolume(label: String, percent: Int, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$percent%", style = MaterialTheme.typography.titleMedium)
    }
}
