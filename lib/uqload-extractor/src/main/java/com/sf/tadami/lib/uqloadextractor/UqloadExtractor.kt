package com.sf.tadami.lib.uqloadextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class UqloadExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        val doc = client.newCall(GET(url)).execute().use { it.asJsoup() }
        val script = doc.selectFirst("script:containsData(sources:)")?.data()
            ?: return emptyList()

        val videoUrl = script.substringAfter("sources: [\"").substringBefore('"')
            .takeIf(String::isNotBlank)
            ?.takeIf { it.startsWith("http") }
            ?: return emptyList()

        val videoHeaders = Headers.headersOf("Referer", "https://uqload.co/")
        val fullName = if (prefix.isNotBlank()) "$prefix Uqload" else "Uqload"

        return listOf(StreamSource(url = videoUrl, fullName = fullName, server = "Uqload", headers = videoHeaders))
    }
}