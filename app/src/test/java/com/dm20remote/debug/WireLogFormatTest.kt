package com.dm20remote.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireLogFormatTest {

    @Test
    fun `hex line renders offset hex and ascii`() {
        val line = WireLogFormat.hexLine(0, byteArrayOf(0x5A, 0x00, 0x41), 3)
        assertEquals("0000  5a 00 41  |" + "Z.A".padEnd(16) + "|", line)
    }

    @Test
    fun `hex line pads a partial final row`() {
        val line = WireLogFormat.hexLine(16, ByteArray(20) { 0x01 }, 20)
        assertEquals("0010  01" + " 01".repeat(3) + "  |" + ".".repeat(4) + " ".repeat(12) + "|", line)
    }

    @Test
    fun `empty payload is still reported`() {
        assertEquals(listOf("    (empty payload)"), WireLogFormat.hexDump(ByteArray(0)))
    }

    @Test
    fun `hex dump emits one line per sixteen bytes`() {
        val dump = WireLogFormat.hexDump(ByteArray(33) { 0x2A })
        assertEquals(3, dump.size)
        assertTrue(dump[0].startsWith("    0000  2a"))
        assertTrue(dump[1].startsWith("    0010  "))
        assertTrue(dump[2].startsWith("    0020  "))
    }

    @Test
    fun `hex dump reports truncation`() {
        val dump = WireLogFormat.hexDump(ByteArray(20) { 1 }, maxBytes = 16)
        assertEquals(2, dump.size)
        assertTrue(dump.last().contains("4 more byte(s) not dumped"))
    }

    @Test
    fun `frame line carries direction channel label and size`() {
        val line = WireLogFormat.frameLine("T", "TX", "control", "OUT_MIC_OUTPUT_PART(110)", 5)
        assertEquals("T  TX  control  OUT_MIC_OUTPUT_PART(110)  5 B", line)
    }

    @Test
    fun `note line is flagged`() {
        assertEquals("T  --  connecting", WireLogFormat.noteLine("T", "connecting"))
    }

    @Test
    fun `indent prefixes every decoded line`() {
        assertEquals(listOf("    moduleId: 4", "    pan: 0"), WireLogFormat.indent("moduleId: 4\npan: 0"))
    }

    @Test
    fun `meter line prints silent slots as dashes`() {
        assertEquals("    dB 0 --- -40", WireLogFormat.meterLine(floatArrayOf(0f, -128f, -40f)))
    }
}
