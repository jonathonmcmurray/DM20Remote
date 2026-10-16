package com.dm20remote.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.ConsoleState
import com.dm20remote.ui.components.ColumnRule
import com.dm20remote.ui.components.DmText
import com.dm20remote.ui.components.ReadoutRow
import com.dm20remote.ui.components.SectionTitle

/**
 * The Console tab (`C2`): what the app knows about the desk on the left — identity,
 * the three links and the current media — and the console's scene list on the right.
 *
 * Every value here is something the app already receives from the wire; nothing is
 * derived or guessed, which is why several rows read back the raw counter.
 */
@Composable
fun ConsolePane(state: ConsoleState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle(stringResource(R.string.tab_info))
            Spacer(Modifier.height(4.dp))
            ReadoutRow(
                label = stringResource(R.string.console_serial),
                value = state.serialId.ifBlank { stringResource(R.string.console_no_value) },
            )
            ReadoutRow(
                label = stringResource(R.string.console_firmware),
                value = state.apkVersion.ifBlank { stringResource(R.string.console_no_value) },
            )
            ReadoutRow(
                label = stringResource(R.string.console_dsp),
                value = state.dspVersion.ifBlank { stringResource(R.string.console_no_value) },
            )

            Spacer(Modifier.height(10.dp))

            LinkRow(R.string.console_link_control, state.controlConnected)
            LinkRow(R.string.console_link_meter, state.meterConnected)
            LinkRow(R.string.console_link_music, state.musicConnected)
            ReadoutRow(
                label = stringResource(R.string.console_meter_frames),
                value = state.meterFrames.toString(),
                valueColor = Dm20Colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            SectionTitle(stringResource(R.string.console_media))
            Spacer(Modifier.height(4.dp))
            ReadoutRow(
                label = stringResource(R.string.console_playing),
                value = stringResource(
                    if (state.transport.isPlaying) R.string.console_yes else R.string.console_no,
                ),
            )
            DmText(
                text = state.transport.songPath.ifBlank { stringResource(R.string.music_none) },
                style = Dm20Type.caption,
                color = Dm20Colors.dim,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            if (state.lastError.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                DmText(
                    text = state.lastError,
                    style = Dm20Type.caption,
                    color = Dm20Colors.error,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        ColumnRule()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle(stringResource(R.string.console_scenes))
            Spacer(Modifier.height(4.dp))
            DmText(
                text = stringResource(R.string.console_scene_count, state.scenes.size),
                style = Dm20Type.caption,
                color = Dm20Colors.dim,
            )
            Spacer(Modifier.height(6.dp))

            if (state.scenes.isEmpty()) {
                DmText(
                    text = stringResource(R.string.console_no_value),
                    style = Dm20Type.body,
                    color = Dm20Colors.dim,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.scenes.forEach { scene ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Dm20Colors.row)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            DmText(
                                text = scene,
                                style = Dm20Type.body,
                                color = Dm20Colors.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One of the three links, as a row with its state dot. */
@Composable
private fun LinkRow(labelRes: Int, connected: Boolean) {
    ReadoutRow(
        label = stringResource(labelRes),
        value = stringResource(if (connected) R.string.status_online else R.string.status_offline),
        valueColor = if (connected) Dm20Colors.onSurface else Dm20Colors.dim,
        dot = if (connected) Dm20Colors.meterLow else Dm20Colors.dim,
    )
}
