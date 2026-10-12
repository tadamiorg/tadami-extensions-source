package com.sf.tadami.lib.youruploadextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class YourUploadExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, headers: Headers, name: String = "YourUpload", prefix: String = ""): List<StreamSource> {
        // Replaces a caller's Referer: with two, the server reads the first one and answers 500 to a foreign one
        val newHeaders = headers.newBuilder().set("Referer", "https://www.yourupload.com/").build()
        return runCatching {
            val document = client.newCall(GET(url, headers = newHeaders)).execute().use { it.asJsoup() }
            val baseData = document.selectFirst("script:containsData(jwplayerOptions)")?.data()
                ?: return@runCatching null
            val basicUrl = baseData.substringAfter("file: '").substringBefore("',")
            // Deleted files point to /embed/novideo.mp4
            if (!basicUrl.startsWith("http") || "novideo" in basicUrl) return@runCatching null
            listOf(StreamSource(url = basicUrl, fullName = prefix + name, server = "YourUpload", headers = newHeaders))
        }.getOrNull() ?: emptyList()
    }
}
