package com.sf.tadami.extension.fr.ianimes.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.POST
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Odysee embeds (odysee.com/$/embed/@Channel:x/Name:y). The LBRY `get` method of Odysee's API
 * returns the stream URL of a claim, the same call its own player makes.
 */
class OdyseeExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val json: Json,
) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String): List<StreamSource> {
        val claim = claimPath(url) ?: throw IOException("Odysee: unrecognised embed URL $url")

        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "get")
            putJsonObject("params") {
                put("uri", "lbry://$claim")
                put("save_file", false)
            }
        }.toString().toRequestBody("application/json".toMediaType())
        val apiHeaders = headers.newBuilder()
            .set("Origin", ODYSEE)
            .set("Referer", "$ODYSEE/")
            .build()

        val response = client.newCall(POST(API_URL, apiHeaders, body)).execute().use {
            json.parseToJsonElement(it.body.string()).jsonObject
        }
        val streamUrl = (response["result"] as? JsonObject)?.string("streaming_url")
            ?: throw IOException(
                "Odysee: ${(response["error"] as? JsonObject)?.string("message") ?: "no stream for $claim"}"
            )

        val streamHeaders = headers.newBuilder().set("Referer", "$ODYSEE/").build()
        return if (".m3u8" in streamUrl) {
            playlistUtils.extractFromHls(
                playlistUrl = streamUrl,
                referer = "$ODYSEE/",
                videoNameGen = { quality -> "$SERVER - $quality" },
            ).map { it.copy(server = SERVER) }
        } else {
            listOf(StreamSource(url = streamUrl, fullName = SERVER, server = SERVER, headers = streamHeaders))
        }
    }

    /**
     * "@Channel:e/Name:7" from "https://odysee.com/%24/embed/%40Channel%3Ae%2FName%3A7?r=...", or
     * "Name:5" from "https://odysee.com/$/embed/Name:5", with LBRY's `#` claim id separator.
     */
    private fun claimPath(url: String): String? {
        val segments = url.toHttpUrlOrNull()?.pathSegments ?: return null
        val embedIndex = segments.indexOf("embed")
        if (embedIndex < 0) return null
        return segments.drop(embedIndex + 1)
            .filter { it.isNotEmpty() }
            .joinToString("/")
            .replace(':', '#')
            .takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        const val SERVER = "Odysee"
        private const val ODYSEE = "https://odysee.com"
        private const val API_URL = "https://api.na-backend.odysee.com/api/v1/proxy?m=get"
    }
}
