package com.sf.tadami.lib.vudeoextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class VudeoExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, headers: Headers): List<StreamSource> {
        val document = client.newCall(GET(url)).execute().asJsoup()
        val videoList = mutableListOf<StreamSource>()
        document.select("script:containsData(sources: [)").forEach { script ->
            val videoUrl = script.data().substringAfter("sources: [").substringBefore("]").replace("\"", "").split(",")
            videoUrl.forEach {
                videoList.add(StreamSource(url = it, fullName = "Vudeo", server = "Vudeo", headers = headers))
            }
        }
        return videoList
    }
}