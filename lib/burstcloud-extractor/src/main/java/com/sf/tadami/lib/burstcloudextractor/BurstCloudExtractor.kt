package com.sf.tadami.lib.burstcloudextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.POST
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

class BurstCloudExtractor(private val client: OkHttpClient) {

    private val json: Json by injectLazy()
    private val serverName: String = "BurstCloud"

    fun videoFromUrl(url: String, headers: Headers, name: String = "BurstCloud", prefix: String = ""): List<StreamSource> {

        val newHeaders = headers.newBuilder().add("referer", "https://www.burstcloud.co/").build()
        return runCatching {
            val response = client.newCall(GET(url, headers = newHeaders)).execute()
            val document = response.asJsoup()
            val videoId = document.selectFirst("div#player")!!.attr("data-file-id")
            val formBody = FormBody.Builder()
                .add("fileId", videoId)
                .build()
            val jsonHeaders = headers.newBuilder().add("referer", document.location()).build()
            val jsonString = client.newCall(POST("https://www.burstcloud.co/file/play-request/", jsonHeaders, formBody)).execute().body.string()
            val jsonObj = json.decodeFromString<BurstCloudDto>(jsonString)
            val videoUrl = jsonObj.purchase.cdnUrl
            if (videoUrl.isNotEmpty()) {
                val fullName = prefix + name
                listOf(StreamSource(url = videoUrl, quality = "", fullName = fullName, server = serverName, headers = newHeaders))
            } else {
                null
            }

        }.getOrNull() ?: emptyList()
    }
}
