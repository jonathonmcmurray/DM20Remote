package com.dm20remote.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtoIdsNameTest {

    @Test
    fun `names known snapshot and change ids`() {
        assertEquals("IN_ALL_DATA", ProtoIds.name(1))
        assertEquals("IN_MIC_BUS_PART", ProtoIds.name(107))
        assertEquals("OUT_MIC_OUTPUT_PART", ProtoIds.name(110))
        assertEquals("OUT_RECORDER_ITEM", ProtoIds.name(192))
    }

    @Test
    fun `falls back for unknown ids`() {
        assertEquals("protoId", ProtoIds.name(9999))
    }
}
