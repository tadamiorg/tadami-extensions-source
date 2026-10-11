package com.sf.tadami.extension.fr.ianimes.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.POST
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Vidara (vidara.to/e/<filecode>): the player page asks `POST /api/stream` for the HLS master,
 * with the file code and the device type in a JSON body.
 */
class VidaraExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val json: Json,
) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String): List<StreamSource> {
        val httpUrl = url.toHttpUrl()
        val origin = "https://${httpUrl.host}"
        val fileCode = httpUrl.pathSegments.lastOrNull { it.isNotBlank() }
            ?: throw IOException("Vidara: no file code in $url")

        val body = buildJsonObject {
            put("filecode", fileCode)
            put("device", "android")
        }.toString().toRequestBody("application/json".toMediaType())
        val apiHeaders = headers.newBuilder()
            .set("Referer", url)
            .set("Origin", origin)
            .build()

        val data = client.newCall(POST("$origin/api/stream", apiHeaders, body)).execute().use {
            json.parseToJsonElement(it.body.string()).jsonObject
        }
        val playlistUrl = data.string("streaming_url")
            ?: throw IOException("Vidara: ${data.string("error") ?: "no stream"}")

        val subtitles = (data["subtitles"] as? JsonArray).orEmpty().mapNotNull { element ->
            val subtitle = element as? JsonObject ?: return@mapNotNull null
            // type 0 are the captions the player shows, the others are burnt-in or forced tracks
            if ((subtitle["type"]?.jsonPrimitive?.intOrNull ?: 0) != 0) return@mapNotNull null
            val file = subtitle.string("file_path") ?: return@mapNotNull null
            Track.SubtitleTrack(url = file, lang = subtitle.string("language") ?: "Subtitle")
        }

        return playlistUtils.extractFromHls(
            playlistUrl = playlistUrl,
            referer = "$origin/",
            videoNameGen = { quality -> "$SERVER - $quality" },
            subtitleList = subtitles,
        ).map { it.copy(server = SERVER) }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        const val SERVER = "Vidara"
    }
}
