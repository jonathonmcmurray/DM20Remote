package com.dm20remote.net

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

/**
 * Engineering-unit conversions recovered from the original client
 * (`com.amo.skdmc.util.HardwareUtil`, `DataTemUtils`, layout bounds).
 *
 * All values are derived from static analysis of the shipped APK; none of the
 * original code or resources are reused.
 */
object Units {
    /** Fader travel on the wire and in the UI: 0 .. 1000. */
    const val FADER_MAX_POS = 1000

    /** The original pre-computes a 1024-entry lookup table from a polynomial. */
    const val FADER_TABLE_SIZE = 1024

    private val faderTable: FloatArray = FloatArray(FADER_TABLE_SIZE) { i ->
        val x = i.toDouble()
        val db = -1.789e-15 * x.pow(6) +
            6.199e-12 * x.pow(5) -
            8.398e-9 * x.pow(4) +
            5.669e-6 * x.pow(3) -
            0.002059 * x.pow(2) +
            0.4686 * x -
            80.51
        db.toFloat()
    }

    /** Wire/UI fader position (0..1000) -> dB gain. */
    fun faderPositionToDb(position: Float): Float {
        val i = position.toInt().coerceIn(0, FADER_TABLE_SIZE - 1)
        return faderTable[i]
    }

    /** dB gain -> nearest wire/UI fader position (0..1023). */
    fun dbToFaderPosition(db: Float): Float {
        if (db <= faderTable[0]) return 0f
        if (db >= faderTable[FADER_TABLE_SIZE - 1]) return (FADER_TABLE_SIZE - 1).toFloat()
        var lo = 0
        var hi = FADER_TABLE_SIZE - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (faderTable[mid] <= db) lo = mid else hi = mid
        }
        val a = faderTable[lo]
        val b = faderTable[hi]
        val t = if (b > a) (db - a) / (b - a) else 0f
        return lo + t
    }

    /** Pan travel recovered from the `LRFader` layout bounds: -100 .. +100. */
    const val PAN_MIN = -100f
    const val PAN_MAX = 100f

    /** Parametric EQ frequency encoder: 0..1000 -> 20 Hz .. 20 kHz (logarithmic). */
    const val PEQ_MIN_HZ = 20.0
    const val PEQ_MAX_HZ = 20000.0

    fun encoderToHz(
        position: Float,
        minHz: Double = PEQ_MIN_HZ,
        maxHz: Double = PEQ_MAX_HZ,
    ): Double = minHz * (maxHz / minHz).pow(position / 1000.0)

    fun hzToEncoder(
        hz: Double,
        minHz: Double = PEQ_MIN_HZ,
        maxHz: Double = PEQ_MAX_HZ,
    ): Float {
        if (hz <= minHz) return 0f
        if (hz >= maxHz) return 1000f
        return (1000.0 * ln(hz / minHz) / ln(maxHz / minHz)).toFloat()
    }

    /** Graphic EQ: DSP value 0..1000 <-> -18 .. +18 dB. */
    fun geqDspToDb(value: Float): Float = 0.036f * value - 18f

    fun geqDbToDsp(db: Float): Float = (18f + db) * 1000f / 36f

    /** Delay time in ms -> distance. */
    fun delayMsToMetres(ms: Float): Float = 343.2f * ms / 1000f

    fun delayMsToFeet(ms: Float): Float = 1126f * ms / 1000f

    fun ratioToDb(ratio: Float): Float = 20f * log10(ratio)

    // ------------------------------------------------------------------
    // Per-control engineering ranges
    //
    // Unlike the mixer fader (whose wire value is a 0..1000 *position*), the
    // dynamics, monitor and FX models carry the *engineering* value itself:
    // the original views copy `model.XValue` straight into the control and the
    // control's value straight back into `model.XValue`, with no rescaling
    // (`DynamicsView.setShowData` / `bindEvent`, `MonitorView.setData`). The
    // ranges below are the `app:min_value`/`app:max_value` bounds declared on
    // those controls in the original layouts, so they are the ranges the wire
    // fields actually use.
    // ------------------------------------------------------------------

    /** `app:edc_unit` values, recovered from `EncoderDisplayControl`'s static map. */
    enum class DisplayUnit(val wireValue: Int, val suffix: String) {
        MILLISECONDS(1, " ms"),
        HERTZ(2, "Hz"),
        DB(3, " dB"),
        DBU(4, " dBu"),
        PERCENT(5, "%"),
        BPM(6, " BPM"),
        FACTOR(7, ""), // ratio, shown as a bare 1:1-style number
        NONE(8, ""),
    }

    /**
     * The engineering range of one control.
     *
     * The wire stores the engineering value itself, so [clamp] is the only
     * conversion a command needs; [toPosition]/[fromPosition] exist to map a
     * value onto a fader/encoder's 0..1000 widget travel.
     */
    data class ParamRange(val min: Float, val max: Float, val unit: DisplayUnit) {
        val span: Float get() = max - min

        fun clamp(value: Float): Float = value.coerceIn(min, max)

        fun contains(value: Float): Boolean = value >= min && value <= max

        /** Engineering value -> widget travel (0..1000). */
        fun toPosition(value: Float): Float =
            if (span <= 0f) 0f else (clamp(value) - min) / span * 1000f

        /** Widget travel (0..1000) -> engineering value. */
        fun fromPosition(position: Float): Float =
            min + (position / 1000f).coerceIn(0f, 1f) * span
    }

    /**
     * `MicInDynamicPartModel` ranges, recovered from `res/layout/view_dynamics_show.xml`
     * (`app:min_value`/`app:max_value` per `SelectedFader`; unit from the paired
     * `EncoderDisplayControl`'s `app:edc_unit`). Wire values go through unchanged.
     */
    object DynamicsRanges {
        // The two threshold values are the *wire/control* domain in dB (-80..0); the
        // original shows them on the read-out as `value + THRESHOLD_TO_DBU_OFFSET` dBu.
        const val THRESHOLD_TO_DBU_OFFSET = 24f

        val gateThreshold = ParamRange(-80f, 0f, DisplayUnit.DB)
        val gateAttack = ParamRange(0.5f, 100f, DisplayUnit.MILLISECONDS)
        val gateRelease = ParamRange(2f, 2000f, DisplayUnit.MILLISECONDS)
        val gateHold = ParamRange(2f, 2000f, DisplayUnit.MILLISECONDS)
        val gateDepth = ParamRange(-80f, 0f, DisplayUnit.DB)
        val compressorThreshold = ParamRange(-80f, 0f, DisplayUnit.DB)
        val compressorAttack = ParamRange(0.5f, 100f, DisplayUnit.MILLISECONDS)
        val compressorRelease = ParamRange(20f, 5000f, DisplayUnit.MILLISECONDS)
        val compressorGain = ParamRange(-12f, 12f, DisplayUnit.DB)
        val compressorRatio = ParamRange(1f, 20f, DisplayUnit.NONE)
    }

    /**
     * `SetupMonitorModel` ranges, recovered from `res/layout/view_monitor_show.xml`.
     * The oscillator frequency is the one monitor value not stored raw: its encoder
     * position maps to Hz through [encoderToHz] with a 10 Hz…20 kHz span.
     */
    object MonitorRanges {
        val level = ParamRange(-76f, 0f, DisplayUnit.DB)
        const val OSCI_MIN_HZ = 10.0
        const val OSCI_MAX_HZ = 20000.0
    }

    /**
     * `EqPartModel` (PEQ) ranges, recovered from `res/layout/view_eq_show.xml` and
     * the `EncoderControl` bindings in `com.esunny.sound.ui.view.innerview.EQView`.
     *
     * Gain and Q are stored on the wire in engineering units, so they use
     * [ParamRange] directly. Frequency is the one PEQ value shown on a logarithmic
     * encoder: the wire still stores **Hz**, and [hzToPosition]/[positionToHz] map
     * it onto the 0..1000 widget travel via [encoderToHz]/[hzToEncoder].
     */
    object PeqRanges {
        val gain = ParamRange(-18f, 18f, DisplayUnit.DB)
        val q = ParamRange(0.5f, 10f, DisplayUnit.NONE)

        /** Hz on the wire -> logarithmic widget travel (0..1000). */
        fun hzToPosition(hz: Float): Float = hzToEncoder(hz.toDouble())

        /** Logarithmic widget travel (0..1000) -> Hz. */
        fun positionToHz(position: Float): Float = encoderToHz(position).toFloat()
    }
}
