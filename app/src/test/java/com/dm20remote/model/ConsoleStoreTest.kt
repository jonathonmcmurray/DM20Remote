package com.dm20remote.model

import com.dm20remote.net.ConsoleEvent
import com.dm20remote.net.Inbound
import com.dm20remote.proto.EqPartModel
import com.dm20remote.proto.MicInDynamicPartModel
import com.dm20remote.proto.MusicItem
import com.dm20remote.proto.MusicList
import com.dm20remote.proto.PlayCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The UI is echo-driven: a channel's EQ and dynamics are only adopted when the
 * console mirrors a command back on the change-event id (`103`/`105`). These
 * tests pin the inbound half of that loop.
 */
class ConsoleStoreTest {

    private val store = ConsoleStore()

    @Test
    fun `eq change events update only the addressed channel`() {
        val eq = EqPartModel.newBuilder()
            .setModuleId(3)
            .setHighGainValue(4f)
            .setLowType(1)
            .build()

        store.onEvent(ConsoleEvent.Message(Inbound.MicEqPart(eq)))

        val channel = store.state.value.inputs[3]
        assertEquals(4f, channel.eq.highGainValue, 0f)
        assertEquals(1, channel.eq.lowType)
        assertEquals(0f, store.state.value.inputs[2].eq.highGainValue, 0f)
    }

    @Test
    fun `dynamics change events update only the addressed channel`() {
        val dynamics = MicInDynamicPartModel.newBuilder()
            .setModuleId(5)
            .setIsGateEnable(true)
            .setCompressorRatioValue(6f)
            .build()

        store.onEvent(ConsoleEvent.Message(Inbound.MicDynamicPart(dynamics)))

        val channel = store.state.value.inputs[5]
        assertTrue(channel.dynamics.isGateEnable)
        assertEquals(6f, channel.dynamics.compressorRatioValue, 0f)
        assertFalse(store.state.value.inputs[4].dynamics.isGateEnable)
    }

    @Test
    fun `change events for an out of range module are ignored`() {
        val before = store.state.value.inputs.map { it.eq }

        store.onEvent(
            ConsoleEvent.Message(
                Inbound.MicEqPart(
                    EqPartModel.newBuilder().setModuleId(99).setHighGainValue(9f).build(),
                ),
            ),
        )

        assertEquals(before, store.state.value.inputs.map { it.eq })
    }

    @Test
    fun `music channel frames update the transport`() {
        val command = PlayCommand.newBuilder()
            .setSongPath("/usb1/demo-song.mp3")
            .setSongDuration(214_000)
            .setCurrentPosition(3_000)
            .setIsPlay(true)
            .build()

        store.onEvent(ConsoleEvent.Message(Inbound.MusicTime(command)))

        val transport = store.state.value.transport
        assertEquals("/usb1/demo-song.mp3", transport.songPath)
        assertEquals(214_000, transport.durationMs)
        assertEquals(3_000, transport.positionMs)
        assertTrue(transport.isPlaying)
    }

    @Test
    fun `music list change event exposes selectable tracks`() {
        val list = MusicList.newBuilder()
            .addItems(MusicItem.newBuilder().setFileName("Demo Song").setFilePathName("/usb1/demo.mp3"))
            .addItems(MusicItem.newBuilder().setFileName("Walk-in Loop").setFilePathName("/usb1/walkin.mp3"))
            .build()

        store.onEvent(ConsoleEvent.Message(Inbound.MusicList(list)))

        assertEquals(listOf("Demo Song", "Walk-in Loop"), store.state.value.musicList.map { it.name })
        assertEquals("/usb1/walkin.mp3", store.state.value.musicList[1].path)
    }

    @Test
    fun `a position-only tick does not blank the reported title`() {
        store.onEvent(
            ConsoleEvent.Message(
                Inbound.MediaState(
                    PlayCommand.newBuilder()
                        .setSongPath("/usb1/demo.mp3")
                        .setSongDuration(90_000)
                        .setIsPlay(true)
                        .build(),
                ),
            ),
        )
        // The music-channel tick carries only the advancing position.
        store.onEvent(
            ConsoleEvent.Message(
                Inbound.MediaState(
                    PlayCommand.newBuilder().setCurrentPosition(5_000).setIsPlay(true).build(),
                ),
            ),
        )

        val transport = store.state.value.transport
        assertEquals("/usb1/demo.mp3", transport.songPath)
        assertEquals(90_000, transport.durationMs)
        assertEquals(5_000, transport.positionMs)
    }
}
