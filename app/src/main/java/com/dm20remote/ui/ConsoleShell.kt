package com.dm20remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.ui.components.DmText
import com.dm20remote.ui.components.LevelBar
import com.dm20remote.ui.components.RowRule
import com.dm20remote.ui.components.SegmentedSwitch

/** The four top-level tabs of the console shell, in the order of the design. */
internal const val TAB_INPUTS = 0
internal const val TAB_BUSES = 1
internal const val TAB_CONSOLE = 2
internal const val TAB_MUSIC = 3

/** The four sub-tabs of an input channel. */
internal const val DETAIL_CHANNEL = 0
internal const val DETAIL_PEQ = 1
internal const val DETAIL_DYNAMICS = 2
internal const val DETAIL_SENDS = 3

/**
 * The console itself, once a console is connected.
 *
 * Navigation is two levels deep and holds no back stack: the tab strip picks the
 * top-level screen, and opening a channel swaps the whole tab body for that
 * channel's detail page (the redesign dropped the modal dialog the first version
 * used). Both levels live in this one composable so `onBack` is a single
 * assignment.
 */
@Composable
fun ConsoleShell(
    state: ConsoleState,
    debugEnabled: Boolean,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(TAB_INPUTS) }
    var openInput by remember { mutableIntStateOf(-1) }
    var openBus by remember { mutableIntStateOf(-1) }
    var detailTab by remember { mutableIntStateOf(DETAIL_CHANNEL) }

    val input = state.inputs.getOrNull(openInput)
    val bus = state.buses.getOrNull(openBus)

    // The detail branches are keyed off the index and the model, so a channel that
    // disappears (a shorter bank) falls back to the tab rather than an empty page.

    when {
        tab == TAB_INPUTS && openInput >= 0 && input != null -> InputDetailScreen(
            state = state,
            index = openInput,
            detailTab = detailTab,
            onDetailTab = { detailTab = it },
            onSelectChannel = { openInput = it },
            onBack = { openInput = -1 },
            modifier = modifier,
        )

        tab == TAB_BUSES && openBus >= 0 && state.buses.getOrNull(openBus) != null -> BusDetailScreen(
            state = state,
            index = openBus,
            onSelectChannel = { openBus = it },
            onBack = { openBus = -1 },
            modifier = modifier,
        )

        else -> Column(modifier.fillMaxSize()) {
            ConsoleHeader(
                state = state,
                tab = tab,
                onTab = {
                    tab = it
                    openInput = -1
                    openBus = -1
                },
                debugEnabled = debugEnabled,
                onOpenLogs = onOpenLogs,
            )
            when (tab) {
                TAB_BUSES -> BusesPane(
                    state = state,
                    onOpen = { openBus = it },
                    modifier = Modifier.weight(1f),
                )
                TAB_CONSOLE -> ConsolePane(state, Modifier.weight(1f))
                TAB_MUSIC -> MusicPane(state, Modifier.weight(1f))
                else -> InputsPane(
                    state = state,
                    onOpen = { openInput = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The console header: connection state on the left, the four-way tab switch in the
 * middle, the log entry (only when logging is on) and Disconnect on the right.
 */
@Composable
private fun ConsoleHeader(
    state: ConsoleState,
    tab: Int,
    onTab: (Int) -> Unit,
    debugEnabled: Boolean,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = state.controlConnected
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dm20Metrics.header)
                .background(Dm20Colors.surface),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(online)
                Spacer(Modifier.width(6.dp))
                DmText(
                    text = stringResource(if (online) R.string.status_online else R.string.status_offline),
                    style = Dm20Type.label,
                    color = Dm20Colors.onSurfaceVariant,
                )
            }

            SegmentedSwitch(
                options = listOf(
                    stringResource(R.string.tab_inputs),
                    stringResource(R.string.tab_buses),
                    stringResource(R.string.tab_info),
                    stringResource(R.string.tab_music),
                ),
                selectedIndex = tab,
                onSelect = onTab,
                itemWidth = 66.dp,
                modifier = Modifier.align(Alignment.Center),
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (debugEnabled) {
                    DmText(
                        text = stringResource(R.string.header_logs),
                        style = Dm20Type.label,
                        color = Dm20Colors.dim,
                        modifier = Modifier
                            .clickable { onOpenLogs() }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                DmText(
                    text = stringResource(R.string.connect_disconnect),
                    style = Dm20Type.label,
                    color = Dm20Colors.primary,
                    modifier = Modifier
                        .clickable { ConsoleSession.disconnect() }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
        }
        RowRule()
    }
}

/**
 * The header of a detail page: the way back, the Channel/PEQ/Dynamics switch when
 * the page has one, the channel's M/S pair and the connection state.
 */
@Composable
internal fun DetailHeader(
    online: Boolean,
    mute: Boolean,
    solo: Boolean,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    detailTab: Int? = null,
    onDetailTab: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dm20Metrics.header)
                .background(Dm20Colors.surface),
        ) {
            DmText(
                text = stringResource(R.string.nav_mixer),
                style = Dm20Type.body,
                color = Dm20Colors.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable { onBack() }
                    .padding(horizontal = 16.dp),
            )

            if (detailTab != null) {
                SegmentedSwitch(
                    options = listOf(
                        stringResource(R.string.detail_channel),
                        stringResource(R.string.detail_peq),
                        stringResource(R.string.detail_dynamics),
                        stringResource(R.string.detail_sends),
                    ),
                    selectedIndex = detailTab,
                    onSelect = onDetailTab,
                    itemWidth = 72.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (detailTab != null) {
                    Box(Modifier.width(1.dp).height(20.dp).background(Dm20Colors.divider))
                    Spacer(Modifier.width(12.dp))
                }
                MuteSolo(
                    width = 36.dp,
                    height = 26.dp,
                    mute = mute,
                    solo = solo,
                    onMuteToggle = onMuteToggle,
                    onSoloToggle = onSoloToggle,
                )
                Spacer(Modifier.width(16.dp))
                StatusDot(online)
                Spacer(Modifier.width(6.dp))
                DmText(
                    text = stringResource(if (online) R.string.status_online else R.string.status_offline),
                    style = Dm20Type.caption,
                    color = Dm20Colors.onSurfaceVariant,
                )
            }
        }
        RowRule()
    }
}

/**
 * The name / level row under a detail header.
 *
 * The design labels the read-out "peak"; the app has no peak-hold data from the
 * console, so it shows the live post-fader level instead and says so.
 */
@Composable
internal fun TitleRow(name: String, levelDb: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dm20Metrics.title)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DmText(
            text = name,
            style = Dm20Type.title,
            color = Dm20Colors.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        LevelBar(levelDb = levelDb, modifier = Modifier.width(200.dp).height(8.dp))
        Spacer(Modifier.width(12.dp))
        DmText(
            text = stringResource(R.string.level_db, formatDb(levelDb)),
            style = Dm20Type.label,
            color = Dm20Colors.dim,
        )
    }
}

/** The online/offline dot every header carries. */
@Composable
private fun StatusDot(online: Boolean) {
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (online) Dm20Colors.meterLow else Dm20Colors.dim),
    )
}
