package com.sf.tadami.lib.megacloudextractor

import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import com.sf.tadami.ui.utils.detectSubtitleFormat
import com.sf.tadami.ui.utils.parallelMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder


// Thanks to https://github.com/yogesh-hacker/MediaVanced/
// Keys fetched from https://github.com/yogesh-hacker/MegacloudKeys/

class MegaCloudExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val megaCloudAPI: String,
) {
    private val json: Json by injectLazy()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    companion object {
        private const val SOURCES_URL = "/embed-2/v3/e-1/getSources?id="
        private const val SOURCES_SPLITTER = "/e-1/"
    }

    fun getVideosFromUrl(url: String, type: String, name: String): List<StreamSource> {
        val video = getVideoDto(url)

        val masterUrl = video.m3u8
        val subs2 = video.tracks
            ?.filter { it.kind == "captions" }
            ?.parallelMap {
                val mimetype = detectSubtitleFormat(it.file)
                Track.SubtitleTrack(url = it.file, lang = it.label, mimeType = mimetype)
            }
            .orEmpty()
            .let { playlistUtils.fixSubtitles(it) }

        return playlistUtils.extractFromHls(
            playlistUrl = masterUrl,
            videoNameGen = { "$name - $type - $it" },
            referer = "https://${url.toHttpUrl().host}/",
            subtitleList = subs2
        ).map {
            it.copy(server = "MegaCloud")
        }
    }

    private fun getVideoDto(url: String): VideoDto {
        val id = url.substringAfter(SOURCES_SPLITTER, "")
            .substringBefore("?", "")
            .ifEmpty { throw Exception("Failed to extract ID from URL") }

        val host = runCatching {
            url.toHttpUrl().host
        }.getOrNull() ?: throw IllegalStateException("MegaCloud host is invalid: $url")

        val megaCloudServerUrl = "https://$host"

        val megaCloudHeaders = headers.newBuilder()
            .add("Accept", "*/*")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Referer", "${megaCloudServerUrl}/")
            .build()

        val responseNonce = client.newCall(GET(url, megaCloudHeaders))
            .execute().use { it.body.string() }
        val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(responseNonce)
        val match2 = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""").find(responseNonce)

        val nonce = match1?.value ?: match2?.let {
            it.groupValues[1] + it.groupValues[2] + it.groupValues[3]
        } ?: throw IllegalStateException("Failed to extract nonce from response")

        val srcRes = client.newCall(GET("${megaCloudServerUrl}${SOURCES_URL}${id}&_k=${nonce}", megaCloudHeaders))
            .execute().use { it.body.string() }

        val data = json.decodeFromString<SourceResponseDto>(srcRes)
        val encoded = data.sources.jsonPrimitive.content
        val key = requestNewKey()

        val m3u8: String = if (".m3u8" in encoded) {
            encoded
        } else {
            val fullUrl = buildString {
                append(megaCloudAPI)
                append("?encrypted_data=").append(URLEncoder.encode(encoded, "UTF-8"))
                append("&nonce=").append(URLEncoder.encode(nonce, "UTF-8"))
                append("&secret=").append(URLEncoder.encode(key, "UTF-8"))
            }

            val decryptedResponse = client.newCall(GET(fullUrl))
                .execute().use { it.body.string() }
            Regex("\"file\":\"(.*?)\"")
                .find(decryptedResponse)
                ?.groupValues?.get(1)
                ?: throw Exception("Video URL not found in decrypted response")
        }

        return VideoDto(m3u8, data.tracks)
    }

    private fun requestNewKey(): String =
        client.newCall(GET("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json"))
            .execute()
            .use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Failed to fetch keys.json")
                val jsonStr = response.body.string()
                if (jsonStr.isEmpty()) throw IllegalStateException("keys.json is empty")
                val key = json.decodeFromString<Map<String, String>>(jsonStr)["mega"]
                    ?: throw IllegalStateException("Mega key not found in keys.json")
                Log.i("MegaCloudExtractor", "Using Mega Key: $key")
                key
            }

    @Serializable
    data class VideoDto(
        val m3u8: String = "",
        val tracks: List<TrackDto>? = null,
    )

    @Serializable
    data class SourceResponseDto(
        val sources: JsonElement,
        val encrypted: Boolean = true,
        val tracks: List<TrackDto>? = null,
    )

    @Serializable
    data class TrackDto(val file: String, val kind: String, val label: String = "")
}
