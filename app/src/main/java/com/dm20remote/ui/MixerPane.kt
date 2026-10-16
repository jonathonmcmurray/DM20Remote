package com.dm20remote.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.net.Units
import com.dm20remote.ui.components.Dm20Chip
import com.dm20remote.ui.components.DmText
import com.dm20remote.ui.components.LevelBar
import com.dm20remote.ui.components.LevelMeter
import com.dm20remote.ui.components.ScrollBar
import com.dm20remote.ui.components.VerticalFader

/**
 * The geometry of one mixer strip.
 *
 * The design draws the input bank (`A2`) and the bus bank (`C1`) with slightly
 * different numbers — bus strips are wider and carry a wider meter — so the two
 * layouts live in one place and [StripBank] reads from it. Everything is measured
 * from the strip's own left edge; the fader is centred on its rail, which is why
 * [faderOffset] is derived from [meterOffset], [meterWidth] and the gap rather
 * than being a free number.
 */
internal data class BankMetrics(
    val stripWidth: Dp,
    val pitch: Dp,
    val meterWidth: Dp,
    val meterOffset: Dp,
    val railWidth: Dp,
    val faderWidth: Dp,
    val faderOffset: Dp,
    val chipWidth: Dp,
    val chipHeight: Dp,
)

/** `A2`: meter and rail 8 dp wide, 24 dp handle, 32 x 30 M/S chips, 78 dp pitch. */
internal val InputBankMetrics = BankMetrics(
    stripWidth = 70.dp,
    pitch = 78.dp,
    meterWidth = 8.dp,
    meterOffset = 8.dp,
    railWidth = 8.dp,
    faderWidth = 24.dp,
    faderOffset = 19.dp,
    chipWidth = 32.dp,
    chipHeight = 30.dp,
)

/** `C1`: meter and rail 10 dp wide, 28 dp handle, 38 x 30 M/S chips, 92 dp pitch. */
internal val BusBankMetrics = BankMetrics(
    stripWidth = 84.dp,
    pitch = 92.dp,
    meterWidth = 10.dp,
    meterOffset = 10.dp,
    railWidth = 10.dp,
    faderWidth = 28.dp,
    faderOffset = 25.dp,
    chipWidth = 38.dp,
    chipHeight = 30.dp,
)

/** The inset the design leaves at each end of a bank. */
private val BankInset = 4.dp

/**
 * The horizontally scrolling row of strips. The bank is wider than the screen
 * (16 inputs at a 78 dp pitch is 1248 dp), so it scrolls instead of paging — one
 * of the decisions the redesign settled.
 */
@Composable
internal fun StripBank(
    count: Int,
    metrics: BankMetrics,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    strip: @Composable (Int) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxHeight().horizontalScroll(scroll),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(BankInset))
        for (index in 0 until count) {
            Box(Modifier.width(metrics.stripWidth).fillMaxHeight()) { strip(index) }
            if (index != count - 1) {
                Spacer(Modifier.width(metrics.pitch - metrics.stripWidth))
            }
        }
        Spacer(Modifier.width(BankInset))
    }
}

/**
 * One column of the mixer bank: name plate, meter, fader, dB read-out and the M/S
 * pair.
 *
 * The fader owns its value for the duration of the gesture and only reports it in
 * `onFaderCommit`, so a console echo that arrives mid-drag cannot pull the handle
 * out from under the finger.
 */
@Composable
internal fun MixStrip(
    name: String,
    metrics: BankMetrics,
    faderPosition: Float,
    mute: Boolean,
    solo: Boolean,
    meterDb: Float,
    onFaderCommit: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(faderPosition) }
    LaunchedEffect(faderPosition) {
        if (!dragging) local = faderPosition
    }

    Column(Modifier.width(metrics.stripWidth).fillMaxHeight()) {
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Dm20Colors.surfaceVariant)
                .clickable { onOpen() },
            contentAlignment = Alignment.Center,
        ) {
            DmText(
                text = name,
                style = Dm20Type.label,
                color = Dm20Colors.onSurface,
                maxLines = 1,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LevelMeter(
                levelDb = meterDb,
                modifier = Modifier
                    .offset(x = metrics.meterOffset)
                    .width(metrics.meterWidth)
                    .fillMaxHeight(),
            )
            VerticalFader(
                position = local,
                onPositionChange = {
                    dragging = true
                    local = it
                },
                onCommit = {
                    dragging = false
                    onFaderCommit(local)
                },
                railWidth = metrics.railWidth,
                modifier = Modifier
                    .offset(x = metrics.faderOffset)
                    .width(metrics.faderWidth)
                    .fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(4.dp))
        DmText(
            text = formatDb(Units.faderPositionToDb(local)),
            style = Dm20Type.label,
            color = Dm20Colors.onSurfaceVariant,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth()) {
            MuteSolo(
                width = metrics.chipWidth,
                height = metrics.chipHeight,
                mute = mute,
                solo = solo,
                onMuteToggle = onMuteToggle,
                onSoloToggle = onSoloToggle,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The M/S pair. Both chips are the same neutral colour when off, which is the
 * redesign's unified M/S treatment — the colour distinguishes on from off, not
 * which of the two it is.
 */
@Composable
internal fun MuteSolo(
    width: Dp,
    mute: Boolean,
    solo: Boolean,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    gap: Dp = 2.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Dm20Chip(
            label = "M",
            active = mute,
            onClick = onMuteToggle,
            width = width,
            height = height,
            radius = 6.dp,
            activeColor = Dm20Colors.error,
            activeContent = Dm20Colors.background,
            style = Dm20Type.body,
        )
        Dm20Chip(
            label = "S",
            active = solo,
            onClick = onSoloToggle,
            width = width,
            height = height,
            radius = 6.dp,
            activeColor = Dm20Colors.primary,
            activeContent = Dm20Colors.onPrimary,
            style = Dm20Type.body,
        )
    }
}

/**
 * The channel selector that runs along the bottom of every detail page: one chip
 * per channel of the current bank with that channel's post-fader meter under it.
 * The ninth chip is deliberately cut off by the screen edge, which is the
 * redesign's scroll affordance.
 */
@Composable
internal fun ChannelDock(
    names: List<String>,
    levelOf: (Int) -> Float,
    selected: Int,
    scroll: ScrollState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Dm20Colors.surface)
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(12.dp))
        for (index in names.indices) {
            Column(Modifier.width(Dm20Metrics.dockChipWidth).padding(top = 8.dp)) {
                Dm20Chip(
                    label = names[index],
                    active = index == selected,
                    onClick = { onSelect(index) },
                    width = Dm20Metrics.dockChipWidth,
                    height = Dm20Metrics.dockChipHeight,
                    radius = 6.dp,
                    style = Dm20Type.label,
                )
                Spacer(Modifier.height(2.dp))
                LevelBar(
                    levelDb = levelOf(index),
                    modifier = Modifier
                        .width(Dm20Metrics.dockChipWidth)
                        .height(Dm20Metrics.dockMeterHeight),
                )
            }
            if (index != names.lastIndex) Spacer(Modifier.width(Dm20Metrics.dockChipPitch - Dm20Metrics.dockChipWidth))
        }
        Spacer(Modifier.width(12.dp))
    }
}

/**
 * The scroll affordance that sits under a bank: how much of the bank is on
 * screen, plus the thick scrollbar.
 *
 * [inline] is the detail-page variant the design uses (caption left, bar right of
 * centre); the mixer variant stacks the caption over a full-width bar. Reading the
 * scroll position here (rather than in the parent) keeps a scroll gesture from
 * recomposing the whole bank.
 */
@Composable
internal fun BankFooter(
    scroll: ScrollState,
    pitch: Dp,
    total: Int,
    label: String,
    viewportPx: Int,
    inline: Boolean,
    modifier: Modifier = Modifier,
) {
    val pitchPx = with(LocalDensity.current) { pitch.toPx() }
    val thumbFraction = if (viewportPx > 0 && pitchPx > 0f && total > 0) {
        (viewportPx / (pitchPx * total)).coerceIn(0.05f, 1f)
    } else {
        1f
    }
    val progress = if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f
    val visible = if (pitchPx > 0f) (viewportPx / pitchPx).toInt().coerceAtLeast(1) else total
    val first = if (pitchPx > 0f) (scroll.value / pitchPx).toInt() + 1 else 1
    val last = (first + visible - 1).coerceAtMost(total.coerceAtLeast(1))
    val caption = when {
        total <= 0 -> label
        visible >= total -> stringResource(R.string.bank_count_all, label, total)
        else -> stringResource(R.string.bank_count, label, first, last, total)
    }

    if (inline) {
        Box(modifier.fillMaxWidth().height(Dm20Metrics.scrollRow)) {
            DmText(
                text = caption,
                style = Dm20Type.caption,
                color = Dm20Colors.dim,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
            )
            ScrollBar(
                progress = progress,
                thumbFraction = thumbFraction,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).fillMaxWidth(0.65f),
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(Dm20Metrics.scrollRow)
                .padding(horizontal = BankInset),
        ) {
            DmText(caption, style = Dm20Type.caption, color = Dm20Colors.dim)
            Spacer(Modifier.height(2.dp))
            ScrollBar(progress = progress, thumbFraction = thumbFraction, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The Inputs tab (`A2`): the whole input bank plus the scroll row that says how
 * much of it is on screen.
 */
@Composable
internal fun InputsPane(
    state: ConsoleState,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        StripBank(
            count = state.inputs.size,
            metrics = InputBankMetrics,
            scroll = scroll,
            modifier = Modifier.weight(1f).onSizeChanged { viewport = it.width },
        ) { index ->
            val channel = state.inputs[index]
            MixStrip(
                name = channel.name,
                metrics = InputBankMetrics,
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
        BankFooter(
            scroll = scroll,
            pitch = InputBankMetrics.pitch,
            total = state.inputs.size,
            label = stringResource(R.string.tab_inputs),
            viewportPx = viewport,
            inline = false,
        )
    }
}

/** The Buses tab (`C1`): the same bank, in the bus strip metrics. */
@Composable
internal fun BusesPane(
    state: ConsoleState,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        StripBank(
            count = state.buses.size,
            metrics = BusBankMetrics,
            scroll = scroll,
            modifier = Modifier.weight(1f).onSizeChanged { viewport = it.width },
        ) { index ->
            val bus = state.buses[index]
            MixStrip(
                name = bus.name,
                metrics = BusBankMetrics,
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
        BankFooter(
            scroll = scroll,
            pitch = BusBankMetrics.pitch,
            total = state.buses.size,
            label = stringResource(R.string.tab_buses),
            viewportPx = viewport,
            inline = false,
        )
    }
}
