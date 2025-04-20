package com.sf.tadami.lib.smoothpreextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import dev.datlag.jsunpacker.JsUnpacker
import okhttp3.OkHttpClient

class SmoothPreExtractor(
    private val client: OkHttpClient,
) {
    private val playlistUtils by lazy { PlaylistUtils(client) }

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        var document = client.newCall(GET(url)).execute().asJsoup()

        val unpackedData = document.selectFirst("script:containsData(eval):containsData(p,a,c,k,e,d)")?.data()
            ?.let { script ->
                try {
                    JsUnpacker.unpackAndCombine(script)
                } catch (e: Exception) {
                    return emptyList()
                }
            } ?: return emptyList()

        val masterUrl = unpackedData.substringAfter("\"hls2\":\"").substringBefore("\"};")

        return playlistUtils.extractFromHls(masterUrl,
            videoNameGen = { quality -> "${prefix}SmoothPre - $quality" }
        ).map {
            it.copy(server = "SmoothPre")
        }
    }
}