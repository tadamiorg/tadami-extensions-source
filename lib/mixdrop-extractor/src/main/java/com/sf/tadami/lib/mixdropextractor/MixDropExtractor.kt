package com.sf.tadami.lib.mixdropextractor

import com.sf.tadami.lib.unpacker.Unpacker
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class MixDropExtractor(private val client: OkHttpClient) {
    fun videoFromUrl(
        url: String,
        lang: String = "",
        prefix: String = ""
    ): List<StreamSource> {
        val doc = client.newCall(GET(url)).execute().asJsoup()
        val unpacked = doc.selectFirst("script:containsData(eval):containsData(MDCore)")
            ?.data()
            ?.let(Unpacker::unpack)
            ?: return emptyList()

        val videoUrl = "https:" + unpacked.substringAfter("Core.wurl=\"")
            .substringBefore("\"")

        val fullName = prefix + ("MixDrop").let {
            when {
                lang.isNotBlank() -> "$it - $lang"
                else -> it
            }
        }

        val headers = Headers.headersOf("Referer", "https://mixdrop.co/")
        return listOf(StreamSource(url = videoUrl, fullName = fullName, server = "MixDrop", quality = "", headers = headers))
    }
}
