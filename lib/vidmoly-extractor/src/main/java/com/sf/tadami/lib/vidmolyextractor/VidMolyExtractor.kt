package com.sf.tadami.lib.vidmolyextractor

import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.lib.unpacker.Unpacker
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

    fun videosFromUrl(url: String, server: String = "Vidmoly"): List<StreamSource> {
        val origin = url.toHttpUrl().origin()
        val actualHeaders = headers.newBuilder()
            .add("Referer", origin)
            .build()

        val body = client.newCall(GET(url, actualHeaders)).execute().body.string()

        // Vidmoly usually serves the player config as plaintext, but some mirrors wrap it
        // in Dean-Edwards packed JS. Unpack defensively and search both.
        val unpacked = runCatching { Unpacker.unpack(body) }.getOrNull()?.takeIf { it.isNotBlank() }
        val source = listOfNotNull(unpacked, body).joinToString("\n")

        // The jwplayer setup lists a `thumbnails` track (file: '/api/v1/slides...') BEFORE the
        // real stream (sources: [{ file: '...master.m3u8' }]). Target the sources entry / an
        // explicit .m3u8 first so we never grab the preview thumbnail by mistake.
        val patterns = listOf(
            Regex("""sources:\s*\[\s*\{\s*file:\s*["']([^"']+)"""),
            Regex("""file:\s*["'](https?[^"']+\.m3u8[^"']*)"""),
            Regex("""source\s+src=["']([^"']+\.m3u8[^"']*)""")
        )

        var masterUrl: String? = null
        for (pattern in patterns) {
            val match = pattern.find(source)

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
            referer = "$origin/",
            videoNameGen = {
                if (it.isNotBlank()) "$server - $it" else server
            },
            videoHeadersGen = { _, referer, _ ->
                playlistUtils.generateMasterHeaders(headers, referer)
            }
        ).map {
            it.copy(server = server)
        }
    }
}
