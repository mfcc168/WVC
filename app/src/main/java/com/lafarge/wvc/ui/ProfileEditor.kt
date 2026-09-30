package com.lafarge.wvc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lafarge.wvc.ProfileValidation
import com.lafarge.wvc.VolumeProfile
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
                    Text(if (original == null) "Create profile" else "Edit profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = dismiss) { Icon(Icons.Default.Close, "Close profile editor") }
                }
                Column(Modifier.weight(1f).verticalScroll(formScroll).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("A familiar place. Just the right volume.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it; saveError = null }, modifier = Modifier.fillMaxWidth().testTag("profile-name"), label = { Text("Profile name") }, placeholder = { Text("e.g. Home or Office") }, shape = RoundedCornerShape(16.dp), singleLine = true, isError = nameError != null, supportingText = { Text(nameError ?: "Choose a name you’ll recognize.") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(ssid, { ssid = it; saveError = null }, modifier = Modifier.fillMaxWidth().testTag("profile-ssid"), label = { Text("Wi-Fi name (SSID)") }, placeholder = { Text("e.g. Home_WiFi") }, shape = RoundedCornerShape(16.dp), singleLine = true, isError = ssidError != null, supportingText = { Text(ssidError ?: "Match the Wi-Fi name exactly, including spaces and capitals.") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                    ResponsivePair(
                        first = { VolumeEditor("In Wi-Fi range", "When this Wi-Fi is nearby", ringIn, notificationIn, { ringIn = it }, { notificationIn = it }) },
                        second = { VolumeEditor("Out of range", "When you leave Wi-Fi range", ringOut, notificationOut, { ringOut = it }, { notificationOut = it }) }
                    )
                    Text("0% is silent. Some phones link ringtone and notification volume. An active Do Not Disturb mode still takes priority.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (saveError != null) Text(saveError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = dismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
                    Button(onClick = {
                        attempted = true
                        if (ProfileValidation.nameError(name, profiles, original?.name) == null && ProfileValidation.ssidError(ssid) == null) {
                            saveError = onSave(VolumeProfile(name.trim(), ssid, volumes))
                        } else {
                            scope.launch { formScroll.animateScrollTo(0) }
                        }
                    }, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("save-profile"), shape = RoundedCornerShape(16.dp)) { Text("Save profile") }
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
    SoftCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VolumeSlider("Ringtone", title, ring, onRing)
        VolumeSlider("Notifications", title, notifications, onNotifications)
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
