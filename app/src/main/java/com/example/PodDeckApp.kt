package com.example

import android.app.Application
import com.example.data.local.PodDeckDatabase
import com.example.data.local.UserPreferences
import com.example.data.repository.PodcastRepository
import com.example.download.EpisodeDownloadManager
import com.example.player.PlayerController
import com.example.youtube.YouTubeExtractorService

class PodDeckApp : Application() {

    lateinit var database: PodDeckDatabase
        private set

    lateinit var repository: PodcastRepository
        private set

    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        database = PodDeckDatabase.getInstance(this)
        val userPrefs = UserPreferences(this)
        val extractor = YouTubeExtractorService()
        val downloadManager = EpisodeDownloadManager(
            context = this,
            episodeDao = database.episodeDao(),
            extractorService = extractor
        )
        repository = PodcastRepository(
            playlistDao = database.playlistDao(),
            episodeDao = database.episodeDao(),
            bookmarkDao = database.bookmarkDao(),
            extractorService = extractor,
            downloadManager = downloadManager,
            userPreferences = userPrefs
        )
        playerController = PlayerController(
            context = this,
            repository = repository
        )
    }
}
