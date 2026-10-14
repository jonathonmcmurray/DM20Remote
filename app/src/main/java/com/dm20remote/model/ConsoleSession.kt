package com.dm20remote.model

import com.dm20remote.net.ConsoleClient
import com.dm20remote.net.ConsoleCommands
import com.dm20remote.net.DiscoveredConsole
import com.dm20remote.net.Discovery
import com.dm20remote.net.ProtoIds
import com.dm20remote.net.TransportInstruction
import com.dm20remote.proto.MicInInputPartModel
import com.dm20remote.proto.PlayCommand
import com.google.protobuf.MessageLite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide session object shared by the foreground service and the UI.
 * It owns the network client and folds its events into a [ConsoleStore].
 */
object ConsoleSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = ConsoleClient()
    private val store = ConsoleStore()
    private var collector: Job? = null

    val state: StateFlow<ConsoleState> get() = store.state

    val isConnected: Boolean get() = client.isConnected

    fun ensureStarted() {
        if (collector == null) {
            collector = scope.launch { client.events.collect { store.onEvent(it) } }
        }
    }

    fun connect(host: String) {
        ensureStarted()
        scope.launch { client.connect(host) }
    }

    fun disconnect() = client.close()

    fun requestInitState() = client.requestInitState()

    fun send(protoId: Int, message: MessageLite) = client.send(protoId, message)

    suspend fun discover(timeoutMs: Int = Discovery.DEFAULT_TIMEOUT_MS): List<DiscoveredConsole> =
        Discovery.discover(timeoutMs)

    // ---- input channel commands -------------------------------------------

    fun setInputFader(channel: InputChannel, position: Float) = send(
        ProtoIds.OUT_MIC_OUTPUT_PART,
        ConsoleCommands.inputFader(channel.output, channel.moduleId, position),
    )

    fun setInputPan(channel: InputChannel, pan: Float) = send(
        ProtoIds.OUT_MIC_OUTPUT_PART,
        ConsoleCommands.inputPan(channel.output, channel.moduleId, pan),
    )

    fun setInputMute(channel: InputChannel, mute: Boolean) = send(
        ProtoIds.OUT_MIC_OUTPUT_PART,
        ConsoleCommands.inputMute(channel.output, channel.moduleId, mute),
    )

    fun setInputSolo(channel: InputChannel, solo: Boolean) = send(
        ProtoIds.OUT_MIC_OUTPUT_PART,
        ConsoleCommands.inputSolo(channel.output, channel.moduleId, solo),
    )

    fun setInputPart(channel: InputChannel, transform: (MicInInputPartModel.Builder) -> Unit) {
        val builder = channel.input.toBuilder().setModuleId(channel.moduleId)
        transform(builder)
        send(ProtoIds.OUT_MIC_INPUT_PART, builder.build())
    }

    // ---- PEQ commands ------------------------------------------------------

    fun setEqBypass(channel: InputChannel, bypass: Boolean) = send(
        ProtoIds.OUT_MIC_EQ_PART,
        ConsoleCommands.eqBypass(channel.eq, channel.moduleId, bypass),
    )

    fun setEqBandFrequency(channel: InputChannel, band: Int, hz: Float) = send(
        ProtoIds.OUT_MIC_EQ_PART,
        ConsoleCommands.eqBandFrequency(channel.eq, channel.moduleId, band, hz),
    )

    fun setEqBandGain(channel: InputChannel, band: Int, db: Float) = send(
        ProtoIds.OUT_MIC_EQ_PART,
        ConsoleCommands.eqBandGain(channel.eq, channel.moduleId, band, db),
    )

    fun setEqBandQ(channel: InputChannel, band: Int, q: Float) = send(
        ProtoIds.OUT_MIC_EQ_PART,
        ConsoleCommands.eqBandQ(channel.eq, channel.moduleId, band, q),
    )

    fun setEqBandShelf(channel: InputChannel, band: Int, shelf: Boolean) = send(
        ProtoIds.OUT_MIC_EQ_PART,
        ConsoleCommands.eqBandShelf(channel.eq, channel.moduleId, band, shelf),
    )

    fun setEqFlat(channel: InputChannel) = send(
        ProtoIds.OUT_MIC_EQ_PART,
        ConsoleCommands.eqFlat(channel.eq, channel.moduleId),
    )

    // ---- dynamics commands -------------------------------------------------

    fun setGateEnable(channel: InputChannel, enabled: Boolean) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.gateEnable(channel.dynamics, channel.moduleId, enabled),
    )

    fun setGateThreshold(channel: InputChannel, db: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.gateThreshold(channel.dynamics, channel.moduleId, db),
    )

    fun setGateAttack(channel: InputChannel, ms: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.gateAttack(channel.dynamics, channel.moduleId, ms),
    )

    fun setGateRelease(channel: InputChannel, ms: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.gateRelease(channel.dynamics, channel.moduleId, ms),
    )

    fun setGateHold(channel: InputChannel, ms: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.gateHold(channel.dynamics, channel.moduleId, ms),
    )

    fun setGateDepth(channel: InputChannel, db: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.gateDepth(channel.dynamics, channel.moduleId, db),
    )

    fun setCompressorEnable(channel: InputChannel, enabled: Boolean) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.compressorEnable(channel.dynamics, channel.moduleId, enabled),
    )

    fun setCompressorThreshold(channel: InputChannel, db: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.compressorThreshold(channel.dynamics, channel.moduleId, db),
    )

    fun setCompressorAttack(channel: InputChannel, ms: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.compressorAttack(channel.dynamics, channel.moduleId, ms),
    )

    fun setCompressorRelease(channel: InputChannel, ms: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.compressorRelease(channel.dynamics, channel.moduleId, ms),
    )

    fun setCompressorGain(channel: InputChannel, db: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.compressorGain(channel.dynamics, channel.moduleId, db),
    )

    fun setCompressorRatio(channel: InputChannel, ratio: Float) = send(
        ProtoIds.OUT_MIC_DYNAMIC_PART,
        ConsoleCommands.compressorRatio(channel.dynamics, channel.moduleId, ratio),
    )

    fun setBusFader(bus: BusChannel, position: Float) = send(
        ProtoIds.OUT_BUS_OUTPUT_PART,
        ConsoleCommands.busFader(bus.output, bus.moduleId, position),
    )

    fun setBusPan(bus: BusChannel, pan: Float) = send(
        ProtoIds.OUT_BUS_OUTPUT_PART,
        ConsoleCommands.busPan(bus.output, bus.moduleId, pan),
    )

    fun setBusMute(bus: BusChannel, mute: Boolean) = send(
        ProtoIds.OUT_BUS_OUTPUT_PART,
        ConsoleCommands.busMute(bus.output, bus.moduleId, mute),
    )

    fun setBusSolo(bus: BusChannel, solo: Boolean) = send(
        ProtoIds.OUT_BUS_OUTPUT_PART,
        ConsoleCommands.busSolo(bus.output, bus.moduleId, solo),
    )

    fun setBusDelayEnable(bus: BusChannel, enabled: Boolean) = send(
        ProtoIds.OUT_BUS_OUTPUT_PART,
        ConsoleCommands.busDelayEnable(bus.output, bus.moduleId, enabled),
    )

    // ---- transport / recorder commands ------------------------------------

    /**
     * The last state the console reported. The console applies a transport command as
     * the complete new playback state, so every command is built from this base.
     */
    private val transport: PlayCommand get() = state.value.transport.toPlayCommand()

    fun transportPlayPause() = send(
        ProtoIds.OUT_RECORDER_ITEM,
        ConsoleCommands.transport(transport, TransportInstruction.PLAY_PAUSE),
    )

    fun transportStop() = send(
        ProtoIds.OUT_RECORDER_ITEM,
        ConsoleCommands.transport(transport, TransportInstruction.STOP),
    )

    fun transportRecord() = send(
        ProtoIds.OUT_RECORDER_ITEM,
        ConsoleCommands.transportRecord(transport, !state.value.transport.isRecording),
    )

    fun transportPlayMode(mode: Int) = send(
        ProtoIds.OUT_RECORDER_ITEM,
        ConsoleCommands.transportPlayMode(transport, mode),
    )

    fun transportSelectSong(path: String) = send(
        ProtoIds.OUT_RECORDER_ITEM,
        ConsoleCommands.transportSelectSong(path),
    )

    fun shutdown() {
        client.shutdown()
    }
}
