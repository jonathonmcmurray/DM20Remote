package com.dm20remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dm20remote.ui.ChannelColors

/**
 * Vertical peak meter. Levels are dBFS; the display window defaults to the
 * -60..0 dB range the original uses for its channel meters.
 */
@Composable
fun MeterBar(
    levelDb: Float,
    modifier: Modifier = Modifier,
    minDb: Float = -60f,
    maxDb: Float = 0f,
) {
    val fraction = ((levelDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
    val color = when {
        fraction > 0.95f -> ChannelColors.meterHigh
        fraction > 0.80f -> ChannelColors.meterMid
        else -> ChannelColors.meterLow
    }
    Box(
        modifier = modifier.background(Color(0xFF1B2024)),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .background(color),
        )
    }
}
