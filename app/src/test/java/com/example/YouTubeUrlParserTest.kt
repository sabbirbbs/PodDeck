package com.example

import com.example.util.TimeFormatters
import com.example.util.YouTubeUrlParser
import com.example.youtube.YouTubeExtractorService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeUrlParserTest {

    @Test
    fun `extracts playlist id from full youtube playlist url`() {
        val url = "https://www.youtube.com/playlist?list=PLillGF-RfqbZ2ybcoD2OaabW2P7Ws8CWu"
        assertEquals("PLillGF-RfqbZ2ybcoD2OaabW2P7Ws8CWu", YouTubeUrlParser.extractPlaylistId(url))
        assertEquals(
            "https://www.youtube.com/playlist?list=PLillGF-RfqbZ2ybcoD2OaabW2P7Ws8CWu",
            YouTubeUrlParser.normalizePlaylistUrl(url)
        )
    }

    @Test
    fun `extracts playlist id from watch url with list parameter`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890ABCDEF&index=3"
        assertEquals("PL1234567890ABCDEF", YouTubeUrlParser.extractPlaylistId(url))
    }

    @Test
    fun `extracts bare playlist id`() {
        val id = "PLillGF-RfqbZ2ybcoD2OaabW2P7Ws8CWu"
        assertEquals(id, YouTubeUrlParser.extractPlaylistId(id))
    }

    @Test
    fun `extracts video id from watch, youtu_be, and shorts urls`() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=42"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrlParser.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(YouTubeUrlParser.extractVideoId("not a url"))
    }

    @Test
    fun `parses duration badge strings and spoken durations`() {
        assertEquals(5_408_000L, YouTubeExtractorService.parseDurationString("1:30:08"))
        assertEquals(724_000L, YouTubeExtractorService.parseDurationString("12:04"))
        assertEquals(5_408_000L, YouTubeExtractorService.parseSpokenDuration("1 hour, 30 minutes, 8 seconds"))
    }

    @Test
    fun `formats durations and sizes accurately`() {
        assertEquals("1:05:09", TimeFormatters.formatDurationTimestamp(3_909_000L))
        assertEquals("12:04", TimeFormatters.formatDurationTimestamp(724_000L))
        assertEquals("2h 15m", TimeFormatters.formatHoursMinutes(8_100_000L))
        assertEquals("1.5 MB", TimeFormatters.formatFileSize(1_572_864L))
        assertEquals(
            12345678L,
            YouTubeExtractorService.extractClenFromUrl("https://rr1---sn.googlevideo.com/videoplayback?expire=999&clen=12345678&mime=audio/mp4")
        )
    }

    @Test
    fun `live playlist extraction returns episodes and durations`() = runTest {
        val service = YouTubeExtractorService()
        val playlist = service.fetchPlaylist("https://www.youtube.com/playlist?list=PLillGF-RfqbZ2ybcoD2OaabW2P7Ws8CWu")
        assertTrue("Expected playlist title, got ${playlist.title}", playlist.title.isNotBlank())
        assertTrue("Expected > 20 episodes, got ${playlist.items.size}", playlist.items.size >= 20)
        assertTrue(
            "Expected non-zero total duration across episodes",
            playlist.items.sumOf { it.durationMs } > 0L
        )
    }

    @Test
    fun `live paginated playlist extraction loads over 100 episodes`() = runTest {
        val service = YouTubeExtractorService()
        val playlist = service.fetchPlaylist("https://www.youtube.com/playlist?list=PL-osiE80TeTt2d9bfVyTiXJA-UTHn6WwU")
        assertTrue("Expected > 100 episodes via pagination, got ${playlist.items.size}", playlist.items.size > 100)
        assertTrue(
            "Expected > 10 hours total duration",
            playlist.items.sumOf { it.durationMs } > 36_000_000L
        )
    }
}
