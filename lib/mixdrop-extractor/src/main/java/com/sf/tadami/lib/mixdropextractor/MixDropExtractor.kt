package com.sf.tadami.lib.mixdropextractor

import com.sf.tadami.lib.unpacker.Unpacker
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.net.URLDecoder

class MixDropExtractor(private val client: OkHttpClient) {
    fun videoFromUrl(
        url: String,
        lang: String = "",
        prefix: String = "",
        externalSubs: List<Track.SubtitleTrack> = emptyList(),
        referer: String = DEFAULT_REFERER,
    ): List<StreamSource> {
        val headers = Headers.headersOf(
            "Referer",
            referer,
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        )
        val doc = client.newCall(GET(url, headers)).execute().asJsoup()
        val unpacked = doc.selectFirst("script:containsData(eval):containsData(MDCore)")
            ?.data()
            ?.let(Unpacker::unpack)
            ?: return emptyList()

        val videoUrl = "https:" + unpacked.substringAfter("Core.wurl=\"")
            .substringBefore("\"")

        val subs = unpacked.substringAfter("Core.remotesub=\"").substringBefore('"')
            .takeIf(String::isNotBlank)
            ?.let {
                listOf(
                    Track.SubtitleTrack(
                        url = URLDecoder.decode(it, "utf-8"),
                        lang = "sub",
                        mimeType = "text/vtt"
                    )
                )
            }
            ?: emptyList()

        val quality = buildString {
            append("${prefix}MixDrop")
            if (lang.isNotBlank()) append("($lang)")
        }

        return listOf(
            StreamSource(
                url = videoUrl,
                fullName = quality,
                server = "MixDrop",
                quality = "",
                headers = headers,
                subtitleTracks = subs + externalSubs
            )
        )
    }

    fun videosFromUrl(
        url: String,
        lang: String = "",
        prefix: String = "",
        externalSubs: List<Track.SubtitleTrack> = emptyList(),
        referer: String = DEFAULT_REFERER,
    ) = videoFromUrl(url, lang, prefix, externalSubs, referer)
}

private const val DEFAULT_REFERER = "https://mixdrop.co/"
