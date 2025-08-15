package com.sf.tadami.lib.vidhide

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import dev.datlag.jsunpacker.JsUnpacker
import okhttp3.OkHttpClient
import org.json.JSONObject

class VidHideExtractor(private val client: OkHttpClient, private val baseUrl: String) {

    private val playlistUtils by lazy { PlaylistUtils(client) }

    fun videosFromUrl(url: String, prefix: String = "VidHide", server: String = "VidHide"): List<StreamSource> {
        val doc = client.newCall(GET(url)).execute().asJsoup()

        val scriptBody = doc.selectFirst("script:containsData(m3u8)")?.data()
            ?.let { script ->
                if (script.contains("eval(function(p,a,c")) {
                    JsUnpacker.unpackAndCombine(script)
                } else {
                    script
                }
            }

        val urls = scriptBody
            ?.substringAfter("links=", "")
            ?.substringBefore(";", "")
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()

        val jsonObject = JSONObject(urls)

        val m3u8Links = jsonObject.keys().asSequence()
            .map { jsonObject.getString(it) }
            .filter { it.contains(".m3u8") }
            .toList()
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()


        val baseNameCounts = m3u8Links.flatMap { link ->
            var masterUrl = link
            if (!link.startsWith("https:")) {
                masterUrl = "$baseUrl$link"
            }

            playlistUtils.extractFromHls(
                playlistUrl = masterUrl,
                referer = url,
                videoNameGen = { baseName ->
                    "${prefix}${if (baseName.isNotEmpty()) " - $baseName" else ""}"
                }
            ).map {
                it.copy(server = server)
            }
        }.groupingBy { it.fullName }.eachCount()


        val nameInstanceCount = mutableMapOf<String, Int>()

        return m3u8Links.map { link ->
            var masterUrl = link
            if (!link.startsWith("https:")) {
                masterUrl = "$baseUrl$link"
            }

            playlistUtils.extractFromHls(
                playlistUrl = masterUrl,
                referer = url,
                videoNameGen = { baseName ->
                    val fullName = "${prefix}${if (baseName.isNotEmpty()) " - $baseName" else ""}"
                    val count = baseNameCounts[fullName] ?: 0

                    if (count > 1) {
                        val instance = nameInstanceCount.getOrDefault(fullName, 0) + 1
                        nameInstanceCount[fullName] = instance
                        "$fullName #$instance"
                    } else {
                        fullName
                    }
                }
            ).map {
                it.copy(server = server)
            }
        }.flatten()
    }
}
