package com.sf.tadami.lib.vidmolyextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class VidmolyExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String): List<StreamSource> {
        val body = client.newCall(GET(url)).execute().body.string()

        val playlistUrl = body.substringAfter("file:\"", "").substringBefore('"', "")
            .takeIf(String::isNotBlank)
            ?: return emptyList()

        return playlistUtils.extractFromHls(
            playlistUrl = playlistUrl,
            referer = url,
            videoNameGen = {
                val hasQuality = it.isNotBlank()
                var quality = ""
                if (hasQuality) {
                    quality = "- $it"
                }
                "Vidmoly $quality"
            },
            videoHeadersGen = { _,referer,_->
                playlistUtils.generateMasterHeaders(headers,referer)
            }
        ).map {
            it.copy(server = "Vidmoly")
        }
    }
}
