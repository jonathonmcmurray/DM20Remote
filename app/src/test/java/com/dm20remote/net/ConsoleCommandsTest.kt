package com.dm20remote.net

import com.dm20remote.proto.BusOutputModel
import com.dm20remote.proto.EqPartModel
import com.dm20remote.proto.MicInDynamicPartModel
import com.dm20remote.proto.MicInOutputPartModel
import com.dm20remote.proto.PlayCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The console applies a command as the complete new state of the model, so a
 * command must never reset fields the user did not touch. These tests pin that
 * behaviour down: every helper changes exactly one field.
 */
class ConsoleCommandsTest {

    private val fullInput = MicInOutputPartModel.newBuilder()
        .setModuleId(5)
        .setPanValue(-25f)
        .setIsSoloEnable(true)
        .setIsMuteEnable(true)
        .setOutFaderValue(760f)
        .setIsLREnable(true)
        .build()

    private val fullBus = BusOutputModel.newBuilder()
        .setModuleId(129)
        .setIsSolo(true)
        .setIsMute(true)
        .setIsDelayEnable(true)
        .setDelayValue(12f)
        .setPanValue(30f)
        .setGainValue(705f)
        .setIsSum(true)
        .build()

    @Test
    fun `input fader keeps pan solo mute and LR`() {
        val updated = ConsoleCommands.inputFader(fullInput, 5, 640f)
        assertEquals(640f, updated.outFaderValue, 0f)
        assertEquals(-25f, updated.panValue, 0f)
        assertTrue(updated.isSoloEnable)
        assertTrue(updated.isMuteEnable)
        assertTrue(updated.isLREnable)
        assertEquals(5, updated.moduleId)
    }

    @Test
    fun `input mute keeps every other field`() {
        val updated = ConsoleCommands.inputMute(fullInput, 5, false)
        assertFalse(updated.isMuteEnable)
        assertEquals(760f, updated.outFaderValue, 0f)
        assertEquals(-25f, updated.panValue, 0f)
        assertTrue(updated.isSoloEnable)
        assertTrue(updated.isLREnable)
    }

    @Test
    fun `input solo keeps every other field`() {
        val updated = ConsoleCommands.inputSolo(fullInput, 5, false)
        assertFalse(updated.isSoloEnable)
        assertTrue(updated.isMuteEnable)
        assertEquals(760f, updated.outFaderValue, 0f)
    }

    @Test
    fun `input commands set moduleId even when the base model was never seen`() {
        // A channel the console has not described yet has no moduleId on the
        // wire (proto3 omits zero), so the caller's index must be written.
        val empty = MicInOutputPartModel.getDefaultInstance()
        assertEquals(12, ConsoleCommands.inputFader(empty, 12, 100f).moduleId)
        assertEquals(13, ConsoleCommands.inputMute(empty, 13, true).moduleId)
    }

    @Test
    fun `bus fader keeps delay sum pan solo and mute`() {
        val updated = ConsoleCommands.busFader(fullBus, 129, 500f)
        assertEquals(500f, updated.gainValue, 0f)
        assertEquals(30f, updated.panValue, 0f)
        assertTrue(updated.isSolo)
        assertTrue(updated.isMute)
        assertTrue(updated.isDelayEnable)
        assertEquals(12f, updated.delayValue, 0f)
        assertTrue(updated.isSum)
    }

    @Test
    fun `bus mute does not clear delay or sum`() {
        val updated = ConsoleCommands.busMute(fullBus, 129, false)
        assertFalse(updated.isMute)
        assertTrue(updated.isDelayEnable)
        assertEquals(12f, updated.delayValue, 0f)
        assertTrue(updated.isSum)
        assertEquals(705f, updated.gainValue, 0f)
    }

    @Test
    fun `bus delay toggle does not clear delay or sum`() {
        val updated = ConsoleCommands.busDelayEnable(fullBus, 129, false)
        assertFalse(updated.isDelayEnable)
        assertEquals(12f, updated.delayValue, 0f)
        assertTrue(updated.isSum)
        assertEquals(705f, updated.gainValue, 0f)
        assertEquals(30f, updated.panValue, 0f)
    }

    @Test
    fun `bus commands set moduleId from the channel index`() {
        val empty = BusOutputModel.getDefaultInstance()
        assertEquals(128, ConsoleCommands.busFader(empty, 128, 100f).moduleId)
        assertEquals(135, ConsoleCommands.busSolo(empty, 135, true).moduleId)
    }

    // ---- PEQ ---------------------------------------------------------------

    private val fullEq = EqPartModel.newBuilder()
        .setModuleId(5)
        .setIsByPass(false)
        .setHighFreqValue(4000f).setHighGainValue(2f).setHighQValue(0.8f).setHighType(EqBands.BELL)
        .setHighMidFreqValue(1000f).setHighMidGainValue(-1f).setHighMidQValue(3f)
        .setLowMidFreqValue(250f).setLowMidGainValue(3f).setLowMidQValue(2f)
        .setLowFreqValue(80f).setLowGainValue(-2f).setLowQValue(0.7f).setLowType(EqBands.LOW_SHELF)
        .build()

    @Test
    fun `eq gain changes only the chosen band`() {
        val updated = ConsoleCommands.eqBandGain(fullEq, 5, EqBands.HIGH_MID, 6f)
        assertEquals(6f, updated.highMidGainValue, 0f)
        assertEquals(2f, updated.highGainValue, 0f)
        assertEquals(3f, updated.lowMidGainValue, 0f)
        assertEquals(-2f, updated.lowGainValue, 0f)
        assertEquals(1000f, updated.highMidFreqValue, 0f)
        assertEquals(3f, updated.highMidQValue, 0f)
        assertEquals(5, updated.moduleId)
    }

    @Test
    fun `eq frequency and Q change only the chosen band`() {
        val freq = ConsoleCommands.eqBandFrequency(fullEq, 5, EqBands.LOW, 120f)
        assertEquals(120f, freq.lowFreqValue, 0f)
        assertEquals(4000f, freq.highFreqValue, 0f)
        assertEquals(-2f, freq.lowGainValue, 0f)

        val q = ConsoleCommands.eqBandQ(fullEq, 5, EqBands.HIGH, 5f)
        assertEquals(5f, q.highQValue, 0f)
        assertEquals(0.7f, q.lowQValue, 0f)
        assertEquals(0.8f, fullEq.highQValue, 0f)
    }

    @Test
    fun `eq bypass does not disturb the bands`() {
        val updated = ConsoleCommands.eqBypass(fullEq, 5, true)
        assertTrue(updated.isByPass)
        assertEquals(4000f, updated.highFreqValue, 0f)
        assertEquals(2f, updated.highGainValue, 0f)
        assertEquals(1, updated.lowType)
    }

    @Test
    fun `eq shelf uses the distinct high and low codes`() {
        val low = ConsoleCommands.eqBandShelf(fullEq, 5, EqBands.LOW, true)
        assertEquals(EqBands.LOW_SHELF, low.lowType)
        assertEquals(EqBands.BELL, low.highType)

        val high = ConsoleCommands.eqBandShelf(fullEq, 5, EqBands.HIGH, true)
        assertEquals(EqBands.HIGH_SHELF, high.highType)
        assertEquals(EqBands.LOW_SHELF, high.lowType)

        val bell = ConsoleCommands.eqBandShelf(fullEq, 5, EqBands.HIGH, false)
        assertEquals(EqBands.BELL, bell.highType)
    }

    @Test
    fun `eq flat restores the original default curve`() {
        val flat = ConsoleCommands.eqFlat(fullEq, 5)
        assertEquals(4000f, flat.highFreqValue, 0f)
        assertEquals(1000f, flat.highMidFreqValue, 0f)
        assertEquals(200f, flat.lowMidFreqValue, 0f)
        assertEquals(60f, flat.lowFreqValue, 0f)
        assertEquals(0f, flat.highGainValue, 0f)
        assertEquals(0f, flat.highMidGainValue, 0f)
        assertEquals(0f, flat.lowMidGainValue, 0f)
        assertEquals(0f, flat.lowGainValue, 0f)
        assertEquals(3f, flat.highQValue, 0f)
        assertEquals(EqBands.BELL, flat.highType)
        assertEquals(EqBands.BELL, flat.lowType)
        assertEquals(5, flat.moduleId)
    }

    @Test
    fun `eq commands set moduleId even when the base model was never seen`() {
        val empty = EqPartModel.getDefaultInstance()
        assertEquals(12, ConsoleCommands.eqBandGain(empty, 12, EqBands.HIGH, 3f).moduleId)
        assertEquals(0, ConsoleCommands.eqFlat(empty, 0).moduleId)
    }

    // ---- dynamics ----------------------------------------------------------

    private val fullDynamics = MicInDynamicPartModel.newBuilder()
        .setModuleId(7)
        .setIsGateEnable(true)
        .setGateThresholdValue(-40f)
        .setGateAttackValue(10f)
        .setGateReleaseValue(200f)
        .setGateHoldValue(100f)
        .setGateDepthValue(-60f)
        .setIsCompressorEnable(true)
        .setCompressorTresholdValue(-20f)
        .setCompressorAttackValue(10f)
        .setCompressorReleaseValue(200f)
        .setCompressorGainValue(3f)
        .setCompressorRatioValue(4f)
        .build()

    @Test
    fun `gate enable keeps the compressor and the other gate fields`() {
        val updated = ConsoleCommands.gateEnable(fullDynamics, 7, false)
        assertFalse(updated.isGateEnable)
        assertTrue(updated.isCompressorEnable)
        assertEquals(-40f, updated.gateThresholdValue, 0f)
        assertEquals(-60f, updated.gateDepthValue, 0f)
        assertEquals(4f, updated.compressorRatioValue, 0f)
        assertEquals(7, updated.moduleId)
    }

    @Test
    fun `gate depth keeps threshold attack release hold and compressor`() {
        val updated = ConsoleCommands.gateDepth(fullDynamics, 7, -24f)
        assertEquals(-24f, updated.gateDepthValue, 0f)
        assertEquals(-40f, updated.gateThresholdValue, 0f)
        assertEquals(10f, updated.gateAttackValue, 0f)
        assertEquals(200f, updated.gateReleaseValue, 0f)
        assertEquals(100f, updated.gateHoldValue, 0f)
        assertEquals(3f, updated.compressorGainValue, 0f)
    }

    @Test
    fun `compressor ratio keeps the gate and the other compressor fields`() {
        val updated = ConsoleCommands.compressorRatio(fullDynamics, 7, 8f)
        assertEquals(8f, updated.compressorRatioValue, 0f)
        assertEquals(-20f, updated.compressorTresholdValue, 0f)
        assertEquals(200f, updated.compressorReleaseValue, 0f)
        assertTrue(updated.isGateEnable)
        assertEquals(-40f, updated.gateThresholdValue, 0f)
    }

    @Test
    fun `dynamics commands set moduleId from the channel index`() {
        val empty = MicInDynamicPartModel.getDefaultInstance()
        assertEquals(12, ConsoleCommands.gateThreshold(empty, 12, -40f).moduleId)
        assertEquals(15, ConsoleCommands.compressorEnable(empty, 15, true).moduleId)
    }

    // ---- transport / recorder ----------------------------------------------

    private val fullTransport = PlayCommand.newBuilder()
        .setPlayModeValue(2)
        .setSongPath("/usb1/walkin.mp3")
        .setSongDuration(180_000)
        .setCurrentPosition(30_000)
        .setIsPlay(true)
        .setIsRecord(true)
        .build()

    @Test
    fun `transport stop keeps the playback state and only sets the instruction`() {
        val stop = ConsoleCommands.transport(fullTransport, TransportInstruction.STOP)
        assertEquals(TransportInstruction.STOP, stop.instruction)
        assertEquals("/usb1/walkin.mp3", stop.songPath)
        assertEquals(180_000, stop.songDuration)
        assertEquals(30_000, stop.currentPosition)
        assertEquals(2, stop.playModeValue)
        assertTrue(stop.isPlay)
        assertTrue(stop.isRecord)
    }

    @Test
    fun `transport play mode keeps the song and sets the new mode`() {
        val updated = ConsoleCommands.transportPlayMode(fullTransport, 4)
        assertEquals(TransportInstruction.PLAY_MODE, updated.instruction)
        assertEquals(4, updated.playModeValue)
        assertEquals("/usb1/walkin.mp3", updated.songPath)
        assertTrue(updated.isPlay)
    }

    @Test
    fun `transport record toggles only the record flag`() {
        val updated = ConsoleCommands.transportRecord(fullTransport, false)
        assertEquals(TransportInstruction.RECORD, updated.instruction)
        assertFalse(updated.isRecord)
        assertTrue(updated.isPlay)
        assertEquals(180_000, updated.songDuration)
    }

    @Test
    fun `transport select song rewinds to zero and starts playing`() {
        val selected = ConsoleCommands.transportSelectSong("/usb1/check.mp3")
        assertEquals(TransportInstruction.SELECT_SONG, selected.instruction)
        assertEquals("/usb1/check.mp3", selected.songPath)
        assertEquals(0, selected.currentPosition)
        assertTrue(selected.isPlay)
        assertFalse(selected.isRecord)
    }
}
