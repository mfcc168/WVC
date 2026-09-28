package com.lafarge.wvc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2458CA), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFFF), onPrimaryContainer = Color(0xFF153877),
    secondary = Color(0xFF466080), secondaryContainer = Color(0xFFE9EEF5),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF172339),
    surface = Color.White, onSurface = Color(0xFF172339),
    surfaceVariant = Color(0xFFEDF1F7), onSurfaceVariant = Color(0xFF536178),
    outline = Color(0xFF748299), outlineVariant = Color(0xFFDDE4EF)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC7FF), onPrimary = Color(0xFF092B6A),
    primaryContainer = Color(0xFF263F6A), onPrimaryContainer = Color(0xFFDDE8FF),
    secondary = Color(0xFFBACBE3), secondaryContainer = Color(0xFF29394F),
    background = Color(0xFF0D1523), onBackground = Color(0xFFE5ECF8),
    surface = Color(0xFF172235), onSurface = Color(0xFFE5ECF8),
    surfaceVariant = Color(0xFF223049), onSurfaceVariant = Color(0xFFB9C6D9),
    outline = Color(0xFF8797AE), outlineVariant = Color(0xFF33435B)
)

@Composable
fun WVCTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}
