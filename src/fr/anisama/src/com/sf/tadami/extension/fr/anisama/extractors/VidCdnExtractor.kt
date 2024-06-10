package com.sf.tadami.extension.fr.anisama.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
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
    private val json : Json,
    headers: Headers = commonEmptyHeaders,
) {

    private val headers = headers.newBuilder()
        .set("Referer", "https://msb.toonanime.xyz")
        .build()

    private val playlistUtils by lazy { PlaylistUtils(client, this.headers) }
    @Serializable
    data class CdnSourceDto(val file: String)

    @Serializable
    data class CdnResponseDto(val sources: List<CdnSourceDto>)

    fun videosFromUrl(
        url: String
    ): List<StreamSource> {
        val httpUrl = url.toHttpUrl()
        val id = httpUrl.queryParameter("id")
        val epid = httpUrl.queryParameter("epid")
        val cdnUrl = "https://r.vidcdn.xyz/v1/api/get_sources/$id?epid=$epid"
        val res = client.newCall(GET(cdnUrl, headers)).execute().use { it.body.string() }
        val sources = json.decodeFromString<CdnResponseDto>(res).sources
        return sources.flatMap { source ->
            playlistUtils.extractFromHls(
                playlistUrl = source.file,
                referer = url,
                videoNameGen = {
                    val hasQuality = it.isNotBlank()
                    var quality = ""
                    if (hasQuality) {
                        quality = "- $it"
                    }
                    "Vidcdn $quality"
                },
            ).map {
                it.copy(server = "Vidcdn")
            }
        }
    }
}
