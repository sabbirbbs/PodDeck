# PodDeck

**Turn YouTube playlists and lectures into an offline-first Android podcast player.**

PodDeck is an open-source Android application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Media3 (ExoPlayer)**, **Room**, and **NewPipeExtractor**. Paste any public or unlisted YouTube playlist link—or single video URL—to automatically import every track, track total hours and listening progress, download audio locally with true pause/resume support, and listen seamlessly offline or with the screen turned off.

---

## Features

### Playlist & Episode Management
- **Automatic YouTube Playlist Extraction:** Paste a YouTube playlist URL to import all episodes, durations, channel metadata, and thumbnails—supporting large playlists via pagination/continuation tokens and multi-strategy InnerTube/HTML fallbacks.
- **Single-Video Import & Custom Playlists:** Paste an individual YouTube video link to auto-save it under *Saved Videos*, or create custom playlists and add videos manually.
- **Sync New Videos (On-Demand & Pull-to-Refresh):** Check for newly added videos in imported YouTube playlists anytime via the overflow menu (`Sync New Videos`) or pull-to-refresh without losing existing playback progress or offline files.
- **Search, Filter & Sort:** Filter episodes by *All*, *Unfinished*, *Downloaded*, or *Completed*; search within playlists or the library; and sort by playlist order, unplayed first, shortest, or longest.

### Resilient Offline Audio Downloads
- **Flexible Download Options:**
  - **Download All** for an entire playlist
  - **Manual Multi-Select Mode** (via the *Select* chip, overflow menu, or long-press on any episode) with quick *Unplayed* and *Select All* actions
  - **Download Next 5 Unplayed**
  - **Single-Episode Download**
- **True Pause, Resume & Stop Controls:** Pause individual or all active downloads while preserving partial `.part` files and progress percentage, then resume from the exact byte offset using HTTP `Range` requests—or stop and discard cleanly.
- **Bounded Chunked Downloader:** Downloads audio streams in 1.5 MiB range chunks with client-matched request headers and automatic fallback resolution, managed by a `dataSync` foreground service (`DownloadForegroundService`).
- **Zero-Permission Export & Cleanup:** Offline audio is stored privately in app storage (requiring no broad storage permissions). Export copies directly to `Downloads/PodDeck` via `MediaStore` (or the system share sheet on older Android versions), or clean up completed offline files in one tap.

### Podcast-Grade Background Playback
- **Screen-Off & Background Audio:** Powered by Media3 `ExoPlayer` and `MediaSessionService` (`PlaybackService`) with lock-screen and notification media controls.
- **Auto-Resume & Progress Tracking:** Automatically saves your exact timestamp every couple of seconds and on pause/exit. Displays total hours, listened hours, remaining time, and completion percentage per playlist.
- **Listening Controls:**
  - Playback speed from `0.5x` to `2.5x` (remembered per playlist and as a default preference)
  - Speech clarity **Volume Boost** (`+6.5 dB` via `LoudnessEnhancer`)
  - **15s Rewind / 30s Forward** skip buttons
  - **Sleep Timer** (`15m`, `30m`, `45m`, `60m`, or *End of Episode*)
  - **Timestamp Bookmarks** with custom notes to jump back to key moments
  - **Play Next / Up Next Queue** management
- **History Management:** *Continue Listening* card on the Library screen and dedicated *History* tab, with individual dismiss/remove and *Clear All History* options.

### Adaptive Material 3 UI
- Built 100% with Jetpack Compose and Material Design 3.
- Edge-to-edge layout (`enableEdgeToEdge()`) with adaptive **Bottom Navigation Bar** on compact screens and **Navigation Rail** on tablets/foldables (`>= 640dp`).
- Persistent **MiniPlayer** bar and expandable full-screen **Now Playing** sheet.

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| **Language** | Kotlin 2.2.10 |
| **UI** | Jetpack Compose, Material 3, Edge-to-Edge, Coil |
| **Navigation** | Navigation Compose (type-safe `@Serializable` routes) |
| **Audio Playback** | AndroidX Media3 (`ExoPlayer`, `MediaSessionService`) |
| **Extraction & Networking** | NewPipeExtractor `v0.27.1`, OkHttp |
| **Persistence** | Room (`2.7.0` + KSP), Jetpack DataStore Preferences |
| **Concurrency** | Kotlin Coroutines & `Flow` / `StateFlow` |
| **Testing** | JUnit 4, Robolectric, Kotlinx Coroutines Test |

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host, adaptive nav bar/rail, NavHost & sheets
├── PodDeckApp.kt                    # Application initialization & dependency graph
├── data/
│   ├── local/
│   │   ├── PodDeckDatabase.kt       # Room database (playlists, episodes, bookmarks)
│   │   ├── PlaylistDao.kt           # Playlist & aggregated stats queries
│   │   ├── EpisodeDao.kt            # Episode progress, history & download state queries
│   │   ├── BookmarkDao.kt           # Timestamp bookmark queries
│   │   ├── Converters.kt            # Room enum TypeConverters
│   │   └── UserPreferences.kt       # DataStore preferences (playback speed)
│   ├── model/
│   │   ├── PlaylistEntity.kt
│   │   ├── EpisodeEntity.kt         # Includes DownloadStatus (NONE, QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED)
│   │   ├── BookmarkEntity.kt
│   │   └── PlaylistWithStats.kt     # Computed durations, offline storage & completion stats
│   └── repository/
│       └── PodcastRepository.kt     # Single source of truth coordinating DB, extractor & downloads
├── download/
│   ├── EpisodeDownloadManager.kt    # Semaphore-bounded chunked downloader with pause/resume/export
│   └── DownloadForegroundService.kt # dataSync foreground service for active downloads
├── player/
│   ├── PlayerController.kt          # MediaController wrapper, queue, progress persistence & sleep timer
│   └── PlaybackService.kt           # MediaSessionService + ExoPlayer + LoudnessEnhancer boost
├── youtube/
│   ├── YouTubeExtractorService.kt   # Playlist pagination, InnerTube clients & audio stream resolver
│   └── OkHttpDownloader.kt          # NewPipeExtractor HTTP client adapter
├── ui/
│   ├── components/                  # MiniPlayerBar, PlaylistDialogs, PodcastArtwork
│   ├── navigation/                  # @Serializable navigation routes
│   ├── screens/                     # LibraryScreen, PlaylistDetailScreen, OfflineScreen, HistoryScreen, NowPlayingSheet
│   ├── theme/                       # Material 3 ColorScheme, Typography & Shapes
│   └── viewmodel/                   # PodDeckViewModel & UI state flows
└── util/
    ├── NetworkUtils.kt
    ├── TimeFormatters.kt
    └── YouTubeUrlParser.kt
```

---

## Getting Started

### Prerequisites
- **Android Studio** (Meerkat / Narwhal or newer with AGP 9.x & JDK 17+ support)
- **Android SDK** (`compileSdk 36`, `minSdk 24`, `targetSdk 36`)

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/sabbirbbs/poddeck.git
   cd poddeck
   ```
2. Open the project in Android Studio or build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run unit & Robolectric tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

> **Note:** No API keys or backend services are required for core playlist extraction, offline downloading, or playback.

---

## Permissions Explained

PodDeck requests only the permissions strictly required for background playback and downloads:
- `INTERNET` & `ACCESS_NETWORK_STATE`: Fetch playlist metadata, resolve streams, and download audio.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`: Keep audio playing with the screen off and prevent interrupted downloads.
- `POST_NOTIFICATIONS` *(Android 13+)*: Display media playback controls and download progress notifications.

No broad storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO`) are requested.

---

## Disclaimer

This project is intended for personal, educational, and offline listening workflows. Users are responsible for complying with YouTube's Terms of Service and applicable copyright laws in their jurisdiction.

---

## License

This project is open source. Add a `LICENSE` file (e.g., **MIT** or **Apache-2.0**) before publishing your repository.
