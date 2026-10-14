package com.dm20remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.debug.WireLog
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.service.ConsoleService

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state by ConsoleSession.state.collectAsState()
    val debugEnabled by WireLog.enabled.collectAsState()
    var showLogs by remember { mutableStateOf(false) }

    LaunchedEffect(state.controlConnected) {
        if (state.controlConnected) ConsoleService.start(context) else ConsoleService.stop(context)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (showLogs) {
            WireLogScreen(onClose = { showLogs = false })
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Header(
                    state = state,
                    debugEnabled = debugEnabled,
                    onOpenLogs = { showLogs = true },
                )
                if (state.controlConnected) {
                    MixerScreen(state, Modifier.weight(1f))
                } else {
                    ConnectScreen(
                        state = state,
                        onConnect = { host -> if (host.isNotBlank()) ConsoleSession.connect(host) },
                        debugEnabled = debugEnabled,
                        onDebugToggle = { WireLog.setEnabled(it) },
                        onOpenLogs = { showLogs = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: ConsoleState,
    debugEnabled: Boolean,
    onOpenLogs: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (state.controlConnected) stringResource(R.string.status_online)
                else stringResource(R.string.status_offline),
                style = MaterialTheme.typography.labelSmall,
                color = if (state.controlConnected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (debugEnabled) {
            TextButton(onClick = onOpenLogs) { Text(stringResource(R.string.debug_open)) }
        }
        if (state.controlConnected) {
            TextButton(onClick = { ConsoleSession.disconnect() }) {
                Text(stringResource(R.string.connect_disconnect))
            }
        }
    }
}
