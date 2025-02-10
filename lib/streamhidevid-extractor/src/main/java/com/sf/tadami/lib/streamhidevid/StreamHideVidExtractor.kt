package com.sf.tadami.lib.streamhidevid

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import dev.datlag.jsunpacker.JsUnpacker
import okhttp3.OkHttpClient

class StreamHideVidExtractor(private val client: OkHttpClient) {

    private val playlistUtils by lazy { PlaylistUtils(client) }

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        val page = client.newCall(GET(url)).execute().body.string()
        val playlistUrl = (JsUnpacker.unpackAndCombine(page) ?: page)
            .substringAfter("sources:")
            .substringAfter("file:\"") // StreamHide
            .substringAfter("src:\"") // StreamVid
            .substringBefore('"')
        if (!playlistUrl.startsWith("http")) return emptyList()
        return playlistUtils.extractFromHls(playlistUrl, videoNameGen = { "${prefix}StreamHideVid${if(it.isNotEmpty()) " - $it" else ""}" }).map {
            it.copy(server = "StreamHideVid")
        }
    }
}
