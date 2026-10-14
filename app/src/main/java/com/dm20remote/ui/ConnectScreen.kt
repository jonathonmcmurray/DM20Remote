package com.dm20remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.net.DiscoveredConsole
import com.dm20remote.net.Discovery
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(
    state: ConsoleState,
    onConnect: (String) -> Unit,
    debugEnabled: Boolean,
    onDebugToggle: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DiscoveredConsole>>(emptyList()) }
    var manual by remember { mutableStateOf(Discovery.DEFAULT_HOST) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.connect_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                searching = true
                searched = false
                scope.launch {
                    results = runCatching { ConsoleSession.discover() }.getOrDefault(emptyList())
                    searching = false
                    searched = true
                }
            },
            enabled = !searching,
        ) {
            Text(
                if (searching) stringResource(R.string.connect_searching)
                else stringResource(R.string.connect_search),
            )
        }

        if (searching) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }

        if (searched && results.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.connect_none_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        results.forEach { console ->
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(console.address, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            console.banner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { onConnect(console.address) }) {
                        Text(stringResource(R.string.connect_connect))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            label = { Text(stringResource(R.string.connect_manual)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Button(onClick = { onConnect(manual.trim()) }, enabled = manual.isNotBlank()) {
            Text(stringResource(R.string.connect_connect))
        }

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    label = stringResource(R.string.debug_title),
                    checked = debugEnabled,
                    onChange = onDebugToggle,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.debug_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (debugEnabled) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onOpenLogs) {
                        Text(stringResource(R.string.debug_open))
                    }
                }
            }
        }

        if (state.lastError.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                state.lastError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
