package com.dm20remote.net

import com.dm20remote.proto.MusicItem
import com.dm20remote.proto.MusicList
import com.dm20remote.proto.PlayCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The music channel (`6402`) carries no protoId, so its payload is parsed
 * directly as a [PlayCommand] rather than looked up in the id table.
 */
class ProtoCodecTest {

    @Test
    fun `music payload decodes to a bare delimited PlayCommand`() {
        val command = PlayCommand.newBuilder()
            .setSongPath("/usb1/demo-song.mp3")
            .setSongDuration(214_000)
            .setCurrentPosition(42_000)
            .setIsPlay(true)
            .setIsRecord(false)
            .setPlayModeValue(2)
            .build()

        val frame = ByteArrayOutputStream()
        Framing.writeMusicPayload(frame, command.toByteArray())
        val payload = Framing.readMusicPayload(ByteArrayInputStream(frame.toByteArray()))

        val inbound = ProtoCodec.decodeMusic(payload)
        assertTrue(inbound is Inbound.MusicTime)
        val model = (inbound as Inbound.MusicTime).model
        assertEquals("/usb1/demo-song.mp3", model.songPath)
        assertEquals(214_000, model.songDuration)
        assertEquals(42_000, model.currentPosition)
        assertTrue(model.isPlay)
        assertEquals(2, model.playModeValue)
    }

    @Test
    fun `a control-framed buffer does not decode as a music payload`() {
        // Leading 0x5A magic is not a valid protobuf tag for a top-level field,
        // so the music decoder rejects it instead of inventing a PlayCommand.
        val inbound = ProtoCodec.decodeMusic(Framing.magic(6))
        assertTrue(inbound is Inbound.Unknown)
        assertEquals(ProtoIds.MUSIC_CHANNEL, (inbound as Inbound.Unknown).protoId)
    }

    @Test
    fun `protoId 193 decodes the console music list`() {
        val list = MusicList.newBuilder()
            .addItems(MusicItem.newBuilder().setFileName("Demo Song").setFilePathName("/usb1/demo.mp3"))
            .addItems(MusicItem.newBuilder().setFileName("Walk-in Loop").setFilePathName("/usb1/walkin.mp3"))
            .build()

        val inbound = ProtoCodec.decode(ProtoIds.IN_MUSIC_LIST, list.toByteArray())
        assertTrue(inbound is Inbound.MusicList)
        val items = (inbound as Inbound.MusicList).model.itemsList
        assertEquals(2, items.size)
        assertEquals("Demo Song", items[0].fileName)
        assertEquals("/usb1/walkin.mp3", items[1].filePathName)
    }
}
