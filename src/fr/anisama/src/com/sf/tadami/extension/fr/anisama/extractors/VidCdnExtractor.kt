package com.sf.tadami.extension.fr.anisama.extractors

import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.internal.commonEmptyHeaders

class VidCdnExtractor(
    private val client: OkHttpClient,
    val json : Json,
    headers: Headers = commonEmptyHeaders,
) {

    private val headers = headers.newBuilder()
        .set("Referer", "https://msb.toonanime.xyz")
        .build()

    @Serializable
    data class CdnSourceDto(val file: String)

    @Serializable
    data class CdnResponseDto(val sources: List<CdnSourceDto>)

    fun videosFromUrl(
        url: String,
        serverName : String,
        videoNameGen: (String) -> String = { quality -> quality },
    ): List<StreamSource> {
        val httpUrl = url.toHttpUrl()
        val source = when {
            url.contains("embeds.html") -> Pair("sib2", "Sibnet")
            // their sendvid server is currently borken lmao
            // url.contains("embedsen.html") -> Pair("azz", "Sendvid")
            else -> return emptyList()
        }
        val id = httpUrl.queryParameter("id")
        val epid = httpUrl.queryParameter("epid")
        val cdnUrl = "https://cdn2.vidcdn.xyz/${source.first}/$id?epid=$epid"
        val res = client.newCall(GET(cdnUrl, headers)).execute().use { it.body.string() }
        val sources = json.decodeFromString<CdnResponseDto>(res).sources
        return sources.map {
            val file = if (it.file.startsWith("http")) it.file else "https:${it.file}"
            StreamSource(
                url = file,
                fullName = videoNameGen(source.second),
                server = serverName,
                quality = ""
            )
        }
    }
}
