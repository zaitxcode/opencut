package com.zaitxcode.opencut.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EditorColors = darkColorScheme(
    primary = Color(0xFF3A7AFE),
    onPrimary = Color.White,
    secondary = Color(0xFFFF5A5A),
    background = Color(0xFF101012),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C20),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFB6B6C0),
)

/** The editor is always dark; the palette is intentionally neutral. */
@Composable
fun OpenCutTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = EditorColors,
        typography = Typography(),
        content = content,
    )
}
