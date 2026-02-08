package com.sf.tadami.lib.vidmolyextractor

import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class VidmolyExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun HttpUrl.origin(): String {
        val portPart = if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) {
            ""
        } else {
            ":$port"
        }
        return "$scheme://$host$portPart"
    }

    fun videosFromUrl(url: String): List<StreamSource> {
        val actualHeaders = headers.newBuilder()
            .add("Referer", url.toHttpUrl().origin())
            .build()

        val body = client.newCall(GET(url, actualHeaders)).execute().body.string()



        val patterns = listOf(
            Regex("""file:\s*['"]([^"']+)"""),
            Regex("""sources:\s*\[\s*\{[^}]*file:\s*["']([^"']+)"""),
            Regex("""source\s*src=["']([^"']+\.m3u8[^"']*)""")
        )

        var masterUrl: String? = null
        for (pattern in patterns) {
            val match = pattern.find(body)

            if (match != null && match.groupValues.size > 1) {
                masterUrl = match.groupValues[1]
                break
            }
        }

        if (masterUrl == null) {
            Log.d(VidmolyExtractor::class.simpleName,"Could not find video source in $url")
            return emptyList()
        }

        return playlistUtils.extractFromHls(
            playlistUrl = masterUrl,
            referer = url,
            videoNameGen = {
                val hasQuality = it.isNotBlank()
                var quality = ""
                if (hasQuality) {
                    quality = "- $it"
                }
                "Vidmoly $quality"
            },
            videoHeadersGen = { _, referer, _ ->
                playlistUtils.generateMasterHeaders(headers, referer)
            }
        ).map {
            it.copy(server = "Vidmoly")
        }
    }
}
