package com.dm20remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dm20remote.ui.Dm20Colors

/**
 * The vertical fader of one mixer strip.
 *
 * Unlike a Material slider it draws the console's own 12 dp handle on an 8/10 dp
 * rail with three reference ticks, and it is the only control in the design that
 * works in pixels the model already uses: [position] is the wire fader position
 * (0..1000), so no conversion happens here.
 *
 * The fader reports continuously while dragging and calls [onCommit] when the
 * gesture ends; the caller sends the command there so the console echo cannot
 * fight the gesture.
 */
@Composable
fun VerticalFader(
    position: Float,
    onPositionChange: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    railWidth: Dp = 8.dp,
    handleHeight: Dp = 12.dp,
    maxPosition: Float = 1000f,
) {
    var height by remember { mutableFloatStateOf(1f) }
    val current = rememberUpdatedState(position)

    Box(
        modifier = modifier
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onCommit() },
                    onDragCancel = { onCommit() },
                    onDrag = { change, drag ->
                        change.consume()
                        val delta = -drag.y / height * maxPosition
                        onPositionChange((current.value + delta).coerceIn(0f, maxPosition))
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (1f - offset.y / height).coerceIn(0f, 1f)
                    onPositionChange(fraction * maxPosition)
                    onCommit()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val rail = railWidth.toPx()
            val handle = handleHeight.toPx()
            val cx = size.width / 2f

            drawRoundRect(
                color = Dm20Colors.rail,
                topLeft = Offset(cx - rail / 2f, 0f),
                size = Size(rail, size.height),
                cornerRadius = CornerRadius(rail / 2f, rail / 2f),
            )

            // Three reference ticks at 25/50/75 % of the travel.
            val tickWidth = 18.dp.toPx()
            val tickHeight = 1.dp.toPx()
            for (i in 1..3) {
                val y = size.height * (i / 4f)
                drawRect(
                    color = Dm20Colors.tick,
                    topLeft = Offset(cx - tickWidth / 2f, y),
                    size = Size(tickWidth, tickHeight),
                )
            }

            val fraction = 1f - (position / maxPosition).coerceIn(0f, 1f)
            val cy = handle / 2f + (size.height - handle) * fraction
            drawRoundRect(
                color = Dm20Colors.handle,
                topLeft = Offset(0f, cy - handle / 2f),
                size = Size(size.width, handle),
                cornerRadius = CornerRadius(handle / 2f, handle / 2f),
            )
        }
    }
}
