package com.sf.tadami.extension.fr.otakufr.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import dev.datlag.jsunpacker.JsUnpacker
import okhttp3.Headers
import okhttp3.OkHttpClient

class UpstreamExtractor(private val client: OkHttpClient, private val headers: Headers) {
    fun videosFromUrl(url: String): List<StreamSource> {
        val jsE = client.newCall(
            GET(url, headers),
        ).execute().asJsoup().selectFirst("script:containsData(m3u8)")?.data() ?: return emptyList()
        val masterUrl = JsUnpacker.unpackAndCombine(jsE)
            ?.substringAfter("{file:\"")
            ?.substringBefore("\"}")
            ?: return emptyList()

        return PlaylistUtils(client, headers).extractFromHls(masterUrl, videoNameGen = { quality -> "Upstream - $quality" }).map {
            it.copy(server = "Upstream")
        }
    }
}