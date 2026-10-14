package com.dm20remote.net

import com.google.protobuf.MessageLite
import android.util.Log
import com.dm20remote.debug.Direction
import com.dm20remote.debug.WireLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import java.net.Socket

sealed interface ConsoleEvent {
    data class Connection(
        val control: Boolean,
        val meter: Boolean,
        val music: Boolean,
    ) : ConsoleEvent

    data class Message(val inbound: Inbound) : ConsoleEvent

    data class Meters(val db: FloatArray) : ConsoleEvent {
        override fun equals(other: Any?): Boolean = other is Meters && db.contentEquals(other.db)
        override fun hashCode(): Int = db.contentHashCode()
    }

    data class Failure(val message: String) : ConsoleEvent
}

/**
 * Maintains the three TCP connections to a DM20 console (control 6400,
 * meter 6401, music 6402) and exposes decoded traffic as a flow of events.
 */
class ConsoleClient(
    private val connectTimeoutMs: Int = 4000,
    private val controlPort: Int = Discovery.CONTROL_PORT,
    private val meterPort: Int = Discovery.METER_PORT,
    private val musicPort: Int = Discovery.MUSIC_PORT,
) {
    private val TAG = "ConsoleClient"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableListOf<Job>()

    /**
     * All outbound frames are serialised through one dedicated thread. Writes
     * happen on a socket, so they must never run on the main thread, and a
     * single writer keeps commands in the order the user issued them.
     */
    private val writer: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "dm20-control-writer").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    private val writeScope = CoroutineScope(SupervisorJob() + writer)

    private val _events = MutableSharedFlow<ConsoleEvent>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ConsoleEvent> = _events.asSharedFlow()

    private var control: Socket? = null
    private var meter: Socket? = null
    private var music: Socket? = null
    private var controlOut: OutputStream? = null
    private val writeLock = Any()

    val isConnected: Boolean get() = control?.isConnected == true

    suspend fun connect(host: String) = withContext(Dispatchers.IO) {
        close()
        WireLog.note("connect $host (control:$controlPort meter:$meterPort music:$musicPort)")

        val controlSocket = try {
            open(host, controlPort)
        } catch (e: Exception) {
            WireLog.note("control connect failed: ${e.javaClass.simpleName}: ${e.message}")
            _events.tryEmit(ConsoleEvent.Failure("Control connection failed: ${e.message}"))
            _events.tryEmit(ConsoleEvent.Connection(false, false, false))
            return@withContext
        }
        WireLog.note("control channel connected to $host:$controlPort")

        control = controlSocket
        val out = BufferedOutputStream(controlSocket.getOutputStream())
        controlOut = out
        try {
            withContext(writer) { synchronized(writeLock) { Framing.writeInitRequest(out) } }
            WireLog.frame(Direction.SENT, "control", ProtoIds.REQUEST_INIT_DATA, ByteArray(0), "init request")
        } catch (e: Exception) {
            WireLog.note("init request failed: ${e.message}")
            _events.tryEmit(ConsoleEvent.Failure("Init request failed: ${e.message}"))
            close()
            return@withContext
        }
        jobs += scope.launch { readControl(BufferedInputStream(controlSocket.getInputStream())) }
        _events.tryEmit(ConsoleEvent.Connection(control = true, meter = false, music = false))

        openBestEffort(host, meterPort)?.let { socket ->
            meter = socket
            WireLog.note("meter channel connected to $host:$meterPort")
            jobs += scope.launch { readMeters(BufferedInputStream(socket.getInputStream())) }
        } ?: WireLog.note("meter channel unavailable ($host:$meterPort)")
        openBestEffort(host, musicPort)?.let { socket ->
            music = socket
            WireLog.note("music channel connected to $host:$musicPort")
            jobs += scope.launch { readMusic(BufferedInputStream(socket.getInputStream())) }
        } ?: WireLog.note("music channel unavailable ($host:$musicPort)")

        _events.tryEmit(ConsoleEvent.Connection(control = true, meter = meter != null, music = music != null))
    }

    /** Sends a control command using the original framing. */
    fun send(protoId: Int, message: MessageLite) {
        val payload = try {
            message.toByteArray()
        } catch (e: Exception) {
            _events.tryEmit(ConsoleEvent.Failure("Encode failed (protoId $protoId): ${e.message}"))
            return
        }
        WireLog.frame(Direction.SENT, "control", protoId, payload, runCatching { message.toString() }.getOrNull())
        writeScope.launch { writeFrame(protoId, payload) }
    }

    fun requestInitState() {
        writeScope.launch { writeInit() }
    }

    private fun writeFrame(protoId: Int, payload: ByteArray) {
        val out = controlOut
        if (out == null) {
            Log.w(TAG, "send(protoId=$protoId) ignored: control channel not open")
            WireLog.note("send ignored (protoId $protoId): control channel not open")
            _events.tryEmit(ConsoleEvent.Failure("Send ignored: control channel not open"))
            return
        }
        try {
            synchronized(writeLock) { Framing.writeControlFrame(out, protoId, payload) }
        } catch (e: Exception) {
            Log.w(TAG, "send(protoId=$protoId) failed", e)
            WireLog.note("send failed (protoId $protoId): ${e.message}")
            _events.tryEmit(ConsoleEvent.Failure("Send failed (protoId $protoId): ${e.message}"))
        }
    }

    private fun writeInit() {
        val out = controlOut
        if (out == null) {
            Log.w(TAG, "init request ignored: control channel not open")
            WireLog.note("init request ignored: control channel not open")
            return
        }
        try {
            synchronized(writeLock) { Framing.writeInitRequest(out) }
            WireLog.frame(Direction.SENT, "control", ProtoIds.REQUEST_INIT_DATA, ByteArray(0), "init request")
        } catch (e: Exception) {
            Log.w(TAG, "init request failed", e)
            WireLog.note("init request failed: ${e.message}")
            _events.tryEmit(ConsoleEvent.Failure("Init request failed: ${e.message}"))
        }
    }

    fun close() {
        WireLog.note("disconnected")
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { control?.close() }
        runCatching { meter?.close() }
        runCatching { music?.close() }
        control = null
        meter = null
        music = null
        controlOut = null
        _events.tryEmit(ConsoleEvent.Connection(false, false, false))
    }

    fun shutdown() {
        close()
        scope.cancel()
        writeScope.cancel()
        writer.close()
    }

    private fun open(host: String, port: Int): Socket {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        return socket
    }

    private fun openBestEffort(host: String, port: Int): Socket? = try {
        open(host, port)
    } catch (_: Exception) {
        null
    }

    private suspend fun readControl(input: InputStream) {
        try {
            while (scope.isActive) {
                val frame = Framing.readControlFrame(input)
                val inbound = ProtoCodec.decode(frame.protoId, frame.payload)
                WireLog.frame(Direction.RECEIVED, "control", frame.protoId, frame.payload, ProtoCodec.debugString(inbound))
                _events.tryEmit(ConsoleEvent.Message(inbound))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onChannelClosed("control", e)
        }
    }

    private suspend fun readMeters(input: InputStream) {
        try {
            while (scope.isActive) {
                val payload = Framing.readMeterPayload(input)
                val db = Meters.decodeDb(payload)
                WireLog.meter(payload, db)
                _events.tryEmit(ConsoleEvent.Meters(db))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onChannelClosed("meter", e)
        }
    }

    private suspend fun readMusic(input: InputStream) {
        try {
            while (scope.isActive) {
                val payload = Framing.readMusicPayload(input)
                val inbound = ProtoCodec.decodeMusic(payload)
                WireLog.frame(Direction.RECEIVED, "music", null, payload, ProtoCodec.debugString(inbound))
                _events.tryEmit(ConsoleEvent.Message(inbound))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onChannelClosed("music", e)
        }
    }

    private fun onChannelClosed(channel: String, e: Exception) {
        WireLog.note("$channel channel closed: ${e.javaClass.simpleName}: ${e.message}")
        _events.tryEmit(ConsoleEvent.Failure("$channel channel closed: ${e.message}"))
        _events.tryEmit(
            ConsoleEvent.Connection(
                control = channel != "control" && control?.isConnected == true,
                meter = channel != "meter" && meter?.isConnected == true,
                music = channel != "music" && music?.isConnected == true,
            ),
        )
    }
}
