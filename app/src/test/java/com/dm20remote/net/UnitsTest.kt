package com.dm20remote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    @Test
    fun `fader endpoints match the recovered polynomial`() {
        assertTrue(Units.faderPositionToDb(0f) < -80f)
        assertTrue(Units.faderPositionToDb(0f) > -81f)
        assertTrue(Units.faderPositionToDb(1000f) > 10f)
        assertTrue(Units.faderPositionToDb(1000f) < 11f)
    }

    @Test
    fun `fader table is monotonic`() {
        var prev = Units.faderPositionToDb(0f)
        for (p in 1..1023) {
            val db = Units.faderPositionToDb(p.toFloat())
            assertTrue("position $p should not decrease", db >= prev - 0.001f)
            prev = db
        }
    }

    @Test
    fun `zero dB sits near position 755`() {
        val pos = Units.dbToFaderPosition(0f)
        assertTrue("0 dB position was $pos", pos in 740f..770f)
    }

    @Test
    fun `fader conversion round trips`() {
        for (p in intArrayOf(0, 100, 250, 500, 755, 900, 1000)) {
            val db = Units.faderPositionToDb(p.toFloat())
            val back = Units.dbToFaderPosition(db)
            assertEquals(p.toFloat(), back, 2f)
        }
    }

    @Test
    fun `peq encoder maps 0 and 1000 to the band edges`() {
        assertEquals(20.0, Units.encoderToHz(0f), 0.001)
        assertEquals(20000.0, Units.encoderToHz(1000f), 0.01)
    }

    @Test
    fun `peq encoder is logarithmic`() {
        // Geometric centre of a 20 Hz..20 kHz decade span is ~632.46 Hz.
        assertEquals(632.4555, Units.encoderToHz(500f), 1.0)
    }

    @Test
    fun `peq encoder round trips`() {
        for (p in intArrayOf(0, 200, 500, 800, 1000)) {
            val hz = Units.encoderToHz(p.toFloat())
            assertEquals(p.toFloat(), Units.hzToEncoder(hz), 0.5f)
        }
    }

    @Test
    fun `geq conversion round trips`() {
        for (db in floatArrayOf(-18f, -6f, 0f, 6f, 18f)) {
            assertEquals(db, Units.geqDspToDb(Units.geqDbToDsp(db)), 0.001f)
        }
        assertEquals(0f, Units.geqDspToDb(500f), 0.001f)
    }

    @Test
    fun `delay conversions match the recovered factors`() {
        assertEquals(343.2f, Units.delayMsToMetres(1000f), 0.001f)
        assertEquals(1126f, Units.delayMsToFeet(1000f), 0.001f)
    }

    private fun assertRange(
        range: Units.ParamRange,
        min: Float,
        max: Float,
        unit: Units.DisplayUnit,
    ) {
        assertEquals(min, range.min, 0f)
        assertEquals(max, range.max, 0f)
        assertEquals(unit, range.unit)
    }

    @Test
    fun `dynamics ranges match the recovered layout bounds`() {
        val d = Units.DynamicsRanges
        assertRange(d.gateThreshold, -80f, 0f, Units.DisplayUnit.DB)
        assertRange(d.gateAttack, 0.5f, 100f, Units.DisplayUnit.MILLISECONDS)
        assertRange(d.gateRelease, 2f, 2000f, Units.DisplayUnit.MILLISECONDS)
        assertRange(d.gateHold, 2f, 2000f, Units.DisplayUnit.MILLISECONDS)
        assertRange(d.gateDepth, -80f, 0f, Units.DisplayUnit.DB)
        assertRange(d.compressorThreshold, -80f, 0f, Units.DisplayUnit.DB)
        assertRange(d.compressorAttack, 0.5f, 100f, Units.DisplayUnit.MILLISECONDS)
        assertRange(d.compressorRelease, 20f, 5000f, Units.DisplayUnit.MILLISECONDS)
        assertRange(d.compressorGain, -12f, 12f, Units.DisplayUnit.DB)
        assertRange(d.compressorRatio, 1f, 20f, Units.DisplayUnit.NONE)
    }

    @Test
    fun `dynamics threshold readouts are offset by 24 dBu`() {
        assertEquals(24f, Units.DynamicsRanges.THRESHOLD_TO_DBU_OFFSET, 0f)
        // The original shows `model value + 24` on the gate/compressor threshold read-outs.
        assertEquals(
            -56f,
            Units.DynamicsRanges.gateThreshold.min + Units.DynamicsRanges.THRESHOLD_TO_DBU_OFFSET,
            0f,
        )
    }

    @Test
    fun `dynamics wire values are engineering values, not fader positions`() {
        // The mixer fader encodes its value as a 0..1000 position (0 dB sits at
        // position ~755), but the dynamics models put the engineering value on the
        // wire directly: a -6 dB gate threshold is just -6.0f, and a 755 position is
        // not a threshold at all.
        val threshold = Units.DynamicsRanges.gateThreshold
        assertTrue(threshold.contains(-6f))
        assertTrue("755 is a position, not a dB threshold", !threshold.contains(755f))
        assertEquals(0f, Units.faderPositionToDb(755f), 0.5f)

        val ratio = Units.DynamicsRanges.compressorRatio
        assertTrue(ratio.contains(4f))
        assertTrue(!ratio.contains(755f))
    }

    @Test
    fun `param range maps its ends onto the widget travel`() {
        val range = Units.DynamicsRanges.gateAttack
        assertEquals(0f, range.toPosition(range.min), 0.001f)
        assertEquals(1000f, range.toPosition(range.max), 0.001f)
        assertEquals(range.min, range.fromPosition(0f), 0.001f)
        assertEquals(range.max, range.fromPosition(1000f), 0.001f)
    }

    @Test
    fun `param range clamps values outside its bounds`() {
        val gain = Units.DynamicsRanges.compressorGain
        assertEquals(-12f, gain.clamp(-40f), 0f)
        assertEquals(12f, gain.clamp(40f), 0f)
        assertEquals(3f, gain.clamp(3f), 0f)
    }

    @Test
    fun `param range position round trips`() {
        val release = Units.DynamicsRanges.compressorRelease
        for (value in floatArrayOf(20f, 100f, 350f, 1000f, 5000f)) {
            assertEquals(value, release.fromPosition(release.toPosition(value)), 0.01f)
        }
    }

    @Test
    fun `monitor ranges are recovered`() {
        assertRange(Units.MonitorRanges.level, -76f, 0f, Units.DisplayUnit.DB)
        assertEquals(
            10.0,
            Units.encoderToHz(0f, Units.MonitorRanges.OSCI_MIN_HZ, Units.MonitorRanges.OSCI_MAX_HZ),
            0.001,
        )
        assertEquals(
            20000.0,
            Units.encoderToHz(1000f, Units.MonitorRanges.OSCI_MIN_HZ, Units.MonitorRanges.OSCI_MAX_HZ),
            0.01,
        )
    }

    @Test
    fun `display unit table matches the recovered edc_unit map`() {
        val expected = mapOf(
            Units.DisplayUnit.MILLISECONDS to (1 to " ms"),
            Units.DisplayUnit.HERTZ to (2 to "Hz"),
            Units.DisplayUnit.DB to (3 to " dB"),
            Units.DisplayUnit.DBU to (4 to " dBu"),
            Units.DisplayUnit.PERCENT to (5 to "%"),
            Units.DisplayUnit.BPM to (6 to " BPM"),
            Units.DisplayUnit.FACTOR to (7 to ""),
            Units.DisplayUnit.NONE to (8 to ""),
        )
        for ((unit, pair) in expected) {
            assertEquals(pair.first, unit.wireValue)
            assertEquals(pair.second, unit.suffix)
        }
    }

    @Test
    fun `peq ranges match the recovered encoder bounds`() {
        assertRange(Units.PeqRanges.gain, -18f, 18f, Units.DisplayUnit.DB)
        assertRange(Units.PeqRanges.q, 0.5f, 10f, Units.DisplayUnit.NONE)
    }

    @Test
    fun `peq frequency maps between Hz and the logarithmic widget travel`() {
        assertEquals(0f, Units.PeqRanges.hzToPosition(20f), 0.5f)
        assertEquals(1000f, Units.PeqRanges.hzToPosition(20000f), 0.5f)
        assertEquals(20f, Units.PeqRanges.positionToHz(0f), 0.01f)
        assertEquals(20000f, Units.PeqRanges.positionToHz(1000f), 0.5f)
        for (hz in floatArrayOf(20f, 100f, 1000f, 4000f, 20000f)) {
            val back = Units.PeqRanges.positionToHz(Units.PeqRanges.hzToPosition(hz))
            assertEquals(hz, back, hz * 0.01f + 0.1f)
        }
    }

    @Test
    fun `eq shelf codes are the recovered per-band values`() {
        assertEquals(0, EqBands.BELL)
        assertEquals(1, EqBands.LOW_SHELF)
        assertEquals(2, EqBands.HIGH_SHELF)
        assertEquals(4, EqBands.COUNT)
    }
}
