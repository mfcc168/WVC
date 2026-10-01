package com.lafarge.wvc.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Soft opposing shadows, drawn with regular GPU primitives on every supported API. */
internal fun Modifier.softSurface(radius: Dp = 26.dp): Modifier = this
    .drawWithCache {
        val corner = radius.toPx()
        val offset = 4.dp.toPx()
        val spread = 8.dp.toPx()
        onDrawBehind {
            // Layered translucent outlines approximate a diffuse shadow without a software layer.
            for (layer in 12 downTo 1) {
                val blur = spread * layer / 12f
                val shadowSize = Size(size.width + blur * 2, size.height + blur * 2)
                val corners = CornerRadius(corner + blur)
                drawRoundRect(Color(0xFFADB5BD).copy(alpha = .018f), Offset(offset - blur, offset - blur), shadowSize, corners)
                drawRoundRect(Color.White.copy(alpha = .075f), Offset(-offset - blur, -offset - blur), shadowSize, corners)
            }
        }
    }
    .clip(RoundedCornerShape(radius))
    .background(Brush.linearGradient(listOf(Color(0xFFFCFDFE), Color(0xFFF1F3F5))))
    .border(1.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(radius))

/** White surface with shadows inside its edge; depth animates without changing the fill. */
internal fun Modifier.softInset(radius: Dp = 18.dp, depth: Float = 1f): Modifier = this
    .drawWithCache {
        val corner = radius.toPx().coerceAtMost(size.minDimension / 2)
        val surface = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner)))
        }
        val offset = 3.dp.toPx()
        val spread = 7.dp.toPx()
        fun insetShadow(inset: Float, shift: Float): Path {
            val hole = Path().apply {
                addRoundRect(RoundRect(inset + shift, inset + shift,
                    size.width - inset + shift, size.height - inset + shift,
                    CornerRadius((corner - inset).coerceAtLeast(0f))))
            }
            return Path.combine(PathOperation.Difference, surface, hole)
        }
        val shadows = (1..12).map { layer ->
            val inset = spread * layer / 12f
            insetShadow(inset, offset) to insetShadow(inset, -offset)
        }
        onDrawBehind {
            drawPath(surface, Color(0xFFFAFBFC))
            val amount = depth.coerceIn(0f, 1f)
            shadows.forEach { (shade, light) ->
                drawPath(shade, Color(0xFF8C969F).copy(alpha = .035f * amount))
                drawPath(light, Color.White.copy(alpha = .12f * amount))
            }
        }
    }

@Composable
internal fun SoftCard(modifier: Modifier = Modifier, animateSize: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().softSurface().then(if (animateSize) Modifier.animateContentSize() else Modifier).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
internal fun SoftAction(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(stiffness = 550f), label = "button press")
    Row(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.softSurface(18.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content
    )
}

@Composable
internal fun ResponsivePair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp && fontScale <= 1.3f) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { first(); second() }
    }
}

@Composable
internal fun SignalMark(modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        val stroke = size.minDimension * .065f
        listOf(.90f, .60f, .30f).forEach { scale ->
            val width = size.minDimension * scale
            drawArc(color, 222f, 96f, false,
                topLeft = Offset((size.width - width) / 2, size.height * .10f + (1 - scale) * size.height * .65f),
                size = Size(width, width), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawCircle(color, stroke * .72f, Offset(size.width / 2, size.height * .79f))
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
