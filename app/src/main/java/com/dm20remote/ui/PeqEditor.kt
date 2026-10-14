package com.dm20remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.InputChannel
import com.dm20remote.net.EqBands
import com.dm20remote.net.Units
import com.dm20remote.net.bandFrequency
import com.dm20remote.net.bandGain
import com.dm20remote.net.bandQ
import com.dm20remote.net.isBandShelf

/**
 * Four-band parametric EQ editor. The wire model flattens the bands into separate
 * fields, so the UI selects one band at a time and edits freq/gain/Q; the band's
 * type is only meaningful for the high and low bands.
 */
@Composable
internal fun PeqEditor(channel: InputChannel) {
    val eq = channel.eq
    var band by remember { mutableIntStateOf(EqBands.HIGH) }

    SwitchRow("EQ bypass", checked = eq.isByPass) { bypass ->
        ConsoleSession.setEqBypass(channel, bypass)
    }

    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EqBands.names.forEachIndexed { index, name ->
            FilterChip(
                selected = band == index,
                onClick = { band = index },
                label = { Text(name) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))

    FrequencySlider("Frequency", hz = eq.bandFrequency(band)) { hz ->
        ConsoleSession.setEqBandFrequency(channel, band, hz)
    }
    ValueSlider("Gain", value = eq.bandGain(band), range = Units.PeqRanges.gain) { db ->
        ConsoleSession.setEqBandGain(channel, band, db)
    }
    ValueSlider("Q", value = eq.bandQ(band), range = Units.PeqRanges.q) { q ->
        ConsoleSession.setEqBandQ(channel, band, q)
    }

    if (band == EqBands.HIGH || band == EqBands.LOW) {
        SwitchRow("Shelving filter", checked = eq.isBandShelf(band)) { shelf ->
            ConsoleSession.setEqBandShelf(channel, band, shelf)
        }
    }

    Spacer(Modifier.height(6.dp))
    TextButton(onClick = { ConsoleSession.setEqFlat(channel) }) { Text("Flat") }

    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth()) {
        EqBands.names.forEachIndexed { index, name ->
            val type = if (eq.isBandShelf(index)) "shelf" else "bell"
            Readout(
                label = name,
                value = "%s  %s  Q %s  (%s)".format(
                    formatHz(eq.bandFrequency(index)),
                    formatEngineering(eq.bandGain(index), Units.DisplayUnit.DB),
                    formatEngineering(eq.bandQ(index), Units.DisplayUnit.NONE),
                    type,
                ),
            )
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(
        text = "Changes are applied by the console and mirrored back.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}