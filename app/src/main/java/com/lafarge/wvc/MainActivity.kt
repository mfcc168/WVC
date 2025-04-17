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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    fun DonutToggleButton(
        isScanning: Boolean,
        onToggle: () -> Unit
    ) {
        val transition = rememberInfiniteTransition(label = "pulse")

        // Pulsing animation
        val pulseScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        // Glow animation
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
            modifier = Modifier
                .size(140.dp)
        ) {
            // Glowing aura
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

            // Donut button
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
    fun MainScreen() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF0F2F5) // soft background color
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6200EE)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            "Smart Volume Control",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Automatically adjust volume based on your WiFi location.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            )
                        )
                    }
                }

                // SSID Input
                OutlinedTextField(
                    value = currentSSID.value,
                    onValueChange = { currentSSID.value = it },
                    label = { Text("Home WiFi SSID") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6200EE),
                        unfocusedBorderColor = Color.Gray
                    )
                )

                // Volume controls grouped
                Card(
                    elevation = CardDefaults.cardElevation(6.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Indoor Volume", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = indoorVolume.value.toFloat(),
                            onValueChange = { indoorVolume.value = it.toInt() },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF6200EE),
                                thumbColor = Color(0xFF6200EE)
                            )
                        )
                        Text("${indoorVolume.value}%", modifier = Modifier.align(Alignment.End))

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Outdoor Volume", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = outdoorVolume.value.toFloat(),
                            onValueChange = { outdoorVolume.value = it.toInt() },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF03DAC6),
                                thumbColor = Color(0xFF03DAC6)
                            )
                        )
                        Text("${outdoorVolume.value}%", modifier = Modifier.align(Alignment.End))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DonutToggleButton(
                        isScanning = isScanning.value,
                        onToggle = {
                            if (isScanning.value) stopScanService() else startScanService()
                            isScanning.value = !isScanning.value
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
        var indoorVolume = mutableStateOf(50)
        var outdoorVolume = mutableStateOf(100)
        var isScanning = mutableStateOf(false)
    }
}
