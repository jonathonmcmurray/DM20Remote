package com.dm20remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.net.DiscoveredConsole
import com.dm20remote.net.Discovery
import com.dm20remote.ui.components.ColumnRule
import com.dm20remote.ui.components.Dm20Chip
import com.dm20remote.ui.components.Dm20Switch
import com.dm20remote.ui.components.DmText
import com.dm20remote.ui.components.RowRule
import com.dm20remote.ui.components.SectionTitle
import kotlinx.coroutines.launch

/**
 * The screen shown until a console answers (`C4`).
 *
 * Discovery is the app's own broadcast probe; the manual column exists because a
 * tablet whose Wi-Fi does not pass broadcasts can still reach the desk by address.
 * Nothing here talks to the console directly except through [ConsoleSession].
 */
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

    Column(modifier.fillMaxSize()) {
        // The connect screen carries its own taller header: there is no tab strip
        // or connection state to show until a console is found.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Dm20Colors.surface)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            DmText(
                text = stringResource(R.string.connect_title),
                style = Dm20Type.title,
                color = Dm20Colors.onSurface,
            )
            DmText(
                text = stringResource(R.string.connect_subtitle),
                style = Dm20Type.caption,
                color = Dm20Colors.dim,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowRule()

        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                SectionTitle(stringResource(R.string.connect_results))
                Spacer(Modifier.height(8.dp))
                Dm20Chip(
                    label = stringResource(
                        if (searching) R.string.connect_searching else R.string.connect_search,
                    ),
                    active = false,
                    onClick = {
                        searching = true
                        searched = false
                        scope.launch {
                            results = runCatching { ConsoleSession.discover() }
                                .getOrDefault(emptyList())
                            searching = false
                            searched = true
                        }
                    },
                    width = 190.dp,
                    height = 34.dp,
                    activeColor = Dm20Colors.primary,
                    activeContent = Dm20Colors.onPrimary,
                    style = Dm20Type.body,
                    enabled = !searching,
                )

                Spacer(Modifier.height(10.dp))

                if (searched && results.isEmpty()) {
                    DmText(
                        text = stringResource(R.string.connect_none_found),
                        style = Dm20Type.caption,
                        color = Dm20Colors.dim,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    results.forEach { console ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Dm20Colors.surfaceVariant)
                                .padding(start = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                DmText(
                                    text = console.address,
                                    style = Dm20Type.body,
                                    color = Dm20Colors.onSurface,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                DmText(
                                    text = console.banner,
                                    style = Dm20Type.caption,
                                    color = Dm20Colors.dim,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Dm20Chip(
                                label = stringResource(R.string.connect_connect),
                                active = true,
                                onClick = { onConnect(console.address) },
                                width = 84.dp,
                                height = 30.dp,
                                style = Dm20Type.label,
                            )
                        }
                    }
                }
            }

            ColumnRule()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                SectionTitle(stringResource(R.string.connect_manual))
                Spacer(Modifier.height(8.dp))
                AddressField(value = manual, onValueChange = { manual = it })
                Spacer(Modifier.height(10.dp))
                Dm20Chip(
                    label = stringResource(R.string.connect_connect),
                    active = true,
                    onClick = { onConnect(manual.trim()) },
                    width = 110.dp,
                    height = 34.dp,
                    style = Dm20Type.body,
                    enabled = manual.isNotBlank(),
                )

                Spacer(Modifier.height(16.dp))

                SectionTitle(stringResource(R.string.debug_title))
                Spacer(Modifier.height(6.dp))
                DmText(
                    text = stringResource(R.string.debug_hint),
                    style = Dm20Type.caption,
                    color = Dm20Colors.dim,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DmText(
                        text = stringResource(R.string.debug_open),
                        style = Dm20Type.body,
                        color = Dm20Colors.primary,
                        modifier = Modifier.weight(1f).clickable { onOpenLogs() },
                    )
                    Dm20Switch(checked = debugEnabled, onCheckedChange = onDebugToggle)
                }

                if (state.lastError.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    DmText(
                        text = state.lastError,
                        style = Dm20Type.caption,
                        color = Dm20Colors.error,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The manual address field. It is a `BasicTextField` inside the design's own box
 * rather than a Material text field, so it matches the rest of the chrome.
 */
@Composable
private fun AddressField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Dm20Type.body.copy(color = Dm20Colors.onSurface),
        cursorBrush = SolidColor(Dm20Colors.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Dm20Colors.surfaceVariant)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    DmText(
                        text = stringResource(R.string.connect_manual_hint),
                        style = Dm20Type.body,
                        color = Dm20Colors.dim,
                    )
                }
                inner()
            }
        },
    )
}
