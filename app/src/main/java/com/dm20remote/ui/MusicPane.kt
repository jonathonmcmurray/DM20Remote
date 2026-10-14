package com.dm20remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.model.MusicTrack
import com.dm20remote.model.TransportState

/** `EnmPlayMode` in the order the console reports it (Loop, Single, Queue, Random, Once). */
private val PLAY_MODES = listOf(
    R.string.play_mode_loop,
    R.string.play_mode_single,
    R.string.play_mode_queue,
    R.string.play_mode_random,
    R.string.play_mode_once,
)

/**
 * Transport and recorder control for the console's USB player.
 *
 * Every button only *sends* a command; the displayed state is whatever the console
 * last reported on 191/195 (and the music-channel position ticks). Nothing is set
 * optimistically, so the UI never shows a play state the console did not confirm.
 */
@Composable
fun MusicPane(state: ConsoleState, modifier: Modifier = Modifier) {
    val transport = state.transport
    val connected = state.musicConnected
    val tracks = state.musicList

    val (previousPath, nextPath) = adjacentPaths(tracks, transport.songPath)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (!connected) {
            Text(
                text = stringResource(R.string.music_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        NowPlaying(transport)

        TransportControls(
            enabled = connected,
            isPlaying = transport.isPlaying,
            isRecording = transport.isRecording,
            canPrevious = previousPath != null,
            canNext = nextPath != null,
            onPlayPause = { ConsoleSession.transportPlayPause() },
            onStop = { ConsoleSession.transportStop() },
            onPrevious = { previousPath?.let { ConsoleSession.transportSelectSong(it) } },
            onNext = { nextPath?.let { ConsoleSession.transportSelectSong(it) } },
            onRecord = { ConsoleSession.transportRecord() },
        )

        PlayModeRow(
            enabled = connected,
            current = transport.playMode,
            onSelect = { ConsoleSession.transportPlayMode(it) },
        )

        MusicListSection(
            tracks = tracks,
            currentPath = transport.songPath,
            enabled = connected,
            onSelect = { ConsoleSession.transportSelectSong(it) },
        )
    }
}

@Composable
private fun NowPlaying(transport: TransportState) {
    val fraction = if (transport.durationMs > 0) {
        (transport.positionMs.toFloat() / transport.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = transport.songPath.substringAfterLast('/').ifBlank { stringResource(R.string.music_none) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Text(
            text = transport.songPath.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(transport.positionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(transport.durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TransportControls(
    enabled: Boolean,
    isPlaying: Boolean,
    isRecording: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRecord: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPrevious, enabled = enabled && canPrevious) { Text("\u23EE") }
        Button(onClick = onPlayPause, enabled = enabled) { Text(if (isPlaying) "\u23F8" else "\u25B6") }
        OutlinedButton(onClick = onStop, enabled = enabled) { Text("\u23F9") }
        OutlinedButton(onClick = onNext, enabled = enabled && canNext) { Text("\u23ED") }
        Spacer(Modifier.width(4.dp))
        OutlinedButton(
            onClick = onRecord,
            enabled = enabled,
            colors = if (isRecording) {
                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
        ) {
            Text(stringResource(if (isRecording) R.string.music_recording else R.string.music_record))
        }
    }
}

@Composable
private fun PlayModeRow(enabled: Boolean, current: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.music_play_mode),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PLAY_MODES.forEachIndexed { mode, label ->
                FilterChip(
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                    enabled = enabled,
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun MusicListSection(
    tracks: List<MusicTrack>,
    currentPath: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.music_list_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tracks.isEmpty()) {
            Text(
                text = stringResource(R.string.music_list_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        tracks.forEach { track ->
            val selected = track.path == currentPath
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable(enabled = enabled) { onSelect(track.path) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selected) "\u25B6" else "\u266A",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = track.name.ifBlank { track.path.substringAfterLast('/') },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    if (track.path.isNotBlank()) {
                        Text(
                            text = track.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The previous/next entries around [currentPath] in the console's list (protoId 193).
 *
 * The console has no previous/next instruction: the original picks the adjacent path
 * client-side from the last list it received and re-sends it as a selection. Both
 * values are null when the list is empty or the current path is not in it.
 */
internal fun adjacentPaths(tracks: List<MusicTrack>, currentPath: String): Pair<String?, String?> {
    val index = tracks.indexOfFirst { it.path == currentPath }
    if (index < 0) return null to null
    return tracks.getOrNull(index - 1)?.path to tracks.getOrNull(index + 1)?.path
}

/** Milliseconds -> "m:ss". */
internal fun formatTime(ms: Int): String {
    val totalSeconds = (if (ms > 0) ms else 0) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
