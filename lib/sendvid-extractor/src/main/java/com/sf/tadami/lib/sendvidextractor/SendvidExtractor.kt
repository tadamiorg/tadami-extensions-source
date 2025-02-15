package com.sf.tadami.lib.sendvidextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class SendvidExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        val masterUrl = document.selectFirst("source#video_source")?.attr("src") ?: return emptyList()

        return if (masterUrl.contains(".m3u8")) {
            playlistUtils.extractFromHls(masterUrl, url, videoNameGen = { prefix + "Sendvid - $it" }).map {
                it.copy(server = "Sendvid")
            }
        } else {
            val httpUrl = "https://${url.toHttpUrl()}"
            val newHeaders = headers.newBuilder()
                .set("Origin", httpUrl)
                .set("Referer", "$httpUrl/")
                .build()
            listOf(StreamSource(url = masterUrl, fullName = "$prefix Sendvid", server = "Sendvid", headers = newHeaders))
        }
    }
}
