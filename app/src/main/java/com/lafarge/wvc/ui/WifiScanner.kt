package com.lafarge.wvc.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** The waves show monitoring intent, not an unthrottled radio scan. */
@Composable
internal fun WifiScanner(enabled: Boolean, visible: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit = {}) {
    val motionAllowed = rememberMotionAllowed()
    val phase = if (enabled && visible && motionAllowed) {
        val transition = rememberInfiniteTransition(label = "Wi-Fi waves")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "wave radius").value
    } else 0f
    ScannerArtwork(phase, enabled, modifier, onToggle)
}

@Composable
internal fun ScannerArtwork(phase: Float, enabled: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit = {}) {
    val ink = MaterialTheme.colorScheme.onSurface
    val color by animateColorForSignal(enabled)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .95f else 1f,
        spring(dampingRatio = .85f, stiffness = 550f), label = "Wi-Fi button press")
    val depth by animateFloatAsState(if (enabled || pressed) 1f else 0f,
        tween(200), label = "Wi-Fi button depth")
    Box(modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
        .testTag("wifi-scanner"),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            // A permanent outer ring anchors the composition when motion is disabled.
            drawCircle(Color(0xFFE5E8EA), size.minDimension * .47f, center, style = Stroke(1.dp.toPx()))
            repeat(3) { index ->
                val progress = (phase + index / 3f) % 1f
                val radius = size.minDimension * (.29f + .19f * progress)
                val opacity = (1 - progress) * if (enabled) .24f else .12f
                drawCircle(ink.copy(alpha = opacity), radius, center, style = Stroke(1.3.dp.toPx()))
            }
        }
        Box(Modifier.fillMaxSize(.58f).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }.softSurface(1000.dp)
            .testTag("wifi-toggle").semantics {
                contentDescription = "Wi-Fi monitoring"
                stateDescription = if (enabled) "On" else "Off"
            }
            .toggleable(value = enabled, interactionSource = interaction, indication = null,
                role = Role.Switch, onValueChange = { onToggle() }), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(.87f).clip(CircleShape).softInset(1000.dp, depth), contentAlignment = Alignment.Center) {
                SignalMark(Modifier.fillMaxSize(.56f), color)
            }
        }
    }
}

@Composable
private fun animateColorForSignal(enabled: Boolean): State<Color> = androidx.compose.animation.animateColorAsState(
    if (enabled) Color(0xFF405E4E) else Color(0xFF596168), tween(350), label = "Wi-Fi state"
)

/** Stop frames off screen; honor Android's Remove animations setting, including live changes. */
@Composable
private fun rememberMotionAllowed(): Boolean {
    val owner = LocalLifecycleOwner.current
    val resolver = LocalContext.current.contentResolver
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var animations by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(owner, resolver) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            animations = ValueAnimator.areAnimatorsEnabled()
        }
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { animations = ValueAnimator.areAnimatorsEnabled() }
        }
        owner.lifecycle.addObserver(observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settingsObserver)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            resolver.unregisterContentObserver(settingsObserver)
        }
    }
    return resumed && animations
}
