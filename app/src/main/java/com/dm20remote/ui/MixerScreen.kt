package com.dm20remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState

@Composable
fun MixerScreen(state: ConsoleState, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    var selectedInput by remember { mutableIntStateOf(-1) }
    var selectedBus by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.tab_inputs)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.tab_buses)) },
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = { Text(stringResource(R.string.tab_info)) },
            )
            Tab(
                selected = tab == 3,
                onClick = { tab = 3 },
                text = { Text(stringResource(R.string.tab_music)) },
            )
        }

        when (tab) {
            0 -> InputsPane(state, onOpen = { selectedInput = it })
            1 -> BusesPane(state, onOpen = { selectedBus = it })
            2 -> InfoPane(state)
            else -> MusicPane(state)
        }
    }

    val input = state.inputs.getOrNull(selectedInput)
    if (input != null) {
        InputDetailDialog(channel = input, onDismiss = { selectedInput = -1 })
    }
    val bus = state.buses.getOrNull(selectedBus)
    if (bus != null) {
        BusDetailDialog(bus = bus, onDismiss = { selectedBus = -1 })
    }
}

@Composable
private fun InputsPane(state: ConsoleState, onOpen: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        itemsIndexed(state.inputs) { index, channel ->
            ChannelStrip(
                name = channel.name,
                faderPosition = channel.faderPosition,
                mute = channel.mute,
                solo = channel.solo,
                meterDb = state.inputMeterDb(index),
                onFaderCommit = { ConsoleSession.setInputFader(channel, it) },
                onMuteToggle = { ConsoleSession.setInputMute(channel, !channel.mute) },
                onSoloToggle = { ConsoleSession.setInputSolo(channel, !channel.solo) },
                onOpen = { onOpen(index) },
            )
        }
    }
}

@Composable
private fun BusesPane(state: ConsoleState, onOpen: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        itemsIndexed(state.buses) { index, bus ->
            ChannelStrip(
                name = bus.name,
                faderPosition = bus.gainPosition,
                mute = bus.mute,
                solo = bus.solo,
                meterDb = state.busMeterDb(index),
                onFaderCommit = { ConsoleSession.setBusFader(bus, it) },
                onMuteToggle = { ConsoleSession.setBusMute(bus, !bus.mute) },
                onSoloToggle = { ConsoleSession.setBusSolo(bus, !bus.solo) },
                onOpen = { onOpen(index) },
            )
        }
    }
}

@Composable
private fun InfoPane(state: ConsoleState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        InfoRow("Serial", state.serialId.ifBlank { "-" })
        InfoRow("Console firmware", state.apkVersion.ifBlank { "-" })
        InfoRow("DSP firmware", state.dspVersion.ifBlank { "-" })
        InfoRow("Control channel", if (state.controlConnected) "connected" else "offline")
        InfoRow("Meter channel", if (state.meterConnected) "connected" else "offline")
        InfoRow("Music channel", if (state.musicConnected) "connected" else "offline")
        InfoRow("Meter frames", state.meterFrames.toString())
        InfoRow("Scenes", state.scenes.size.toString())

        if (state.transport.songPath.isNotBlank()) {
            InfoRow("Media", state.transport.songPath)
            InfoRow("Playing", state.transport.isPlaying.toString())
        }

        state.scenes.take(32).forEach { InfoRow("Scene", it) }

        if (state.lastError.isNotBlank()) {
            InfoRow("Last error", state.lastError)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
