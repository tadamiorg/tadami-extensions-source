package com.sf.tadami.lib.lpayerextractor

import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The embed4me player (lpayer.embed4me.com/#<id>): its API answers AES-encrypted JSON listing the stream
 * on several delivery routes, in the order and with the adjustments of its streamingConfig.
 */
class LpayerExtractor(private val client: OkHttpClient) {

    private val playlistUtils by lazy { PlaylistUtils(client) }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** [siteUrl] is the page embedding the player, sent to the API as the player does. */
    fun videosFromUrl(videoUrl: String, prefix: String = "LPayer - ", siteUrl: String? = null): List<StreamSource> {
        return runCatching {
            val url = videoUrl.trim().toHttpUrl()
            val id = url.fragment?.substringBefore("&")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return emptyList()
            val origin = "${url.scheme}://${url.host}"
            val referer = "$origin/"

            val apiUrl = "$origin/api/v1/video".toHttpUrl().newBuilder()
                .addQueryParameter("id", id)
                .addQueryParameter("w", "1920")
                .addQueryParameter("h", "1080")
                .addQueryParameter("r", siteUrl?.toHttpUrlOrNull()?.host.orEmpty())
                .build()
            val body = client.newCall(GET(apiUrl.toString(), Headers.headersOf("Referer", referer))).execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "API answered HTTP ${response.code} for $videoUrl")
                        return emptyList()
                    }
                    response.body.string()
                }

            val data = decrypt(body)?.let { json.parseToJsonElement(it) as? JsonObject } ?: run {
                Log.w(TAG, "Could not decrypt the API response for $videoUrl")
                return emptyList()
            }

            playlistUrls(data, origin.toHttpUrl()).flatMap { playlistUrl ->
                runCatching {
                    playlistUtils.extractFromHls(playlistUrl, referer = referer, videoNameGen = { "$prefix$it" })
                        .map { it.copy(server = "LPayer") }
                }.onFailure { Log.w(TAG, "Could not load $playlistUrl", it) }
                    .getOrNull()
                    .orEmpty()
            }
        }.onFailure { Log.e(TAG, "Could not extract $videoUrl", it) }
            .getOrElse { emptyList() }
    }

    /** The body is hex AES-CBC ciphertext (older answers were plain JSON). */
    private fun decrypt(body: String): String? {
        val text = body.trim().removeSurrounding("\"").filterNot { it.isWhitespace() }
        if (text.startsWith("{")) return text
        if (text.isEmpty() || text.length % 2 != 0 || !HEX_REGEX.matches(text)) return null
        return runCatching {
            val encrypted = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(AES_KEY, "AES"), IvParameterSpec(AES_IV))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * The routes in the player's order, as it adjusts them: skipped when disabled, query parameters added,
     * /hls/ proxied through /hlsmod/<domain>/, and the pk keys on /v4/ links. The Cloudflare route ("cf")
     * is left out: its variants need tokens on every request, which the playlist parser doesn't carry.
     */
    private fun playlistUrls(data: JsonObject, origin: HttpUrl): List<String> {
        val config = data.objectOrEncoded("streamingConfig")
        val order = (config?.get("order") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        val adjust = config?.get("adjust") as? JsonObject
        val pk = data.objectOrEncoded("pk")

        val urls = (order + DEFAULT_ORDER).distinct().mapNotNull { route ->
            var link = ROUTES[route]?.let { data.string(it) } ?: return@mapNotNull null
            val adjustment = adjust?.get(route) as? JsonObject
            if ((adjustment?.get("disabled") as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
            adjustment?.string("domain")?.let { domain ->
                if ("/hls/" in link) link = link.replace("/hls/", "/hlsmod/$domain/")
            }
            val builder = origin.resolve(link)?.newBuilder() ?: return@mapNotNull null
            (adjustment?.get("params") as? JsonObject)?.forEach { (name, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { builder.setQueryParameter(name, it) }
            }
            if ("/v4/" in link) {
                pk?.string("k")?.let { builder.setQueryParameter("k", it) }
                pk?.string("kx")?.let { builder.setQueryParameter("kx", it) }
            }
            builder.build().toString()
        }

        // Older answers had a single link
        val legacy = listOf("hls", "url", "file").mapNotNull { data.string(it) }
            .mapNotNull { origin.resolve(it)?.toString() }
        return (urls + legacy).distinct()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** streamingConfig and pk come as JSON objects encoded in a string. */
    private fun JsonObject.objectOrEncoded(key: String): JsonObject? {
        return when (val value: JsonElement? = this[key]) {
            is JsonObject -> value
            is JsonPrimitive -> value.contentOrNull
                ?.takeIf { it.trimStart().startsWith("{") }
                ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            else -> null
        }
    }

    companion object {
        private const val TAG = "LpayerExtractor"
        private val AES_KEY = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
        private val AES_IV = "1234567890oiuytr".toByteArray(Charsets.UTF_8)
        private val HEX_REGEX = Regex("^[0-9A-Fa-f]+$")
        private val DEFAULT_ORDER = listOf("Tiktok", "Google", "Cloudflare", "In-House")
        private val ROUTES = mapOf("Tiktok" to "hlsVideoTiktok", "Google" to "hlsVideoGoogle", "In-House" to "source")
    }
}
