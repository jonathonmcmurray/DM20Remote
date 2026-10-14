package com.dm20remote.model

import com.dm20remote.net.ProtoIds
import com.dm20remote.net.Units
import com.dm20remote.proto.BusOutputModel
import com.dm20remote.proto.EqPartModel
import com.dm20remote.proto.MicInBusSendModel
import com.dm20remote.proto.MicInDynamicPartModel
import com.dm20remote.proto.MicInInputPartModel
import com.dm20remote.proto.MicInOutputPartModel
import com.dm20remote.proto.PlayCommand

/** One input channel (IN1..IN12, ST1, ST2, USB, SPDIF). */
data class InputChannel(
    val moduleId: Int,
    val name: String,
    val output: MicInOutputPartModel = MicInOutputPartModel.getDefaultInstance(),
    val input: MicInInputPartModel = MicInInputPartModel.getDefaultInstance(),
    val eq: EqPartModel = EqPartModel.getDefaultInstance(),
    val dynamics: MicInDynamicPartModel = MicInDynamicPartModel.getDefaultInstance(),
    val busSend: MicInBusSendModel = MicInBusSendModel.getDefaultInstance(),
) {
    val faderPosition: Float get() = output.outFaderValue
    val pan: Float get() = output.panValue
    val mute: Boolean get() = output.isMuteEnable
    val solo: Boolean get() = output.isSoloEnable
    val gainDb: Float get() = Units.faderPositionToDb(output.outFaderValue)
}

/** One output bus (BUS1..BUS8). */
data class BusChannel(
    val moduleId: Int,
    val name: String,
    val output: BusOutputModel = BusOutputModel.getDefaultInstance(),
) {
    val gainPosition: Float get() = output.gainValue
    val pan: Float get() = output.panValue
    val mute: Boolean get() = output.isMute
    val solo: Boolean get() = output.isSolo
    val gainDb: Float get() = Units.faderPositionToDb(output.gainValue)
}

data class TransportState(
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val songPath: String = "",
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val playMode: Int = 0,
)

/** One selectable music file the console listed (protoId 193). */
data class MusicTrack(
    val name: String,
    val path: String,
)

data class ConsoleState(
    val controlConnected: Boolean = false,
    val meterConnected: Boolean = false,
    val musicConnected: Boolean = false,
    val inputs: List<InputChannel> = defaultInputs(),
    val buses: List<BusChannel> = defaultBuses(),
    val scenes: List<String> = emptyList(),
    val currentSceneGuid: String = "",
    val apkVersion: String = "",
    val dspVersion: String = "",
    val serialId: String = "",
    val transport: TransportState = TransportState(),
    val musicList: List<MusicTrack> = emptyList(),
    val meters: FloatArray = FloatArray(80),
    val meterFrames: Long = 0,
    val lastError: String = "",
) {
    val connected: Boolean get() = controlConnected

    /** Post-fader dB for an input channel, including the live meter tap. */
    fun inputMeterDb(index: Int): Float {
        val ch = inputs.getOrNull(index) ?: return Float.NEGATIVE_INFINITY
        val raw = com.dm20remote.net.Meters.moduleOutDb(meters, ch.moduleId) ?: return Float.NEGATIVE_INFINITY
        return raw + ch.gainDb
    }

    fun busMeterDb(index: Int): Float {
        val bus = buses.getOrNull(index) ?: return Float.NEGATIVE_INFINITY
        val raw = com.dm20remote.net.Meters.moduleOutDb(meters, bus.moduleId) ?: return Float.NEGATIVE_INFINITY
        return raw + bus.gainDb
    }

    companion object {
        fun defaultInputs(): List<InputChannel> = List(ProtoIds.INPUT_COUNT) { index ->
            InputChannel(moduleId = index, name = ProtoIds.defaultInputName(index))
        }

        fun defaultBuses(): List<BusChannel> = List(ProtoIds.BUS_COUNT) { index ->
            val moduleId = ProtoIds.BUS1 + index
            BusChannel(moduleId = moduleId, name = ProtoIds.defaultBusName(moduleId))
        }
    }
}

/** Cheap helpers used by the snapshot fold. */
internal fun PlayCommand.toTransport(): TransportState = TransportState(
    isPlaying = isPlay,
    isRecording = isRecord,
    songPath = songPath,
    positionMs = currentPosition,
    durationMs = songDuration,
    playMode = playModeValue,
)

/**
 * Rebuilds the last known [PlayCommand] so a transport edit can be sent as the
 * complete new state, exactly as the original recorder UI does.
 */
internal fun TransportState.toPlayCommand(): PlayCommand = PlayCommand.newBuilder()
    .setPlayModeValue(playMode)
    .setSongPath(songPath)
    .setSongDuration(durationMs)
    .setCurrentPosition(positionMs)
    .setIsPlay(isPlaying)
    .setIsRecord(isRecording)
    .build()
