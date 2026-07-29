package com.sf.tadami.lib.uqloadextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.lib.unpacker.Unpacker
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class UqloadExtractor(private val client: OkHttpClient) {
    private val playlistUtils by lazy { PlaylistUtils(client) }

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        val doc = client.newCall(GET(url)).execute().use { it.asJsoup() }

        // The packed script contains no literal "sources:", so match on the eval
        // signature first and fall back to the plain script for non-packed pages.
        val packedScript = doc.selectFirst("script:containsData(eval):containsData(p,a,c,k,e,d)")?.data()
        val source = packedScript
            ?.let { runCatching { Unpacker.unpack(it) }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("script:containsData(sources:)")?.data()
            ?: return emptyList()

        val masterUrl = Regex("""sources:\s*\[\s*\{\s*file:\s*["']([^"']+)""")
            .find(source)?.groupValues?.get(1)
            ?.takeIf { it.startsWith("http") }
            ?: return emptyList()

        val referer = "https://" + url.toHttpUrl().host + "/"
        val serverName = if (prefix.isNotBlank()) "$prefix Uqload" else "Uqload"

        return playlistUtils.extractFromHls(
            playlistUrl = masterUrl,
            referer = referer,
            videoNameGen = { quality ->
                if (quality.isNotBlank()) "$serverName - $quality" else serverName
            }
        ).map { it.copy(server = "Uqload") }
    }
}
