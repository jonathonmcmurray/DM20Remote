package com.dm20remote.debug

/**
 * Pure text formatting for [WireLog]. Kept free of Android and IO so the exact
 * shape of a log line can be unit-tested directly.
 */
object WireLogFormat {
    const val BYTES_PER_LINE = 16

    private const val HEX = "0123456789abcdef"
    private const val INDENT = "    "
    private const val GAP = "  "

    /**
     * One canonical `offset  hex  |ascii|` line over `[offset, end)`, padded to a
     * fixed width so columns line up in a monospaced viewer.
     */
    fun hexLine(offset: Int, bytes: ByteArray, end: Int): String {
        val limit = minOf(end, bytes.size)
        val hex = StringBuilder(BYTES_PER_LINE * 3)
        val ascii = StringBuilder(BYTES_PER_LINE)
        for (i in 0 until BYTES_PER_LINE) {
            val index = offset + i
            if (index < limit) {
                val b = bytes[index].toInt() and 0xFF
                hex.append(HEX[b ushr 4]).append(HEX[b and 0x0F]).append(' ')
                ascii.append(if (b in 0x20..0x7E) b.toChar() else '.')
            } else {
                hex.append("   ")
                ascii.append(' ')
            }
        }
        return offset.toString(16).padStart(4, '0') + GAP +
            hex.toString().trimEnd() + "  |" + ascii + "|"
    }

    /** A full hex + ASCII dump, one line per [BYTES_PER_LINE] bytes. */
    fun hexDump(bytes: ByteArray, maxBytes: Int = Int.MAX_VALUE): List<String> {
        if (bytes.isEmpty()) return listOf(INDENT + "(empty payload)")
        val shown = minOf(bytes.size, maxBytes)
        val lines = ArrayList<String>((shown + BYTES_PER_LINE - 1) / BYTES_PER_LINE + 1)
        var offset = 0
        while (offset < shown) {
            val end = minOf(offset + BYTES_PER_LINE, shown)
            lines += INDENT + hexLine(offset, bytes, end)
            offset = end
        }
        if (shown < bytes.size) lines += INDENT + "... ${bytes.size - shown} more byte(s) not dumped"
        return lines
    }

    /** The single-line prefix of a frame: `time  TX/RX  channel  LABEL(id)  N B`. */
    fun frameLine(timestamp: String, tag: String, channel: String, label: String, size: Int): String =
        "$timestamp  $tag  $channel  $label  $size B"

    fun noteLine(timestamp: String, text: String): String = "$timestamp  --  $text"

    /** Indents a decoded protobuf text block so it lines up under the dump. */
    fun indent(text: String, prefix: String = INDENT): List<String> =
        text.trim().lines().map { if (it.isBlank()) prefix.trimEnd() else prefix + it }

    /** Compact dBFS readout for one 80-slot meter frame (`-128` prints as `---`). */
    fun meterLine(db: FloatArray): String =
        INDENT + "dB " + db.joinToString(" ") { v -> if (v <= -128f) "---" else "%.0f".format(v) }
}
