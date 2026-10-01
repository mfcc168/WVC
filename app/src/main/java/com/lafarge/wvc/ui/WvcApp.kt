package com.lafarge.wvc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lafarge.wvc.VolumeProfile

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
    onDelete: (String) -> Unit,
    profileNotice: ProfileNotice? = null,
    onNoticeDismissed: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingName by rememberSaveable { mutableStateOf<String?>(null) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var chooserOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val homeScroll = rememberScrollState()
    val profileScroll = rememberScrollState()
    val openEditor: (String?) -> Unit = { editingName = it; editorOpen = true }
    BackHandler(tab != 0 && !editorOpen && deletingName == null && !setupOpen && !chooserOpen) { tab = 0 }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onMessageShown() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomTabs(tab) { tab = it } }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            Crossfade(tab, animationSpec = tween(220), label = "page transition") { page ->
                Column(
                    Modifier.widthIn(max = 1000.dp).fillMaxSize()
                        .verticalScroll(if (page == 0) homeScroll else profileScroll)
                        .padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    AppHeader { setupOpen = true }
                    if (page == 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sound, in sync.", style = MaterialTheme.typography.headlineLarge)
                            Text("A little quieter. Automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HomeLayout(
                            monitor = {
                                MonitoringPanel(state, tab == 0 && !editorOpen && !setupOpen && !chooserOpen,
                                    onStart = {
                                        when {
                                            state.enabled -> onStart()
                                            state.activeProfile == null -> if (state.profiles.isEmpty()) openEditor(null) else chooserOpen = true
                                            state.requiredMissing > 0 -> setupOpen = true
                                            else -> onStart()
                                        }
                                    }, onStop = onStop, onSetupAction = onSetupAction)
                            },
                            controls = {
                                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                    SelectedProfile(state) { if (state.profiles.isEmpty()) openEditor(null) else chooserOpen = true }
                                    state.activeProfile?.let { SoundSummary(it) { openEditor(it.name) } }
                                    if (state.requiredMissing > 0) SetupNotice(state.requiredMissing) { setupOpen = true }
                                }
                            }
                        )
                        Text("Nearby Wi-Fi sets your sound, even when you’re not connected. Android manages scan timing; changes can take a few minutes.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your sound profiles.", style = MaterialTheme.typography.headlineLarge)
                            Text("Give every place its own volume.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { openEditor(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Create profile")
                        }
                        if (state.profiles.isEmpty()) EmptyProfiles()
                        else {
                            SectionLabel("${state.profiles.size} SAVED ${if (state.profiles.size == 1) "PROFILE" else "PROFILES"} · ONE SELECTED AT A TIME")
                            state.profiles.chunked(2).forEach { pair ->
                                ResponsivePair(
                                    first = { ProfileCard(pair[0], state.activeName, onSelect, openEditor) { deletingName = it } },
                                    second = { pair.getOrNull(1)?.let { ProfileCard(it, state.activeName, onSelect, openEditor) { deletingName = it } } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (chooserOpen) ProfileChooser(state, { chooserOpen = false }, {
        onSelect(it); chooserOpen = false
    }, { chooserOpen = false; openEditor(null) })
    if (setupOpen) SetupDialog(state, { setupOpen = false }, onSetupAction)
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
            onDismissRequest = { deletingName = null }, title = { Text("Delete $name?") },
            text = { Text(if (name == state.activeName) "This is your selected profile. Monitoring will stop and your current volume will stay unchanged." else "This removes the saved Wi-Fi name and volume settings. It won’t affect the selected profile.") },
            confirmButton = { TextButton(onClick = { onDelete(name); deletingName = null }) { Text("Delete profile", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletingName = null }) { Text("Keep profile") } }
        )
    }
    ProfileChangePopup(profileNotice, onNoticeDismissed)
}

@Composable
private fun AppHeader(onSetup: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SignalMark(Modifier.size(27.dp))
        Spacer(Modifier.width(10.dp))
        Text("WVC", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onSetup, modifier = Modifier.softSurface(16.dp).size(48.dp)) {
            Icon(Icons.Default.Settings, "App setup", Modifier.size(21.dp))
        }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 440.dp).fillMaxWidth().softSurface(24.dp).selectableGroup().padding(7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Home", "Profile").forEachIndexed { index, label ->
                val active = selected == index
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val depth by animateFloatAsState(if (active || pressed) 1f else 0f, tween(200), label = "tab inset depth")
                val scale by animateFloatAsState(if (pressed) .98f else 1f, tween(140), label = "tab press")
                val foreground = MaterialTheme.colorScheme.onSurface
                Row(Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(18.dp)).softInset(18.dp, depth).testTag("tab-$label")
                    .selectable(active, interactionSource = interaction, indication = null, role = Role.Tab, onClick = { onSelect(index) })
                    .heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (index == 0) Icon(Icons.Default.Home, null, Modifier.size(22.dp), tint = foreground)
                    else ProfileGlyph(Modifier.size(22.dp), foreground)
                    Spacer(Modifier.width(9.dp))
                    Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ProfileGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        repeat(3) { i ->
            val y = size.height * (.2f + i * .3f)
            val knob = size.width * if (i == 1) .66f else .34f
            drawLine(color, Offset(size.width * .1f, y), Offset(size.width * .9f, y), 1.5.dp.toPx(), StrokeCap.Round)
            drawCircle(color, 3.dp.toPx(), Offset(knob, y))
        }
    }
}

@Composable
private fun HomeLayout(monitor: @Composable () -> Unit, controls: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 700.dp && fontScale <= 1.3f) Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { monitor() }
            Box(Modifier.weight(1f)) { controls() }
        } else Column(verticalArrangement = Arrangement.spacedBy(28.dp)) { monitor(); controls() }
    }
}

@Composable
private fun MonitoringPanel(state: WvcState, visible: Boolean, onStart: () -> Unit, onStop: () -> Unit, onSetupAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("monitoring-panel"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WifiScanner(state.monitoringOn, visible, Modifier.widthIn(max = 266.dp),
            onToggle = if (state.monitoringOn) onStop else onStart)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).background(if (state.monitoringOn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, CircleShape))
                Text(if (state.monitoringOn) "Monitoring enabled" else "Monitoring paused", style = MaterialTheme.typography.titleMedium)
            }
            Text(if (state.monitoringOn) "Tap to stop scanning" else "Tap to start scanning",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (state.enabled || state.recoveryAction != null) state.status else "Ready whenever you are.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.recoveryAction?.let { action ->
            if (action == "dnd" || action == "location") {
                TextButton(onClick = { onSetupAction(action) }, modifier = Modifier.testTag("monitoring-recovery")) {
                    Text(if (action == "dnd") "Allow sound control" else "Review location access")
                }
            }
        }
    }
}

@Composable
private fun SelectedProfile(state: WvcState, onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("SELECTED PROFILE")
        SoftAction(onChoose, Modifier.fillMaxWidth().testTag("choose-profile")) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                ProfileGlyph(Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(state.activeProfile?.name ?: "Choose your sound", style = MaterialTheme.typography.titleMedium)
                Text(state.activeProfile?.ssid ?: "Create a profile to get started", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.KeyboardArrowDown, "Select profile", Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SoundSummary(profile: VolumeProfile, onEdit: () -> Unit) {
    SoftCard(animateSize = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sound settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Edit, "Edit sound settings", Modifier.size(19.dp)) }
        }
        SoundArea("In Wi-Fi range", profile, true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SoundArea("Out of range", profile, false)
    }
}

@Composable
private fun SoundArea(title: String, profile: VolumeProfile, inside: Boolean) {
    val ring = profile.volumes[if (inside) VolumeProfile.RINGTONE_INDOOR else VolumeProfile.RINGTONE_OUTDOOR] ?: 50
    val notification = profile.volumes[if (inside) VolumeProfile.NOTIFICATION_INDOOR else VolumeProfile.NOTIFICATION_OUTDOOR] ?: 50
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VolumeReadout("Ringtone", ring, Modifier.weight(1f))
            VolumeReadout("Notifications", notification, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VolumeReadout(label: String, value: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("${value.coerceIn(0, 100)}%", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupNotice(count: Int, onSetup: () -> Unit) {
    SoftCard {
        Text("One more thing…", style = MaterialTheme.typography.titleMedium)
        Text("$count required ${if (count == 1) "setting needs" else "settings need"} your attention.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onSetup) { Text("Complete setup"); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
    }
}

@Composable
private fun EmptyProfiles() {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(88.dp).softSurface(28.dp), contentAlignment = Alignment.Center) { ProfileGlyph(Modifier.size(34.dp), MaterialTheme.colorScheme.onSurface) }
        Text("A sound for every place.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text("Start with Home, Office, or your favorite quiet spot. Pick a Wi-Fi name and set your volumes.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileCard(profile: VolumeProfile, activeName: String, onSelect: (String) -> Unit, onEdit: (String?) -> Unit, onDelete: (String) -> Unit) {
    val selected = profile.name == activeName
    SoftCard(Modifier.testTag("profile-${profile.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleLarge)
                Text(profile.ssid, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Check, "Selected profile", tint = MaterialTheme.colorScheme.secondary)
        }
        SoundArea("In Wi-Fi range", profile, true)
        SoundArea("Out of range", profile, false)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelect(profile.name) }, enabled = !selected, modifier = Modifier.weight(1f)) {
                Text(if (selected) "Selected" else "Use profile", color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onEdit(profile.name) }) { Icon(Icons.Default.Edit, "Edit ${profile.name}", Modifier.size(20.dp)) }
            IconButton(onClick = { onDelete(profile.name) }) { Icon(Icons.Default.Delete, "Delete ${profile.name}", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ProfileChooser(state: WvcState, onDismiss: () -> Unit, onSelect: (String) -> Unit, onCreate: () -> Unit) {
    UtilityDialog("Choose a profile", onDismiss) {
        Text("Use one profile at a time. Sound changes after a fresh Wi-Fi scan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.profiles.forEach { profile ->
                Row(Modifier.fillMaxWidth().softSurface(18.dp)
                    .selectable(profile.name == state.activeName, role = Role.RadioButton, onClick = { onSelect(profile.name) })
                    .padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text(profile.ssid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(profile.name == state.activeName, onClick = null)
                }
            }
        }
        TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create profile") }
    }
}

@Composable
private fun SetupDialog(state: WvcState, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    UtilityDialog("App setup", onDismiss) {
        Text(if (state.requiredMissing == 0) "You’re ready to monitor." else "Let’s get everything ready.", style = MaterialTheme.typography.titleLarge)
        state.setup.forEach { item ->
            SoftCard {
                SectionLabel(when { item.ready -> "READY"; item.required -> "REQUIRED"; else -> "OPTIONAL" })
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onAction(item.id) }, modifier = Modifier.semantics { contentDescription = "Manage ${item.title}" }) { Text(if (item.ready) "Manage setting" else item.action) }
            }
        }
        Text("After a restart", style = MaterialTheme.typography.titleMedium)
        Text(if (state.needsRebootResume) "On Android 17, open WVC and tap the Wi-Fi button after a reboot or app update. Android requires this before background sound changes can resume." else "Monitoring restarts after reboot if you left it enabled and granted the required access. After force-stopping WVC, open it and switch monitoring off and on using the Wi-Fi button.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("If monitoring is delayed", style = MaterialTheme.typography.titleMedium)
        Text("Sleeping phones and battery restrictions can delay Wi-Fi scans. Check WVC’s battery settings if monitoring stops overnight.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onAction("battery") }) { Text("Open app settings") }
    }
}

@Composable
private fun UtilityDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 600.dp).fillMaxWidth().heightIn(max = 720.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close $title") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
            }
        }
    }
}
