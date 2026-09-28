package com.lafarge.wvc.ui

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lafarge.wvc.ProfileValidation
import com.lafarge.wvc.VolumeProfile
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Navy = Color(0xFF142B50)
private val Ice = Color(0xFFBCD5FF)

@Composable
fun WvcApp(
    state: WvcState,
    message: String? = null,
    onMessageShown: () -> Unit = {},
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSetupAction: (String) -> Unit,
    onSelect: (String) -> Unit,
    onSave: (VolumeProfile, String?) -> String?,
    onDelete: (String) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingName by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = tab != 0 && !editorOpen && deletingName == null) { tab = 0 }
    val snackbar = remember { SnackbarHostState() }
    val pageScroll = rememberScrollState()
    LaunchedEffect(tab) { pageScroll.scrollTo(0) }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    val openEditor: (String?) -> Unit = { editingName = it; editorOpen = true }
    val labels = listOf("Overview", "Profiles", "Setup")
    val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.List, Icons.Default.Settings)
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                NavigationRail(modifier = Modifier.fillMaxHeight().safeDrawingPadding(), containerColor = MaterialTheme.colorScheme.surface) {
                    SignalMark(Modifier.padding(vertical = 24.dp).size(36.dp), MaterialTheme.colorScheme.primary)
                    labels.forEachIndexed { index, label ->
                        NavigationRailItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], null) }, label = { Text(label) })
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], null) }, label = { Text(label) })
                        }
                    }
                }
            ) { insets ->
                Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 1120.dp).fillMaxSize().verticalScroll(pageScroll).padding(if (wide) 32.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!wide) SignalMark(Modifier.size(26.dp), MaterialTheme.colorScheme.primary)
                            Text("WVC", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("/  Wi-Fi Volume Control", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (tab) {
                            0 -> {
                                PageHeading("Sound, in the\nright place.", "Your Wi-Fi area. Your volume preferences.")
                                ResponsivePair(
                                    first = { MonitoringCard(state, onStart, onStop, { if (state.activeProfile == null) tab = 1 else tab = 2 }) },
                                    second = { ActiveProfileCard(state.activeProfile, { tab = 1 }, { openEditor(state.activeName) }) }
                                )
                                if (state.activeProfile == null && state.profiles.isEmpty()) {
                                    EmptyProfiles { openEditor(null) }
                                } else {
                                    state.activeProfile?.let { profile ->
                                        SectionHeading("Your sound settings", "Applied when your Wi-Fi area changes")
                                        ResponsivePair(
                                            first = { AreaSummary("Inside the area", "Your Wi-Fi is nearby", profile, true) },
                                            second = { AreaSummary("Outside the area", "Your Wi-Fi is no longer detected", profile, false) }
                                        )
                                    }
                                }
                                SetupSummary(state) { tab = 2 }
                                Text("WVC checks for nearby Wi-Fi, even when you’re not connected. Android controls scan timing, so changes may take a few minutes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            1 -> {
                                PageHeading("A profile for\nevery place.", "Save a Wi-Fi name and the sound settings that fit.")
                                Button(onClick = { openEditor(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New profile")
                                }
                                if (state.profiles.isEmpty()) EmptyProfiles { openEditor(null) }
                                else {
                                    Text("One profile is used at a time. Saving an edit does not change your phone’s volume immediately.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    state.profiles.forEach { profile ->
                                        key(profile.name) {
                                            ProfileCard(profile, profile.name == state.activeName, { onSelect(profile.name) }, { openEditor(profile.name) }, { deletingName = profile.name })
                                        }
                                    }
                                }
                            }
                            else -> {
                                PageHeading("Ready when\nyou are.", "A few Android settings keep monitoring working.")
                                SetupSummary(state, null)
                                state.setup.forEach { item -> SetupCard(item) { onSetupAction(item.id) } }
                                InfoCard("After a restart", if (state.needsRebootResume) "On Android 17, open WVC and tap Resume after a reboot or app update. Android requires this before background sound changes can resume." else "Monitoring restarts after reboot only if you left it enabled and granted the required access. Force-stopping WVC requires opening it again.")
                                InfoCard("If monitoring is delayed", "Battery restrictions and sleeping phones can delay scans. Check WVC’s battery settings if your phone stops monitoring overnight.") {
                                    TextButton(onClick = { onSetupAction("battery") }) { Text("Open app settings") }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
    if (editorOpen) ProfileEditor(
        original = state.profiles.find { it.name == editingName }, profiles = state.profiles,
        onDismiss = { editorOpen = false }, onSave = { profile ->
            val error = onSave(profile, editingName)
            if (error == null) editorOpen = false
            error
        }
    )
    deletingName?.let { name ->
        AlertDialog(
            onDismissRequest = { deletingName = null },
            title = { Text("Delete $name?") },
            text = { Text(if (name == state.activeName) "This is your selected profile. Monitoring will stop and your current volume will stay unchanged." else "This removes the saved Wi-Fi name and volume settings. It won’t affect the selected profile.") },
            confirmButton = { TextButton(onClick = { onDelete(name); deletingName = null }) { Text("Delete profile", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingName = null }) { Text("Keep profile") } }
        )
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResponsivePair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp && fontScale <= 1.3f) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { first(); second() }
    }
}

@Composable
private fun MonitoringCard(state: WvcState, onStart: () -> Unit, onStop: () -> Unit, onConfigure: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Navy, contentColor = Color.White), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().testTag("monitoring-card")) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                    SignalMark(Modifier.padding(10.dp).size(28.dp), Ice)
                }
                Text(if (state.enabled) "MONITORING ENABLED" else "MONITORING OFF", style = MaterialTheme.typography.labelMedium, color = Ice)
            }
            Text(if (state.enabled) "Your preferences,\non autopilot." else "Let your space\nset the volume.", style = MaterialTheme.typography.headlineMedium)
            Text(if (state.enabled) state.status else "Start monitoring to switch sound settings when your selected Wi-Fi enters or leaves range.", color = Color(0xFFDBE6F7), style = MaterialTheme.typography.bodyMedium)
            if (state.enabled) {
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) {
                    Text("Stop monitoring")
                }
                TextButton(onClick = onStart, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = Ice)) { Text("Resume monitoring") }
            } else {
                Button(onClick = { if (state.activeProfile == null || state.requiredMissing > 0) onConfigure() else onStart() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                    Text(when { state.activeProfile == null -> "Choose a profile"; state.requiredMissing > 0 -> "Finish setup"; else -> "Start monitoring" })
                }
                Text("You’re always in control. Stop at any time.", style = MaterialTheme.typography.bodySmall, color = Ice)
            }
        }
    }
}

@Composable
private fun ActiveProfileCard(profile: VolumeProfile?, onChoose: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("SELECTED PROFILE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(profile?.name ?: "No profile selected", style = MaterialTheme.typography.titleLarge)
            Text(profile?.ssid ?: "Choose where WVC should listen for Wi-Fi.", style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("The network name must match exactly. A Wi-Fi password is not needed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose profile") }
            if (profile != null) TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit sound settings") }
        }
    }
}

@Composable
private fun AreaSummary(title: String, detail: String, profile: VolumeProfile, indoor: Boolean) {
    val ring = profile.volumes[if (indoor) VolumeProfile.RINGTONE_INDOOR else VolumeProfile.RINGTONE_OUTDOOR] ?: if (indoor) 50 else 100
    val notification = profile.volumes[if (indoor) VolumeProfile.NOTIFICATION_INDOOR else VolumeProfile.NOTIFICATION_OUTDOOR] ?: if (indoor) 50 else 100
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VolumeSummary("Ringtone", ring)
            VolumeSummary("Notifications", notification)
        }
    }
}

@Composable
private fun VolumeSummary(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${value.coerceIn(0, 100)}%", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { value.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun EmptyProfiles(onCreate: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Start with a place", style = MaterialTheme.typography.titleLarge)
            Text("Create a profile for home, work, or anywhere you want a different sound setting.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onCreate) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create first profile") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(profile: VolumeProfile, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected) Text("SELECTED", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(profile.name, style = MaterialTheme.typography.titleLarge)
            Text(profile.ssid, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!selected) Button(onClick = onSelect, modifier = Modifier.semantics { contentDescription = "Use profile ${profile.name}" }) { Text("Use profile") }
                OutlinedButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Edit ${profile.name}" }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit") }
                TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Delete ${profile.name}" }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun SetupSummary(state: WvcState, onOpen: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.requiredMissing == 0) "Ready to monitor" else "Let’s finish setup", style = MaterialTheme.typography.titleMedium)
            Text(if (state.requiredMissing == 0) "Required settings are ready. Review optional access for notifications and silent volume." else "${state.requiredMissing} required ${if (state.requiredMissing == 1) "setting needs" else "settings need"} your attention before monitoring can start.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onOpen != null) TextButton(onClick = onOpen) { Text(if (state.requiredMissing == 0) "Review setup" else "Complete setup") }
        }
    }
}

@Composable
private fun SetupCard(item: SetupItem, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(when { item.ready -> "READY"; item.required -> "REQUIRED"; else -> "OPTIONAL" }, style = MaterialTheme.typography.labelMedium, color = if (!item.ready && item.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.ready) TextButton(onClick = onAction, modifier = Modifier.semantics { contentDescription = "Manage ${item.title}" }) { Text("Manage setting") }
            else OutlinedButton(onClick = onAction) { Text(item.action) }
        }
    }
}

@Composable
private fun InfoCard(title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
private fun SignalMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = size.minDimension * .09f
        listOf(.9f, .57f).forEach { scale ->
            val width = size.minDimension * scale
            drawArc(color, 220f, 100f, false, topLeft = Offset((size.width - width) / 2, size.height * .12f + (1 - scale) * size.height * .58f), size = Size(width, width), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawCircle(color, stroke * .65f, Offset(size.width / 2, size.height * .76f))
    }
}

@Composable
fun ProfileEditor(original: VolumeProfile?, profiles: List<VolumeProfile>, onDismiss: () -> Unit, onSave: (VolumeProfile) -> String?) {
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var ssid by rememberSaveable { mutableStateOf(original?.ssid.orEmpty()) }
    val initial = (VolumeProfile.defaultVolumeMap() + original?.volumes.orEmpty()).mapValues { it.value.coerceIn(0, 100) }
    var ringIn by rememberSaveable { mutableIntStateOf((initial[VolumeProfile.RINGTONE_INDOOR] ?: 50).coerceIn(0, 100)) }
    var ringOut by rememberSaveable { mutableIntStateOf((initial[VolumeProfile.RINGTONE_OUTDOOR] ?: 100).coerceIn(0, 100)) }
    var notificationIn by rememberSaveable { mutableIntStateOf((initial[VolumeProfile.NOTIFICATION_INDOOR] ?: 50).coerceIn(0, 100)) }
    var notificationOut by rememberSaveable { mutableIntStateOf((initial[VolumeProfile.NOTIFICATION_OUTDOOR] ?: 100).coerceIn(0, 100)) }
    val formScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val volumes = mapOf(VolumeProfile.RINGTONE_INDOOR to ringIn, VolumeProfile.RINGTONE_OUTDOOR to ringOut, VolumeProfile.NOTIFICATION_INDOOR to notificationIn, VolumeProfile.NOTIFICATION_OUTDOOR to notificationOut)
    val dirty = name != original?.name.orEmpty() || ssid != original?.ssid.orEmpty() || volumes.any { (key, value) -> value != initial[key] }
    val dismiss = { if (dirty) confirmDiscard = true else onDismiss() }
    val nameError = if (attempted) ProfileValidation.nameError(name, profiles, original?.name) else null
    val ssidError = if (attempted) ProfileValidation.ssidError(ssid) else null
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.testTag("profile-editor").padding(12.dp).widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight(.95f).imePadding(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (original == null) "New profile" else "Edit profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "Close profile editor") }
                }
                Column(Modifier.weight(1f).verticalScroll(formScroll).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Set the sound for this place. Changes take effect only after you save.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it; saveError = null }, modifier = Modifier.fillMaxWidth().testTag("profile-name"), label = { Text("Profile name") }, placeholder = { Text("e.g. Home or Office") }, singleLine = true, isError = nameError != null, supportingText = { Text(nameError ?: "Choose a name you’ll recognize.") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(ssid, { ssid = it; saveError = null }, modifier = Modifier.fillMaxWidth().testTag("profile-ssid"), label = { Text("Wi-Fi name (SSID)") }, placeholder = { Text("e.g. Home_WiFi") }, singleLine = true, isError = ssidError != null, supportingText = { Text(ssidError ?: "Match the Wi-Fi name exactly, including spaces and capitals.") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                    ResponsivePair(
                        first = { VolumeEditor("Inside the area", "When this Wi-Fi is nearby", ringIn, notificationIn, { ringIn = it }, { notificationIn = it }) },
                        second = { VolumeEditor("Outside the area", "When you leave Wi-Fi range", ringOut, notificationOut, { ringOut = it }, { notificationOut = it }) }
                    )
                    Text("0% is silent. Some phones link ringtone and notification volume. An active Do Not Disturb mode still takes priority.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (saveError != null) Text(saveError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = dismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                    Button(onClick = {
                        attempted = true
                        if (ProfileValidation.nameError(name, profiles, original?.name) == null && ProfileValidation.ssidError(ssid) == null) {
                            saveError = onSave(VolumeProfile(name.trim(), ssid, volumes))
                        } else {
                            scope.launch { formScroll.animateScrollTo(0) }
                        }
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save-profile")) { Text("Save profile") }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false }, title = { Text("Discard unsaved edits?") },
        text = { Text("Your saved profile will stay unchanged.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
    )
}

@Composable
private fun VolumeEditor(title: String, detail: String, ring: Int, notifications: Int, onRing: (Int) -> Unit, onNotifications: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VolumeSlider("Ringtone", title, ring, onRing)
            VolumeSlider("Notifications", title, notifications, onNotifications)
        }
    }
}

@Composable
private fun VolumeSlider(label: String, area: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$value%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value.toFloat(), { onChange(it.roundToInt()) }, valueRange = 0f..100f,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$area $label volume" })
}
