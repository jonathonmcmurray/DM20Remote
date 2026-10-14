package com.dm20remote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dm20Colors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00202E),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF0B0F12),
    onBackground = Color(0xFFE3E6E8),
    surface = Color(0xFF14191E),
    onSurface = Color(0xFFE3E6E8),
    surfaceVariant = Color(0xFF1E252B),
    onSurfaceVariant = Color(0xFFB6BDC4),
    error = Color(0xFFFF6E6E),
)

@Composable
fun Dm20Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dm20Colors, content = content)
}

object ChannelColors {
    val meterLow = Color(0xFF37D67A)
    val meterMid = Color(0xFFF2C744)
    val meterHigh = Color(0xFFE5544B)
    val trackBackground = Color(0xFF171C21)
    val trackRail = Color(0xFF2A3138)
    val handle = Color(0xFFE8EAED)
}
