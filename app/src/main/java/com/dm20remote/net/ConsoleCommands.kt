package com.dm20remote.net

import com.dm20remote.proto.BusOutputModel
import com.dm20remote.proto.EqPartModel
import com.dm20remote.proto.MicInDynamicPartModel
import com.dm20remote.proto.MicInOutputPartModel
import com.dm20remote.proto.PlayCommand

/**
 * Builders for the outbound command payloads, mirroring what the original
 * `SendCommandManager` builds before calling `sendCommand(protoId, message)`.
 *
 * All fader/gain fields carry a UI *position* (0..1000), not dB.
 */
object ConsoleCommands {
    // ---- state-preserving edits -------------------------------------------
    //
    // The console applies a command as the *complete* new state of that model,
    // so a command must start from what the console last reported for the
    // channel and change only the touched field. Building a fresh model instead
    // silently resets everything else (delay, sum, LR link, pan, ...) on the
    // hardware.

    fun inputFader(base: MicInOutputPartModel, moduleId: Int, position: Float) =
        base.toBuilder().setModuleId(moduleId).setOutFaderValue(position).build()

    fun inputPan(base: MicInOutputPartModel, moduleId: Int, pan: Float) =
        base.toBuilder().setModuleId(moduleId).setPanValue(pan).build()

    fun inputMute(base: MicInOutputPartModel, moduleId: Int, mute: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsMuteEnable(mute).build()

    fun inputSolo(base: MicInOutputPartModel, moduleId: Int, solo: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsSoloEnable(solo).build()

    fun busFader(base: BusOutputModel, moduleId: Int, position: Float) =
        base.toBuilder().setModuleId(moduleId).setGainValue(position).build()

    fun busPan(base: BusOutputModel, moduleId: Int, pan: Float) =
        base.toBuilder().setModuleId(moduleId).setPanValue(pan).build()

    fun busMute(base: BusOutputModel, moduleId: Int, mute: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsMute(mute).build()

    fun busSolo(base: BusOutputModel, moduleId: Int, solo: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsSolo(solo).build()

    fun busDelayEnable(base: BusOutputModel, moduleId: Int, enabled: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsDelayEnable(enabled).build()

    // ---- PEQ (EqPartModel, OUT_MIC_EQ_PART) --------------------------------
    //
    // Frequencies travel in Hz, gains in dB and Q raw (see Units.PeqRanges), so
    // the only rescue needed is the band selector: the wire model flattens the
    // four bands into `highFreqValue`/`highMidFreqValue`/... rather than a list.

    fun eqBypass(base: EqPartModel, moduleId: Int, bypass: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsByPass(bypass).build()

    fun eqBandFrequency(base: EqPartModel, moduleId: Int, band: Int, hz: Float): EqPartModel {
        val builder = base.toBuilder().setModuleId(moduleId)
        when (band) {
            EqBands.HIGH -> builder.setHighFreqValue(hz)
            EqBands.HIGH_MID -> builder.setHighMidFreqValue(hz)
            EqBands.LOW_MID -> builder.setLowMidFreqValue(hz)
            EqBands.LOW -> builder.setLowFreqValue(hz)
        }
        return builder.build()
    }

    fun eqBandGain(base: EqPartModel, moduleId: Int, band: Int, db: Float): EqPartModel {
        val builder = base.toBuilder().setModuleId(moduleId)
        when (band) {
            EqBands.HIGH -> builder.setHighGainValue(db)
            EqBands.HIGH_MID -> builder.setHighMidGainValue(db)
            EqBands.LOW_MID -> builder.setLowMidGainValue(db)
            EqBands.LOW -> builder.setLowGainValue(db)
        }
        return builder.build()
    }

    fun eqBandQ(base: EqPartModel, moduleId: Int, band: Int, q: Float): EqPartModel {
        val builder = base.toBuilder().setModuleId(moduleId)
        when (band) {
            EqBands.HIGH -> builder.setHighQValue(q)
            EqBands.HIGH_MID -> builder.setHighMidQValue(q)
            EqBands.LOW_MID -> builder.setLowMidQValue(q)
            EqBands.LOW -> builder.setLowQValue(q)
        }
        return builder.build()
    }

    /**
     * Only the high and low bands have a shelf mode; the recovered codes differ
     * (`lowType` 1, `highType` 2), so each gets its own constant.
     */
    fun eqBandShelf(base: EqPartModel, moduleId: Int, band: Int, shelf: Boolean): EqPartModel {
        val builder = base.toBuilder().setModuleId(moduleId)
        when (band) {
            EqBands.HIGH -> builder.setHighType(if (shelf) EqBands.HIGH_SHELF else EqBands.BELL)
            EqBands.LOW -> builder.setLowType(if (shelf) EqBands.LOW_SHELF else EqBands.BELL)
        }
        return builder.build()
    }

    /** The original's "Flat" button: bells at 60/200/1000/4000 Hz, Q 3, 0 dB. */
    fun eqFlat(base: EqPartModel, moduleId: Int): EqPartModel = base.toBuilder()
        .setModuleId(moduleId)
        .setHighFreqValue(4000f).setHighGainValue(0f).setHighQValue(3f).setHighType(EqBands.BELL)
        .setHighMidFreqValue(1000f).setHighMidGainValue(0f).setHighMidQValue(3f)
        .setLowMidFreqValue(200f).setLowMidGainValue(0f).setLowMidQValue(3f)
        .setLowFreqValue(60f).setLowGainValue(0f).setLowQValue(3f).setLowType(EqBands.BELL)
        .build()

    // ---- dynamics (MicInDynamicPartModel, OUT_MIC_DYNAMIC_PART) ------------

    fun gateEnable(base: MicInDynamicPartModel, moduleId: Int, enabled: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsGateEnable(enabled).build()

    fun gateThreshold(base: MicInDynamicPartModel, moduleId: Int, db: Float) =
        base.toBuilder().setModuleId(moduleId).setGateThresholdValue(db).build()

    fun gateAttack(base: MicInDynamicPartModel, moduleId: Int, ms: Float) =
        base.toBuilder().setModuleId(moduleId).setGateAttackValue(ms).build()

    fun gateRelease(base: MicInDynamicPartModel, moduleId: Int, ms: Float) =
        base.toBuilder().setModuleId(moduleId).setGateReleaseValue(ms).build()

    fun gateHold(base: MicInDynamicPartModel, moduleId: Int, ms: Float) =
        base.toBuilder().setModuleId(moduleId).setGateHoldValue(ms).build()

    fun gateDepth(base: MicInDynamicPartModel, moduleId: Int, db: Float) =
        base.toBuilder().setModuleId(moduleId).setGateDepthValue(db).build()

    fun compressorEnable(base: MicInDynamicPartModel, moduleId: Int, enabled: Boolean) =
        base.toBuilder().setModuleId(moduleId).setIsCompressorEnable(enabled).build()

    fun compressorThreshold(base: MicInDynamicPartModel, moduleId: Int, db: Float) =
        base.toBuilder().setModuleId(moduleId).setCompressorTresholdValue(db).build()

    fun compressorAttack(base: MicInDynamicPartModel, moduleId: Int, ms: Float) =
        base.toBuilder().setModuleId(moduleId).setCompressorAttackValue(ms).build()

    fun compressorRelease(base: MicInDynamicPartModel, moduleId: Int, ms: Float) =
        base.toBuilder().setModuleId(moduleId).setCompressorReleaseValue(ms).build()

    fun compressorGain(base: MicInDynamicPartModel, moduleId: Int, db: Float) =
        base.toBuilder().setModuleId(moduleId).setCompressorGainValue(db).build()

    fun compressorRatio(base: MicInDynamicPartModel, moduleId: Int, ratio: Float) =
        base.toBuilder().setModuleId(moduleId).setCompressorRatioValue(ratio).build()

    // ---- transport / recorder (PlayCommand, OUT_RECORDER_ITEM) -------------
    //
    // The original `RecorderView` edits one `instruction` field on the last
    // `PlayCommand` the console reported and re-sends the whole message, so these
    // builders start from that base too. Play/pause/stop carry no other state: the
    // console decides the resulting position and reports it back on 191/195.

    fun transport(base: PlayCommand, instruction: Int): PlayCommand =
        base.toBuilder().setInstruction(instruction).build()

    fun transportPlayMode(base: PlayCommand, mode: Int): PlayCommand = base.toBuilder()
        .setInstruction(TransportInstruction.PLAY_MODE)
        .setPlayModeValue(mode)
        .build()

    fun transportRecord(base: PlayCommand, recording: Boolean): PlayCommand = base.toBuilder()
        .setInstruction(TransportInstruction.RECORD)
        .setIsRecord(recording)
        .build()

    /**
     * Selects a song by path and starts it. The original console has no dedicated
     * previous/next instruction; it picks the adjacent path from the list it last
     * sent (protoId 193) and sends this.
     */
    fun transportSelectSong(path: String): PlayCommand = PlayCommand.newBuilder()
        .setInstruction(TransportInstruction.SELECT_SONG)
        .setSongPath(path)
        .setCurrentPosition(0)
        .setIsPlay(true)
        .build()
}

/**
 * The ad-hoc `instruction` values the original `RecorderView` writes into the
 * outbound `PlayCommand`. Recovered from its `onClick` / `startAndPause` /
 * `selectPlay` handlers (see `docs/protocol-spec.md` §5.1); they are *not*
 * `EnmCommand` members despite the numeric overlap.
 */
object TransportInstruction {
    const val SELECT_SONG = 1
    const val STOP = 3
    const val PLAY_PAUSE = 4
    const val PLAY_MODE = 5
    const val RECORD = 6
}
