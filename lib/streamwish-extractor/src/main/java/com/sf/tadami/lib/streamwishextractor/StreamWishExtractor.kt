package com.sf.tadami.lib.streamwishextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import dev.datlag.jsunpacker.JsUnpacker
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient

class StreamWishExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun videosFromUrl(url: String, prefix: String) = videosFromUrl(url) { "$prefix - $it" }

    fun videosFromUrl(url: String, videoNameGen: (String) -> String = { quality -> "StreamWish - $quality" }): List<StreamSource> {

        val doc = client.newCall(GET(getEmbedUrl(url), headers)).execute().asJsoup()

        val scriptBody = doc.selectFirst("script:containsData(m3u8)")?.data()
            ?.let { script ->
                if (script.contains("eval(function(p,a,c")) {
                    JsUnpacker.unpackAndCombine(script)
                } else {
                    script
                }
            }
        val masterUrl = scriptBody
            ?.substringAfter("source", "")
            ?.substringAfter("file:\"", "")
            ?.substringBefore("\"", "")
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()

        val subtitleList = extractSubtitles(scriptBody)

        val streamSources = playlistUtils.extractFromHls(masterUrl, url, videoNameGen = videoNameGen,subtitleList = subtitleList)
        return streamSources.map {
            it.copy(
                server = "StreamWish"
            )
        }
    }

    private fun getEmbedUrl(url: String): String {
        return if (url.contains("/f/")) {
            val videoId = url.substringAfter("/f/")
            "https://streamwish.com/$videoId"
        } else {
            url
        }
    }

    private fun extractSubtitles(script: String): List<Track.SubtitleTrack> {
        return try {
            val subtitleStr = script
                .substringAfter("tracks")
                .substringAfter("[")
                .substringBefore("]")
            json.decodeFromString<List<TrackDto>>("[$subtitleStr]")
                .filter { it.kind.equals("captions", true) }
                .map { Track.SubtitleTrack(url = it.file, lang = it.label ?: "", mimeType = "text/vtt") }
        } catch (e: SerializationException) {
            emptyList()
        }
    }

    @Serializable
    private data class TrackDto(val file: String, val kind: String, val label: String? = null)
}