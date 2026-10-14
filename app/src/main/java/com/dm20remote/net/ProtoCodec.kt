package com.dm20remote.net

import com.dm20remote.proto.BusInputModel
import com.dm20remote.proto.BusOutputModel
import com.dm20remote.proto.EqPartModel
import com.dm20remote.proto.MicInBusModel
import com.dm20remote.proto.MicInDynamicPartModel
import com.dm20remote.proto.MicInInputPartModel
import com.dm20remote.proto.MicInOutputPartModel
import com.dm20remote.proto.MusicList as MusicListProto
import com.dm20remote.proto.PlayCommand
import com.dm20remote.proto.SKDMCData
import com.dm20remote.proto.SceneModel
import com.dm20remote.proto.SetupInfoModel
import com.dm20remote.proto.SetupMeterModel
import com.dm20remote.proto.SetupMonitorModel

/** A decoded console -> client message, keyed by the recovered protoId. */
sealed interface Inbound {
    /** protoId 1 — the full console state snapshot. */
    data class Snapshot(val data: SKDMCData) : Inbound

    data class MicInputPart(val model: MicInInputPartModel) : Inbound
    data class MicEqPart(val model: EqPartModel) : Inbound
    data class MicDynamicPart(val model: MicInDynamicPartModel) : Inbound
    data class MicBusPart(val model: MicInBusModel) : Inbound
    data class MicOutputPart(val model: MicInOutputPartModel) : Inbound
    data class BusInputPart(val model: BusInputModel) : Inbound
    data class BusOutputPart(val model: BusOutputModel) : Inbound
    data class SetupInfoPart(val model: SetupInfoModel) : Inbound
    data class SetupMonitorPart(val model: SetupMonitorModel) : Inbound
    data class SetupMeterPart(val model: SetupMeterModel) : Inbound

    /** protoId 191 / 195 — transport and recorder state. */
    data class MediaState(val model: PlayCommand) : Inbound

    /** protoId 193 — the selectable music files on the console. */
    data class MusicList(val model: MusicListProto) : Inbound

    /**
     * Music channel (`6402`) — a bare delimited [PlayCommand] with no protoId and no
     * magic. Carries the advancing playback position; the original app discarded it.
     */
    data class MusicTime(val model: PlayCommand) : Inbound

    /** protoId 141 — scene data payload. */
    data class Scene(val model: SceneModel) : Inbound

    data class Unknown(val protoId: Int, val size: Int) : Inbound
}

/** Decodes a raw control-channel payload using the recovered protoId table. */
object ProtoCodec {
    fun decode(protoId: Int, payload: ByteArray): Inbound = try {
        when (protoId) {
            ProtoIds.IN_ALL_DATA -> Inbound.Snapshot(SKDMCData.parseFrom(payload))
            ProtoIds.IN_MIC_INPUT_PART -> Inbound.MicInputPart(MicInInputPartModel.parseFrom(payload))
            ProtoIds.IN_MIC_EQ_PART -> Inbound.MicEqPart(EqPartModel.parseFrom(payload))
            ProtoIds.IN_MIC_DYNAMIC_PART -> Inbound.MicDynamicPart(MicInDynamicPartModel.parseFrom(payload))
            ProtoIds.IN_MIC_BUS_PART -> Inbound.MicBusPart(MicInBusModel.parseFrom(payload))
            ProtoIds.IN_MIC_OUTPUT_PART -> Inbound.MicOutputPart(MicInOutputPartModel.parseFrom(payload))
            ProtoIds.IN_BUS_INPUT_PART -> Inbound.BusInputPart(BusInputModel.parseFrom(payload))
            ProtoIds.IN_BUS_OUTPUT_PART -> Inbound.BusOutputPart(BusOutputModel.parseFrom(payload))
            ProtoIds.IN_SETUP_INFO_PART -> Inbound.SetupInfoPart(SetupInfoModel.parseFrom(payload))
            ProtoIds.IN_SETUP_MONITOR_PART -> Inbound.SetupMonitorPart(SetupMonitorModel.parseFrom(payload))
            ProtoIds.IN_SETUP_METER_PART -> Inbound.SetupMeterPart(SetupMeterModel.parseFrom(payload))
            ProtoIds.IN_MEDIA_CHANGED, ProtoIds.IN_MUSIC_STATE -> Inbound.MediaState(PlayCommand.parseFrom(payload))
            ProtoIds.IN_MUSIC_LIST -> Inbound.MusicList(MusicListProto.parseFrom(payload))
            ProtoIds.IN_SCENE_SELECT -> Inbound.Scene(SceneModel.parseFrom(payload))
            else -> Inbound.Unknown(protoId, payload.size)
        }
    } catch (_: Exception) {
        Inbound.Unknown(protoId, payload.size)
    }

    /**
     * Decodes one music-channel payload. `6402` has no protoId, so the payload is
     * parsed directly as a [PlayCommand]; a malformed frame is reported as
     * [Inbound.Unknown] rather than thrown away silently.
     */
    fun decodeMusic(payload: ByteArray): Inbound = try {
        Inbound.MusicTime(PlayCommand.parseFrom(payload))
    } catch (_: Exception) {
        Inbound.Unknown(ProtoIds.MUSIC_CHANNEL, payload.size)
    }

    /**
     * The decoded message as protobuf text, for the wire log. Returns null for
     * payloads that carry no message body, and never throws.
     */
    fun debugString(inbound: Inbound): String? = runCatching {
        when (inbound) {
            is Inbound.Snapshot -> inbound.data.toString()
            is Inbound.MicInputPart -> inbound.model.toString()
            is Inbound.MicEqPart -> inbound.model.toString()
            is Inbound.MicDynamicPart -> inbound.model.toString()
            is Inbound.MicBusPart -> inbound.model.toString()
            is Inbound.MicOutputPart -> inbound.model.toString()
            is Inbound.BusInputPart -> inbound.model.toString()
            is Inbound.BusOutputPart -> inbound.model.toString()
            is Inbound.SetupInfoPart -> inbound.model.toString()
            is Inbound.SetupMonitorPart -> inbound.model.toString()
            is Inbound.SetupMeterPart -> inbound.model.toString()
            is Inbound.MediaState -> inbound.model.toString()
            is Inbound.MusicList -> inbound.model.toString()
            is Inbound.MusicTime -> inbound.model.toString()
            is Inbound.Scene -> inbound.model.toString()
            is Inbound.Unknown -> null
        }
    }.getOrNull()
}
