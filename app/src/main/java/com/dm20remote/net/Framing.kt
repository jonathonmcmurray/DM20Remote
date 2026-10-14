package com.dm20remote.net

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Wire framing recovered from `com.esunny.sound` 1.0.3.
 *
 * Control channel (TCP 6400), both directions:
 *
 * ```
 * [ 0x5A x6 ][ int32 protoId ][ int32 size ][ varint size ][ protobuf payload ]
 * ```
 *
 * Meter channel (TCP 6401), console -> client:
 *
 * ```
 * [ 0x5A x2 ][ int32 0 ][ int32 size ][ varint size ][ MeterLevelData ]
 * ```
 *
 * Music channel (TCP 6402), console -> client:
 *
 * ```
 * [ varint size ][ PlayCommand ]
 * ```
 *
 * Unlike the other two, the music channel has **no** magic bytes and **no** protoId:
 * its handler pipes the raw buffer straight into `PlayCommand.parseDelimitedFrom`.
 *
 * All int32s are big-endian. The `varint size` is the length prefix produced by
 * `writeDelimitedTo` and consumed by `parseDelimitedFrom`.
 */
object Framing {
    const val MAGIC_BYTE = 0x5A
    const val CONTROL_MAGIC_COUNT = 6
    const val METER_MAGIC_COUNT = 2
    const val MAX_FRAME = 16 * 1024 * 1024

    data class Frame(val protoId: Int, val payload: ByteArray)

    fun magic(count: Int): ByteArray = ByteArray(count) { MAGIC_BYTE.toByte() }

    fun encodeVarint(value: Int): ByteArray {
        require(value >= 0) { "varint must be non-negative: $value" }
        var v = value
        val out = ArrayList<Byte>(5)
        while (true) {
            if (v and 0x7F.inv() == 0) {
                out.add(v.toByte())
                break
            }
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        return out.toByteArray()
    }

    /**
     * Writes a control frame exactly as the original `CmdHandler.sendCommand` does:
     * the declared length covers the delimited varint prefix **and** the payload.
     *
     * The one exception is the init request (protoId 1 with an empty payload), which
     * the original writes as a bare 14-byte frame with no varint at all.
     */
    fun writeControlFrame(out: OutputStream, protoId: Int, payload: ByteArray) {
        if (protoId == ProtoIds.REQUEST_INIT_DATA && payload.isEmpty()) {
            writeInitRequest(out)
            return
        }
        val header = ByteArray(CONTROL_MAGIC_COUNT + 8)
        for (i in 0 until CONTROL_MAGIC_COUNT) header[i] = MAGIC_BYTE.toByte()
        val varint = encodeVarint(payload.size)
        writeInt32(header, CONTROL_MAGIC_COUNT, protoId)
        writeInt32(header, CONTROL_MAGIC_COUNT + 4, varint.size + payload.size)
        out.write(header)
        out.write(varint)
        out.write(payload)
        out.flush()
    }

    /** `[ 5A x6 ][ int32 1 ][ int32 0 ]` — no payload, no varint. */
    fun writeInitRequest(out: OutputStream) {
        val header = ByteArray(CONTROL_MAGIC_COUNT + 8)
        for (i in 0 until CONTROL_MAGIC_COUNT) header[i] = MAGIC_BYTE.toByte()
        writeInt32(header, CONTROL_MAGIC_COUNT, ProtoIds.REQUEST_INIT_DATA)
        writeInt32(header, CONTROL_MAGIC_COUNT + 4, 0)
        out.write(header)
        out.flush()
    }

    fun writeInt32(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    fun readInt32(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) throw EOFException("eof while reading int32")
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    fun readVarint(input: InputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("eof while reading varint")
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 28) throw IllegalStateException("varint too long")
        }
    }

    /** Consumes bytes until [count] consecutive [MAGIC_BYTE] bytes have been seen. */
    fun syncMagic(input: InputStream, count: Int) {
        var matched = 0
        while (matched < count) {
            val b = input.read()
            if (b < 0) throw EOFException("stream ended while syncing to magic")
            matched = if (b == MAGIC_BYTE) matched + 1 else 0
        }
    }

    fun readN(input: InputStream, size: Int): ByteArray {
        if (size < 0 || size > MAX_FRAME) throw IllegalStateException("implausible frame size: $size")
        val buf = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(buf, read, size - read)
            if (n < 0) throw EOFException("eof while reading payload")
            read += n
        }
        return buf
    }

    fun readControlFrame(input: InputStream): Frame {
        syncMagic(input, CONTROL_MAGIC_COUNT)
        val protoId = readInt32(input)
        readInt32(input) // redundant declared size
        val size = readVarint(input)
        return Frame(protoId, readN(input, size))
    }

    fun readMeterPayload(input: InputStream): ByteArray {
        syncMagic(input, METER_MAGIC_COUNT)
        readInt32(input) // reserved, expected 0
        readInt32(input) // redundant declared size
        val size = readVarint(input)
        return readN(input, size)
    }

    /**
     * Writes a bare length-delimited music message (`writeDelimitedTo`), with no
     * magic and no protoId — the shape `parseDelimitedFrom` expects on `6402`.
     */
    fun writeMusicPayload(out: OutputStream, payload: ByteArray) {
        out.write(encodeVarint(payload.size))
        out.write(payload)
        out.flush()
    }

    /**
     * Reads one bare length-delimited music message. The console's music channel
     * carries nothing but the varint length prefix and the message bytes.
     */
    fun readMusicPayload(input: InputStream): ByteArray {
        val size = readVarint(input)
        return readN(input, size)
    }
}
