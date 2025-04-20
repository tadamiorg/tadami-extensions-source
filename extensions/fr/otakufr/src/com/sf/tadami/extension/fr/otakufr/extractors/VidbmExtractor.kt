package com.sf.tadami.extension.fr.otakufr.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class VidbmExtractor(private val client: OkHttpClient, private val headers: Headers) {
    fun videosFromUrl(url: String): List<StreamSource> {
        val doc = client.newCall(GET(url, headers = headers)).execute().asJsoup()
        val js = doc.selectFirst("script:containsData(m3u8),script:containsData(mp4)")?.data() ?: return emptyList()

        val masterUrl = js.substringAfter("source")
            .substringAfter("file:\"")
            .substringBefore("\"")

        val quality = js.substringAfter("source")
            .substringAfter("file")
            .substringBefore("]")
            .substringAfter("label:\"")
            .substringBefore("\"")

        return if (masterUrl.contains("m3u8")) {
            PlaylistUtils(client, headers).extractFromHls(masterUrl, videoNameGen = { qual -> "Vidbm - $qual" }).map {
                it.copy(server = "Vidbm")
            }
        } else {
            listOf(StreamSource(url = masterUrl, fullName = "Vidbm - $quality", quality = quality, server = "Vidbm"))
        }
    }
}