package com.sf.tadami.lib.vkextractor

import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.OkHttpClient

class VkExtractor(private val client: OkHttpClient, private val headers: Headers) {

    // VK keeps an A/B bucket in its remixstlid cookie: some sessions get a JavaScript-only embed without
    // the stream URLs, so VK cookies are never kept and a retry starts a fresh session
    private val embedClient by lazy { client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build() }

    private val documentHeaders by lazy {
        headers.newBuilder()
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .build()
    }

    private val videoHeaders by lazy {
        headers.newBuilder()
            .add("Accept", "*/*")
            .add("Origin", VK_URL)
            .add("Referer", "$VK_URL/")
            .build()
    }

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        // Without cookies vkvideo.ru loops through login.vk.ru, vk.com serves the same ids directly
        val embedUrl = url.replace(VKVIDEO_REGEX, "://vk.com/")
        repeat(MAX_ATTEMPTS) {
            val page = embedClient.newCall(GET(embedUrl, documentHeaders)).execute().use { it.body.string() }
            // The first "files" object is the video's, a trailer's comes after it
            val files = FILES_REGEX.find(page)?.groupValues?.get(1)
            if (files != null) {
                return MP4_REGEX.findAll(files).map {
                    val quality = "${it.groupValues[1]}p"
                    StreamSource(
                        url = it.groupValues[2].replace("\\/", "/"),
                        fullName = "${prefix}Vk - $quality",
                        server = "Vk",
                        quality = quality,
                        headers = videoHeaders,
                    )
                }.sortedByDescending { it.quality.removeSuffix("p").toIntOrNull() ?: 0 }.toList()
            }
            // Removed, blocked or unknown video: no point retrying
            if (!page.contains("vkvideo-embed")) return emptyList()
        }
        return emptyList()
    }

    companion object {
        private const val VK_URL = "https://vk.com"
        private const val MAX_ATTEMPTS = 3
        private val VKVIDEO_REGEX = Regex("""://(?:m\.)?vkvideo\.ru/""")
        private val FILES_REGEX = Regex(""""files":\{(.*?)\}""")
        private val MP4_REGEX = Regex(""""mp4_(\d+)":"(.*?)"""")
    }
}
