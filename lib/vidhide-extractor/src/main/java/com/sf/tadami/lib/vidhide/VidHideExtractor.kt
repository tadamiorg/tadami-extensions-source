package com.sf.tadami.lib.vidhide

import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import dev.datlag.jsunpacker.JsUnpacker
import okhttp3.OkHttpClient

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

        val regex = """var links=(\{[^}]+\});""".toRegex()
        val matchResult = regex.find(scriptBody ?: "")

        if(matchResult == null){
            Log.d(VidHideExtractor::class.java.name,"Could not find video source in $url")
            return emptyList()
        }

        val linksJson = matchResult.groupValues[1]

        // Find all URLs containing .m3u8
        val urlRegex = """"([^"]*\.m3u8[^"]*)"""".toRegex()
        val m3u8Urls = urlRegex.findAll(linksJson)
            .map { it.groupValues[1] }
            .toList()

        if(m3u8Urls.isEmpty()){
            Log.d(VidHideExtractor::class.java.name,"Could not find video source in $url")
            return emptyList()
        }

        // Track which URLs successfully extracted
        val successfulUrls = mutableSetOf<String>()

        val baseNameCounts = m3u8Urls.flatMap { link ->
            var masterUrl = link
            if (!link.startsWith("https:")) {
                masterUrl = "$baseUrl$link"
            }

            try {
                val results = playlistUtils.extractFromHls(
                    playlistUrl = masterUrl,
                    referer = url,
                    videoNameGen = { baseName ->
                        "${prefix}${if (baseName.isNotEmpty()) " - $baseName" else ""}"
                    }
                ).map {
                    it.copy(server = server)
                }

                // Mark this URL as successful
                successfulUrls.add(link)
                results
            } catch (e: Exception) {
                Log.d(VidHideExtractor::class.java.name, "Error extracting video source from $masterUrl: ${e.message}")
                emptyList() // Return empty list on error
            }
        }.groupingBy { it.fullName }.eachCount()

        // Filter to only successful URLs
        val validM3u8Urls = m3u8Urls.filter { it in successfulUrls }

        if(validM3u8Urls.isEmpty()){
            Log.d(VidHideExtractor::class.java.name,"All URLs failed to extract")
            return emptyList()
        }

        val nameInstanceCount = mutableMapOf<String, Int>()

        return validM3u8Urls.map { link ->
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