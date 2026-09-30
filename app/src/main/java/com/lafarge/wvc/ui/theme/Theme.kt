package com.lafarge.wvc.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// A deliberately light, neutral palette, independent of wallpaper and system theme.
private val WhiteColors = lightColorScheme(
    primary = Color(0xFF303539), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EBE9), onPrimaryContainer = Color(0xFF303539),
    secondary = Color(0xFF4F6C5E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EFEA), onSecondaryContainer = Color(0xFF385345),
    tertiary = Color(0xFF665D50),
    background = Color(0xFFF5F6F7), onBackground = Color(0xFF282D31),
    surface = Color(0xFFFAFBFC), onSurface = Color(0xFF282D31),
    surfaceVariant = Color(0xFFECEEF0), onSurfaceVariant = Color(0xFF62696F),
    outline = Color(0xFF7D858B), outlineVariant = Color(0xFFDDE1E4),
    error = Color(0xFFAB3434), errorContainer = Color(0xFFFCECEB)
)

@Composable
fun WVCTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WhiteColors,
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(32.dp)),
        content = content
    )
}
