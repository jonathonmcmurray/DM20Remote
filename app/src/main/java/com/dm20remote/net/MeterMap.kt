package com.dm20remote.net

import com.dm20remote.proto.MeterLevelData

/**
 * Static meter slot map recovered from the original `MeterListener` bytecode.
 *
 * The console sends a single `MeterLevelData` message whose `gainValue` byte array
 * carries **100** values; the original app only ever addressed the first 80, and
 * [slots] below is that recovered map. Byte `i` (1-based meter id `i + 1`) is a
 * sign-extended dBFS value: `0x00` = 0 dBFS, `0x80` = -128 dB (silent floor).
 */
object Meters {
    enum class Kind { NG_OUT, COMP_OUT, OUT, L_OUT, R_OUT, IN, L_IN, R_IN }

    data class Slot(val moduleId: Int, val kind: Kind, val label: String)

    const val COUNT = 80

    /** Silent floor reported by the console (`0x80`). */
    const val SILENT_DB = -128f

    val slots: List<Slot> = buildList {
        // 1..36: IN1..IN12, three mono taps each (gate out, comp out, out).
        for (ch in 0..11) {
            val name = ProtoIds.defaultInputName(ch)
            add(Slot(ch, Kind.NG_OUT, name))
            add(Slot(ch, Kind.COMP_OUT, name))
            add(Slot(ch, Kind.OUT, name))
        }
        // 37..40: ST1 (dynamics + stereo out).
        add(Slot(12, Kind.NG_OUT, "ST 1"))
        add(Slot(12, Kind.COMP_OUT, "ST 1"))
        add(Slot(12, Kind.L_OUT, "ST 1"))
        add(Slot(12, Kind.R_OUT, "ST 1"))
        // 41..44: ST2.
        add(Slot(13, Kind.NG_OUT, "ST 2"))
        add(Slot(13, Kind.COMP_OUT, "ST 2"))
        add(Slot(13, Kind.L_OUT, "ST 2"))
        add(Slot(13, Kind.R_OUT, "ST 2"))
        // 45..56: IN1..IN12 inputs.
        for (ch in 0..11) add(Slot(ch, Kind.IN, ProtoIds.defaultInputName(ch)))
        // 57..58: ST1 inputs.
        add(Slot(12, Kind.L_IN, "ST 1"))
        add(Slot(12, Kind.R_IN, "ST 1"))
        // 59..60: USB (shared input/output tap).
        add(Slot(14, Kind.L_IN, "USB"))
        add(Slot(14, Kind.R_IN, "USB"))
        // 61..62: ST2 inputs.
        add(Slot(13, Kind.L_IN, "ST 2"))
        add(Slot(13, Kind.R_IN, "ST 2"))
        // 63..64: SPDIF (shared input/output tap).
        add(Slot(15, Kind.L_IN, "SPDIF"))
        add(Slot(15, Kind.R_IN, "SPDIF"))
        // 65..66: monitor output.
        add(Slot(ProtoIds.MODULE_MONITOR, Kind.L_OUT, "MONITOR"))
        add(Slot(ProtoIds.MODULE_MONITOR, Kind.R_OUT, "MONITOR"))
        // 67..68: output return (module 255).
        add(Slot(ProtoIds.MODULE_OUTPUT_RETURN, Kind.L_OUT, "RETURN"))
        add(Slot(ProtoIds.MODULE_OUTPUT_RETURN, Kind.R_OUT, "RETURN"))
        // 69..72: BUS1..BUS4 mono out.
        for (bus in 128..131) add(Slot(bus, Kind.OUT, ProtoIds.defaultBusName(bus)))
        // 73..80: BUS5..BUS8 stereo out.
        for (bus in 132..135) {
            add(Slot(bus, Kind.L_OUT, ProtoIds.defaultBusName(bus)))
            add(Slot(bus, Kind.R_OUT, ProtoIds.defaultBusName(bus)))
        }
    }.also { check(it.size == COUNT) { "meter map has ${it.size} slots, expected $COUNT" } }

    /**
     * Unwraps a meter frame's payload into the `gainValue` byte array.
     *
     * A frame payload is a protobuf *message*, not the array itself: the console
     * writes `0a <len> <bytes>`, so reading the payload as the array would consume
     * the field tag and length as the first two meter values (10 dB and 100 dB) and
     * shift every real slot two places right. Caught on hardware — see
     * `docs/protocol-spec.md` §3.3.
     */
    fun unpack(raw: ByteArray): ByteArray = MeterLevelData.parseFrom(raw).gainValue.toByteArray()

    /** Decodes the 100-byte `gainValue` array into one dBFS value per mapped slot. */
    fun decodeDb(raw: ByteArray): FloatArray {
        val out = FloatArray(COUNT) { SILENT_DB }
        val n = minOf(raw.size, COUNT)
        for (i in 0 until n) out[i] = raw[i].toFloat()
        return out
    }

    fun slotIndex(moduleId: Int, kind: Kind): Int =
        slots.indexOfFirst { it.moduleId == moduleId && it.kind == kind }

    /**
     * Post-tap output level for a module, averaging stereo pairs.
     * Returns null when the module has no mapped output meter.
     */
    fun moduleOutDb(decoded: FloatArray, moduleId: Int): Float? {
        val mono = slotIndex(moduleId, Kind.OUT)
        if (mono >= 0) return decoded[mono]
        val left = slotIndex(moduleId, Kind.L_OUT)
        val right = slotIndex(moduleId, Kind.R_OUT)
        if (left >= 0 && right >= 0) return (decoded[left] + decoded[right]) / 2f
        if (left >= 0) return decoded[left]
        return null
    }
}
