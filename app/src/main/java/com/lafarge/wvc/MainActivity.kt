package com.lafarge.wvc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.lafarge.wvc.ui.theme.WVCTheme

class MainActivity : ComponentActivity() {

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

        requestPermissions()
        createNotificationChannel()

        setContent {
            WVCTheme {
                MainScreen()
            }
        }
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
        val serviceIntent = Intent(this, WiFiScanService::class.java).apply {
            putExtra("HOME_SSID", currentSSID.value)
            putExtra("INDOOR_VOLUME", indoorVolume.value)
            putExtra("OUTDOOR_VOLUME", outdoorVolume.value)
        }
        startService(serviceIntent)
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

    @Composable
    fun MainScreen() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F5F5)
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentSSID.value,
                        onValueChange = { currentSSID.value = it },
                        label = { Text("Home WiFi SSID", fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color(0xFF6200EE),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        textStyle = TextStyle(fontSize = 16.sp)
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Indoor Volume: ${indoorVolume.value}%", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Slider(
                        value = indoorVolume.value.toFloat(),
                        onValueChange = { indoorVolume.value = it.toInt() },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6200EE),
                            activeTrackColor = Color(0xFF6200EE),
                            inactiveTrackColor = Color.LightGray
                        )
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Outdoor Volume: ${outdoorVolume.value}%", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Slider(
                        value = outdoorVolume.value.toFloat(),
                        onValueChange = { outdoorVolume.value = it.toInt() },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF6200EE),
                            activeTrackColor = Color(0xFF6200EE),
                            inactiveTrackColor = Color.LightGray
                        )
                    )
                }

// Toggle scanning button with animation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            if (isScanning.value) {
                                stopScanService()
                            } else {
                                startScanService()
                            }
                            isScanning.value = !isScanning.value
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (!isScanning.value) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Scanning",
                                tint = Color.Black
                            )
                        } else {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 3.dp,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(2.dp)
                            )
                        }
                    }


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
        var indoorVolume = mutableStateOf(50)
        var outdoorVolume = mutableStateOf(100)
        var isScanning = mutableStateOf(false)
    }
}
