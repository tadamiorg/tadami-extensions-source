package com.sf.tadami.lib.mailruextractor


import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy

class MailRuExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private val json: Json by injectLazy()
    private val urlRegex by lazy { "^//".toRegex() }

    fun videosFromUrl(url: String): List<StreamSource> {
        val document = client.newCall(GET(url, headers)).execute()
            .asJsoup()

        val metaUrl = document.selectFirst("script:containsData(metadataUrl)")
            ?.data()
            ?.run {
                substringAfter("metadataUrl\":\"")
                    .substringBefore("\"")
                    .replace(urlRegex, "https://") // Fix URLs
            } ?: return emptyList()

        val metaHeaders = headers.newBuilder()
            .set("Referer", url)
            .set("Origin", "https://${url.toHttpUrl().host}")
            .build()

        val metaResponse = client.newCall(GET(metaUrl, metaHeaders)).execute()

        val metaJson = json.decodeFromString<MetaResponse>(
            metaResponse.body.string(),
        )

        val videoKey = metaResponse.headers.firstOrNull {
            it.first.equals("set-cookie", true) && it.second.startsWith("video_key", true)
        }?.second?.substringBefore(";") ?: ""

        val videoHeaders = metaHeaders.newBuilder()
            .set("Cookie", videoKey)
            .build()

        return metaJson.videos.map {
            val videoUrl = it.url
                .replace(urlRegex, "https://")
                .replace(".mp4", ".mp4/stream.mpd")

            StreamSource(url = videoUrl, fullName = "MailRu ${it.key}", server = "MailRu", headers = videoHeaders)
        }
    }

    @Serializable
    data class MetaResponse(val videos: List<VideoObject>)

    @Serializable
    data class VideoObject(val url: String, val key: String)
}