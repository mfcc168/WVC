package com.lafarge.wvc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lafarge.wvc.ui.theme.WVCTheme

class MainActivity : ComponentActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var audioManager: AudioManager
    private lateinit var scanReceiver: BroadcastReceiver
    private lateinit var context: Context


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

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        requestPermissions()

        createNotificationChannel()
        setupReceiver()

        setContent {
            WVCTheme {
                MainScreen()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
        )
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)

        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun setupReceiver() {
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this@MainActivity, "WiFi scan failed: Location permission not granted", Toast.LENGTH_SHORT).show()
                    return
                }

                val results = try {
                    wifiManager.scanResults
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    emptyList<ScanResult>()
                }

                val homeSSID = currentSSID.value
                val isIndoor = results.any { it.SSID == homeSSID }
                Log.d("WiFiScan", "Found SSIDs: ${results.joinToString { it.SSID }}")

                val volumePercent = if (isIndoor) indoorVolume.value else outdoorVolume.value
                setVolume(volumePercent)

                val message = if (isIndoor)
                    "You are indoor, setting volume to $volumePercent%"
                else
                    "You are outdoor, setting volume to $volumePercent%"

                showNotification(message)
            }

        }
    }

    private fun startScanService() {
        val serviceIntent = Intent(this, WiFiScanService::class.java).apply {
            putExtra("HOME_SSID", currentSSID.value)
        }
        startService(serviceIntent)
    }

    private fun stopScanService() {
        val serviceIntent = Intent(this, WiFiScanService::class.java)
        stopService(serviceIntent)
    }

    private fun setVolume(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (percent / 100.0 * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
    }

    private fun showNotification(message: String) {
        // Android 13+ needs POST_NOTIFICATIONS permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notification permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val notification = NotificationCompat.Builder(this, "wifi_volume_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("WiFi Volume Control")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(101, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "Notification failed: permission denied", Toast.LENGTH_SHORT).show()
        }
    }


    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder("wifi_volume_channel", NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("WiFi Volume Channel")
            .setDescription("Notification for volume control by WiFi")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    @Composable
    fun MainScreen() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F5F5) // Light, neutral background color
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp) // Added top padding
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp) // Increased spacing between elements for a clean look
            ) {
                // Card with rounded corners and a subtle shadow effect
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp), // Larger rounded corners
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentSSID.value,
                        onValueChange = { currentSSID.value = it },
                        label = { Text("Home WiFi SSID", fontWeight = FontWeight.Bold) }, // Bold label for contrast
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color.Gray, // Slightly lighter label when unfocused
                            cursorColor = Color(0xFF6200EE), // Modern accent color for cursor
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        textStyle = TextStyle(fontSize = 16.sp) // Slightly larger font for readability
                    )
                }

                // Volume control with modern slider and labels
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Indoor Volume: ${indoorVolume.value}%",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Slider(
                        value = indoorVolume.value.toFloat(),
                        onValueChange = { indoorVolume.value = it.toInt() },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6200EE), // Accent color for the thumb
                            activeTrackColor = Color(0xFF6200EE),
                            inactiveTrackColor = Color.LightGray
                        )
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Outdoor Volume: ${outdoorVolume.value}%",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Slider(
                        value = outdoorVolume.value.toFloat(),
                        onValueChange = { outdoorVolume.value = it.toInt() },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6200EE), // Accent color for the thumb
                            activeTrackColor = Color(0xFF6200EE),
                            inactiveTrackColor = Color.LightGray
                        )
                    )
                }

                // Modern buttons with rounded corners and padding
                Button(
                    onClick = { startScanService() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Scanning")
                }

                Button(
                    onClick = { stopScanService() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Scanning")
                }
            }
        }
    }





    override fun onDestroy() {
        super.onDestroy()
        stopScanService()
    }

        companion object {
            // Using mutableStateOf to reflect values in UI and logic
            var currentSSID = mutableStateOf("")
            var indoorVolume = mutableStateOf(50)
            var outdoorVolume = mutableStateOf(100)
        }
    }
