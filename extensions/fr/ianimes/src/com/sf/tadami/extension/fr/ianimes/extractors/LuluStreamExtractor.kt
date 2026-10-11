package com.sf.tadami.extension.fr.ianimes.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.lib.unpacker.Unpacker
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * LuluStream (luluvdo.com, luluvido.com, lulustream.com...): a jwplayer page whose setup is packed
 * with Dean Edwards' packer; the HLS master is in `sources: [{file: "..."}]`.
 */
class LuluStreamExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String): List<StreamSource> {
        val referer = "https://${url.toHttpUrl().host}/"
        val pageHeaders = headers.newBuilder().set("Referer", referer).build()
        val page = client.newCall(GET(url, pageHeaders)).execute().use { it.body.string() }

        val packedStart = page.indexOf(PACKER_MARKER)
        val script = if (packedStart >= 0) {
            runCatching { Unpacker.unpack(page.substring(packedStart)) }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: page
        } else {
            page
        }

        val playlistUrl = SOURCE_REGEX.find(script)?.groupValues?.get(1)
            ?: throw IOException("LuluStream: no stream in the player page (video removed?)")

        return playlistUtils.extractFromHls(
            playlistUrl = playlistUrl,
            referer = referer,
            videoNameGen = { quality -> "$SERVER - $quality" },
        ).map { it.copy(server = SERVER) }
    }

    companion object {
        const val SERVER = "LuluStream"
        private const val PACKER_MARKER = "eval(function(p,a,c,k,e,d)"
        private val SOURCE_REGEX = Regex("""sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)""")
    }
}
