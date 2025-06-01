package com.lafarge.wvc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.lafarge.wvc.ui.theme.WVCTheme

class MainActivity : ComponentActivity() {

    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var profileManager: ProfileStorageManager
    private val profileListState = mutableStateOf<List<VolumeProfile>>(emptyList())
    private val selectedProfileName = mutableStateOf("")
    private val showDeleteDialog = mutableStateOf(false)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions needed for WiFi scan", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        sharedPrefs = getSharedPreferences("wifi_volume_prefs", Context.MODE_PRIVATE)
        profileManager = ProfileStorageManager(this)

        // Load profiles first
        loadProfiles()

        // If no active profile, load from shared prefs (legacy support)
        if (selectedProfileName.value.isEmpty()) {
            loadSettings()
        }

        requestPermissions()
        createNotificationChannel()

        setContent {
            WVCTheme {
                MainScreen()
            }
        }
    }


    private fun loadSettings() {
        currentSSID.value = sharedPrefs.getString("HOME_SSID", "") ?: ""
//        mediaIndoorVolume.value = sharedPrefs.getInt("MEDIA_INDOOR_VOLUME", 50)
//        mediaOutdoorVolume.value = sharedPrefs.getInt("MEDIA_OUTDOOR_VOLUME", 100)
        ringtoneIndoorVolume.value = sharedPrefs.getInt("RINGTONE_INDOOR_VOLUME", 50)
        ringtoneOutdoorVolume.value = sharedPrefs.getInt("RINGTONE_OUTDOOR_VOLUME", 100)
        notificationIndoorVolume.value = sharedPrefs.getInt("NOTIFICATION_INDOOR_VOLUME", 50)
        notificationOutdoorVolume.value = sharedPrefs.getInt("NOTIFICATION_OUTDOOR_VOLUME", 100)
//        systemIndoorVolume.value = sharedPrefs.getInt("SYSTEM_INDOOR_VOLUME", 50)
//        systemOutdoorVolume.value = sharedPrefs.getInt("SYSTEM_OUTDOOR_VOLUME", 100)
//        callIndoorVolume.value = sharedPrefs.getInt("CALL_INDOOR_VOLUME", 50)
//        callOutdoorVolume.value = sharedPrefs.getInt("CALL_OUTDOOR_VOLUME", 100)
//        alarmIndoorVolume.value = sharedPrefs.getInt("ALARM_INDOOR_VOLUME", 50)
//        alarmOutdoorVolume.value = sharedPrefs.getInt("ALARM_OUTDOOR_VOLUME", 100)
    }

    private fun requestPermissions() {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }


    private fun startScanService() {
        if (currentSSID.value.isNotEmpty()) {
            val serviceIntent = Intent(this, WiFiScanService::class.java).apply {
                putExtra("HOME_SSID", currentSSID.value)
            }
            startService(serviceIntent)
        }
    }

    private fun stopScanService() {
        val serviceIntent = Intent(this, WiFiScanService::class.java)
        stopService(serviceIntent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder("wifi_volume_channel", NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("WiFi Volume Channel")
            .setDescription("Notification for volume control by WiFi")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }
    private fun loadProfiles() {
        profileListState.value = profileManager.loadProfiles()
        selectedProfileName.value = profileManager.getActiveProfileName()

        if (selectedProfileName.value.isNotEmpty()) {
            loadActiveProfileSettings()
        }
    }

    private fun loadActiveProfileSettings() {
        val activeProfile = profileManager.getActiveProfile()
        activeProfile?.let { profile ->
            currentSSID.value = profile.ssid
            // Indoor volumes
//            mediaIndoorVolume.value = profile.volumes[VolumeProfile.MEDIA_INDOOR] ?: 50
            ringtoneIndoorVolume.value = profile.volumes[VolumeProfile.RINGTONE_INDOOR] ?: 50
            notificationIndoorVolume.value = profile.volumes[VolumeProfile.NOTIFICATION_INDOOR] ?: 50
//            systemIndoorVolume.value = profile.volumes[VolumeProfile.SYSTEM_INDOOR] ?: 50
//            callIndoorVolume.value = profile.volumes[VolumeProfile.CALL_INDOOR] ?: 50
//            alarmIndoorVolume.value = profile.volumes[VolumeProfile.ALARM_INDOOR] ?: 50

            // Outdoor volumes
//            mediaOutdoorVolume.value = profile.volumes[VolumeProfile.MEDIA_OUTDOOR] ?: 100
            ringtoneOutdoorVolume.value = profile.volumes[VolumeProfile.RINGTONE_OUTDOOR] ?: 100
            notificationOutdoorVolume.value = profile.volumes[VolumeProfile.NOTIFICATION_OUTDOOR] ?: 100
//            systemOutdoorVolume.value = profile.volumes[VolumeProfile.SYSTEM_OUTDOOR] ?: 100
//            callOutdoorVolume.value = profile.volumes[VolumeProfile.CALL_OUTDOOR] ?: 100
//            alarmOutdoorVolume.value = profile.volumes[VolumeProfile.ALARM_OUTDOOR] ?: 100
        }
    }

    private fun createNewProfile(name: String) {
        if (profileListState.value.any { it.name == name }) {
            Toast.makeText(this, "Profile name already exists", Toast.LENGTH_SHORT).show()
            return
        }
        val newProfile = VolumeProfile(
            name = name,
            ssid = currentSSID.value,
            volumes = mapOf(
//                VolumeProfile.MEDIA_INDOOR to mediaIndoorVolume.value,
//                VolumeProfile.MEDIA_OUTDOOR to mediaOutdoorVolume.value,
                VolumeProfile.RINGTONE_INDOOR to ringtoneIndoorVolume.value,
                VolumeProfile.RINGTONE_OUTDOOR to ringtoneOutdoorVolume.value,
                VolumeProfile.NOTIFICATION_INDOOR to notificationIndoorVolume.value,
                VolumeProfile.NOTIFICATION_OUTDOOR to notificationOutdoorVolume.value,
//                VolumeProfile.SYSTEM_INDOOR to systemIndoorVolume.value,
//                VolumeProfile.SYSTEM_OUTDOOR to systemOutdoorVolume.value,
//                VolumeProfile.CALL_INDOOR to callIndoorVolume.value,
//                VolumeProfile.CALL_OUTDOOR to callOutdoorVolume.value,
//                VolumeProfile.ALARM_INDOOR to alarmIndoorVolume.value,
//                VolumeProfile.ALARM_OUTDOOR to alarmOutdoorVolume.value
            )
        )

        val updatedProfiles = profileManager.loadProfiles().toMutableList().apply {
            add(newProfile)
        }

        profileManager.saveProfiles(updatedProfiles)
        profileManager.setActiveProfileName(name)
        selectedProfileName.value = name
        profileListState.value = updatedProfiles
    }

    private fun deleteCurrentProfile() {
        val profileName = selectedProfileName.value
        if (profileName.isNotEmpty()) {
            profileManager.deleteProfile(profileName)
            profileListState.value = profileManager.loadProfiles()
            selectedProfileName.value = profileManager.getActiveProfileName()

            // If we deleted the active profile, load default settings
            if (selectedProfileName.value.isEmpty()) {
                loadSettings()
            }
        }
    }
    private fun updateActiveProfileSsid(newSsid: String) {
        val activeProfile = profileManager.getActiveProfile() ?: return
        val updatedProfile = activeProfile.copy(ssid = newSsid)

        val updatedProfiles = profileManager.loadProfiles().map {
            if (it.name == activeProfile.name) updatedProfile else it
        }

        profileManager.saveProfiles(updatedProfiles)
        profileListState.value = updatedProfiles
    }

    private fun saveCurrentSettingsToProfile() {
        val activeProfile = profileManager.getActiveProfile() ?: return

        val updatedProfile = activeProfile.copy(
            volumes = mapOf(
//                VolumeProfile.MEDIA_INDOOR to mediaIndoorVolume.value,
//                VolumeProfile.MEDIA_OUTDOOR to mediaOutdoorVolume.value,
                VolumeProfile.RINGTONE_INDOOR to ringtoneIndoorVolume.value,
                VolumeProfile.RINGTONE_OUTDOOR to ringtoneOutdoorVolume.value,
                VolumeProfile.NOTIFICATION_INDOOR to notificationIndoorVolume.value,
                VolumeProfile.NOTIFICATION_OUTDOOR to notificationOutdoorVolume.value,
//                VolumeProfile.SYSTEM_INDOOR to systemIndoorVolume.value,
//                VolumeProfile.SYSTEM_OUTDOOR to systemOutdoorVolume.value,
//                VolumeProfile.CALL_INDOOR to callIndoorVolume.value,
//                VolumeProfile.CALL_OUTDOOR to callOutdoorVolume.value,
//                VolumeProfile.ALARM_INDOOR to alarmIndoorVolume.value,
//                VolumeProfile.ALARM_OUTDOOR to alarmOutdoorVolume.value
            )
        )

        val updatedProfiles = profileManager.loadProfiles().map {
            if (it.name == activeProfile.name) updatedProfile else it
        }

        profileManager.saveProfiles(updatedProfiles)
        profileListState.value = updatedProfiles
    }

    @Composable
    fun DeleteProfileDialog(
        showDialog: Boolean,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    ) {
        if (showDialog) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Delete Profile") },
                text = { Text("Are you sure you want to delete this profile?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun ProfileSelectionSection(
        profiles: List<VolumeProfile>,
        selectedProfileName: String,
        onProfileSelected: (String) -> Unit,
        onCreateNewProfile: () -> Unit,
        onDeleteProfile: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Profiles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    if (selectedProfileName.isNotEmpty()) {
                        IconButton(
                            onClick = { onDeleteProfile() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Profile",
                                tint = Color.Red
                            )
                        }
                    }
                    Button(
                        onClick = onCreateNewProfile,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                    ) {
                        Text("New Profile")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (profiles.isEmpty()) {
                Text(
                    "No profiles yet. Create your first profile!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(profiles) { profile ->
                        ProfileChip(
                            profile = profile,
                            isSelected = profile.name == selectedProfileName,
                            onSelected = { onProfileSelected(profile.name) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ProfileChip(
        profile: VolumeProfile,
        isSelected: Boolean,
        onSelected: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            color = if (isSelected) Color(0xFF6200EE) else Color(0xFFE0E0E0),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.clickable { onSelected() }
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) Color.White else Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    @Composable
    fun NewProfileDialog(
        showDialog: Boolean,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit
    ) {
        if (showDialog) {
            val newProfileName = remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Create New Profile") },
                text = {
                    OutlinedTextField(
                        value = newProfileName.value,
                        onValueChange = { newProfileName.value = it },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProfileName.value.isNotBlank()) {
                                onConfirm(newProfileName.value)
                            }
                        },
                        enabled = newProfileName.value.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }
    }


    @Composable
    fun DonutToggleButton(
        isScanning: Boolean,
        onToggle: () -> Unit
    ) {
        val transition = rememberInfiniteTransition(label = "pulse")

        val pulseScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        val glowAlpha by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )

        val actualScale = if (isScanning) pulseScale else 1f
        val actualGlow = if (isScanning) glowAlpha else 0f

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = actualScale
                        scaleY = actualScale
                        alpha = actualGlow
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF03DAC6), Color.Transparent),
                            radius = 250f
                        ),
                        shape = CircleShape
                    )
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = actualScale
                        scaleY = actualScale
                    }
                    .shadow(10.dp, shape = CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isScanning)
                                listOf(Color(0xFF03DAC6), Color(0xFF018786))
                            else
                                listOf(Color(0xFF6200EE), Color(0xFF3700B3)),
                            radius = 200f
                        ),
                        shape = CircleShape
                    )
                    .clickable { onToggle() }
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Scanning",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun VolumeControlCard(
        title: String,
        indoorVolume: Int,
        outdoorVolume: Int,
        onIndoorChange: (Int) -> Unit,
        onOutdoorChange: (Int) -> Unit
    ) {
        Card(
            elevation = CardDefaults.cardElevation(6.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)

                Spacer(modifier = Modifier.height(8.dp))

                Text("Indoor Volume", fontWeight = FontWeight.Medium)
                Slider(
                    value = indoorVolume.toFloat(),
                    onValueChange = { onIndoorChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF6200EE),
                        thumbColor = Color(0xFF6200EE)
                    )
                )
                Text("${indoorVolume}%", modifier = Modifier.align(Alignment.End))

                Spacer(modifier = Modifier.height(12.dp))

                Text("Outdoor Volume", fontWeight = FontWeight.Medium)
                Slider(
                    value = outdoorVolume.toFloat(),
                    onValueChange = { onOutdoorChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF03DAC6),
                        thumbColor = Color(0xFF03DAC6)
                    )
                )
                Text("${outdoorVolume}%", modifier = Modifier.align(Alignment.End))
            }
        }
    }





    @Composable
    fun MainScreen() {
        val showNewProfileDialog = remember { mutableStateOf(false) }
        val showDeleteDialog = remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF0F2F5)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Selection Section
                ProfileSelectionSection(
                    profiles = profileListState.value,
                    selectedProfileName = selectedProfileName.value,
                    onProfileSelected = { name ->
                        profileManager.setActiveProfileName(name)
                        selectedProfileName.value = name
                        loadActiveProfileSettings()
                    },
                    onCreateNewProfile = { showNewProfileDialog.value = true },
                    onDeleteProfile = { showDeleteDialog.value = true }
                )

                // New Profile Dialog
                NewProfileDialog(
                    showDialog = showNewProfileDialog.value,
                    onDismiss = { showNewProfileDialog.value = false },
                    onConfirm = { name ->
                        createNewProfile(name)
                        showNewProfileDialog.value = false
                    }
                )
                // Delete Profile Dialog
                DeleteProfileDialog(
                    showDialog = showDeleteDialog.value,
                    onDismiss = { showDeleteDialog.value = false },
                    onConfirm = {
                        deleteCurrentProfile()
                        showDeleteDialog.value = false
                    }
                )
                // Rest of your existing UI (SSID input, volume controls, etc.)
                OutlinedTextField(
                    value = currentSSID.value,
                    onValueChange = {
                        currentSSID.value = it
                        sharedPrefs.edit().putString("HOME_SSID", it).apply()
                        updateActiveProfileSsid(it)
                    },
                    label = { Text("Home WiFi SSID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6200EE),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color(0xFF333333),
                        unfocusedTextColor = Color(0xFF333333)
                    )
                )

                // Volume Control Cards
//                VolumeControlCard(
//                    title = "Media Volume",
//                    indoorVolume = mediaIndoorVolume.value,
//                    outdoorVolume = mediaOutdoorVolume.value,
//                    onIndoorChange = {
//                        mediaIndoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    },
//                    onOutdoorChange = {
//                        mediaOutdoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    }
//                )

                VolumeControlCard(
                    title = "Ringtone Volume",
                    indoorVolume = ringtoneIndoorVolume.value,
                    outdoorVolume = ringtoneOutdoorVolume.value,
                    onIndoorChange = {
                        ringtoneIndoorVolume.value = it
                        saveCurrentSettingsToProfile()
                    },
                    onOutdoorChange = {
                        ringtoneOutdoorVolume.value = it
                        saveCurrentSettingsToProfile()
                    }
                )

                VolumeControlCard(
                    title = "Notification Volume",
                    indoorVolume = notificationIndoorVolume.value,
                    outdoorVolume = notificationOutdoorVolume.value,
                    onIndoorChange = {
                        notificationIndoorVolume.value = it
                        saveCurrentSettingsToProfile()
                    },
                    onOutdoorChange = {
                        notificationOutdoorVolume.value = it
                        saveCurrentSettingsToProfile()
                    }
                )
//
//                VolumeControlCard(
//                    title = "System Volume",
//                    indoorVolume = systemIndoorVolume.value,
//                    outdoorVolume = systemOutdoorVolume.value,
//                    onIndoorChange = {
//                        systemIndoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    },
//                    onOutdoorChange = {
//                        systemOutdoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    }
//                )
//
//                VolumeControlCard(
//                    title = "Call Volume",
//                    indoorVolume = callIndoorVolume.value,
//                    outdoorVolume = callOutdoorVolume.value,
//                    onIndoorChange = {
//                        callIndoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    },
//                    onOutdoorChange = {
//                        callOutdoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    }
//                )
//
//                VolumeControlCard(
//                    title = "Alarm Volume",
//                    indoorVolume = alarmIndoorVolume.value,
//                    outdoorVolume = alarmOutdoorVolume.value,
//                    onIndoorChange = {
//                        alarmIndoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    },
//                    onOutdoorChange = {
//                        alarmOutdoorVolume.value = it
//                        saveCurrentSettingsToProfile()
//                    }
//                )

                // Scan Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DonutToggleButton(
                        isScanning = isScanning.value,
                        onToggle = {
                            if (currentSSID.value.isNotEmpty()) {
                                if (isScanning.value) stopScanService() else startScanService()
                                isScanning.value = !isScanning.value
                            } else {
                                Toast.makeText(context, "Please enter your Home WiFi SSID", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isScanning.value) "Scanning..." else "Tap to Start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanService()
    }

    companion object {
        var currentSSID = mutableStateOf("")
//        var mediaIndoorVolume = mutableStateOf(50)
//        var mediaOutdoorVolume = mutableStateOf(100)
        var ringtoneIndoorVolume = mutableStateOf(50)
        var ringtoneOutdoorVolume = mutableStateOf(100)
        var notificationIndoorVolume = mutableStateOf(50)
        var notificationOutdoorVolume = mutableStateOf(100)
//        var systemIndoorVolume = mutableStateOf(50)
//        var systemOutdoorVolume = mutableStateOf(100)
//        var callIndoorVolume = mutableStateOf(50)
//        var callOutdoorVolume = mutableStateOf(100)
//        var alarmIndoorVolume = mutableStateOf(50)
//        var alarmOutdoorVolume = mutableStateOf(100)
        var isScanning = mutableStateOf(false)
    }
}