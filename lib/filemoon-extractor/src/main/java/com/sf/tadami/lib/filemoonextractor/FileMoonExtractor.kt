package com.sf.tadami.lib.filemoonextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import dev.datlag.jsunpacker.JsUnpacker
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class FileMoonExtractor(private val client: OkHttpClient) {

    private val playlistUtils by lazy { PlaylistUtils(client) }

    fun videosFromUrl(url: String, prefix: String = "Filemoon - ", headers: Headers? = null): List<StreamSource> {

        val httpUrl = url.toHttpUrl()
        val videoHeaders = (headers?.newBuilder() ?: Headers.Builder())
            .set("Referer", url)
            .set("Origin", "https://${httpUrl.host}")
            .build()
        val doc = client.newCall(GET(url, videoHeaders)).execute().asJsoup()
        val iframeUrl = doc.selectFirst("iframe")?.attr("src")

        if(iframeUrl.isNullOrEmpty()) return emptyList()
        val iframeDoc = client.newCall(GET(iframeUrl, videoHeaders)).execute().asJsoup()
        val jsEval = iframeDoc.selectFirst("script:containsData(eval):containsData(m3u8)")!!.data()
        val unpacked = JsUnpacker.unpackAndCombine(jsEval).orEmpty()

        val masterUrl = unpacked.takeIf(String::isNotBlank)
            ?.substringAfter("{file:\"", "")
            ?.substringBefore("\"}", "")
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()

        return playlistUtils.extractFromHls(
            masterUrl,
            referer = "https://${httpUrl.host}/",
            videoNameGen = { "$prefix$it" },
            videoHeadersGen = { _,referer,_->
                playlistUtils.generateMasterHeaders(videoHeaders,referer)
            }
        ).map {
            it.copy(
                server = "Filemoon"
            )
        }
    }
}