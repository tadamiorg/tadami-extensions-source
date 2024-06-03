package com.sf.tadami.lib.voeextractor

import android.util.Base64
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class VoeExtractor(
    private val client: OkHttpClient,
    private val json: Json
) {

    private val playlistUtils by lazy { PlaylistUtils(client) }

    private val linkRegex = "(http|https)://([\\w_-]+(?:\\.[\\w_-]+)+)([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])".toRegex()

    private val base64Regex = Regex("'.*'")

    @Serializable
    data class VideoLinkDTO(val file: String)

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        val document = client.newCall(GET(url)).execute().asJsoup()
        val script =
            document.selectFirst("script:containsData(const sources), script:containsData(var sources), script:containsData(wc0)")
                ?.data()
                ?: return emptyList()
        val playlistUrl = when {
            // Layout 1
            script.contains("sources") -> {
                val link = script.substringAfter("hls': '").substringBefore("'")
                if (linkRegex.matches(link)) link else String(Base64.decode(link, Base64.DEFAULT))
            }
            // Layout 2
            script.contains("wc0") -> {
                val base64 = base64Regex.find(script)!!.value
                val decoded = Base64.decode(base64, Base64.DEFAULT).let(::String)
                json.decodeFromString<VideoLinkDTO>(decoded).file
            }

            else -> return emptyList()
        }
        return playlistUtils.extractFromHls(playlistUrl,
            videoNameGen = { quality -> "${prefix}Voe - $quality" }
        ).map {
            it.copy(server = "Voe")
        }
    }
}