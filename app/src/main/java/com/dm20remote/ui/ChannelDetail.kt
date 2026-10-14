package com.dm20remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dm20remote.model.BusChannel
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.InputChannel

/**
 * Tabbed detail panel for an input channel. The channel tab keeps the original
 * strip controls; EQ and dynamics get their own editors because they carry many
 * more parameters than fit on a strip.
 */
@Composable
fun InputDetailDialog(channel: InputChannel, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            TabRow(selectedTabIndex = tab) {
                listOf("Channel", "PEQ", "Dynamics").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (tab) {
                    0 -> ChannelTab(channel)
                    1 -> PeqEditor(channel)
                    else -> DynamicsEditor(channel)
                }
            }
        }
    }
}

@Composable
private fun ChannelTab(channel: InputChannel) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Pan", style = MaterialTheme.typography.bodyMedium)
            Text(
                formatPan(channel.pan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = channel.pan,
            onValueChange = { ConsoleSession.setInputPan(channel, it) },
            valueRange = -100f..100f,
        )
    }

    SwitchRow(
        label = "48V phantom",
        checked = channel.input.is48VEnable,
    ) { enabled ->
        ConsoleSession.setInputPart(channel) { it.setIs48VEnable(enabled) }
    }

    SwitchRow(
        label = "High-pass filter",
        checked = channel.input.isHPFEnable,
    ) { enabled ->
        ConsoleSession.setInputPart(channel) { it.setIsHPFEnable(enabled) }
    }

    SwitchRow(
        label = "Trim",
        checked = channel.input.isTrimEnable,
    ) { enabled ->
        ConsoleSession.setInputPart(channel) { it.setIsTrimEnable(enabled) }
    }

    Spacer(Modifier.height(12.dp))
    Readout("Trim value", channel.input.trimValue.toString())
    Readout("Input gain", channel.input.gainValue.toString())
    Readout("HPF value", channel.input.hpfValue.toString())
}

/** Detail panel for an output bus: pan, delay and raw read-outs. */
@Composable
fun BusDetailDialog(bus: BusChannel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        SurfaceCard {
            Text(bus.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Pan", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatPan(bus.pan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = bus.pan,
                    onValueChange = { ConsoleSession.setBusPan(bus, it) },
                    valueRange = -100f..100f,
                )
            }

            SwitchRow(label = "Delay", checked = bus.output.isDelayEnable) { enabled ->
                ConsoleSession.setBusDelayEnable(bus, enabled)
            }

            Spacer(Modifier.height(8.dp))
            Readout("Fader", formatDb(bus.gainDb))
            Readout("Delay", "%.2f ms".format(bus.output.delayValue))

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}

fun formatPan(pan: Float): String = when {
    pan > 0.5f -> "R${pan.toInt()}"
    pan < -0.5f -> "L${(-pan).toInt()}"
    else -> "C"
}