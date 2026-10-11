package com.sf.tadami.extension.fr.ianimes.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Vidsonic (vidsonic.net/e/<code>): the stream URL is a hex string, split in chunks with `|` and
 * reversed, decoded by the page before it is given to hls.js.
 */
class VidsonicExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    fun videosFromUrl(url: String): List<StreamSource> {
        val referer = "https://${url.toHttpUrl().host}/"
        val pageHeaders = headers.newBuilder().set("Referer", referer).build()
        val page = client.newCall(GET(url, pageHeaders)).execute().use { it.body.string() }

        val videoUrl = HEX_REGEX.findAll(page)
            .map { decode(it.groupValues[1]) }
            .firstOrNull { it.startsWith("http") }
            ?: throw IOException("Vidsonic: no stream in the player page (video removed?)")

        return if (".m3u8" in videoUrl) {
            playlistUtils.extractFromHls(
                playlistUrl = videoUrl,
                referer = referer,
                videoNameGen = { quality -> "$SERVER - $quality" },
            ).map { it.copy(server = SERVER) }
        } else {
            listOf(
                StreamSource(
                    url = videoUrl,
                    fullName = SERVER,
                    server = SERVER,
                    headers = pageHeaders,
                )
            )
        }
    }

    private fun decode(hex: String): String {
        val clean = hex.replace("|", "")
        return clean.chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toChar() }
            .joinToString("")
            .reversed()
    }

    companion object {
        const val SERVER = "Vidsonic"

        /** A quoted string made of `|`-separated hex chunks, e.g. '3862353239|3963333739|...' */
        private val HEX_REGEX = Regex("""['"]((?:[0-9a-fA-F]{2,}\|){3,}[0-9a-fA-F]{2,})['"]""")
    }
}
