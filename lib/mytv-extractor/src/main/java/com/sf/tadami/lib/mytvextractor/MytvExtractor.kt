package com.sf.tadami.lib.mytvextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.OkHttpClient

class MytvExtractor(private val client: OkHttpClient) {

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        val document = client.newCall(GET(url)).execute().asJsoup()
        val videoList = mutableListOf<StreamSource>()
        document.select("script").forEach { script ->
            if (script.data().contains("CreatePlayer(\"v")) {
                val videosString = script.data().toString()
                val videoUrl = videosString.substringAfter("\"v=").substringBefore("\\u0026tp=video").replace("%26", "&").replace("%3a", ":").replace("%2f", "/").replace("%3f", "?").replace("%3d", "=")
                if (!videoUrl.contains("https:")) {
                    val videoUrl = "https:$videoUrl"
                    videoList.add(StreamSource(url = videoUrl, fullName = "${prefix}Stream", server = "Stream"))
                } else {
                    videoList.add(StreamSource(url = videoUrl, fullName = "${prefix}Mytv", server = "MyTv"))
                }
            }
        }
        return videoList
    }

}
