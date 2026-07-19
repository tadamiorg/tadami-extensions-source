package com.sf.tadami.extension.en.kickassanime.extractors

import com.sf.tadami.extension.en.kickassanime.dto.VideoDto
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import com.sf.tadami.ui.utils.detectSubtitleFormat
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.security.MessageDigest

/**
 * Resolves a KickAssAnime player URL into a playable [StreamSource].
 *
 * The app's ExoPlayer only consumes [StreamSource.url] (+ subtitles); it does NOT merge a separate
 * audio group. KAA streams are demuxed HLS (video-only variants + a separate audio group), so we emit
 * the **master** manifest URL directly and let ExoPlayer handle audio + adaptive quality.
 */
class KickAssAnimeExtractor(
    private val client: OkHttpClient,
    private val json: Json,
    private val headers: Headers,
) {
    private fun getVideoHeaders(url: String): Headers {
        val host = url.toHttpUrl().host
        return headers.newBuilder()
            .set("Accept", "*/*")
            .set("Accept-Language", "en-US,en;q=0.9")
            .set("Origin", "https://$host")
            .set("Referer", "https://$host/")
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "same-site")
            .build()
    }

    private fun subMimeType(url: String): String = runBlocking { detectSubtitleFormat(url) }

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    private val httpRegex by lazy { Regex("""^(https?:)//+""") }

    /** Normalizes `https:////host`, `//host`, `/path`, and absolute URLs. */
    private fun fixUrl(rawUrl: String, baseUrl: String): String {
        val trimmed = rawUrl.trim().replace("\\/", "/")
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") ->
                trimmed.replace(httpRegex, "$1//")
            trimmed.startsWith("//") -> "https://${trimmed.substring(2)}"
            trimmed.startsWith("/") -> {
                val base = baseUrl.toHttpUrl()
                "${base.scheme}://${base.host}$trimmed"
            }
            else -> trimmed
        }
    }

    fun videosFromUrl(url: String, name: String): List<StreamSource> {
        val finalUrl = if (url.contains("/vast")) {
            url.toHttpUrl().newBuilder().encodedPath("/cat-player/player").build().toString()
        } else {
            url
        }

        val html = client.newCall(GET(finalUrl, headers)).execute().body.string()
        val cleanHtml = html.replace("&quot;", "\"")

        if (cleanHtml.contains("""manifest":[0,"""")) {
            return parseNewPlayer(cleanHtml, finalUrl, name)
        }
        if (html.contains("cid: '")) {
            return parseLegacyPlayer(html, finalUrl, name)
        }
        return emptyList()
    }

    // ============================== New player ===============================

    private val manifestRegex by lazy { Regex("""manifest":\[0,"((?:https?:)?//[^"]+)"]""") }
    private val trackRegex by lazy { Regex(""""language":\[\d+,"([^"]+)"][^}]*?"name":\[\d+,"([^"]+)"][^}]*?"src":\[\d+,"([^"]+)"]""") }

    private fun parseNewPlayer(cleanHtml: String, url: String, name: String): List<StreamSource> {
        val rawManifestUrl = manifestRegex.find(cleanHtml)?.groupValues?.get(1) ?: return emptyList()
        val manifestUrl = fixUrl(rawManifestUrl, url)

        val subtitles = trackRegex.findAll(cleanHtml).mapNotNull { match ->
            val subName = match.groupValues[2]
            val subUrl = fixUrl(match.groupValues[3], url)
            subUrl.toHttpUrlOrNull()?.let {
                Track.SubtitleTrack(url = subUrl, lang = subName, mimeType = subMimeType(subUrl))
            }
        }.toList()

        // Demuxed HLS: split the master into per-quality (video-only) sources and pull the separate audio
        // group into audioTracks; the app merges them back at playback.
        return playlistUtils.extractFromHls(
            playlistUrl = manifestUrl,
            masterHeaders = getVideoHeaders(url),
            videoHeaders = getVideoHeaders(url),
            videoNameGen = { quality -> if (quality == "Video") name else "$name - $quality" },
            subtitleList = subtitles,
        ).map { it.copy(server = name) }
    }

    // =============================== Legacy ==================================

    private fun parseLegacyPlayer(html: String, finalUrl: String, name: String): List<StreamSource> {
        val finalHttpUrl = finalUrl.toHttpUrlOrNull() ?: return emptyList()
        val host = finalHttpUrl.host
        val mid = if (name == "DuckStream") "mid" else "id"
        val isBird = name == "BirdStream"
        val query = finalHttpUrl.queryParameter(mid) ?: return emptyList()

        val key = when (name) {
            "VidStreaming" -> "e13d38099bf562e8b9851a652d2043d3"
            "DuckStream" -> "4504447b74641ad972980a6b8ffd7631"
            "BirdStream" -> "4b14d0ff625163e3c9c7a47926484bf2"
            else -> return emptyList()
        }.toByteArray()

        val (sig, timeStamp, route) = getSignature(html, name, query, key) ?: return emptyList()
        val sourceUrl = buildString {
            append("https://$host$route?$mid=$query")
            if (!isBird) append("&e=$timeStamp")
            append("&s=$sig")
        }

        val response = client.newCall(
            GET(sourceUrl, headers.newBuilder().set("Referer", finalUrl).set("Origin", "https://$host").build())
        ).execute().body.string()

        val parts = response.substringAfter(":\"").substringBefore('"').replace("\\", "").split(":")
        if (parts.size < 2) return emptyList()
        val iv = parts[1].decodeHex()

        val videoObject = try {
            json.decodeFromString<VideoDto>(CryptoAES.decrypt(parts[0], key, iv))
        } catch (e: Exception) {
            return emptyList()
        }

        val subtitles = videoObject.subtitles.map {
            val subUrl = fixUrl(it.src, finalUrl)
            Track.SubtitleTrack(url = subUrl, lang = it.name, mimeType = subMimeType(subUrl))
        }

        val nameGen = { quality: String -> if (quality == "Video") name else "$name - $quality" }
        val videoHeaders = getVideoHeaders(finalUrl)
        val hlsUrl = videoObject.hls.takeIf { it.isNotBlank() }?.let { fixUrl(it, finalUrl) }
        val dashUrl = videoObject.dash.takeIf { it.isNotBlank() }?.let { fixUrl(it, finalUrl) }

        // Demuxed: split into per-quality sources + separate audio group (merged back by the app).
        return when {
            hlsUrl != null -> playlistUtils.extractFromHls(
                playlistUrl = hlsUrl,
                masterHeaders = videoHeaders,
                videoHeaders = videoHeaders,
                videoNameGen = nameGen,
                subtitleList = subtitles,
            )
            dashUrl != null -> playlistUtils.extractFromDash(
                mpdUrl = dashUrl,
                videoNameGen = nameGen,
                mpdHeaders = videoHeaders,
                videoHeaders = videoHeaders,
                subtitleList = subtitles,
            )
            else -> emptyList()
        }.map { it.copy(server = name) }
    }

    private fun getSignature(html: String, server: String, query: String, key: ByteArray): Triple<String, String, String>? {
        val order = when (server) {
            "VidStreaming", "DuckStream" -> listOf("IP", "USERAGENT", "ROUTE", "MID", "TIMESTAMP", "KEY")
            "BirdStream" -> listOf("IP", "USERAGENT", "ROUTE", "MID", "KEY")
            else -> return null
        }

        val cid = String(html.substringAfter("cid: '").substringBefore("'").decodeHex()).split("|")
        if (cid.size < 2) return null
        val timeStamp = (System.currentTimeMillis() / 1000 + 60).toString()
        val route = cid[1].replace("player.php", "source.php")

        val signature = buildString {
            order.forEach {
                when (it) {
                    "IP" -> append(cid[0])
                    "USERAGENT" -> append(headers["User-Agent"] ?: "")
                    "ROUTE" -> append(route)
                    "MID" -> append(query)
                    "TIMESTAMP" -> append(timeStamp)
                    "KEY" -> append(String(key))
                    else -> {}
                }
            }
        }
        return Triple(sha1sum(signature), timeStamp, route)
    }

    private fun sha1sum(value: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun String.decodeHex(): ByteArray {
        val clean = replace(" ", "")
        check(clean.length % 2 == 0) { "Must have an even length" }
        return ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}
