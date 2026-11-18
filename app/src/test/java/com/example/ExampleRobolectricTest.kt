package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PodDeckDatabase
import com.example.data.model.DownloadStatus
import com.example.data.model.EpisodeEntity
import com.example.data.model.PlaylistEntity
import com.example.util.TimeFormatters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: PodDeckDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PodDeckDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PodDeck", appName)
    }

    @Test
    fun `playlist and episode CRUD and progress calculations work`() = runTest {
        val playlistDao = db.playlistDao()
        val episodeDao = db.episodeDao()

        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                sourceUrl = "https://www.youtube.com/playlist?list=PLtest123456789",
                youtubePlaylistId = "PLtest123456789",
                title = "System Design Lectures",
                author = "Tech Channel"
            )
        )

        val ep1Id = episodeDao.insertEpisode(
            EpisodeEntity(
                playlistId = playlistId,
                youtubeVideoId = "dQw4w9WgXcQ",
                videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Lecture 1: Sharding",
                durationMs = 3_600_000L, // 1 hour
                sortOrder = 0
            )
        )
        val ep2Id = episodeDao.insertEpisode(
            EpisodeEntity(
                playlistId = playlistId,
                youtubeVideoId = "abc12345678",
                videoUrl = "https://www.youtube.com/watch?v=abc12345678",
                title = "Lecture 2: Consistent Hashing",
                durationMs = 1_800_000L, // 30 minutes
                sortOrder = 1
            )
        )

        // Update progress on episode 1 (30 mins = 1,800,000 ms) and mark downloaded
        episodeDao.updatePlaybackProgress(ep1Id, 1_800_000L, 1000L, isCompleted = false)
        episodeDao.markDownloadCompleted(ep1Id, "/data/user/0/app/files/ep1.m4a", 25_000_000L)

        // Update playlist title
        val savedPlaylist = playlistDao.getPlaylistById(playlistId)!!
        playlistDao.updatePlaylist(savedPlaylist.copy(title = "System Design Masterclass"))

        val withEpisodes = playlistDao.observePlaylistWithEpisodes(playlistId).first()
        assertNotNull(withEpisodes)
        assertEquals("System Design Masterclass", withEpisodes!!.playlist.title)
        assertEquals(2, withEpisodes.episodeCount)
        assertEquals(1, withEpisodes.downloadedCount)
        assertEquals(5_400_000L, withEpisodes.totalDurationMs) // 1h 30m
        assertEquals(1_800_000L, withEpisodes.listenedDurationMs) // 30m
        assertEquals(3_600_000L, withEpisodes.remainingDurationMs) // 1h
        assertEquals("1h 30m", TimeFormatters.formatHoursMinutes(withEpisodes.totalDurationMs))

        // Bookmarks CRUD
        val bookmarkDao = db.bookmarkDao()
        val bmId = bookmarkDao.insertBookmark(
            com.example.data.model.BookmarkEntity(
                episodeId = ep1Id,
                positionMs = 450_000L,
                note = "Consistent hashing ring explanation"
            )
        )
        val bookmarks = bookmarkDao.observeBookmarksForEpisode(ep1Id).first()
        assertEquals(1, bookmarks.size)
        assertEquals(450_000L, bookmarks.first().positionMs)
        bookmarkDao.deleteBookmarkById(bmId)
        assertEquals(0, bookmarkDao.observeBookmarksForEpisode(ep1Id).first().size)

        // Delete episode 2
        episodeDao.deleteEpisodeById(ep2Id)
        val afterEpisodeDelete = playlistDao.observePlaylistWithEpisodes(playlistId).first()!!
        assertEquals(1, afterEpisodeDelete.episodeCount)
        assertEquals(DownloadStatus.COMPLETED, afterEpisodeDelete.episodes.first().downloadStatus)

        // Delete playlist cascades episodes
        playlistDao.deletePlaylistById(playlistId)
        assertNull(playlistDao.getPlaylistById(playlistId))
        assertEquals(0, episodeDao.getEpisodesForPlaylist(playlistId).size)
    }

    @Test
    fun `last played and history items can be removed or cleared cleanly`() = runTest {
        val playlistDao = db.playlistDao()
        val episodeDao = db.episodeDao()

        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                sourceUrl = "https://www.youtube.com/playlist?list=PLhistory123",
                youtubePlaylistId = "PLhistory123",
                title = "History Test Playlist"
            )
        )
        val ep1Id = episodeDao.insertEpisode(
            EpisodeEntity(
                playlistId = playlistId,
                youtubeVideoId = "vid11111111",
                videoUrl = "https://www.youtube.com/watch?v=vid11111111",
                title = "Episode One",
                durationMs = 600_000L,
                sortOrder = 0
            )
        )
        val ep2Id = episodeDao.insertEpisode(
            EpisodeEntity(
                playlistId = playlistId,
                youtubeVideoId = "vid22222222",
                videoUrl = "https://www.youtube.com/watch?v=vid22222222",
                title = "Episode Two",
                durationMs = 600_000L,
                sortOrder = 1
            )
        )

        episodeDao.updatePlaybackProgress(ep1Id, 120_000L, 1000L, isCompleted = false)
        episodeDao.updatePlaybackProgress(ep2Id, 240_000L, 2000L, isCompleted = false)

        assertEquals(ep2Id, episodeDao.observeLastPlayedEpisode().first()?.episode?.id)
        assertEquals(2, episodeDao.observeRecentlyPlayed().first().size)

        // Dismiss ep2 from Last Played / Recent
        episodeDao.removeFromHistory(ep2Id)
        assertEquals(ep1Id, episodeDao.observeLastPlayedEpisode().first()?.episode?.id)
        assertEquals(1, episodeDao.observeRecentlyPlayed().first().size)

        // Reset progress on ep1 clears lastPlayedAt as well
        episodeDao.resetEpisodeProgress(ep1Id)
        assertNull(episodeDao.observeLastPlayedEpisode().first())
        assertEquals(0, episodeDao.observeRecentlyPlayed().first().size)

        // Re-add and test clearAllHistory + failed download error tracking
        episodeDao.updatePlaybackProgress(ep1Id, 60_000L, 3000L, isCompleted = false)
        episodeDao.updatePlaybackProgress(ep2Id, 90_000L, 4000L, isCompleted = false)
        assertEquals(2, episodeDao.observeRecentlyPlayed().first().size)
        episodeDao.clearAllHistory()
        assertEquals(0, episodeDao.observeRecentlyPlayed().first().size)

        episodeDao.updateDownloadStatus(ep1Id, DownloadStatus.FAILED, 0, "HTTP 403 from media server")
        val failedEp = episodeDao.getEpisodeById(ep1Id)!!
        assertEquals(DownloadStatus.FAILED, failedEp.downloadStatus)
        assertEquals("HTTP 403 from media server", failedEp.downloadError)
        episodeDao.clearDownloadMetadata(ep1Id)
        assertEquals(DownloadStatus.NONE, episodeDao.getEpisodeById(ep1Id)!!.downloadStatus)
        assertNull(episodeDao.getEpisodeById(ep1Id)!!.downloadError)
    }

    @Test
    fun `paused downloads survive interruption and active or paused queries stay accurate`() = runTest {
        val playlistDao = db.playlistDao()
        val episodeDao = db.episodeDao()

        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                sourceUrl = "https://www.youtube.com/playlist?list=PLpause123",
                youtubePlaylistId = "PLpause123",
                title = "Download Pause Test"
            )
        )
        val ep1Id = episodeDao.insertEpisode(
            EpisodeEntity(
                playlistId = playlistId,
                youtubeVideoId = "pausevid001",
                videoUrl = "https://www.youtube.com/watch?v=pausevid001",
                title = "Downloading Episode",
                sortOrder = 0
            )
        )
        val ep2Id = episodeDao.insertEpisode(
            EpisodeEntity(
                playlistId = playlistId,
                youtubeVideoId = "pausevid002",
                videoUrl = "https://www.youtube.com/watch?v=pausevid002",
                title = "Queued Zero Progress",
                sortOrder = 1
            )
        )
        val ep3Id = episodeDao.insertEpisode(
            EpisodeEntity(
                playlistId = playlistId,
                youtubeVideoId = "pausevid003",
                videoUrl = "https://www.youtube.com/watch?v=pausevid003",
                title = "Explicitly Paused",
                sortOrder = 2
            )
        )

        episodeDao.updateDownloadStatus(ep1Id, DownloadStatus.DOWNLOADING, 42, null)
        episodeDao.updateDownloadStatus(ep2Id, DownloadStatus.QUEUED, 0, null)
        episodeDao.updateDownloadStatus(ep3Id, DownloadStatus.PAUSED, 67, null)

        assertEquals(2, episodeDao.getActiveDownloadingEpisodes().size)
        assertEquals(1, episodeDao.getPausedEpisodes().size)
        assertEquals(3, episodeDao.observeDownloadQueueAndCompleted().first().size)

        // App restart / crash recovery preserves >0% progress as PAUSED, resets 0% to NONE
        episodeDao.resetInterruptedDownloads()

        val recovered1 = episodeDao.getEpisodeById(ep1Id)!!
        val recovered2 = episodeDao.getEpisodeById(ep2Id)!!
        val recovered3 = episodeDao.getEpisodeById(ep3Id)!!

        assertEquals(DownloadStatus.PAUSED, recovered1.downloadStatus)
        assertEquals(42, recovered1.downloadProgress)
        assertEquals(DownloadStatus.NONE, recovered2.downloadStatus)
        assertEquals(0, recovered2.downloadProgress)
        assertEquals(DownloadStatus.PAUSED, recovered3.downloadStatus)
        assertEquals(67, recovered3.downloadProgress)

        assertEquals(0, episodeDao.getActiveDownloadingEpisodes().size)
        assertEquals(2, episodeDao.getPausedEpisodes().size)
        assertEquals("Never synced", TimeFormatters.formatRelativeSyncTime(0L, 100_000L))
        assertEquals("Synced just now", TimeFormatters.formatRelativeSyncTime(95_000L, 100_000L))
        assertEquals("Synced 5m ago", TimeFormatters.formatRelativeSyncTime(100_000L, 400_000L))
    }
}
