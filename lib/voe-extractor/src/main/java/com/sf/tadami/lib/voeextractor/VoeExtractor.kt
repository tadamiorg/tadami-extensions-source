package com.sf.tadami.lib.voeextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.OkHttpClient

class VoeExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, quality: String? = null): List<StreamSource> {
        val document = client.newCall(GET(url)).execute().asJsoup()
        val script = document.selectFirst("script:containsData(const sources),script:containsData(var sources)")
            ?.data()
            ?: return emptyList()
        val videoUrl = script.substringAfter("hls': '").substringBefore("'")
        val resolution = script.substringAfter("video_height': ").substringBefore(",")
        val qualityStr = quality ?: "VoeCDN(${resolution}p)"
        return listOf(StreamSource(videoUrl, qualityStr))
    }
}