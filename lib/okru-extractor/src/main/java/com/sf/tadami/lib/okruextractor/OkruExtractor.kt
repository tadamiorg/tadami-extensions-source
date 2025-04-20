package com.sf.tadami.lib.okruextractor

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.OkHttpClient

class OkruExtractor(private val client: OkHttpClient) {
    private val playlistUtils by lazy { PlaylistUtils(client) }

    private fun fixQuality(quality: String): String {
        val qualities = listOf(
            Pair("ultra", "2160p"),
            Pair("quad", "1440p"),
            Pair("full", "1080p"),
            Pair("hd", "720p"),
            Pair("sd", "480p"),
            Pair("low", "360p"),
            Pair("lowest", "240p"),
            Pair("mobile", "144p"),
        )
        return qualities.find { it.first == quality }?.second ?: quality
    }

    fun videosFromUrl(url: String, prefix: String = "", fixQualities: Boolean = true): List<StreamSource> {
        val document = client.newCall(GET(url)).execute().asJsoup()
        val videoString = document.selectFirst("div[data-options]")
            ?.attr("data-options")
            ?: return emptyList()

        return when {
            "ondemandHls" in videoString -> {
                val playlistUrl = videoString.extractLink("ondemandHls")
                playlistUtils.extractFromHls(playlistUrl, videoNameGen = { "Okru:$it".addPrefix(prefix) }).map {
                    it.copy(server = "Okru")
                }
            }
            "ondemandDash" in videoString -> {
                val playlistUrl = videoString.extractLink("ondemandDash")
                playlistUtils.extractFromDash(playlistUrl, videoNameGen = { it -> "Okru:$it".addPrefix(prefix) }).map {
                    it.copy(server = "Okru")
                }
            }
            else -> videosFromJson(videoString, prefix, fixQualities)
        }
    }

    private fun String.addPrefix(prefix: String) =
        prefix.takeIf(String::isNotBlank)
            ?.let { "$prefix $this" }
            ?: this

    private fun String.extractLink(attr: String) =
        substringAfter("$attr\\\":\\\"")
            .substringBefore("\\\"")
            .replace("\\\\u0026", "&")

    private fun videosFromJson(videoString: String, prefix: String = "", fixQualities: Boolean = true): List<StreamSource> {
        val arrayData = videoString.substringAfter("\\\"videos\\\":[{\\\"name\\\":\\\"")
            .substringBefore("]")

        return arrayData.split("{\\\"name\\\":\\\"").reversed().mapNotNull {
            val videoUrl = it.extractLink("url")
            val quality = it.substringBefore("\\\"").let {
                if (fixQualities) {
                    fixQuality(it)
                } else {
                    it
                }
            }
            val videoQuality = "Okru:$quality".addPrefix(prefix)

            if (videoUrl.startsWith("https://")) {
                StreamSource(url = videoUrl, fullName = videoQuality, server = "Okru", quality = quality)
            } else {
                null
            }
        }
    }
}
