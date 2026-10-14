package com.dm20remote.net

import com.dm20remote.proto.MicInOutputPartModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProtoCodecDebugStringTest {

    @Test
    fun `decoded text is available for a wrapped message`() {
        val text = ProtoCodec.debugString(Inbound.MicOutputPart(MicInOutputPartModel.getDefaultInstance()))
        assertNotNull(text)
    }

    @Test
    fun `unknown ids have no decoded text`() {
        assertNull(ProtoCodec.debugString(Inbound.Unknown(9999, 4)))
    }
}
