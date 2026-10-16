package com.dm20remote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The Material theme is kept only so the handful of Material3 pieces still in use
 * (`Surface`, text defaults, the wire-log screen) inherit the redesign palette.
 * Everything in the console UI draws from [Dm20Colors] directly.
 */
private val Dm20Scheme = darkColorScheme(
    primary = Dm20Colors.primary,
    onPrimary = Dm20Colors.onPrimary,
    background = Dm20Colors.background,
    onBackground = Dm20Colors.onSurface,
    surface = Dm20Colors.surface,
    onSurface = Dm20Colors.onSurface,
    surfaceVariant = Dm20Colors.surfaceVariant,
    onSurfaceVariant = Dm20Colors.onSurfaceVariant,
    outline = Dm20Colors.divider,
    error = Dm20Colors.error,
    onError = Dm20Colors.background,
)

@Composable
fun Dm20Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dm20Scheme, content = content)
}
