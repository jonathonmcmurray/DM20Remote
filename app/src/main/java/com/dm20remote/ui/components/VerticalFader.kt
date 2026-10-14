package com.dm20remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/**
 * Vertical fader over a 0..[maxPosition] travel.
 *
 * The caller owns the value and receives every change while dragging plus a
 * final [onCommit] when the gesture ends, so commands are only sent on release.
 */
@Composable
fun VerticalFader(
    position: Float,
    onPositionChange: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    maxPosition: Float = 1000f,
) {
    val current = rememberUpdatedState(position)
    val trackHeight = remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF171C21))
            .onSizeChanged { trackHeight.floatValue = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { onCommit() },
                    onDragCancel = { onCommit() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount.y / trackHeight.floatValue * maxPosition
                        onPositionChange((current.value + delta).coerceIn(0f, maxPosition))
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val fraction = (1f - offset.y / trackHeight.floatValue).coerceIn(0f, 1f)
                        onPositionChange(fraction * maxPosition)
                        onCommit()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val top = 16f
            val bottom = size.height - 16f
            val usable = (bottom - top).coerceAtLeast(1f)
            val fraction = 1f - (position / maxPosition).coerceIn(0f, 1f)
            val handleY = top + usable * fraction

            drawLine(
                color = Color(0xFF2A3138),
                start = Offset(cx, top),
                end = Offset(cx, bottom),
                strokeWidth = 6f,
            )
            drawLine(
                color = Color(0xFF4FC3F7),
                start = Offset(cx, handleY),
                end = Offset(cx, bottom),
                strokeWidth = 6f,
            )
            drawRoundRect(
                color = Color(0xFFE8EAED),
                topLeft = Offset(cx - size.width * 0.36f, handleY - 9f),
                size = Size(size.width * 0.72f, 18f),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
