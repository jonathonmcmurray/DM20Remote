package com.dm20remote.debug

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.dm20remote.BuildConfig
import com.dm20remote.net.ProtoIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Direction tag printed on every log line. */
enum class Direction(val tag: String) {
    SENT("TX"),
    RECEIVED("RX"),
    NOTE("--"),
}

/**
 * Opt-in capture of every byte the app sends to and receives from a console,
 * written to a text file so a real-hardware session (issue #5) can be inspected
 * without `adb logcat`.
 *
 * The object is process-wide and self-contained: it owns its file and a daemon
 * writer thread, keeps a bounded in-memory tail for the on-screen viewer, and
 * formats through the pure helpers in [WireLogFormat]. It is a no-op until
 * [setEnabled] turns it on, and without [install] it degrades to memory only
 * (which is what the unit tests exercise).
 */
object WireLog {
    private const val PREFS = "wire_log"
    private const val KEY_ENABLED = "enabled"
    private const val DIR_NAME = "logs"
    private const val MAX_BYTES = 16L * 1024 * 1024
    private const val TAIL_LIMIT = 500
    private const val PUBLISH_INTERVAL_MS = 250L

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _tail = MutableStateFlow<List<String>>(emptyList())

    /** Bounded recent lines, for the on-screen viewer. */
    val tail: StateFlow<List<String>> = _tail.asStateFlow()

    /** Guards [ring], [queue] and [sink]. */
    private val lock = Any()

    private val ring = ArrayDeque<String>()
    private val queue = ArrayDeque<String>()
    private var sink: Sink? = null
    private var pump: Thread? = null

    @Volatile private var running = false
    @Volatile private var context: Context? = null
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var lastPublish = 0L

    private var lastMeterPayload: ByteArray? = null
    private var skippedMeterFrames = 0

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Called once from the application. Safe to call again. */
    fun install(context: Context) {
        this.context = context.applicationContext
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        startPump()
        if (prefs?.getBoolean(KEY_ENABLED, false) == true) enable()
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) enable() else disable()
    }

    // ---- recording --------------------------------------------------------

    /** Records one complete wire frame, with an optional decoded text block. */
    fun frame(
        direction: Direction,
        channel: String,
        protoId: Int?,
        payload: ByteArray,
        decoded: String? = null,
    ) {
        if (!_enabled.value) return
        val label = if (protoId == null) "(no protoId)" else "${ProtoIds.name(protoId)}($protoId)"
        val lines = ArrayList<String>(8)
        lines += WireLogFormat.frameLine(now(), direction.tag, channel, label, payload.size)
        lines += WireLogFormat.hexDump(payload)
        if (!decoded.isNullOrBlank()) lines += WireLogFormat.indent(decoded)
        enqueue(lines)
    }

    /** A free-form lifecycle line (connect attempts, channel closes, …). */
    fun note(text: String) {
        if (!_enabled.value) return
        enqueue(listOf(WireLogFormat.noteLine(now(), text)))
    }

    /**
     * One meter frame. Identical consecutive payloads are collapsed into a
     * "N unchanged frame(s) skipped" marker so an idle console does not flood the
     * file, while a changing frame is always recorded in full.
     */
    fun meter(payload: ByteArray, db: FloatArray) {
        if (!_enabled.value) return
        if (lastMeterPayload?.contentEquals(payload) == true) {
            skippedMeterFrames++
            return
        }
        val lines = ArrayList<String>(8)
        if (skippedMeterFrames > 0) {
            lines += WireLogFormat.noteLine(now(), "meter: $skippedMeterFrames unchanged frame(s) skipped")
            skippedMeterFrames = 0
        }
        lines += WireLogFormat.frameLine(now(), Direction.RECEIVED.tag, "meter", "MeterLevelData", payload.size)
        lines += WireLogFormat.hexDump(payload)
        lines += WireLogFormat.meterLine(db)
        lastMeterPayload = payload.copyOf()
        enqueue(lines)
    }

    // ---- control ----------------------------------------------------------

    private fun enable() {
        if (_enabled.value) return
        openSink()
        _enabled.value = true
        prefs?.edit()?.putBoolean(KEY_ENABLED, true)?.apply()
        startPump()
        headerLines().forEach { enqueue(listOf(it)) }
        enqueue(listOf(WireLogFormat.noteLine(now(), "wire logging started -> ${currentFileName() ?: "memory only"}")))
        publish(force = true)
    }

    private fun disable() {
        if (!_enabled.value) return
        enqueue(listOf(WireLogFormat.noteLine(now(), "wire logging stopped")))
        _enabled.value = false
        prefs?.edit()?.putBoolean(KEY_ENABLED, false)?.apply()
        // Keep the sink open so the file can still be shared/copied afterwards.
        flush()
    }

    /** Clears the on-screen tail only; bytes already queued still reach the file. */
    fun clear() {
        synchronized(lock) { ring.clear() }
        _tail.value = emptyList()
    }

    /** Writes everything queued so far and publishes the tail. Safe from any thread. */
    fun flush() {
        drainToSink()
        publish(force = true)
    }

    fun currentFile(): File? = synchronized(lock) { sink?.file }

    fun currentFileName(): String? = currentFile()?.name

    fun listFiles(): List<File> =
        logDir()?.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** The current in-memory tail joined by newlines. Deterministic; used by tests. */
    fun snapshotText(): String = synchronized(lock) { ring.joinToString("\n") }

    // ---- internals --------------------------------------------------------

    private fun headerLines(): List<String> {
        val lines = ArrayList<String>(4)
        lines += WireLogFormat.noteLine(now(), "DM20 Remote ${BuildConfig.VERSION_NAME} wire log")
        if (context == null) {
            lines += WireLogFormat.noteLine(now(), "no app context: file output disabled")
        } else {
            lines += WireLogFormat.noteLine(
                now(),
                "device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
            lines += WireLogFormat.noteLine(now(), "file: ${currentFile()?.absolutePath ?: "-"}")
        }
        return lines
    }

    private fun logDir(): File? {
        val ctx = context ?: return null
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, DIR_NAME).apply { mkdirs() }
    }

    private fun openSink() {
        lastMeterPayload = null
        skippedMeterFrames = 0
        val dir = logDir() ?: return
        synchronized(lock) {
            sink?.close()
            sink = null
            val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            runCatching { sink = Sink(File(dir, "wire-$fileStamp.log")) }
        }
    }

    private fun enqueue(lines: List<String>) {
        if (lines.isEmpty()) return
        synchronized(lock) {
            for (line in lines) {
                ring.addLast(line)
                while (ring.size > TAIL_LIMIT) ring.removeFirst()
                queue.addLast(line)
            }
        }
    }

    private fun drainToSink() {
        synchronized(lock) {
            if (queue.isEmpty()) return
            val target = sink
            while (queue.isNotEmpty()) {
                val line = queue.removeFirst()
                target?.write(line)
            }
            target?.flush()
        }
    }

    private fun startPump() {
        if (pump != null) return
        running = true
        pump = Thread(::pumpLoop, "dm20-wirelog").apply {
            isDaemon = true
            start()
        }
    }

    private fun pumpLoop() {
        while (running) {
            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) {
                return
            }
            drainToSink()
            publish(force = false)
        }
    }

    private fun publish(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPublish < PUBLISH_INTERVAL_MS) return
        lastPublish = now
        _tail.value = synchronized(lock) { ring.toList() }
    }

    /** Append-only UTF-8 file with a hard size cap. */
    private class Sink(val file: File) {
        private val out = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), Charsets.UTF_8))
        private var bytes = 0L
        private var capped = false

        fun write(line: String) {
            if (capped) return
            out.write(line)
            out.write("\n")
            bytes += line.length + 1
            if (bytes >= MAX_BYTES) {
                out.write("... log reached $MAX_BYTES bytes; further lines are dropped ...\n")
                capped = true
            }
        }

        fun flush() {
            runCatching { out.flush() }
        }

        fun close() {
            runCatching {
                out.flush()
                out.close()
            }
        }
    }

    private fun now(): String = synchronized(stamp) { stamp.format(Date()) }
}
