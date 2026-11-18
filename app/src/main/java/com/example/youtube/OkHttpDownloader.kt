package com.example.youtube

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor(
    val client: OkHttpClient
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = request.url()
        val httpMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.let { bytes ->
            val mimeType = headers["Content-Type"]?.firstOrNull() ?: "application/octet-stream"
            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        }

        val okHeadersBuilder = Headers.Builder()
        var hasUserAgent = false
        headers.forEach { (key, values) ->
            if (key.equals("User-Agent", ignoreCase = true)) {
                hasUserAgent = true
            }
            values.forEach { value ->
                okHeadersBuilder.add(key, value)
            }
        }
        if (!hasUserAgent) {
            okHeadersBuilder.add("User-Agent", DEFAULT_USER_AGENT)
        }

        val okHttpRequest = okhttp3.Request.Builder()
            .url(url)
            .headers(okHeadersBuilder.build())
            .method(httpMethod, requestBody)
            .build()

        client.newCall(okHttpRequest).execute().use { okHttpResponse ->
            if (okHttpResponse.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            val responseBodyString = okHttpResponse.body?.string().orEmpty()
            return Response(
                okHttpResponse.code,
                okHttpResponse.message,
                okHttpResponse.headers.toMultimap(),
                responseBodyString,
                okHttpResponse.request.url.toString()
            )
        }
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        val instance: OkHttpDownloader by lazy {
            OkHttpDownloader(sharedClient)
        }
    }
}
