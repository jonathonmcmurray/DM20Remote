package com.dm20remote.debug

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the in-memory path of [WireLog]. The object is only ever installed
 * from the Android application, so on the JVM it records to the tail buffer and
 * nothing else — which is exactly what these tests read back.
 */
class WireLogTest {

    @After
    fun tearDown() {
        WireLog.setEnabled(false)
        WireLog.clear()
    }

    @Test
    fun `records nothing while disabled`() {
        WireLog.setEnabled(false)
        WireLog.clear()
        WireLog.frame(Direction.SENT, "control", 110, byteArrayOf(1, 2, 3), "moduleId: 4")
        assertEquals("", WireLog.snapshotText())
    }

    @Test
    fun `records a frame with label hex and decoded text`() {
        WireLog.setEnabled(true)
        WireLog.clear()
        WireLog.frame(Direction.SENT, "control", 110, byteArrayOf(0x0A, 0x02), "moduleId: 4")
        val text = WireLog.snapshotText()
        assertTrue(text.contains("TX  control"))
        assertTrue(text.contains("OUT_MIC_OUTPUT_PART(110)"))
        assertTrue(text.contains("0a 02"))
        assertTrue(text.contains("moduleId: 4"))
    }

    @Test
    fun `records the lifecycle of enable and disable`() {
        WireLog.setEnabled(true)
        WireLog.clear()
        WireLog.setEnabled(false)
        assertTrue(WireLog.snapshotText().contains("wire logging stopped"))
    }

    @Test
    fun `identifies a frame without a protoId`() {
        WireLog.setEnabled(true)
        WireLog.clear()
        WireLog.frame(Direction.RECEIVED, "music", null, byteArrayOf(0x08, 0x01))
        assertTrue(WireLog.snapshotText().contains("(no protoId)"))
    }

    @Test
    fun `collapses identical meter frames`() {
        WireLog.setEnabled(true)
        WireLog.clear()
        val payload = byteArrayOf(1, 2, 3)
        val db = FloatArray(80) { -128f }

        WireLog.meter(payload, db)
        val afterFirst = WireLog.snapshotText()
        assertTrue(afterFirst.contains("meter"))

        WireLog.meter(payload, db)
        assertEquals(afterFirst, WireLog.snapshotText())

        WireLog.meter(byteArrayOf(4, 5, 6), db)
        assertTrue(WireLog.snapshotText().contains("1 unchanged frame(s) skipped"))
    }

    @Test
    fun `clear empties the tail`() {
        WireLog.setEnabled(true)
        WireLog.note("something")
        assertTrue(WireLog.snapshotText().isNotEmpty())
        WireLog.clear()
        assertEquals("", WireLog.snapshotText())
    }
}
