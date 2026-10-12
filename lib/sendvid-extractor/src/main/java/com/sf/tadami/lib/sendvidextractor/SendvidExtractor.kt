package com.sf.tadami.lib.sendvidextractor

import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class SendvidExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String, prefix: String = "", suffix: String = ""): List<StreamSource> {
        val document = client.newCall(GET(url, headers)).execute().use { it.asJsoup() }
        // Empty while Sendvid is still encoding the upload
        val videoUrl = document.selectFirst("source#video_source")?.attr("abs:src")
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        if (".m3u8" in videoUrl) {
            return playlistUtils.extractFromHls(videoUrl, url, videoNameGen = { prefix + "Sendvid - $it" }).map {
                it.copy(server = "Sendvid")
            }
        }

        // Old uploads are answered with a 5 s "This video is temporarily unavailable" clip (about 37 kB)
        // instead of an error, so a tiny file is not the episode
        val size = client.newCall(GET(videoUrl, headers.newBuilder().set("Range", "bytes=0-0").build()))
            .execute()
            .use { it.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() }
        if (size != null && size < PLACEHOLDER_MAX_SIZE) {
            Log.w("SendvidExtractor", "Placeholder clip instead of the video ($size bytes): $url")
            return emptyList()
        }

        val quality = document.selectFirst("meta[property=og:video:height]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?.let { "${it}p" }
            ?: ""
        val name = listOf(prefix.trim(), "Sendvid", quality, suffix.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return listOf(StreamSource(url = videoUrl, fullName = name, quality = quality, server = "Sendvid", headers = headers))
    }

    companion object {
        private const val PLACEHOLDER_MAX_SIZE = 1_000_000L
    }
}
