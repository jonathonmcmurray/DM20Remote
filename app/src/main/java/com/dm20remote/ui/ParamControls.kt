package com.dm20remote.ui

import com.dm20remote.net.Units
import kotlin.math.roundToInt

/**
 * Value formatters shared by the mixer, the detail pages and the music tab.
 *
 * The redesign has no Material widgets of its own any more — every control lives
 * in `ui/components` and `ui/Dm20Design` — so what is left here is the display
 * arithmetic, which is also what the unit tests pin down.
 */

/** A strip read-out: -80 dB and below reads as a dash, otherwise one decimal. */
internal fun formatDb(db: Float): String =
    if (db <= -80f) "-\u221E" else "%.1f".format(db)

/** The same, with the unit spelled out for the detail pages. */
internal fun formatDbUnit(db: Float): String =
    if (db <= -80f) "-\u221E dB" else "%.1f dB".format(db)

/** -100..100 -> "L25" / "C" / "R25". */
internal fun formatPan(pan: Float): String = when {
    pan > 0.5f -> "R${pan.roundToInt()}"
    pan < -0.5f -> "L${(-pan).roundToInt()}"
    else -> "C"
}

/** Pan travel (-100..+100) as a 0..1000 slider travel, and back. */
internal fun panToTravel(pan: Float): Float = (pan.coerceIn(-100f, 100f) + 100f) * 5f

internal fun travelToPan(travel: Float): Float = travel / 5f - 100f

/** Formats a value in the engineering unit the control displays. */
internal fun formatEngineering(value: Float, unit: Units.DisplayUnit): String = when (unit) {
    Units.DisplayUnit.MILLISECONDS -> "${value.roundToInt()} ms"
    Units.DisplayUnit.HERTZ -> formatHz(value)
    Units.DisplayUnit.DB -> "%+.1f dB".format(value)
    Units.DisplayUnit.DBU -> "%+.1f dBu".format(value)
    Units.DisplayUnit.PERCENT -> "${value.roundToInt()}%"
    Units.DisplayUnit.BPM -> "${value.roundToInt()} BPM"
    Units.DisplayUnit.FACTOR -> "%.2f".format(value)
    Units.DisplayUnit.NONE -> "%.2f".format(value)
}

/** 20 -> "20 Hz", 4000 -> "4.0 kHz". */
internal fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.2f kHz".format(hz / 1000f) else "${hz.roundToInt()} Hz"

/** 0.7 -> "1:2.0"; the console reports the compressor ratio as a factor. */
internal fun formatRatio(ratio: Float): String = "1:%.1f".format(ratio)

/**
 * The raw `MicInInputPartModel.trimValue`. The recovery never pinned the scaling
 * of this field down (see `docs/protocol-notes`), so the detail page shows it
 * read-only and without a unit rather than inventing a range for it.
 */
internal fun formatTrim(value: Float): String = "%.1f".format(value)
