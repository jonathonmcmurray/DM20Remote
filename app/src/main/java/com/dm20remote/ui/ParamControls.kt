package com.dm20remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.dm20remote.net.Units
import kotlin.math.roundToInt

/** The rounded surface shared by the channel and bus detail dialogs. */
@Composable
internal fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun Readout(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * A labelled slider over an engineering [range]. Like the mixer fader it holds a
 * local value while the user drags and only reports on release, so the console
 * echo does not fight the gesture.
 */
@Composable
internal fun ValueSlider(
    label: String,
    value: Float,
    range: Units.ParamRange,
    steps: Int = 0,
    format: (Float) -> String = { formatEngineering(it, range.unit) },
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (!dragging) local = value }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                format(local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = {
                dragging = true
                local = range.clamp(it)
            },
            onValueChangeFinished = {
                dragging = false
                onChange(local)
            },
            valueRange = range.min..range.max,
            steps = steps,
        )
    }
}

/**
 * The PEQ frequency control. The wire stores Hz but the encoder is logarithmic,
 * so the slider travels over the recovered 0..1000 encoder positions.
 */
@Composable
internal fun FrequencySlider(
    label: String,
    hz: Float,
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(Units.PeqRanges.hzToPosition(hz)) }
    LaunchedEffect(hz) {
        if (!dragging) local = Units.PeqRanges.hzToPosition(hz)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                formatHz(Units.PeqRanges.positionToHz(local)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = { dragging = true; local = it },
            onValueChangeFinished = {
                dragging = false
                onChange(Units.PeqRanges.positionToHz(local))
            },
            valueRange = 0f..1000f,
        )
    }
}

/** Formats a value in the engineering unit the control displays. */
internal fun formatEngineering(value: Float, unit: Units.DisplayUnit): String = when (unit) {
    Units.DisplayUnit.MILLISECONDS -> "${value.roundToInt()} ms"
    Units.DisplayUnit.HERTZ -> formatHz(value)
    Units.DisplayUnit.DB -> "%+.1f dB".format(value)
    Units.DisplayUnit.DBU -> "%+.1f dBu".format(value)
    Units.DisplayUnit.PERCENT -> "${value.roundToInt()}%"
    Units.DisplayUnit.BPM -> "${value.roundToInt()} BPM"
    Units.DisplayUnit.FACTOR -> "%.2f".format(value)
    Units.DisplayUnit.NONE -> "%.2f".format(value)
}

/** 20 -> "20 Hz", 4000 -> "4.0 kHz". */
internal fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.2f kHz".format(hz / 1000f) else "${hz.roundToInt()} Hz"