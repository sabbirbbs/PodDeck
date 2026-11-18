package com.example.youtube

import com.example.util.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class ExtractedPlaylistItem(
    val videoId: String,
    val videoUrl: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationMs: Long
)

data class ExtractedPlaylist(
    val sourceUrl: String,
    val youtubePlaylistId: String,
    val title: String,
    val author: String,
    val description: String,
    val thumbnailUrl: String?,
    val items: List<ExtractedPlaylistItem>
)

data class ResolvedAudioStream(
    val streamUrl: String,
    val fileExtension: String,
    val mimeType: String,
    val durationMs: Long,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val requestHeaders: Map<String, String> = emptyMap(),
    val contentLengthBytes: Long = 0L
)

class YouTubeExtractorService {

    @Volatile
    private var initialized = false

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun ensureInitialized() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(
                        OkHttpDownloader.instance,
                        Localization("en", "US"),
                        ContentCountry("US")
                    )
                    initialized = true
                }
            }
        }
    }

    suspend fun fetchPlaylist(rawInput: String): ExtractedPlaylist = withContext(Dispatchers.IO) {
        ensureInitialized()
        val playlistId = YouTubeUrlParser.extractPlaylistId(rawInput)
            ?: throw IllegalArgumentException("Could not find a valid YouTube playlist ID in that link.")
        val normalizedUrl = YouTubeUrlParser.normalizePlaylistUrl(rawInput)
            ?: throw IllegalArgumentException("Invalid YouTube playlist URL.")

        // Strategy 1: Try NewPipeExtractor first
        val newPipeResult = runCatching { fetchPlaylistViaNewPipe(normalizedUrl, playlistId) }.getOrNull()
        if (newPipeResult != null && newPipeResult.items.isNotEmpty()) {
            return@withContext newPipeResult
        }

        // Strategy 2: InnerTube WEB /youtubei/v1/browse (supports lockupViewModel + continuationItemViewModel + playlistVideoRenderer)
        val innerTubeResult = runCatching { fetchPlaylistViaInnerTube(playlistId, normalizedUrl) }.getOrNull()
        if (innerTubeResult != null && innerTubeResult.items.isNotEmpty()) {
            return@withContext mergeMetadata(innerTubeResult, newPipeResult)
        }

        // Strategy 3: YouTube playlist HTML ytInitialData scraper
        val htmlResult = runCatching { fetchPlaylistViaHtml(playlistId, normalizedUrl) }.getOrNull()
        if (htmlResult != null && htmlResult.items.isNotEmpty()) {
            return@withContext mergeMetadata(htmlResult, newPipeResult ?: innerTubeResult)
        }

        newPipeResult ?: innerTubeResult ?: htmlResult
            ?: throw IllegalStateException("Could not load episodes from this YouTube playlist. Check that the playlist is public or unlisted.")
    }

    private fun mergeMetadata(primary: ExtractedPlaylist, fallback: ExtractedPlaylist?): ExtractedPlaylist {
        if (fallback == null) return primary
        return primary.copy(
            title = primary.title.takeIf { it != "YouTube Playlist" && it.isNotBlank() } ?: fallback.title,
            author = primary.author.ifBlank { fallback.author },
            description = primary.description.ifBlank { fallback.description },
            thumbnailUrl = primary.thumbnailUrl ?: fallback.thumbnailUrl
        )
    }

    private fun fetchPlaylistViaNewPipe(normalizedUrl: String, playlistId: String): ExtractedPlaylist {
        val service = ServiceList.YouTube
        val playlistInfo = PlaylistInfo.getInfo(service, normalizedUrl)

        val collectedItems = mutableListOf<StreamInfoItem>()
        collectedItems.addAll(playlistInfo.relatedItems)

        var nextPage = playlistInfo.nextPage
        var hasNext = playlistInfo.hasNextPage()
        var pageCount = 0
        while (hasNext && nextPage != null && pageCount < 8 && collectedItems.size < 500) {
            val page = PlaylistInfo.getMoreItems(service, normalizedUrl, nextPage)
            collectedItems.addAll(page.items)
            nextPage = page.nextPage
            hasNext = page.hasNextPage()
            pageCount++
        }

        val mappedItems = collectedItems.mapNotNull { item ->
            val videoId = YouTubeUrlParser.extractVideoId(item.url) ?: return@mapNotNull null
            ExtractedPlaylistItem(
                videoId = videoId,
                videoUrl = YouTubeUrlParser.normalizeVideoUrl(item.url) ?: item.url,
                title = item.name?.takeIf { it.isNotBlank() } ?: "Untitled Episode",
                uploader = item.uploaderName.orEmpty(),
                thumbnailUrl = pickBestThumbnail(item.thumbnails)
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationMs = if (item.duration > 0L) item.duration * 1000L else 0L
            )
        }.distinctBy { it.videoId }

        val playlistThumb = pickBestThumbnail(playlistInfo.thumbnails)
            ?: mappedItems.firstOrNull()?.thumbnailUrl

        return ExtractedPlaylist(
            sourceUrl = normalizedUrl,
            youtubePlaylistId = playlistId,
            title = playlistInfo.name?.takeIf { it.isNotBlank() } ?: "YouTube Playlist",
            author = playlistInfo.uploaderName.orEmpty(),
            description = playlistInfo.description?.content.orEmpty(),
            thumbnailUrl = playlistThumb,
            items = mappedItems
        )
    }

    private fun fetchPlaylistViaInnerTube(playlistId: String, normalizedUrl: String): ExtractedPlaylist {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val initialPayload = """
            {
              "context": {
                "client": {
                  "clientName": "WEB",
                  "clientVersion": "$WEB_CLIENT_VERSION",
                  "hl": "en",
                  "gl": "US"
                }
              },
              "browseId": "$browseId"
            }
        """.trimIndent()

        val initialRoot = postInnerTubeJson(
            url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
            jsonBody = initialPayload
        )

        val items = mutableListOf<ExtractedPlaylistItem>()
        extractPlaylistItemsRecursively(initialRoot, items)

        var continuationToken = findPlaylistContinuationToken(initialRoot)
        var pageCount = 0
        while (continuationToken != null && pageCount < 8 && items.size < 500) {
            val contPayload = """
                {
                  "context": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "$WEB_CLIENT_VERSION",
                      "hl": "en",
                      "gl": "US"
                    }
                  },
                  "continuation": "$continuationToken"
                }
            """.trimIndent()
            val contRoot = runCatching {
                postInnerTubeJson(
                    url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                    jsonBody = contPayload
                )
            }.getOrNull() ?: break

            val beforeCount = items.size
            extractPlaylistItemsRecursively(contRoot, items)
            if (items.size == beforeCount) break
            continuationToken = findPlaylistContinuationToken(contRoot)
            pageCount++
        }

        val distinctItems = items.distinctBy { it.videoId }
        val meta = extractPlaylistHeaderMetadata(initialRoot)

        return ExtractedPlaylist(
            sourceUrl = normalizedUrl,
            youtubePlaylistId = playlistId,
            title = meta.title ?: "YouTube Playlist",
            author = meta.author.orEmpty(),
            description = meta.description.orEmpty(),
            thumbnailUrl = meta.thumbnailUrl ?: distinctItems.firstOrNull()?.thumbnailUrl,
            items = distinctItems
        )
    }

    private fun fetchPlaylistViaHtml(playlistId: String, normalizedUrl: String): ExtractedPlaylist {
        val request = Request.Builder()
            .url("https://www.youtube.com/playlist?list=$playlistId&hl=en")
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Cookie", "CONSENT=YES+cb")
            .build()

        val html = OkHttpDownloader.sharedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} fetching YouTube playlist page")
            }
            response.body?.string().orEmpty()
        }

        val jsonText = extractYtInitialDataJson(html)
            ?: throw IllegalStateException("Could not locate playlist data in YouTube page")

        val root = json.parseToJsonElement(jsonText)
        val items = mutableListOf<ExtractedPlaylistItem>()
        extractPlaylistItemsRecursively(root, items)

        var continuationToken = findPlaylistContinuationToken(root)
        var pageCount = 0
        while (continuationToken != null && pageCount < 8 && items.size < 500) {
            val contPayload = """
                {
                  "context": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "$WEB_CLIENT_VERSION",
                      "hl": "en",
                      "gl": "US"
                    }
                  },
                  "continuation": "$continuationToken"
                }
            """.trimIndent()
            val contRoot = runCatching {
                postInnerTubeJson(
                    url = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false",
                    jsonBody = contPayload
                )
            }.getOrNull() ?: break
            val before = items.size
            extractPlaylistItemsRecursively(contRoot, items)
            if (items.size == before) break
            continuationToken = findPlaylistContinuationToken(contRoot)
            pageCount++
        }

        val distinctItems = items.distinctBy { it.videoId }
        val meta = extractPlaylistHeaderMetadata(root)

        return ExtractedPlaylist(
            sourceUrl = normalizedUrl,
            youtubePlaylistId = playlistId,
            title = meta.title ?: "YouTube Playlist",
            author = meta.author.orEmpty(),
            description = meta.description.orEmpty(),
            thumbnailUrl = meta.thumbnailUrl ?: distinctItems.firstOrNull()?.thumbnailUrl,
            items = distinctItems
        )
    }

    private fun extractYtInitialDataJson(html: String): String? {
        val markers = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ")
        for (marker in markers) {
            val startIdx = html.indexOf(marker)
            if (startIdx >= 0) {
                val jsonStart = html.indexOf('{', startIdx + marker.length)
                if (jsonStart >= 0) {
                    var depth = 0
                    var inString = false
                    var escaped = false
                    for (i in jsonStart until html.length) {
                        val ch = html[i]
                        if (escaped) {
                            escaped = false
                            continue
                        }
                        if (ch == '\\' && inString) {
                            escaped = true
                            continue
                        }
                        if (ch == '"') {
                            inString = !inString
                            continue
                        }
                        if (!inString) {
                            if (ch == '{') depth++
                            else if (ch == '}') {
                                depth--
                                if (depth == 0) {
                                    return html.substring(jsonStart, i + 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private data class PlaylistHeaderMeta(
        val title: String? = null,
        val author: String? = null,
        val description: String? = null,
        val thumbnailUrl: String? = null
    )

    private fun extractPlaylistHeaderMetadata(root: JsonElement): PlaylistHeaderMeta {
        val rootObj = root as? JsonObject ?: return PlaylistHeaderMeta()
        val header = rootObj["header"] as? JsonObject
        val playlistHeader = header?.get("playlistHeaderRenderer") as? JsonObject
        val metadata = (rootObj["metadata"] as? JsonObject)?.get("playlistMetadataRenderer") as? JsonObject

        val title = readTextField(playlistHeader?.get("title"))
            ?: readTextField(metadata?.get("title"))
            ?: findFirstPageHeaderTitle(header)

        val author = readTextField(playlistHeader?.get("ownerText"))
            ?: findPageHeaderAuthor(header)

        val description = readTextField(playlistHeader?.get("descriptionText"))
            ?: readTextField(metadata?.get("description"))

        val thumb = extractBestThumbnailUrl(
            playlistHeader?.get("playlistHeaderBanner")
                ?.asObjOrNull()?.get("heroPlaylistThumbnailRenderer")
                ?.asObjOrNull()?.get("thumbnail")
        ) ?: findPageHeaderThumbnail(header)

        return PlaylistHeaderMeta(title, author, description, thumb)
    }

    private fun findFirstPageHeaderTitle(header: JsonObject?): String? {
        val pageHeader = header?.get("pageHeaderRenderer") as? JsonObject ?: return null
        val content = pageHeader["content"]?.asObjOrNull()?.get("pageHeaderViewModel")?.asObjOrNull()
            ?: return readTextField(pageHeader["pageTitle"])
        return content["title"]?.asObjOrNull()?.get("dynamicTextViewModel")?.asObjOrNull()
            ?.get("text")?.asObjOrNull()?.get("content")?.asStringOrNull()
            ?: readTextField(pageHeader["pageTitle"])
    }

    private fun findPageHeaderAuthor(header: JsonObject?): String? {
        val pageHeader = header?.get("pageHeaderRenderer") as? JsonObject ?: return null
        val content = pageHeader["content"]?.asObjOrNull()?.get("pageHeaderViewModel")?.asObjOrNull() ?: return null
        val rows = content["metadata"]?.asObjOrNull()?.get("contentMetadataViewModel")
            ?.asObjOrNull()?.get("metadataRows") as? JsonArray ?: return null
        val firstRow = rows.firstOrNull() as? JsonObject ?: return null
        val parts = firstRow["metadataParts"] as? JsonArray ?: return null
        return parts.firstOrNull()?.asObjOrNull()?.get("text")?.asObjOrNull()?.get("content")?.asStringOrNull()
            ?.removePrefix("by ")
            ?.trim()
    }

    private fun findPageHeaderThumbnail(header: JsonObject?): String? {
        val pageHeader = header?.get("pageHeaderRenderer") as? JsonObject ?: return null
        val content = pageHeader["content"]?.asObjOrNull()?.get("pageHeaderViewModel")?.asObjOrNull() ?: return null
        val hero = content["heroImage"]?.asObjOrNull()?.get("contentPreviewImageViewModel")
            ?.asObjOrNull()?.get("image")
        return extractBestThumbnailUrl(hero)
    }

    private fun extractPlaylistItemsRecursively(
        element: JsonElement,
        out: MutableList<ExtractedPlaylistItem>
    ) {
        when (element) {
            is JsonObject -> {
                element["lockupViewModel"]?.asObjOrNull()?.let { lockup ->
                    parseLockupViewModel(lockup)?.let { out.add(it) }
                }
                element["playlistVideoRenderer"]?.asObjOrNull()?.let { renderer ->
                    parsePlaylistVideoRenderer(renderer)?.let { out.add(it) }
                }
                element["playlistPanelVideoRenderer"]?.asObjOrNull()?.let { renderer ->
                    parsePlaylistVideoRenderer(renderer)?.let { out.add(it) }
                }
                element.values.forEach { child ->
                    extractPlaylistItemsRecursively(child, out)
                }
            }
            is JsonArray -> {
                element.forEach { child ->
                    extractPlaylistItemsRecursively(child, out)
                }
            }
            else -> Unit
        }
    }

    private fun parseLockupViewModel(lockup: JsonObject): ExtractedPlaylistItem? {
        val videoId = lockup["contentId"]?.asStringOrNull()?.takeIf { it.length == 11 } ?: return null
        val lockupMeta = lockup["metadata"]?.asObjOrNull()?.get("lockupMetadataViewModel")?.asObjOrNull()
        val title = lockupMeta?.get("title")?.asObjOrNull()?.get("content")?.asStringOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null

        val uploader = lockupMeta["metadata"]?.asObjOrNull()
            ?.get("contentMetadataViewModel")?.asObjOrNull()
            ?.get("metadataRows")?.let { it as? JsonArray }
            ?.firstOrNull()?.asObjOrNull()
            ?.get("metadataParts")?.let { it as? JsonArray }
            ?.firstOrNull()?.asObjOrNull()
            ?.get("text")?.asObjOrNull()
            ?.get("content")?.asStringOrNull()
            .orEmpty()

        val thumbModel = lockup["contentImage"]?.asObjOrNull()?.get("thumbnailViewModel")?.asObjOrNull()
        val thumbnailUrl = extractBestThumbnailUrl(thumbModel?.get("image"))
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val durationMs = extractDurationFromThumbnailOverlays(thumbModel?.get("overlays"))

        return ExtractedPlaylistItem(
            videoId = videoId,
            videoUrl = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs
        )
    }

    private fun extractDurationFromThumbnailOverlays(overlaysElement: JsonElement?): Long {
        val overlays = overlaysElement as? JsonArray ?: return 0L
        for (overlay in overlays) {
            val badge = overlay.asObjOrNull()
                ?.get("thumbnailBottomOverlayViewModel")?.asObjOrNull()
                ?.get("badges")?.let { it as? JsonArray }
                ?.firstOrNull()?.asObjOrNull()
                ?.get("thumbnailBadgeViewModel")?.asObjOrNull()
                ?: continue
            val text = badge["text"]?.asStringOrNull()
            val parsed = parseDurationString(text)
            if (parsed > 0L) return parsed

            val accessLabel = badge["rendererContext"]?.asObjOrNull()
                ?.get("accessibilityContext")?.asObjOrNull()
                ?.get("label")?.asStringOrNull()
            val parsedLabel = parseSpokenDuration(accessLabel)
            if (parsedLabel > 0L) return parsedLabel
        }
        return 0L
    }

    private fun parsePlaylistVideoRenderer(renderer: JsonObject): ExtractedPlaylistItem? {
        val videoId = renderer["videoId"]?.asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val title = readTextField(renderer["title"])?.takeIf { it.isNotBlank() } ?: return null
        val uploader = readTextField(renderer["shortBylineText"])
            ?: readTextField(renderer["longBylineText"])
            ?: ""

        val seconds = renderer["lengthSeconds"]?.asStringOrNull()?.toLongOrNull()
        val durationMs = if (seconds != null && seconds > 0L) {
            seconds * 1000L
        } else {
            parseDurationString(readTextField(renderer["lengthText"]))
        }

        val thumb = extractBestThumbnailUrl(renderer["thumbnail"])
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return ExtractedPlaylistItem(
            videoId = videoId,
            videoUrl = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            uploader = uploader,
            thumbnailUrl = thumb,
            durationMs = durationMs
        )
    }

    private fun findPlaylistContinuationToken(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                element["continuationItemViewModel"]?.asObjOrNull()?.let { civm ->
                    val token = civm["continuationCommand"]?.asObjOrNull()
                        ?.get("innertubeCommand")?.asObjOrNull()
                        ?.get("continuationCommand")?.asObjOrNull()
                        ?.get("token")?.asStringOrNull()
                    if (!token.isNullOrBlank() && !token.contains("FEcomment")) {
                        return token
                    }
                }
                element["continuationItemRenderer"]?.asObjOrNull()?.let { cir ->
                    val cmd = cir["continuationEndpoint"]?.asObjOrNull()
                        ?.get("continuationCommand")?.asObjOrNull()
                    val reqType = cmd?.get("request")?.asStringOrNull()
                    val token = cmd?.get("token")?.asStringOrNull()
                    if (!token.isNullOrBlank() && reqType == "CONTINUATION_REQUEST_TYPE_BROWSE" && !token.contains("FEcomment")) {
                        return token
                    }
                }
                for (value in element.values) {
                    val found = findPlaylistContinuationToken(value)
                    if (found != null) return found
                }
            }
            is JsonArray -> {
                for (value in element) {
                    val found = findPlaylistContinuationToken(value)
                    if (found != null) return found
                }
            }
            else -> Unit
        }
        return null
    }

    suspend fun fetchSingleVideoMetadata(rawInput: String): ExtractedPlaylistItem =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val normalizedUrl = YouTubeUrlParser.normalizeVideoUrl(rawInput)
                ?: throw IllegalArgumentException("Invalid YouTube video URL.")
            val videoId = YouTubeUrlParser.extractVideoId(normalizedUrl)
                ?: throw IllegalArgumentException("Could not extract YouTube video ID.")

            val newPipeItem = runCatching {
                val info = StreamInfo.getInfo(ServiceList.YouTube, normalizedUrl)
                ExtractedPlaylistItem(
                    videoId = videoId,
                    videoUrl = normalizedUrl,
                    title = info.name?.takeIf { it.isNotBlank() } ?: "YouTube Audio",
                    uploader = info.uploaderName.orEmpty(),
                    thumbnailUrl = pickBestThumbnail(info.thumbnails)
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    durationMs = if (info.duration > 0L) info.duration * 1000L else 0L
                )
            }.getOrNull()
            if (newPipeItem != null) return@withContext newPipeItem

            // Fallback to YouTube oEmbed
            val oEmbedReq = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$normalizedUrl&format=json")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .build()
            val oEmbed = OkHttpDownloader.sharedClient.newCall(oEmbedReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Could not load video metadata (HTTP ${resp.code})")
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            }

            ExtractedPlaylistItem(
                videoId = videoId,
                videoUrl = normalizedUrl,
                title = oEmbed["title"]?.asStringOrNull() ?: "YouTube Audio",
                uploader = oEmbed["author_name"]?.asStringOrNull().orEmpty(),
                thumbnailUrl = oEmbed["thumbnail_url"]?.asStringOrNull()
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationMs = 0L
            )
        }

    suspend fun resolveAudioStream(
        videoUrl: String,
        preferInnerTubeFallback: Boolean = false
    ): ResolvedAudioStream =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val normalizedUrl = YouTubeUrlParser.normalizeVideoUrl(videoUrl) ?: videoUrl
            val videoId = YouTubeUrlParser.extractVideoId(normalizedUrl)
            val failureReasons = mutableListOf<String>()

            if (!preferInnerTubeFallback) {
                // Strategy 1: NewPipeExtractor v0.27.1
                val npAttempt = runCatching {
                    val info = StreamInfo.getInfo(ServiceList.YouTube, normalizedUrl)
                    val progressiveAudio = info.audioStreams
                        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.startsWith("http") }
                    val candidates = progressiveAudio.ifEmpty {
                        info.audioStreams.filter { it.content.startsWith("http") }
                    }
                    val chosen = selectBestAudioStream(candidates)
                        ?: throw IllegalStateException("No audio stream returned by extractor")
                    val format = chosen.format
                    val streamUrl = chosen.content
                    val contentLen = runCatching { chosen.itagItem?.contentLength ?: 0L }.getOrDefault(0L)
                        .takeIf { it > 0L } ?: extractClenFromUrl(streamUrl)
                    ResolvedAudioStream(
                        streamUrl = streamUrl,
                        fileExtension = format?.suffix?.takeIf { it.isNotBlank() } ?: "m4a",
                        mimeType = format?.mimeType?.takeIf { it.isNotBlank() } ?: "audio/mp4",
                        durationMs = if (info.duration > 0L) info.duration * 1000L else 0L,
                        title = info.name.orEmpty(),
                        uploader = info.uploaderName.orEmpty(),
                        thumbnailUrl = pickBestThumbnail(info.thumbnails),
                        requestHeaders = headersForStreamUrl(streamUrl),
                        contentLengthBytes = contentLen
                    )
                }
                npAttempt.getOrNull()?.let { return@withContext it }
                npAttempt.exceptionOrNull()?.let { err ->
                    failureReasons += "Extractor: ${err.message ?: err.javaClass.simpleName}"
                }
            }

            // Strategy 2: InnerTube mobile & VR player clients
            if (videoId != null) {
                val clients = listOf(
                    InnerTubePlayerClient(
                        clientName = "ANDROID_VR",
                        clientVersion = "1.65.10",
                        userAgent = ANDROID_VR_USER_AGENT,
                        extraJson = "\"deviceModel\": \"Quest 3\", \"osName\": \"Android\", \"osVersion\": \"12L\""
                    ),
                    InnerTubePlayerClient(
                        clientName = "IOS",
                        clientVersion = "20.10.4",
                        userAgent = IOS_USER_AGENT,
                        extraJson = "\"deviceModel\": \"iPhone16,2\", \"osName\": \"iPhone\", \"osVersion\": \"18.3.1\""
                    ),
                    InnerTubePlayerClient(
                        clientName = "ANDROID",
                        clientVersion = "20.10.38",
                        userAgent = ANDROID_USER_AGENT,
                        extraJson = "\"androidSdkVersion\": 34, \"osName\": \"Android\", \"osVersion\": \"14\""
                    )
                )
                for (client in clients) {
                    val attempt = runCatching { resolveViaInnerTubePlayer(videoId, client) }
                    attempt.getOrNull()?.let { return@withContext it }
                    attempt.exceptionOrNull()?.let { err ->
                        failureReasons += "${client.clientName}: ${err.message ?: err.javaClass.simpleName}"
                    }
                }
            }

            val summary = failureReasons.firstOrNull { it.isNotBlank() }
                ?: "Could not resolve playable audio stream"
            throw IllegalStateException(summary)
        }

    private data class InnerTubePlayerClient(
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val extraJson: String
    )

    private fun resolveViaInnerTubePlayer(
        videoId: String,
        client: InnerTubePlayerClient
    ): ResolvedAudioStream {
        val payload = """
            {
              "context": {
                "client": {
                  "clientName": "${client.clientName}",
                  "clientVersion": "${client.clientVersion}",
                  ${client.extraJson},
                  "hl": "en",
                  "gl": "US"
                }
              },
              "playbackContext": {
                "contentPlaybackContext": {
                  "html5Preference": "HTML5_PREF_WANTS"
                }
              },
              "contentCheckOk": true,
              "racyCheckOk": true,
              "videoId": "$videoId"
            }
        """.trimIndent()

        val root = postInnerTubeJson(
            url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            jsonBody = payload,
            userAgent = client.userAgent
        ).asObjOrNull() ?: throw IllegalStateException("Empty player response")

        val streamingData = root["streamingData"]?.asObjOrNull()
        if (streamingData == null) {
            val playability = root["playabilityStatus"]?.asObjOrNull()
            val status = playability?.get("status")?.asStringOrNull().orEmpty()
            val reason = playability?.get("reason")?.asStringOrNull().orEmpty()
            val msg = listOf(status, reason).filter { it.isNotBlank() }.joinToString(": ")
                .ifBlank { "No streaming data returned" }
            throw IllegalStateException(msg)
        }

        val adaptiveFormats = streamingData["adaptiveFormats"] as? JsonArray ?: JsonArray(emptyList())
        val muxedFormats = streamingData["formats"] as? JsonArray ?: JsonArray(emptyList())

        val audioFormats = adaptiveFormats.mapNotNull { it.asObjOrNull() }
            .filter {
                val mime = it["mimeType"]?.asStringOrNull().orEmpty()
                val url = it["url"]?.asStringOrNull().orEmpty()
                mime.startsWith("audio/") && url.startsWith("http")
            }

        val chosen = audioFormats
            .filter { it["mimeType"]?.asStringOrNull().orEmpty().contains("audio/mp4") }
            .maxByOrNull { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?: audioFormats.maxByOrNull { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?: muxedFormats.mapNotNull { it.asObjOrNull() }
                .firstOrNull { it["url"]?.asStringOrNull()?.startsWith("http") == true }
            ?: throw IllegalStateException("Stream ciphered or missing playable audio URL")

        val streamUrl = chosen["url"]?.asStringOrNull()
            ?: throw IllegalStateException("Missing audio URL")
        val mimeType = chosen["mimeType"]?.asStringOrNull()?.substringBefore(";") ?: "audio/mp4"
        val ext = when {
            mimeType.contains("webm") -> "webm"
            mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
            else -> "m4a"
        }
        val contentLen = chosen["contentLength"]?.asStringOrNull()?.toLongOrNull()
            ?: extractClenFromUrl(streamUrl)
        val videoDetails = root["videoDetails"]?.asObjOrNull()
        val lengthSec = videoDetails?.get("lengthSeconds")?.asStringOrNull()?.toLongOrNull() ?: 0L

        return ResolvedAudioStream(
            streamUrl = streamUrl,
            fileExtension = ext,
            mimeType = mimeType,
            durationMs = lengthSec * 1000L,
            title = videoDetails?.get("title")?.asStringOrNull().orEmpty(),
            uploader = videoDetails?.get("author")?.asStringOrNull().orEmpty(),
            thumbnailUrl = extractBestThumbnailUrl(videoDetails?.get("thumbnail")),
            requestHeaders = mapOf("User-Agent" to client.userAgent),
            contentLengthBytes = contentLen
        )
    }

    private fun headersForStreamUrl(streamUrl: String): Map<String, String> {
        val clientTag = Regex("[?&]c=([^&]+)").find(streamUrl)?.groupValues?.get(1)?.uppercase().orEmpty()
        return when {
            clientTag.contains("IOS") -> mapOf("User-Agent" to IOS_USER_AGENT)
            clientTag.contains("ANDROID_VR") -> mapOf("User-Agent" to ANDROID_VR_USER_AGENT)
            clientTag.contains("ANDROID") -> mapOf("User-Agent" to ANDROID_USER_AGENT)
            else -> mapOf(
                "User-Agent" to DESKTOP_USER_AGENT,
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            )
        }
    }

    private fun postInnerTubeJson(
        url: String,
        jsonBody: String,
        userAgent: String = DESKTOP_USER_AGENT
    ): JsonElement {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .header("Origin", "https://www.youtube.com")
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        OkHttpDownloader.sharedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("InnerTube HTTP ${response.code}")
            }
            val bodyStr = response.body?.string().orEmpty()
            return json.parseToJsonElement(bodyStr)
        }
    }

    private fun selectBestAudioStream(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        val m4aStreams = streams.filter {
            it.format == MediaFormat.M4A || it.format?.suffix.equals("m4a", ignoreCase = true)
        }
        if (m4aStreams.isNotEmpty()) {
            return m4aStreams.maxByOrNull { it.averageBitrate }
        }
        return streams.maxByOrNull { it.averageBitrate }
    }

    private fun pickBestThumbnail(thumbnails: List<Image>?): String? {
        if (thumbnails.isNullOrEmpty()) return null
        return thumbnails
            .sortedByDescending { it.width * it.height }
            .firstOrNull { it.url.isNotBlank() }
            ?.url
    }

    private fun extractBestThumbnailUrl(container: JsonElement?): String? {
        val obj = container as? JsonObject ?: return null
        val list = (obj["thumbnails"] ?: obj["sources"]) as? JsonArray ?: return null
        return list.mapNotNull { it as? JsonObject }
            .sortedByDescending {
                (it["width"]?.jsonPrimitive?.intOrNull ?: 0) *
                    (it["height"]?.jsonPrimitive?.intOrNull ?: 0)
            }
            .firstNotNullOfOrNull { it["url"]?.asStringOrNull()?.takeIf { u -> u.isNotBlank() } }
    }

    private fun readTextField(element: JsonElement?): String? {
        val obj = element as? JsonObject ?: return null
        obj["simpleText"]?.asStringOrNull()?.let { if (it.isNotBlank()) return it }
        obj["content"]?.asStringOrNull()?.let { if (it.isNotBlank()) return it }
        val runs = obj["runs"] as? JsonArray ?: return null
        val combined = runs.mapNotNull { (it as? JsonObject)?.get("text")?.asStringOrNull() }
            .joinToString("")
        return combined.takeIf { it.isNotBlank() }
    }

    private fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val WEB_CLIENT_VERSION = "2.20260101.01.00"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        private const val IOS_USER_AGENT =
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_1 like Mac OS X;)"
        private const val ANDROID_USER_AGENT =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14; en_US) gzip"

        fun extractClenFromUrl(url: String): Long {
            return Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        }

        fun parseDurationString(raw: String?): Long {
            val cleaned = raw?.trim()?.takeIf { it.isNotBlank() } ?: return 0L
            val parts = cleaned.split(":").mapNotNull { it.trim().toLongOrNull() }
            return when (parts.size) {
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                2 -> (parts[0] * 60L + parts[1]) * 1000L
                1 -> parts[0] * 1000L
                else -> 0L
            }
        }

        fun parseSpokenDuration(raw: String?): Long {
            val text = raw?.lowercase() ?: return 0L
            var totalSeconds = 0L
            Regex("(\\d+)\\s*hour").find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { totalSeconds += it * 3600L }
            Regex("(\\d+)\\s*minute").find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { totalSeconds += it * 60L }
            Regex("(\\d+)\\s*second").find(text)?.groupValues?.get(1)?.toLongOrNull()?.let { totalSeconds += it }
            return totalSeconds * 1000L
        }
    }
}
