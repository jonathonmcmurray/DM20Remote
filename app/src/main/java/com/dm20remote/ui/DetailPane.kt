package com.dm20remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dm20remote.R
import com.dm20remote.model.BusChannel
import com.dm20remote.model.ConsoleSession
import com.dm20remote.model.ConsoleState
import com.dm20remote.model.InputChannel
import com.dm20remote.net.EqBands
import com.dm20remote.net.Units
import com.dm20remote.net.bandFrequency
import com.dm20remote.net.bandGain
import com.dm20remote.net.bandQ
import com.dm20remote.net.isBandShelf
import com.dm20remote.ui.components.ColumnRule
import com.dm20remote.ui.components.Dm20Chip
import com.dm20remote.ui.components.Dm20Switch
import com.dm20remote.ui.components.DmText
import com.dm20remote.ui.components.ParamRow
import com.dm20remote.ui.components.ReadoutRow
import com.dm20remote.ui.components.SectionTitle

/**
 * The full-width detail pages that replaced the old modal dialogs.
 *
 * All three input pages share the same frame — header, title row, content, the
 * scroll row and the channel dock — so only the content area differs. The bus page
 * has no design of its own (the mockups only covered the input pages); it reuses
 * the same frame and lays the few bus parameters out in the two-column form.
 */

@Composable
internal fun InputDetailScreen(
    state: ConsoleState,
    index: Int,
    detailTab: Int,
    onDetailTab: (Int) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channel = state.inputs.getOrNull(index) ?: return
    Column(modifier.fillMaxSize()) {
        DetailHeader(
            online = state.controlConnected,
            mute = channel.mute,
            solo = channel.solo,
            onMuteToggle = { ConsoleSession.setInputMute(channel, !channel.mute) },
            onSoloToggle = { ConsoleSession.setInputSolo(channel, !channel.solo) },
            onBack = onBack,
            detailTab = detailTab,
            onDetailTab = onDetailTab,
        )
        TitleRow(name = channel.name, levelDb = state.inputMeterDb(index))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (detailTab) {
                DETAIL_PEQ -> PeqPage(channel)
                DETAIL_DYNAMICS -> DynamicsPage(channel)
                else -> ChannelPage(channel)
            }
        }
        DockSection(
            names = state.inputs.map { it.name },
            levelOf = { state.inputMeterDb(it) },
            selected = index,
            label = stringResource(R.string.tab_inputs),
            onSelect = onSelectChannel,
        )
    }
}

@Composable
internal fun BusDetailScreen(
    state: ConsoleState,
    index: Int,
    onSelectChannel: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bus = state.buses.getOrNull(index) ?: return
    Column(modifier.fillMaxSize()) {
        DetailHeader(
            online = state.controlConnected,
            mute = bus.mute,
            solo = bus.solo,
            onMuteToggle = { ConsoleSession.setBusMute(bus, !bus.mute) },
            onSoloToggle = { ConsoleSession.setBusSolo(bus, !bus.solo) },
            onBack = onBack,
        )
        TitleRow(name = bus.name, levelDb = state.busMeterDb(index))
        Box(Modifier.weight(1f).fillMaxWidth()) { BusPage(bus) }
        DockSection(
            names = state.buses.map { it.name },
            levelOf = { state.busMeterDb(it) },
            selected = index,
            label = stringResource(R.string.tab_buses),
            onSelect = onSelectChannel,
        )
    }
}

/**
 * The scroll row and the dock under a detail page. The row describes the dock
 * rather than the page content, because the dock is the thing that scrolls.
 */
@Composable
private fun DockSection(
    names: List<String>,
    levelOf: (Int) -> Float,
    selected: Int,
    label: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        BankFooter(
            scroll = scroll,
            pitch = Dm20Metrics.dockChipPitch,
            total = names.size,
            label = label,
            viewportPx = viewport,
            inline = true,
        )
        ChannelDock(
            names = names,
            levelOf = levelOf,
            selected = selected,
            scroll = scroll,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth().height(Dm20Metrics.dock).onSizeChanged { viewport = it.width },
        )
    }
}

// ---------------------------------------------------------------------------
// Channel page (B3): gain, trim, pan and the input switches
// ---------------------------------------------------------------------------

@Composable
private fun ChannelPage(channel: InputChannel) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).then(PageColumn)) {
            SectionTitle(stringResource(R.string.section_gain))
            Spacer(Modifier.height(6.dp))
            ParamRow(
                label = stringResource(R.string.param_fader),
                value = channel.faderPosition,
                format = { formatDbUnit(Units.faderPositionToDb(it)) },
                onCommit = { ConsoleSession.setInputFader(channel, it) },
                height = 46.dp,
            )
            Spacer(Modifier.height(10.dp))
            TrimRow(channel)
        }
        ColumnRule()
        Column(Modifier.weight(1f).then(PageColumn)) {
            SectionTitle(stringResource(R.string.section_pan_input))
            Spacer(Modifier.height(6.dp))
            ParamRow(
                label = stringResource(R.string.param_pan),
                value = panToTravel(channel.pan),
                format = { formatPan(travelToPan(it)) },
                onCommit = { ConsoleSession.setInputPan(channel, travelToPan(it)) },
                detent = true,
                height = 46.dp,
            )
            Spacer(Modifier.height(8.dp))
            SwitchLine(
                label = stringResource(R.string.param_48v),
                checked = channel.input.is48VEnable,
                onCheckedChange = { on -> ConsoleSession.setInputPart(channel) { it.setIs48VEnable(on) } },
            )
            SwitchLine(
                label = stringResource(R.string.param_hpf),
                checked = channel.input.isHPFEnable,
                onCheckedChange = { on -> ConsoleSession.setInputPart(channel) { it.setIsHPFEnable(on) } },
            )
        }
    }
}

/**
 * Trim, as the wire model exposes it: an enable flag and a value whose scaling the
 * recovery never pinned down, so the value is shown read-only next to its switch.
 */
@Composable
private fun TrimRow(channel: InputChannel) {
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DmText(
            text = stringResource(R.string.param_trim),
            style = Dm20Type.body,
            color = Dm20Colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        DmText(
            text = formatTrim(channel.input.trimValue),
            style = Dm20Type.body,
            color = Dm20Colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Dm20Switch(
            checked = channel.input.isTrimEnable,
            onCheckedChange = { on -> ConsoleSession.setInputPart(channel) { it.setIsTrimEnable(on) } },
        )
    }
}

// ---------------------------------------------------------------------------
// PEQ page (B1): the four-band table and the editor for the selected band
// ---------------------------------------------------------------------------

@Composable
private fun PeqPage(channel: InputChannel) {
    val eq = channel.eq
    var band by remember(channel.moduleId) { mutableIntStateOf(EqBands.HIGH) }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(440f).fillMaxHeight().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)) {
            BandHeaderRow()
            EqBands.names.forEachIndexed { index, name ->
                val type = when {
                    !eq.isBandShelf(index) -> stringResource(R.string.peq_type_bell)
                    index == EqBands.HIGH -> stringResource(R.string.peq_type_high_shelf)
                    else -> stringResource(R.string.peq_type_low_shelf)
                }
                BandRow(
                    name = name,
                    frequency = eq.bandFrequency(index),
                    gain = eq.bandGain(index),
                    q = eq.bandQ(index),
                    type = type,
                    selected = band == index,
                    onClick = { band = index },
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dm20Chip(
                    label = stringResource(R.string.peq_flat),
                    active = false,
                    onClick = { ConsoleSession.setEqFlat(channel) },
                    width = 64.dp,
                    height = 26.dp,
                    radius = 6.dp,
                )
                Spacer(Modifier.width(14.dp))
                DmText(
                    text = stringResource(R.string.param_eq_bypass),
                    style = Dm20Type.body,
                    color = Dm20Colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Dm20Switch(
                    checked = !eq.isByPass,
                    onCheckedChange = { on -> ConsoleSession.setEqBypass(channel, !on) },
                )
            }
        }
        ColumnRule()
        Column(Modifier.weight(300f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SectionTitle(stringResource(R.string.peq_band_title, EqBands.names[band]))
            Spacer(Modifier.height(2.dp))
            ParamRow(
                label = stringResource(R.string.param_freq),
                value = Units.PeqRanges.hzToPosition(eq.bandFrequency(band)),
                format = { formatHz(Units.PeqRanges.positionToHz(it)) },
                onCommit = { ConsoleSession.setEqBandFrequency(channel, band, Units.PeqRanges.positionToHz(it)) },
                height = 44.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_gain),
                value = eq.bandGain(band),
                range = Units.PeqRanges.gain,
                onValue = { ConsoleSession.setEqBandGain(channel, band, it) },
                height = 44.dp,
                detent = true,
            )
            RangeParamRow(
                label = stringResource(R.string.param_q),
                value = eq.bandQ(band),
                range = Units.PeqRanges.q,
                onValue = { ConsoleSession.setEqBandQ(channel, band, it) },
                height = 44.dp,
                format = { "%.2f".format(it) },
            )
            if (band == EqBands.HIGH || band == EqBands.LOW) {
                SwitchLine(
                    label = stringResource(R.string.peq_shelving),
                    checked = eq.isBandShelf(band),
                    onCheckedChange = { on -> ConsoleSession.setEqBandShelf(channel, band, on) },
                )
            }
        }
    }
}

@Composable
private fun BandHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BandCell(stringResource(R.string.param_band), 1.1f, Dm20Colors.dim, Dm20Type.caption)
        BandCell(stringResource(R.string.param_freq), 1.3f, Dm20Colors.dim, Dm20Type.caption)
        BandCell(stringResource(R.string.param_gain), 1f, Dm20Colors.dim, Dm20Type.caption)
        BandCell(stringResource(R.string.param_q), 0.8f, Dm20Colors.dim, Dm20Type.caption)
        BandCell(stringResource(R.string.param_type), 1f, Dm20Colors.dim, Dm20Type.caption)
    }
}

/** One band of the table. The selected row is the one the editor on the right edits. */
@Composable
private fun BandRow(
    name: String,
    frequency: Float,
    gain: Float,
    q: Float,
    type: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Dm20Colors.rowSelected else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BandCell(name, 1.1f, if (selected) Dm20Colors.primary else Dm20Colors.onSurface)
        BandCell(formatHz(frequency), 1.3f, Dm20Colors.onSurfaceVariant)
        BandCell(formatEngineering(gain, Units.DisplayUnit.DB), 1f, Dm20Colors.onSurfaceVariant)
        BandCell("%.2f".format(q), 0.8f, Dm20Colors.onSurfaceVariant)
        BandCell(type, 1f, Dm20Colors.dim)
    }
}

@Composable
private fun RowScope.BandCell(text: String, weight: Float, color: Color, style: TextStyle = Dm20Type.label) {
    DmText(text, style = style, color = color, maxLines = 1, modifier = Modifier.weight(weight))
}

// ---------------------------------------------------------------------------
// Dynamics page (B2): gate and compressor side by side
// ---------------------------------------------------------------------------

@Composable
private fun DynamicsPage(channel: InputChannel) {
    val d = channel.dynamics
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            SwitchLine(
                label = stringResource(R.string.section_gate),
                checked = d.isGateEnable,
                onCheckedChange = { ConsoleSession.setGateEnable(channel, it) },
                labelStyle = Dm20Type.row,
            )
            Spacer(Modifier.height(4.dp))
            RangeParamRow(
                label = stringResource(R.string.param_gate_threshold),
                value = d.gateThresholdValue,
                range = Units.DynamicsRanges.gateThreshold,
                onValue = { ConsoleSession.setGateThreshold(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_gate_attack),
                value = d.gateAttackValue,
                range = Units.DynamicsRanges.gateAttack,
                onValue = { ConsoleSession.setGateAttack(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_gate_release),
                value = d.gateReleaseValue,
                range = Units.DynamicsRanges.gateRelease,
                onValue = { ConsoleSession.setGateRelease(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_gate_hold),
                value = d.gateHoldValue,
                range = Units.DynamicsRanges.gateHold,
                onValue = { ConsoleSession.setGateHold(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_gate_depth),
                value = d.gateDepthValue,
                range = Units.DynamicsRanges.gateDepth,
                onValue = { ConsoleSession.setGateDepth(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
        }
        ColumnRule()
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SwitchLine(
                label = stringResource(R.string.section_compressor),
                checked = d.isCompressorEnable,
                onCheckedChange = { ConsoleSession.setCompressorEnable(channel, it) },
                labelStyle = Dm20Type.row,
            )
            Spacer(Modifier.height(4.dp))
            RangeParamRow(
                label = stringResource(R.string.param_compressor_threshold),
                value = d.compressorTresholdValue,
                range = Units.DynamicsRanges.compressorThreshold,
                onValue = { ConsoleSession.setCompressorThreshold(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_compressor_attack),
                value = d.compressorAttackValue,
                range = Units.DynamicsRanges.compressorAttack,
                onValue = { ConsoleSession.setCompressorAttack(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_compressor_release),
                value = d.compressorReleaseValue,
                range = Units.DynamicsRanges.compressorRelease,
                onValue = { ConsoleSession.setCompressorRelease(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
            RangeParamRow(
                label = stringResource(R.string.param_compressor_ratio),
                value = d.compressorRatioValue,
                range = Units.DynamicsRanges.compressorRatio,
                onValue = { ConsoleSession.setCompressorRatio(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
                format = { formatRatio(it) },
            )
            RangeParamRow(
                label = stringResource(R.string.param_compressor_gain),
                value = d.compressorGainValue,
                range = Units.DynamicsRanges.compressorGain,
                onValue = { ConsoleSession.setCompressorGain(channel, it) },
                height = 38.dp,
                sliderHeight = 22.dp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bus page: the bus model is much smaller than an input, so this is short
// ---------------------------------------------------------------------------

@Composable
private fun BusPage(bus: BusChannel) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).then(PageColumn)) {
            SectionTitle(stringResource(R.string.section_gain))
            Spacer(Modifier.height(6.dp))
            ParamRow(
                label = stringResource(R.string.param_fader),
                value = bus.gainPosition,
                format = { formatDbUnit(Units.faderPositionToDb(it)) },
                onCommit = { ConsoleSession.setBusFader(bus, it) },
                height = 46.dp,
            )
            Spacer(Modifier.height(10.dp))
            ParamRow(
                label = stringResource(R.string.param_pan),
                value = panToTravel(bus.pan),
                format = { formatPan(travelToPan(it)) },
                onCommit = { ConsoleSession.setBusPan(bus, travelToPan(it)) },
                detent = true,
                height = 46.dp,
            )
        }
        ColumnRule()
        Column(Modifier.weight(1f).then(PageColumn)) {
            SectionTitle(stringResource(R.string.section_delay))
            Spacer(Modifier.height(6.dp))
            SwitchLine(
                label = stringResource(R.string.param_delay),
                checked = bus.output.isDelayEnable,
                onCheckedChange = { ConsoleSession.setBusDelayEnable(bus, it) },
            )
            Spacer(Modifier.height(6.dp))
            ReadoutRow(
                label = stringResource(R.string.param_delay_time),
                value = "%.2f ms".format(bus.output.delayValue),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/** The padding every two-column page column uses. */
private val PageColumn = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)

/** A labelled boolean row: label, then the model switch. */
@Composable
private fun SwitchLine(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = Dm20Type.body,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DmText(
            text = label,
            style = labelStyle,
            color = Dm20Colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Dm20Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * A labelled slider over a recovered [Units.ParamRange]. The slider works in the
 * widget's 0..1000 travel and the range converts both ways, so the wire always
 * receives the engineering value.
 */
@Composable
private fun RangeParamRow(
    label: String,
    value: Float,
    range: Units.ParamRange,
    onValue: (Float) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    sliderHeight: Dp = 26.dp,
    detent: Boolean = false,
    format: (Float) -> String = { formatEngineering(it, range.unit) },
) {
    ParamRow(
        label = label,
        value = range.toPosition(value),
        format = { format(range.fromPosition(it)) },
        onCommit = { onValue(range.fromPosition(it)) },
        modifier = modifier,
        height = height,
        sliderHeight = sliderHeight,
        detent = detent,
    )
}
