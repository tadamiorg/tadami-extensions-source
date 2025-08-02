package com.sf.tadami.lib.filemoonextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import dev.datlag.jsunpacker.JsUnpacker
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

class FileMoonExtractor(private val client: OkHttpClient) {

    private val playlistUtils by lazy { PlaylistUtils(client) }
    private val json: Json by injectLazy()

    fun videosFromUrl(url: String, prefix: String = "Filemoon - ", headers: Headers? = null): List<StreamSource> {
        var httpUrl = url.toHttpUrl()
        val videoHeaders = (headers?.newBuilder() ?: Headers.Builder())
            .set("Referer", url)
            .set("Origin", "https://${httpUrl.host}")
            .build()

        val doc = client.newCall(GET(url, videoHeaders)).execute().asJsoup()
        val jsEval = doc.selectFirst("script:containsData(eval):containsData(m3u8)")?.data() ?: run {
            val iframeUrl = doc.selectFirst("iframe[src]")!!.attr("src")
            httpUrl = iframeUrl.toHttpUrl()
            val iframeDoc = client.newCall(GET(iframeUrl, videoHeaders)).execute().asJsoup()
            iframeDoc.selectFirst("script:containsData(eval):containsData(m3u8)")!!.data()
        }
        val unpacked = JsUnpacker.unpackAndCombine(jsEval).orEmpty()
        val masterUrl = unpacked.takeIf(String::isNotBlank)
            ?.substringAfter("{file:\"", "")
            ?.substringBefore("\"}", "")
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()

        val subtitleTracks = buildList {
            // Subtitles from a external URL
            val subUrl = httpUrl.queryParameter("sub.info")
                ?: unpacked.substringAfter("fetch('", "")
                    .substringBefore("').")
                    .takeIf(String::isNotBlank)
            if (subUrl != null) {
                runCatching { // to prevent failures on serialization errors
                    client.newCall(GET(subUrl, videoHeaders)).execute()
                        .body.string()
                        .let { json.decodeFromString<List<SubtitleDto>>(it) }
                        .forEach { add(Track.SubtitleTrack(url = it.file, lang = it.label, mimeType = "text/vtt")) }
                }
            }
        }

        return playlistUtils.extractFromHls(
            masterUrl,
            referer = "https://${httpUrl.host}/",
            videoNameGen = { "$prefix$it" },
            videoHeadersGen = { _,referer,_->
                playlistUtils.generateMasterHeaders(videoHeaders,referer)
            },
            subtitleList = subtitleTracks
        ).map {
            it.copy(
                server = "Filemoon"
            )
        }
    }

    @Serializable
    data class SubtitleDto(val file: String, val label: String)
}