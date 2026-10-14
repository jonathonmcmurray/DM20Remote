package com.dm20remote.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class FramingTest {
    @Test
    fun `varint round trips small values`() {
        for (v in intArrayOf(0, 1, 127, 128, 300, 16383, 16384, 1_000_000)) {
            val encoded = Framing.encodeVarint(v)
            val decoded = Framing.readVarint(ByteArrayInputStream(encoded))
            assertEquals("value $v", v, decoded)
        }
    }

    @Test
    fun `control frame round trips`() {
        val payload = byteArrayOf(0x08, 0x01, 0x10, 0x02)
        val buffer = java.io.ByteArrayOutputStream()
        Framing.writeControlFrame(buffer, 110, payload)

        val bytes = buffer.toByteArray()
        // 6 magic + 4 protoId + 4 size + 1 varint + 4 payload
        assertEquals(19, bytes.size)
        for (i in 0 until 6) assertEquals(Framing.MAGIC_BYTE.toByte(), bytes[i])

        val frame = Framing.readControlFrame(ByteArrayInputStream(bytes))
        assertEquals(110, frame.protoId)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun `init request is a bare fourteen byte frame`() {
        val buffer = java.io.ByteArrayOutputStream()
        Framing.writeControlFrame(buffer, ProtoIds.REQUEST_INIT_DATA, ByteArray(0))
        val bytes = buffer.toByteArray()
        assertEquals(14, bytes.size)
        // 6 magic + big-endian protoId 1 + big-endian length 0
        assertEquals(0x5A, bytes[0].toInt() and 0xFF)
        assertEquals(1, bytes[9].toInt())
        assertEquals(0, bytes[10].toInt())
        assertEquals(0, bytes[13].toInt())
    }

    @Test
    fun `empty payload round trips for a normal command`() {
        val buffer = java.io.ByteArrayOutputStream()
        Framing.writeControlFrame(buffer, ProtoIds.OUT_MIC_OUTPUT_PART, ByteArray(0))
        val frame = Framing.readControlFrame(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(ProtoIds.OUT_MIC_OUTPUT_PART, frame.protoId)
        assertEquals(0, frame.payload.size)
    }

    @Test
    fun `parser resynchronises on leading noise`() {
        val buffer = java.io.ByteArrayOutputStream()
        Framing.writeControlFrame(buffer, 1, byteArrayOf(0x2A))
        val noisy = byteArrayOf(0x00, 0x11, 0x5A, 0x5A, 0x00) + buffer.toByteArray()
        val frame = Framing.readControlFrame(ByteArrayInputStream(noisy))
        assertEquals(1, frame.protoId)
        assertArrayEquals(byteArrayOf(0x2A), frame.payload)
    }

    @Test
    fun `meter frame reads payload`() {
        val payload = ByteArray(80) { 1 }
        val buffer = java.io.ByteArrayOutputStream()
        buffer.write(Framing.magic(2))
        val header = ByteArray(8)
        Framing.writeInt32(header, 0, 0)
        Framing.writeInt32(header, 4, payload.size)
        buffer.write(header)
        buffer.write(Framing.encodeVarint(payload.size))
        buffer.write(payload)

        val read = Framing.readMeterPayload(ByteArrayInputStream(buffer.toByteArray()))
        assertArrayEquals(payload, read)
    }

    @Test(expected = EOFException::class)
    fun `truncated frame throws`() {
        Framing.readControlFrame(ByteArrayInputStream(byteArrayOf(0x5A, 0x5A)))
    }

    @Test
    fun `music frame is a bare delimited message with no magic`() {
        val payload = byteArrayOf(0x08, 0x00, 0x12, 0x03, 0x61, 0x62, 0x63)
        val buffer = java.io.ByteArrayOutputStream()
        Framing.writeMusicPayload(buffer, payload)
        val bytes = buffer.toByteArray()

        // One varint length byte + payload: no 0x5A magic, no protoId.
        assertEquals(payload.size + 1, bytes.size)
        assertEquals(payload.size, bytes[0].toInt())
        assertNotEquals(Framing.MAGIC_BYTE, bytes[0].toInt() and 0xFF)

        val read = Framing.readMusicPayload(ByteArrayInputStream(bytes))
        assertArrayEquals(payload, read)
    }

    @Test
    fun `music frames read back to back`() {
        val buffer = java.io.ByteArrayOutputStream()
        Framing.writeMusicPayload(buffer, byteArrayOf(1, 2, 3))
        Framing.writeMusicPayload(buffer, byteArrayOf(4, 5))
        val input = ByteArrayInputStream(buffer.toByteArray())
        assertArrayEquals(byteArrayOf(1, 2, 3), Framing.readMusicPayload(input))
        assertArrayEquals(byteArrayOf(4, 5), Framing.readMusicPayload(input))
    }

    @Test(expected = EOFException::class)
    fun `truncated music frame throws`() {
        // Declares 5 bytes but supplies none beyond the length prefix.
        Framing.readMusicPayload(ByteArrayInputStream(byteArrayOf(0x05)))
    }
}
