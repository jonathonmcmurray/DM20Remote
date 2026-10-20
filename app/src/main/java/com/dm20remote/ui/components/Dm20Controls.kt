package com.dm20remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dm20remote.ui.Dm20Colors
import com.dm20remote.ui.Dm20Metrics
import com.dm20remote.ui.Dm20Type
import com.dm20remote.ui.DRAG_EMIT_NS
import kotlin.math.abs
import kotlin.math.min

/** Text helper so the panes do not repeat style and colour on every line. */
@Composable
fun DmText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Dm20Type.body,
    color: Color = Dm20Colors.onSurface,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        softWrap = false,
        textAlign = align,
    )
}

/** A 1 dp hairline. */
@Composable
fun RowRule(modifier: Modifier = Modifier, color: Color = Dm20Colors.divider) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * The one chip in the design: a rounded box with a centred label that fills with
 * [activeColor] when it is on. Used for M/S, the dock, play modes and buttons.
 */
@Composable
fun Dm20Chip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 26.dp,
    radius: Dp = 6.dp,
    activeColor: Color = Dm20Colors.primary,
    inactiveColor: Color = Dm20Colors.surfaceVariant,
    activeContent: Color = Dm20Colors.onPrimary,
    inactiveContent: Color = Dm20Colors.onSurfaceVariant,
    style: TextStyle = Dm20Type.body,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (active) activeColor else inactiveColor)
            .then(
                if (enabled) {
                    Modifier.pointerInput(onClick) { detectTapGestures { onClick() } }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        DmText(
            text = label,
            style = style,
            color = if (active) activeContent else inactiveContent,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The 46 x 24 track/knob switch used for every boolean parameter. */
@Composable
fun Dm20Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val w = Dm20Metrics.switchWidth
    val h = Dm20Metrics.switchHeight
    val knob = 16.dp
    Box(
        modifier = modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(h / 2))
            .background(if (checked) Dm20Colors.primary else Dm20Colors.surfaceVariant)
            .then(
                if (enabled) {
                    Modifier.pointerInput(checked, onCheckedChange) {
                        detectTapGestures { onCheckedChange(!checked) }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = if (checked) w - knob - 4.dp else 4.dp)
                .size(knob)
                .clip(RoundedCornerShape(knob / 2))
                .background(
                    when {
                        !enabled -> Dm20Colors.dim
                        checked -> Dm20Colors.onPrimary
                        else -> Dm20Colors.dim
                    },
                ),
        )
    }
}

/**
 * The header's segmented switch: a rounded track holding one pill per option.
 *
 * It is used twice — the four-way tab switch in the console header and the
 * three-way Channel/PEQ/Dynamics switch on the detail pages — so the item width
 * is a parameter (62 dp and 84 dp in the design).
 */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 26.dp,
    style: TextStyle = Dm20Type.body,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Dm20Colors.surfaceVariant)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .height(itemHeight)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) Dm20Colors.primary else Color.Transparent)
                    .pointerInput(index) { detectTapGestures { onSelect(index) } },
                contentAlignment = Alignment.Center,
            ) {
                DmText(
                    text = option,
                    style = style,
                    color = if (selected) Dm20Colors.onPrimary else Dm20Colors.onSurfaceVariant,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * A horizontal parameter slider.
 *
 * Like the mixer fader it works in the widget's own 0..1000 travel and reports
 * continuously while dragging; the caller sends the command in [onCommit] so the
 * gesture always ends with the exact value. [onDrag] is the live path: it is
 * invoked during the gesture, throttled so a drag does not flood the console with
 * a command per touch sample. Bipolar parameters pass [detent] so the fill grows
 * out of the centre and a centre mark is drawn.
 */
@Composable
fun Dm20Slider(
    travel: Float,
    onTravelChange: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    detent: Boolean = false,
    enabled: Boolean = true,
    height: Dp = Dm20Metrics.sliderHeight,
    onDrag: (Float) -> Unit = {},
) {
    var width by remember { mutableFloatStateOf(1f) }
    val current = rememberUpdatedState(travel)
    var lastDragEmit by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .height(height)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { onCommit() },
                                onDragCancel = { onCommit() },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val delta = drag.x / width * 1000f
                                    val next = (current.value + delta).coerceIn(0f, 1000f)
                                    onTravelChange(next)
                                    val now = System.nanoTime()
                                    if (now - lastDragEmit >= DRAG_EMIT_NS) {
                                        lastDragEmit = now
                                        onDrag(next)
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                onTravelChange((offset.x / width * 1000f).coerceIn(0f, 1000f))
                                onCommit()
                            }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val trackH = Dm20Metrics.sliderTrack.toPx()
            val handleR = Dm20Metrics.sliderHandle.toPx() / 2f
            val trackR = CornerRadius(trackH / 2f, trackH / 2f)
            val usable = (size.width - handleR * 2f).coerceAtLeast(1f)
            val x = handleR + usable * (travel / 1000f).coerceIn(0f, 1f)

            drawRoundRect(
                color = Dm20Colors.rail,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = trackR,
            )

            val fillStart = if (detent) size.width / 2f else 0f
            val fillWidth = abs(x - fillStart)
            if (fillWidth > 0.5f) {
                drawRoundRect(
                    color = if (enabled) Dm20Colors.primary else Dm20Colors.rail,
                    topLeft = Offset(min(fillStart, x), cy - trackH / 2f),
                    size = Size(fillWidth, trackH),
                    cornerRadius = trackR,
                )
            }

            if (detent) {
                val markW = 2.dp.toPx()
                val markH = Dm20Metrics.detentHeight.toPx()
                drawRoundRect(
                    color = Dm20Colors.onSurfaceVariant,
                    topLeft = Offset(size.width / 2f - markW / 2f, cy - markH / 2f),
                    size = Size(markW, markH),
                )
            }

            drawCircle(
                color = if (enabled) Dm20Colors.handle else Dm20Colors.dim,
                radius = handleR,
                center = Offset(x, cy),
            )
        }
    }
}

/**
 * Vertical peak meter. [levelDb] is dBFS; the window defaults to the -60..0 dB
 * range the console's channel meters use. The part of the bar above [hotDb] is
 * drawn in the hot colour, so a hot channel is visible at a glance.
 */
@Composable
fun LevelMeter(
    levelDb: Float,
    modifier: Modifier = Modifier,
    minDb: Float = -60f,
    maxDb: Float = 0f,
    hotDb: Float = -6f,
) {
    Canvas(modifier) {
        val radius = size.width / 2f
        drawRoundRect(
            color = Dm20Colors.trackBackground,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )
        if (!levelDb.isFinite()) return@Canvas
        val span = maxDb - minDb
        fun frac(db: Float) = ((db - minDb) / span).coerceIn(0f, 1f)
        val level = frac(levelDb)
        val hot = frac(hotDb)
        val green = min(level, hot)
        drawSegments(level, green, hot, radius)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegments(
    level: Float,
    green: Float,
    hot: Float,
    radius: Float,
) {
    if (level <= 0f) return
    val h = size.height
    if (green > 0f) {
        val height = h * green
        drawRoundRect(
            color = Dm20Colors.meterLow,
            topLeft = Offset(0f, h - height),
            size = Size(size.width, height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
    if (level > hot) {
        val height = h * (level - hot)
        drawRoundRect(
            color = Dm20Colors.meterHot,
            topLeft = Offset(0f, h - h * level),
            size = Size(size.width, height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

/** The horizontal twin of [LevelMeter], for the dock and the detail title row. */
@Composable
fun LevelBar(
    levelDb: Float,
    modifier: Modifier = Modifier,
    minDb: Float = -60f,
    maxDb: Float = 0f,
    hotDb: Float = -6f,
) {
    Canvas(modifier) {
        val radius = size.height / 2f
        drawRoundRect(
            color = Dm20Colors.trackBackground,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )
        if (!levelDb.isFinite()) return@Canvas
        val span = maxDb - minDb
        fun frac(db: Float) = ((db - minDb) / span).coerceIn(0f, 1f)
        val level = frac(levelDb)
        val hot = frac(hotDb)
        val green = min(level, hot)
        if (level <= 0f) return@Canvas
        if (green > 0f) {
            drawRoundRect(
                color = Dm20Colors.meterLow,
                topLeft = Offset.Zero,
                size = Size(size.width * green, size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
        if (level > hot) {
            drawRoundRect(
                color = Dm20Colors.meterHot,
                topLeft = Offset(size.width * hot, 0f),
                size = Size(size.width * (level - hot), size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}

/**
 * The thick scrollbar under the mixer bank and on the detail pages. [progress]
 * and [thumbFraction] come from the scrolled row, so the thumb always reflects
 * how much of the bank is on screen. It sizes itself to the given width, which
 * keeps the same look on every screen size.
 */
@Composable
fun ScrollBar(
    progress: Float,
    thumbFraction: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Dm20Colors.trackBackground),
    ) {
        val thumb = (maxWidth * thumbFraction.coerceIn(0.05f, 1f)).coerceAtLeast(10.dp)
        val travel = (maxWidth - thumb).coerceAtLeast(0.dp)
        Box(
            Modifier
                .offset(x = travel * progress.coerceIn(0f, 1f))
                .width(thumb)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Dm20Colors.scrollThumb),
        )
    }
}

/** Vertical spacer helper with an explicit height. */
@Composable
fun Gap(height: Dp) {
    Column(Modifier.height(height)) {}
}

/** A one-line section heading, the way the detail pages group their controls. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    DmText(text, modifier = modifier, style = Dm20Type.row, color = Dm20Colors.onSurface)
}

/**
 * One labelled parameter row of a two-column detail page.
 *
 * The value is held locally between the drag and the console's acknowledgement
 * (the console echoes a change as the new full model), which is what makes the
 * handle keep up with the finger without the echo fighting it.
 */
@Composable
fun ParamRow(
    label: String,
    value: Float,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    sliderHeight: Dp = 26.dp,
    detent: Boolean = false,
    enabled: Boolean = true,
    labelColor: Color = Dm20Colors.onSurface,
    onDrag: (Float) -> Unit = {},
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) {
        if (!dragging) local = value
    }

    Column(modifier.fillMaxWidth().height(height)) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            DmText(label, style = Dm20Type.body, color = labelColor, modifier = Modifier.weight(1f))
            DmText(format(local), style = Dm20Type.body, color = Dm20Colors.onSurfaceVariant)
        }
        Dm20Slider(
            travel = local.coerceIn(0f, 1000f),
            onTravelChange = {
                dragging = true
                local = it
            },
            onCommit = {
                dragging = false
                onCommit(local)
            },
            detent = detent,
            enabled = enabled,
            height = sliderHeight,
            onDrag = onDrag,
        )
    }
}

/** A hairline that separates the two columns of a full-width page. */
@Composable
fun ColumnRule(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).fillMaxHeight().background(Dm20Colors.divider))
}

/** A label on the left and a read-only value on the right, used by info rows. */
@Composable
fun ReadoutRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    valueColor: Color = Dm20Colors.onSurface,
    dot: Color? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DmText(label, style = Dm20Type.body, color = Dm20Colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (dot != null) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dot),
            )
        }
        DmText(value, style = Dm20Type.body, color = valueColor)
    }
}

/** Arranges children left to right with a fixed gap. */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    gap: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}
