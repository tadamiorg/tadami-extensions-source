package com.sf.tadami.lib.streamtapeextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import okhttp3.OkHttpClient

class StreamTapeExtractor(private val client: OkHttpClient) {
    fun videoFromUrl(url: String, quality: String = "Streamtape", subtitleList: List<Track.SubtitleTrack> = emptyList()): StreamSource? {
        val baseUrl = "https://streamtape.com/e/"
        val newUrl = if (!url.startsWith(baseUrl)) {
            // ["https", "", "<domain>", "<???>", "<id>", ...]
            val id = url.split("/").getOrNull(4) ?: return null
            baseUrl + id
        } else { url }

        val document = client.newCall(GET(newUrl)).execute().asJsoup()
        val targetLine = "document.getElementById('robotlink')"
        val script = document.selectFirst("script:containsData($targetLine)")
            ?.data()
            ?.substringAfter("$targetLine.innerHTML = '")
            ?: return null
        val videoUrl = "https:" + script.substringBefore("'") +
                script.substringAfter("+ ('xcd").substringBefore("'")

        return StreamSource(url = videoUrl, fullName = quality, server = "StreamTape", subtitleTracks = subtitleList)
    }

    fun videosFromUrl(url: String, quality: String = "Streamtape", subtitleList: List<Track.SubtitleTrack> = emptyList()): List<StreamSource> {
        return videoFromUrl(url, quality, subtitleList)?.let(::listOf).orEmpty()
    }
}