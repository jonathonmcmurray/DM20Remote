package com.dm20remote.ui

import com.dm20remote.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The console has no previous/next instruction, so the app derives the adjacent
 * path from the list it was last sent (protoId 193). These tests pin the edges of
 * that computation down.
 */
class MusicPaneTest {

    private val tracks = listOf(
        MusicTrack("Demo Song", "/usb1/demo-song.mp3"),
        MusicTrack("Soundcheck Sweep", "/usb1/soundcheck.mp3"),
        MusicTrack("Walk-in Loop", "/usb1/walkin.mp3"),
    )

    @Test
    fun `middle track has both neighbours`() {
        val (previous, next) = adjacentPaths(tracks, "/usb1/soundcheck.mp3")
        assertEquals("/usb1/demo-song.mp3", previous)
        assertEquals("/usb1/walkin.mp3", next)
    }

    @Test
    fun `first track has no previous`() {
        val (previous, next) = adjacentPaths(tracks, "/usb1/demo-song.mp3")
        assertEquals(null, previous)
        assertEquals("/usb1/soundcheck.mp3", next)
    }

    @Test
    fun `last track has no next`() {
        val (previous, next) = adjacentPaths(tracks, "/usb1/walkin.mp3")
        assertEquals("/usb1/soundcheck.mp3", previous)
        assertEquals(null, next)
    }

    @Test
    fun `unknown or empty list disables both`() {
        assertEquals(null to null, adjacentPaths(tracks, "/usb1/missing.mp3"))
        assertEquals(null to null, adjacentPaths(emptyList(), "/usb1/demo-song.mp3"))
    }

    @Test
    fun `time is formatted as minutes and seconds`() {
        assertEquals("0:00", formatTime(0))
        assertEquals("0:19", formatTime(19_000))
        assertEquals("3:34", formatTime(214_000))
        assertEquals("0:00", formatTime(-5))
    }
}
