package com.dm20remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dm20remote.net.Units
import com.dm20remote.ui.components.MeterBar
import com.dm20remote.ui.components.VerticalFader

/**
 * A single mixer strip. The fader keeps a local value while the user drags and
 * only sends a command on release, so the console echo does not fight the gesture.
 */
@Composable
fun ChannelStrip(
    name: String,
    faderPosition: Float,
    mute: Boolean,
    solo: Boolean,
    meterDb: Float,
    onFaderCommit: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(faderPosition) }
    LaunchedEffect(faderPosition) {
        if (!dragging) local = faderPosition
    }

    Column(
        modifier = modifier
            .width(78.dp)
            .fillMaxHeight()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { onOpen() }
                .padding(vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeterBar(
                levelDb = meterDb,
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp),
            )
            Spacer(Modifier.width(6.dp))
            VerticalFader(
                position = local,
                onPositionChange = {
                    dragging = true
                    local = it
                },
                onCommit = {
                    dragging = false
                    onFaderCommit(local)
                },
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight(),
            )
        }

        Text(
            text = formatDb(Units.faderPositionToDb(local)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToggleChip("M", mute, MaterialTheme.colorScheme.error) { onMuteToggle() }
            ToggleChip("S", solo, MaterialTheme.colorScheme.primary) { onSoloToggle() }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun ToggleChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) activeColor else Color(0xFF262E35))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.Black else Color(0xFFB6BDC4),
        )
    }
}

/** Formats a dB value the way a mixing console does. */
fun formatDb(db: Float): String = when {
    db <= -80f -> "-\u221E"
    db >= 0f -> "+%.1f".format(db)
    else -> "%.1f".format(db)
}
