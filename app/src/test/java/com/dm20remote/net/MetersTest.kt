package com.dm20remote.net

import com.dm20remote.proto.MeterLevelData
import com.google.protobuf.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetersTest {
    @Test
    fun `map has eighty slots`() {
        assertEquals(80, Meters.slots.size)
    }

    @Test
    fun `first twelve channels expose mono output meters`() {
        assertEquals(2, Meters.slotIndex(0, Meters.Kind.OUT))
        assertEquals(5, Meters.slotIndex(1, Meters.Kind.OUT))
        assertEquals(35, Meters.slotIndex(11, Meters.Kind.OUT))
    }

    @Test
    fun `stereo channels expose left and right meters`() {
        assertEquals(38, Meters.slotIndex(12, Meters.Kind.L_OUT))
        assertEquals(39, Meters.slotIndex(12, Meters.Kind.R_OUT))
        assertEquals(42, Meters.slotIndex(13, Meters.Kind.L_OUT))
        assertEquals(43, Meters.slotIndex(13, Meters.Kind.R_OUT))
    }

    @Test
    fun `bus meters are mapped from 128 onwards`() {
        assertEquals(68, Meters.slotIndex(128, Meters.Kind.OUT))
        assertEquals(71, Meters.slotIndex(131, Meters.Kind.OUT))
        assertEquals(72, Meters.slotIndex(132, Meters.Kind.L_OUT))
        assertEquals(73, Meters.slotIndex(132, Meters.Kind.R_OUT))
        assertEquals(78, Meters.slotIndex(135, Meters.Kind.L_OUT))
        assertEquals(79, Meters.slotIndex(135, Meters.Kind.R_OUT))
    }

    @Test
    fun `monitor meter is at the tail`() {
        assertEquals(64, Meters.slotIndex(ProtoIds.MODULE_MONITOR, Meters.Kind.L_OUT))
        assertEquals(65, Meters.slotIndex(ProtoIds.MODULE_MONITOR, Meters.Kind.R_OUT))
    }

    @Test
    fun `decode sign extends bytes`() {
        val raw = ByteArray(80)
        raw[0] = 0x00
        raw[1] = 0x80.toByte()
        raw[2] = 0xBF.toByte()
        val decoded = Meters.decodeDb(raw)
        assertEquals(0f, decoded[0], 0f)
        assertEquals(-128f, decoded[1], 0f)
        assertEquals(-65f, decoded[2], 0f)
    }

    @Test
    fun `decode tolerates short payloads`() {
        val decoded = Meters.decodeDb(ByteArray(4) { 0x01 })
        assertEquals(1f, decoded[0], 0f)
        assertEquals(Meters.SILENT_DB, decoded[79], 0f)
    }

    /**
     * Regression for the hardware capture `wire-20261018-102004.log`.
     *
     * The console sends a `MeterLevelData` *message*, so the payload begins with
     * the `0a 64` field tag and length. Decoding the payload as the array itself
     * read those two bytes as meters 1 and 2 (10 dB and 100 dB) and shifted every
     * real slot two places right.
     */
    @Test
    fun `unpack strips the protobuf envelope before decoding`() {
        // First three meter bytes of the captured frame: -78, 0, -90 dB.
        val values = ByteArray(100) { 0xA6.toByte() }
        values[0] = 0xB2.toByte() // -78
        values[1] = 0x00
        values[2] = 0xA6.toByte() // -90

        val raw = MeterLevelData.newBuilder()
            .setGainValue(ByteString.copyFrom(values))
            .build()
            .toByteArray()

        // The envelope really is on the wire.
        assertEquals(0x0A, raw[0].toInt() and 0xFF)
        assertEquals(100, raw[1].toInt() and 0xFF)
        assertEquals(102, raw.size)

        val decoded = Meters.decodeDb(Meters.unpack(raw))
        assertEquals(-78f, decoded[0], 0f)
        assertEquals(0f, decoded[1], 0f)
        assertEquals(-90f, decoded[2], 0f)
    }

    @Test
    fun `unpack keeps the silent floor for the unmapped tail`() {
        val raw = MeterLevelData.newBuilder()
            .setGainValue(ByteString.copyFrom(ByteArray(100) { 0x80.toByte() }))
            .build()
            .toByteArray()
        val decoded = Meters.decodeDb(Meters.unpack(raw))
        assertEquals(Meters.SILENT_DB, decoded[0], 0f)
        assertEquals(Meters.SILENT_DB, decoded[79], 0f)
    }

    @Test
    fun `module out averages stereo pairs`() {
        val decoded = FloatArray(80) { -100f }
        decoded[38] = -10f
        decoded[39] = -20f
        val level = Meters.moduleOutDb(decoded, 12)
        assertNotNull(level)
        assertEquals(-15f, level!!, 0.001f)
    }

    @Test
    fun `module out uses mono tap for channels one to twelve`() {
        val decoded = FloatArray(80) { -100f }
        decoded[5] = -3f
        assertEquals(-3f, Meters.moduleOutDb(decoded, 1)!!, 0.001f)
        assertNull(Meters.moduleOutDb(decoded, 99))
    }

    @Test
    fun `slot labels cover every entry`() {
        assertTrue(Meters.slots.all { it.label.isNotBlank() })
    }
}
