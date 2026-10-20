package com.dm20remote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimum spacing between the live updates a fader or slider drag sends to the
 * console.
 *
 * A touch sample arrives per frame, so an unthrottled drag would push ~60 commands
 * a second. 25 Hz keeps the desk tracking the gesture while leaving headroom for the
 * console's own ~6 Hz meter stream and its change-event echoes. The gesture's final
 * value is always sent separately on release, so dropping samples here is safe.
 */
internal const val DRAG_EMIT_NS = 40_000_000L

/**
 * Palette of the direction-B redesign.
 *
 * Every value is the exact fill used in the FigCraft mockups
 * (`design/redesign.figcraft`, the `REDESIGN A`/`REDESIGN B` frames), so the
 * ported Compose UI and the approved design agree one for one. The design is a
 * 740 x 360 dp landscape layout, which is the orientation the app already locks.
 */
object Dm20Colors {
    /** Page background. */
    val background = Color(0xFF0B0F12)

    /** Bars: the header and the channel dock. */
    val surface = Color(0xFF14191E)

    /** Chips, switches, cards and rows. */
    val surfaceVariant = Color(0xFF1E252B)

    val onSurface = Color(0xFFE3E6E8)
    val onSurfaceVariant = Color(0xFFB6BDC4)
    val primary = Color(0xFF4FC3F7)
    val onPrimary = Color(0xFF00202E)

    /** Secondary text: captions, paths, units, section labels. */
    val dim = Color(0xFF7E8A95)

    val divider = Color(0xFF232A31)

    /** Fader rail and slider track. */
    val rail = Color(0xFF2A3138)

    /** Scale marks across the fader rail. */
    val tick = Color(0xFF3A4249)

    /** Meter and progress troughs. */
    val trackBackground = Color(0xFF171C21)

    val scrollThumb = Color(0xFF7FB3C8)

    /** Off-state mixer M/S chip. */
    val chipOff = Color(0xFF2B343B)

    /** Fader and slider handle. */
    val handle = Color(0xFFE8EAED)

    val meterLow = Color(0xFF37D67A)
    val meterHot = Color(0xFFF2C744)
    val error = Color(0xFFE5544B)

    /** Alternating list row, and the selected row of a scene/track list. */
    val row = Color(0xFF161C22)
    val rowSelected = Color(0xFF1B3A4D)
}

/**
 * Fixed geometry of the shell. The mockups were drawn against exactly these
 * numbers in a 740 x 360 dp frame; the flexible parts (how many strips fit) are
 * left to the layout and scroll.
 */
object Dm20Metrics {
    val header = 40.dp
    val title = 36.dp
    val scrollRow = 24.dp
    val dock = 52.dp

    /** Mixer strip. Input strips are narrower than bus strips. */
    val inputStripWidth = 70.dp
    val inputStripPitch = 78.dp
    val busStripWidth = 84.dp
    val busStripPitch = 92.dp

    /** Fader/meter column inside a strip. */
    val faderTravel = 176.dp
    val inputMeterWidth = 8.dp
    val busMeterWidth = 10.dp
    val inputRailWidth = 8.dp
    val busRailWidth = 10.dp
    val inputHandle = 24.dp
    val busHandle = 28.dp
    val handleHeight = 12.dp

    /** Horizontal sliders: track, handle and the centre detent. */
    val sliderTrack = 12.dp
    val sliderHandle = 20.dp
    val detentHeight = 28.dp

    /** Switch (48V, HPF, EQ bypass, ...). */
    val switchWidth = 46.dp
    val switchHeight = 24.dp

    /** Channel dock. */
    val dockChipWidth = 84.dp
    val dockChipHeight = 26.dp
    val dockChipPitch = 90.dp
    val dockMeterHeight = 6.dp

    /** Minimum height a slider needs so the 20 dp handle is not clipped. */
    val sliderHeight = 26.dp

    /** Row pitch of a labelled parameter in the two-column detail pages. */
    val paramRowHeight = 42.dp
}

/**
 * The type scale of the redesign. Sizes are the `fontSize` values used in the
 * mockups (12 and 13 for most text, 11 for strip labels, 10 for captions).
 */
object Dm20Type {
    val caption = TextStyle(fontSize = 10.sp, lineHeight = 12.sp)
    val label = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
    val body = TextStyle(fontSize = 12.sp, lineHeight = 15.sp)
    val row = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)
    val title = TextStyle(fontSize = 16.sp, lineHeight = 19.sp)
    val control = TextStyle(fontSize = 14.sp, lineHeight = 17.sp)
}

/** Convenience for the fixed-height captions scattered through the mockups. */
internal val Dp.half: Dp get() = this / 2
