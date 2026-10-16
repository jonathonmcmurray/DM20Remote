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
import androidx.compose.foundation.layout.width
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
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.model.MusicTrack
import com.dm20remote.ui.components.ChipRow
import com.dm20remote.ui.components.ColumnRule
import com.dm20remote.ui.components.Dm20Chip
import com.dm20remote.ui.components.DmText
import com.dm20remote.ui.components.SectionTitle

/** Play-mode wire values, in the order the console numbers them. */
private val PLAY_MODES = listOf(
    R.string.play_mode_loop,
    R.string.play_mode_single,
    R.string.play_mode_queue,
    R.string.play_mode_random,
    R.string.play_mode_once,
)

/**
 * The Music tab (`C3`): the media player on the left and the console's track list
 * on the right.
 *
 * Transport is driven the way the original does it — the console has play/pause,
 * stop, record and play-mode instructions but no previous/next, so those two are
 * derived from the last list the console sent (protoId 193) and re-sent as a song
 * selection.
 */
@Composable
fun MusicPane(state: ConsoleState, modifier: Modifier = Modifier) {
    val transport = state.transport
    val connected = state.musicConnected
    val tracks = state.musicList
    val (previousPath, nextPath) = adjacentPaths(tracks, transport.songPath)

    Row(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!connected) {
                DmText(
                    text = stringResource(R.string.music_not_connected),
                    style = Dm20Type.caption,
                    color = Dm20Colors.error,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }

            SectionTitle(stringResource(R.string.music_now_playing))
            Spacer(Modifier.height(6.dp))
            DmText(
                text = transport.songPath.substringAfterLast('/').ifBlank {
                    stringResource(R.string.music_none)
                },
                style = Dm20Type.title,
                color = Dm20Colors.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            DmText(
                text = transport.songPath.ifBlank { stringResource(R.string.console_no_value) },
                style = Dm20Type.caption,
                color = Dm20Colors.dim,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            ProgressBar(
                fraction = if (transport.durationMs > 0) {
                    transport.positionMs.toFloat() / transport.durationMs
                } else {
                    0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            DmText(
                text = stringResource(
                    R.string.music_position,
                    formatTime(transport.positionMs),
                    formatTime(transport.durationMs),
                ),
                style = Dm20Type.caption,
                color = Dm20Colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            SectionTitle(stringResource(R.string.music_transport))
            Spacer(Modifier.height(6.dp))
            ChipRow(gap = 6.dp) {
                Dm20Chip(
                    label = "\u23EE",
                    active = false,
                    onClick = { previousPath?.let { ConsoleSession.transportSelectSong(it) } },
                    width = 44.dp,
                    height = 30.dp,
                    style = Dm20Type.control,
                    enabled = connected && previousPath != null,
                )
                Dm20Chip(
                    label = if (transport.isPlaying) "\u23F8" else "\u25B6",
                    active = transport.isPlaying,
                    onClick = { ConsoleSession.transportPlayPause() },
                    width = 56.dp,
                    height = 30.dp,
                    style = Dm20Type.control,
                    enabled = connected,
                )
                Dm20Chip(
                    label = "\u23F9",
                    active = false,
                    onClick = { ConsoleSession.transportStop() },
                    width = 44.dp,
                    height = 30.dp,
                    style = Dm20Type.control,
                    enabled = connected,
                )
                Dm20Chip(
                    label = "\u23ED",
                    active = false,
                    onClick = { nextPath?.let { ConsoleSession.transportSelectSong(it) } },
                    width = 44.dp,
                    height = 30.dp,
                    style = Dm20Type.control,
                    enabled = connected && nextPath != null,
                )
                Dm20Chip(
                    label = stringResource(
                        if (transport.isRecording) R.string.music_recording else R.string.music_record,
                    ),
                    active = transport.isRecording,
                    onClick = { ConsoleSession.transportRecord() },
                    width = 84.dp,
                    height = 30.dp,
                    radius = 6.dp,
                    activeColor = Dm20Colors.error,
                    activeContent = Dm20Colors.onSurface,
                    style = Dm20Type.label,
                    enabled = connected,
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionTitle(stringResource(R.string.music_play_mode))
            Spacer(Modifier.height(6.dp))
            ChipRow(gap = 6.dp) {
                PLAY_MODES.forEachIndexed { mode, labelRes ->
                    Dm20Chip(
                        label = stringResource(labelRes),
                        active = transport.playMode == mode,
                        onClick = { ConsoleSession.transportPlayMode(mode) },
                        width = 62.dp,
                        height = 26.dp,
                        style = Dm20Type.label,
                        enabled = connected,
                    )
                }
            }
        }

        ColumnRule()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle(stringResource(R.string.music_tracks))
            Spacer(Modifier.height(6.dp))

            if (tracks.isEmpty()) {
                DmText(
                    text = stringResource(R.string.music_no_tracks),
                    style = Dm20Type.caption,
                    color = Dm20Colors.dim,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tracks.forEach { track ->
                        TrackRow(
                            track = track,
                            selected = track.path == transport.songPath,
                            enabled = connected,
                            onSelect = { ConsoleSession.transportSelectSong(track.path) },
                        )
                    }
                }
            }
        }
    }
}

/** One entry of the console's music list. */
@Composable
private fun TrackRow(
    track: MusicTrack,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Dm20Colors.rowSelected else Dm20Colors.row)
            .clickable(enabled = enabled) { onSelect() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DmText(
            text = if (selected) "\u25B6" else "\u266A",
            style = Dm20Type.label,
            color = if (selected) Dm20Colors.primary else Dm20Colors.dim,
            modifier = Modifier.width(16.dp),
        )
        Column(Modifier.weight(1f)) {
            DmText(
                text = track.name.ifBlank { track.path.substringAfterLast('/') },
                style = Dm20Type.body,
                color = if (selected) Dm20Colors.onSurface else Dm20Colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            DmText(
                text = track.path,
                style = Dm20Type.caption,
                color = Dm20Colors.dim,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A read-only progress trough; this is the one bar the user cannot drag. */
@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Dm20Colors.trackBackground),
    ) {
        val width = fraction.coerceIn(0f, 1f)
        if (width > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(width)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Dm20Colors.primary),
            )
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
