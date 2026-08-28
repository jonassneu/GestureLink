package de.gesturelink.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GestureColors = darkColorScheme(
    primary = Color(0xFF62E6C5),
    onPrimary = Color(0xFF002019),
    secondary = Color(0xFF8BCBFF),
    background = Color(0xFF07111F),
    surface = Color(0xFF101D2E),
    error = Color(0xFFFFB4AB),
)

@Composable
fun GestureLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GestureColors, typography = MaterialTheme.typography, content = content)
}

