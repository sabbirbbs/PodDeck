package com.example.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.EpisodeDao
import com.example.data.model.DownloadStatus
import com.example.data.model.EpisodeEntity
import com.example.util.NetworkUtils
import com.example.youtube.OkHttpDownloader
import com.example.youtube.ResolvedAudioStream
import com.example.youtube.YouTubeExtractorService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

sealed interface DownloadEvent {
    val message: String

    data class Started(val episodeTitle: String) : DownloadEvent {
        override val message: String = "Downloading \"$episodeTitle\"..."
    }

    data class BatchInfo(override val message: String) : DownloadEvent

    data class Completed(val episodeTitle: String) : DownloadEvent {
        override val message: String = "Offline ready: \"$episodeTitle\""
    }

    data class Failed(val episodeTitle: String, val reason: String) : DownloadEvent {
        override val message: String = "Download failed for \"$episodeTitle\": $reason"
    }
}

sealed interface ExportResult {
    data class SavedToDownloads(val displayName: String) : ExportResult
    data class NeedsShareSheet(val uri: Uri, val mimeType: String, val fileName: String) : ExportResult
}

class EpisodeDownloadManager(
    private val context: Context,
    private val episodeDao: EpisodeDao,
    private val extractorService: YouTubeExtractorService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(2)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pauseRequestedIds = ConcurrentHashMap.newKeySet<Long>()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 24)
    val events = _events.asSharedFlow()

    private val podcastsRootDir: File
        get() = File(context.filesDir, "podcasts").apply { mkdirs() }

    init {
        scope.launch {
            episodeDao.resetInterruptedDownloads()
        }
    }

    fun enqueueDownload(episode: EpisodeEntity, announceStart: Boolean = true) {
        if (activeJobs.containsKey(episode.id)) return
        val existingFile = episode.localFilePath?.let { File(it) }
        if (episode.isDownloaded && existingFile != null && existingFile.exists() && existingFile.length() >= MIN_AUDIO_BYTES) {
            return
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            val offlineMsg = "No internet connection. Connect to Wi-Fi or mobile data to download."
            scope.launch {
                episodeDao.updateDownloadStatus(
                    episodeId = episode.id,
                    status = DownloadStatus.FAILED,
                    progress = 0,
                    error = offlineMsg
                )
            }
            _events.tryEmit(DownloadEvent.Failed(episode.title, offlineMsg))
            return
        }

        if (announceStart) {
            _events.tryEmit(DownloadEvent.Started(episode.title))
        }

        val initialProgress = if (episode.downloadStatus == DownloadStatus.PAUSED) {
            episode.downloadProgress.coerceIn(0, 99)
        } else {
            0
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                pauseRequestedIds.remove(episode.id)
                episodeDao.updateDownloadStatus(
                    episodeId = episode.id,
                    status = DownloadStatus.QUEUED,
                    progress = initialProgress,
                    error = null
                )
                DownloadForegroundService.updateProgress(
                    context = context,
                    title = episode.title,
                    progress = initialProgress,
                    activeCount = activeJobs.size.coerceAtLeast(1)
                )
                semaphore.withPermit {
                    performDownload(episode.id)
                }
            } catch (_: CancellationException) {
                val isPause = pauseRequestedIds.remove(episode.id)
                val latest = episodeDao.getEpisodeById(episode.id)
                val savedProgress = latest?.downloadProgress ?: initialProgress
                if (isPause && savedProgress > 0) {
                    episodeDao.updateDownloadStatus(
                        episodeId = episode.id,
                        status = DownloadStatus.PAUSED,
                        progress = savedProgress.coerceIn(1, 99),
                        error = null
                    )
                } else {
                    if (!isPause) {
                        latest?.let { deletePartFiles(it.playlistId, it.youtubeVideoId) }
                    }
                    episodeDao.updateDownloadStatus(
                        episodeId = episode.id,
                        status = DownloadStatus.NONE,
                        progress = 0,
                        error = null
                    )
                }
            } catch (e: Exception) {
                val reason = formatReadableError(e)
                episodeDao.updateDownloadStatus(
                    episodeId = episode.id,
                    status = DownloadStatus.FAILED,
                    progress = 0,
                    error = reason
                )
                _events.tryEmit(DownloadEvent.Failed(episode.title, reason))
            } finally {
                activeJobs.remove(episode.id)
                if (activeJobs.isEmpty()) {
                    DownloadForegroundService.stop(context)
                }
            }
        }
        activeJobs[episode.id] = job
        job.start()
    }

    fun enqueuePlaylistDownload(episodes: List<EpisodeEntity>) {
        val pending = filterDownloadableEpisodes(episodes)
        if (pending.isEmpty()) return
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _events.tryEmit(
                DownloadEvent.Failed(
                    pending.first().title,
                    "No internet connection. Connect to Wi-Fi or mobile data to download."
                )
            )
            return
        }
        pending.forEach { enqueueDownload(it, announceStart = false) }
    }

    fun enqueueSelectedEpisodes(episodes: List<EpisodeEntity>): Int {
        val pending = filterDownloadableEpisodes(episodes)
        if (pending.isEmpty()) return 0
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _events.tryEmit(
                DownloadEvent.Failed(
                    pending.first().title,
                    "No internet connection. Connect to Wi-Fi or mobile data to download."
                )
            )
            return 0
        }
        pending.forEach { enqueueDownload(it, announceStart = false) }
        _events.tryEmit(DownloadEvent.BatchInfo("Queued ${pending.size} selected episode(s) for download"))
        return pending.size
    }

    fun enqueueNextUnplayed(episodes: List<EpisodeEntity>, limit: Int = 5) {
        val pending = filterDownloadableEpisodes(episodes)
            .filter { !it.isCompleted }
            .take(limit)
        if (pending.isEmpty()) return
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _events.tryEmit(
                DownloadEvent.Failed(
                    pending.first().title,
                    "No internet connection. Connect to Wi-Fi or mobile data to download."
                )
            )
            return
        }
        pending.forEach { enqueueDownload(it, announceStart = false) }
    }

    fun pauseDownload(episodeId: Long) {
        pauseRequestedIds.add(episodeId)
        val job = activeJobs.remove(episodeId)
        if (job != null) {
            job.cancel()
        } else {
            scope.launch {
                val ep = episodeDao.getEpisodeById(episodeId) ?: return@launch
                if (ep.downloadStatus == DownloadStatus.QUEUED || ep.downloadStatus == DownloadStatus.DOWNLOADING) {
                    val nextStatus = if (ep.downloadProgress > 0) DownloadStatus.PAUSED else DownloadStatus.NONE
                    episodeDao.updateDownloadStatus(episodeId, nextStatus, ep.downloadProgress, null)
                }
            }
        }
        if (activeJobs.isEmpty()) {
            DownloadForegroundService.stop(context)
        }
    }

    fun pauseDownloadsForEpisodes(episodes: List<EpisodeEntity>) {
        val active = episodes.filter {
            it.downloadStatus == DownloadStatus.QUEUED || it.downloadStatus == DownloadStatus.DOWNLOADING
        }
        if (active.isEmpty() && activeJobs.isEmpty()) return
        val ids = active.map { it.id }
        ids.forEach { pauseDownload(it) }
        if (ids.isNotEmpty()) {
            _events.tryEmit(DownloadEvent.BatchInfo("Paused ${ids.size} active download(s)"))
        }
    }

    fun pauseAllActiveDownloads() {
        scope.launch {
            val activeIds = (activeJobs.keys.toList() +
                episodeDao.getActiveDownloadingEpisodes().map { it.id }).distinct()
            if (activeIds.isEmpty()) return@launch
            activeIds.forEach { pauseDownload(it) }
            _events.tryEmit(DownloadEvent.BatchInfo("Paused ${activeIds.size} active download(s)"))
        }
    }

    fun resumePausedDownloads(episodes: List<EpisodeEntity>? = null) {
        scope.launch {
            val paused = episodes?.filter { it.downloadStatus == DownloadStatus.PAUSED }
                ?: episodeDao.getPausedEpisodes()
            if (paused.isEmpty()) return@launch
            if (!NetworkUtils.isNetworkAvailable(context)) {
                _events.tryEmit(
                    DownloadEvent.Failed(
                        paused.first().title,
                        "No internet connection. Connect to Wi-Fi or mobile data to resume."
                    )
                )
                return@launch
            }
            paused.forEach { enqueueDownload(it, announceStart = false) }
            _events.tryEmit(DownloadEvent.BatchInfo("Resumed ${paused.size} paused download(s)"))
        }
    }

    fun cancelDownload(episodeId: Long) {
        pauseRequestedIds.remove(episodeId)
        val job = activeJobs.remove(episodeId)
        job?.cancel()
        scope.launch {
            val ep = episodeDao.getEpisodeById(episodeId)
            if (ep != null) {
                deletePartFiles(ep.playlistId, ep.youtubeVideoId)
                if (ep.downloadStatus != DownloadStatus.COMPLETED) {
                    episodeDao.updateDownloadStatus(episodeId, DownloadStatus.NONE, 0, null)
                }
            }
        }
        if (activeJobs.isEmpty()) {
            DownloadForegroundService.stop(context)
        }
    }

    fun stopDownloadsForEpisodes(episodes: List<EpisodeEntity>) {
        val stoppable = episodes.filter {
            it.downloadStatus == DownloadStatus.QUEUED ||
                it.downloadStatus == DownloadStatus.DOWNLOADING ||
                it.downloadStatus == DownloadStatus.PAUSED ||
                it.downloadStatus == DownloadStatus.FAILED
        }
        if (stoppable.isEmpty() && activeJobs.isEmpty()) return
        stoppable.forEach { cancelDownload(it.id) }
        if (stoppable.isNotEmpty()) {
            _events.tryEmit(DownloadEvent.BatchInfo("Stopped ${stoppable.size} download(s)"))
        }
    }

    fun stopAllActiveDownloads() {
        scope.launch {
            val toStop = (activeJobs.keys.toList() +
                episodeDao.getActiveDownloadingEpisodes().map { it.id } +
                episodeDao.getPausedEpisodes().map { it.id }).distinct()
            if (toStop.isEmpty()) return@launch
            toStop.forEach { cancelDownload(it) }
            _events.tryEmit(DownloadEvent.BatchInfo("Stopped ${toStop.size} download(s)"))
        }
    }

    suspend fun deleteLocalAudio(episode: EpisodeEntity) {
        pauseRequestedIds.remove(episode.id)
        activeJobs.remove(episode.id)?.cancel()
        episode.localFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        deletePartFiles(episode.playlistId, episode.youtubeVideoId)
        episodeDao.clearDownloadMetadata(episode.id)
        if (activeJobs.isEmpty()) {
            DownloadForegroundService.stop(context)
        }
    }

    fun deletePlaylistFolder(playlistId: Long) {
        val dir = File(podcastsRootDir, "playlist_$playlistId")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    suspend fun exportToDeviceDownloads(episode: EpisodeEntity): ExportResult = withContext(Dispatchers.IO) {
        val path = episode.localFilePath
            ?: throw IllegalStateException("Episode has not been downloaded yet.")
        val sourceFile = File(path)
        if (!sourceFile.exists() || sourceFile.length() < MIN_AUDIO_BYTES) {
            throw IllegalStateException("Offline audio file is missing on device.")
        }

        val ext = sourceFile.extension.ifBlank { "m4a" }
        val mimeType = if (ext.equals("webm", ignoreCase = true)) "audio/webm" else "audio/mp4"
        val safeTitle = episode.title
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .take(64)
            .ifBlank { episode.youtubeVideoId }
        val fileName = "$safeTitle.$ext"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PodDeck")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads/PodDeck")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 32 * 1024)
                    }
                } ?: throw IllegalStateException("Could not open Downloads output stream")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)
                ExportResult.SavedToDownloads("Downloads/PodDeck/$fileName")
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sourceFile
            )
            ExportResult.NeedsShareSheet(uri = uri, mimeType = mimeType, fileName = fileName)
        }
    }

    private fun filterDownloadableEpisodes(episodes: List<EpisodeEntity>): List<EpisodeEntity> {
        return episodes.filter { ep ->
            val fileExists = ep.localFilePath?.let { File(it).exists() } == true
            !(ep.isDownloaded && fileExists) &&
                ep.downloadStatus != DownloadStatus.DOWNLOADING &&
                ep.downloadStatus != DownloadStatus.QUEUED
        }
    }

    private fun deletePartFiles(playlistId: Long, youtubeVideoId: String) {
        val playlistDir = File(podcastsRootDir, "playlist_$playlistId")
        if (!playlistDir.exists()) return
        playlistDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("$youtubeVideoId.") && file.name.endsWith(".part")) {
                runCatching { file.delete() }
            }
        }
    }

    private suspend fun performDownload(episodeId: Long) {
        val latest = episodeDao.getEpisodeById(episodeId) ?: return
        val startPct = if (latest.downloadProgress in 1..99) latest.downloadProgress else 1
        episodeDao.updateDownloadStatus(episodeId, DownloadStatus.DOWNLOADING, startPct, null)

        var resolved = extractorService.resolveAudioStream(latest.videoUrl)
        if (resolved.durationMs > 0L && latest.durationMs <= 0L) {
            episodeDao.updateDurationIfMissing(episodeId, resolved.durationMs)
        }

        val playlistDir = File(podcastsRootDir, "playlist_${latest.playlistId}").apply { mkdirs() }
        var ext = resolved.fileExtension.ifBlank { "m4a" }
        var targetFile = File(playlistDir, "${latest.youtubeVideoId}.$ext")
        var tempFile = File(playlistDir, "${latest.youtubeVideoId}.$ext.part")

        val firstAttempt = runCatching {
            downloadStreamWithRanges(episodeId, latest.title, resolved, tempFile)
        }

        if (firstAttempt.isFailure) {
            val err = firstAttempt.exceptionOrNull()
            if (err is CancellationException) throw err

            tempFile.delete()
            // Fallback: re-resolve via InnerTube mobile/VR clients if primary stream rejected (e.g. HTTP 403)
            val fallbackResolved = runCatching {
                extractorService.resolveAudioStream(latest.videoUrl, preferInnerTubeFallback = true)
            }.getOrNull()

            if (fallbackResolved != null && fallbackResolved.streamUrl != resolved.streamUrl) {
                resolved = fallbackResolved
                ext = resolved.fileExtension.ifBlank { "m4a" }
                targetFile = File(playlistDir, "${latest.youtubeVideoId}.$ext")
                tempFile = File(playlistDir, "${latest.youtubeVideoId}.$ext.part")
                if (tempFile.exists()) tempFile.delete()
                downloadStreamWithRanges(episodeId, latest.title, resolved, tempFile)
            } else {
                throw err!!
            }
        }

        if (!tempFile.exists() || tempFile.length() < MIN_AUDIO_BYTES) {
            tempFile.delete()
            throw IllegalStateException("Downloaded audio stream was empty or corrupted.")
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        episodeDao.markDownloadCompleted(
            episodeId = episodeId,
            filePath = targetFile.absolutePath,
            fileSizeBytes = targetFile.length()
        )
        _events.tryEmit(DownloadEvent.Completed(latest.title))
    }

    private suspend fun downloadStreamWithRanges(
        episodeId: Long,
        episodeTitle: String,
        resolved: ResolvedAudioStream,
        tempFile: File
    ) {
        val expectedLength = resolved.contentLengthBytes.takeIf { it > 0L }
            ?: YouTubeExtractorService.extractClenFromUrl(resolved.streamUrl)

        val canResumePartial = tempFile.exists() &&
            tempFile.length() > 0L &&
            expectedLength > 0L &&
            tempFile.length() < expectedLength

        var downloadedBytes = if (canResumePartial) tempFile.length() else 0L
        if (!canResumePartial && tempFile.exists()) {
            tempFile.delete()
        }

        var totalBytes = expectedLength
        var lastReportedProgress = if (totalBytes > 0L && downloadedBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(1, 99)
        } else {
            0
        }
        var lastUpdateMs = 0L
        var useQueryRange = false

        FileOutputStream(tempFile, canResumePartial).use { output ->
            while (true) {
                val rangeStart = downloadedBytes
                val rangeEnd = if (totalBytes > 0L) {
                    minOf(totalBytes - 1, rangeStart + CHUNK_SIZE_BYTES - 1)
                } else {
                    rangeStart + CHUNK_SIZE_BYTES - 1
                }

                var response = executeChunkRequest(
                    streamUrl = resolved.streamUrl,
                    headers = resolved.requestHeaders,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    useQueryRange = useQueryRange
                )

                // If HTTP 403 occurs on Range header, retry once with YouTube's &range=start-end query param
                if (response.code == 403 && !useQueryRange) {
                    response.close()
                    useQueryRange = true
                    response = executeChunkRequest(
                        streamUrl = resolved.streamUrl,
                        headers = resolved.requestHeaders,
                        rangeStart = rangeStart,
                        rangeEnd = rangeEnd,
                        useQueryRange = true
                    )
                }

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code} from media server")
                    }
                    val contentType = resp.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("text/html") || contentType.contains("application/json")) {
                        throw IllegalStateException("Media server returned $contentType instead of audio")
                    }

                    val body = resp.body ?: throw IllegalStateException("Empty audio response body")

                    if (resp.code == 206) {
                        val parsedTotal = parseTotalFromContentRange(resp.header("Content-Range"))
                        if (parsedTotal > 0L) {
                            totalBytes = parsedTotal
                        }
                    } else if (totalBytes <= 0L && body.contentLength() > 0L) {
                        totalBytes = downloadedBytes + body.contentLength()
                    }

                    var chunkReadTotal = 0L
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            chunkReadTotal += read

                            val now = System.currentTimeMillis()
                            val pct = if (totalBytes > 0L) {
                                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(1, 99)
                            } else {
                                ((downloadedBytes / 120_000L) % 90 + 5).toInt()
                            }
                            if ((pct != lastReportedProgress && now - lastUpdateMs >= 250L) ||
                                pct - lastReportedProgress >= 4
                            ) {
                                lastReportedProgress = pct
                                lastUpdateMs = now
                                episodeDao.updateDownloadStatus(
                                    episodeId = episodeId,
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = pct,
                                    error = null
                                )
                                DownloadForegroundService.updateProgress(
                                    context = context,
                                    title = episodeTitle,
                                    progress = pct,
                                    activeCount = activeJobs.size.coerceAtLeast(1)
                                )
                            }
                        }
                    }
                    output.flush()

                    // Exit conditions: full 200 OK stream completed, reached known totalBytes, or chunk returned EOF
                    if (resp.code == 200 && (totalBytes <= 0L || downloadedBytes >= totalBytes)) {
                        return
                    }
                    if (totalBytes > 0L && downloadedBytes >= totalBytes) {
                        return
                    }
                    if (chunkReadTotal == 0L) {
                        return
                    }
                    if (resp.code == 206 && chunkReadTotal < (rangeEnd - rangeStart + 1) && totalBytes <= 0L) {
                        return
                    }
                }
            }
        }
    }

    private fun executeChunkRequest(
        streamUrl: String,
        headers: Map<String, String>,
        rangeStart: Long,
        rangeEnd: Long,
        useQueryRange: Boolean
    ): Response {
        val targetUrl = if (useQueryRange) {
            val cleanBase = streamUrl.replace(Regex("[?&]range=[^&]*"), "")
            val sep = if (cleanBase.contains("?")) "&" else "?"
            "$cleanBase${sep}range=$rangeStart-$rangeEnd"
        } else {
            streamUrl
        }

        val builder = Request.Builder().url(targetUrl)
        var hasUa = false
        headers.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
            builder.header(k, v)
        }
        if (!hasUa) {
            builder.header(
                "User-Agent",
                "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            )
        }
        if (!useQueryRange) {
            builder.header("Range", "bytes=$rangeStart-$rangeEnd")
        }
        return OkHttpDownloader.sharedClient.newCall(builder.build()).execute()
    }

    private fun parseTotalFromContentRange(header: String?): Long {
        if (header.isNullOrBlank()) return 0L
        return header.substringAfter("/", "").trim().toLongOrNull() ?: 0L
    }

    private fun formatReadableError(e: Exception): String {
        val msg = e.message?.trim().orEmpty()
        return when {
            msg.contains("LOGIN_REQUIRED", ignoreCase = true) ->
                "YouTube asked for verification on this video (LOGIN_REQUIRED)"
            msg.contains("UnknownHostException", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Network unreachable — check your internet connection"
            msg.contains("timeout", ignoreCase = true) ->
                "Connection timed out — tap retry"
            msg.isNotBlank() -> msg.take(140)
            else -> "Download failed"
        }
    }

    companion object {
        private const val CHUNK_SIZE_BYTES = 1_572_864L // 1.5 MiB bounded range chunks
        private const val MIN_AUDIO_BYTES = 4_096L
    }
}
