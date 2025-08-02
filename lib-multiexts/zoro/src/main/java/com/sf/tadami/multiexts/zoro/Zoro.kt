package com.sf.tadami.multiexts.zoro

import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.multiexts.zoro.dto.HtmlResponse
import com.sf.tadami.multiexts.zoro.dto.SourcesResponse
import com.sf.tadami.network.GET
import com.sf.tadami.network.asCancelableObservable
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.online.ConfigurableParsedHttpAnimeSource
import com.sf.tadami.ui.utils.parallelMap
import io.reactivex.rxjava3.core.Observable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

abstract class Zoro<T : ZoroPreferences>(
    sourceId: Long,
    prefGroup: CustomPreferences<T>,
) : ConfigurableParsedHttpAnimeSource<T>(
    sourceId = sourceId,
    prefGroup = prefGroup
) {

    override val baseUrl: String
        get() = preferences.baseUrl

    override val supportRecent = true

    private val json: Json by injectLazy()

    protected val docHeaders = headers.newBuilder().apply {
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Host", baseUrl.toHttpUrl().host)
        add("Referer", "$baseUrl/")
    }.build()

    protected open val ajaxRoute = ""

    // =============================== Latest ===============================

    override fun latestAnimesRequest(page: Int): Request =
        GET("$baseUrl/top-airing?page=$page", docHeaders)

    override fun latestSelector(): String = "div.flw-item"

    override fun latestAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            element.selectFirst("div.film-detail a")!!.let {
                setUrlWithoutDomain(it.attr("href"))
                title = if (it.hasAttr("title")) {
                    it.attr("title")
                } else {
                    it.attr("data-jname")
                }
            }
            thumbnailUrl = element.selectFirst("div.film-poster > img")!!.attr("data-src")
        }
    }

    override fun latestAnimeNextPageSelector(): String = "li.page-item a[title=Next]"

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {
        val params = ZoroFilters.getSearchParameters(filters)
        val endpoint = if (query.isEmpty()) "filter" else "search"

        val url = "$baseUrl/$endpoint".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addIfNotBlank("keyword", query)
            addIfNotBlank("type", params.type)
            addIfNotBlank("status", params.status)
            addIfNotBlank("rated", params.rated)
            addIfNotBlank("score", params.score)
            addIfNotBlank("season", params.season)
            addIfNotBlank("language", params.language)
            addIfNotBlank("sort", params.sort)
            addIfNotBlank("sy", params.start_year)
            addIfNotBlank("sm", params.start_month)
            addIfNotBlank("sd", params.start_day)
            addIfNotBlank("ey", params.end_year)
            addIfNotBlank("em", params.end_month)
            addIfNotBlank("ed", params.end_day)
            addIfNotBlank("genres", params.genres)
        }.build()

        return GET(url.toString(), docHeaders)
    }

    override fun searchSelector() = latestSelector()

    override fun searchAnimeFromElement(element: Element) = latestAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = latestAnimeNextPageSelector()

    // ============================== Filters ===============================

    override fun getFilterList() = ZoroFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        thumbnailUrl = document.selectFirst("div.anisc-poster img")!!.attr("src")

        document.selectFirst("div.anisc-info")!!.let { info ->
            status = info.getInfo("Status:")
            genres = info.select("div.item-list:contains(Genres:) > a").eachText()

            description = buildString {
                info.getInfo("Studios:")
                info.getInfo("Overview:")?.also { append(it + "\n") }
                info.getInfo("Aired:", full = true)?.also(::append)
                info.getInfo("Premiered:", full = true)?.also(::append)
                info.getInfo("Synonyms:", full = true)?.also(::append)
                info.getInfo("Japanese:", full = true)?.also(::append)
            }
        }
    }

    private fun Element.getInfo(
        tag: String,
        full: Boolean = false,
    ): String? {
        val value = selectFirst("div.item-title:contains($tag)")
            ?.selectFirst("*.name, *.text")
            ?.text()
        return if (full && value != null) "\n$tag $value" else value
    }

    // ============================== Episodes ==============================

    override fun episodesListRequest(anime: Anime): Request {
        val id = anime.url.substringAfterLast("-")
        return GET("$baseUrl/ajax$ajaxRoute/episode/list/$id", apiHeaders(baseUrl + anime.url))
    }

    override fun episodesListSelector() = "a.ep-item"

    override fun episodesListParse(response: Response): List<SEpisode> {
        val jsonString = response.use { it.body.string() }
        val document = json.decodeFromString<HtmlResponse>(jsonString).getHtml()

        return document.select(episodesListSelector())
            .map(::episodeFromElement)
            .reversed()
    }

    override fun episodeFromElement(element: Element) = SEpisode.create().apply {
        episodeNumber = element.attr("data-number").toFloatOrNull() ?: 1F
        name = "Ep. ${element.attr("data-number")}: ${element.attr("title")}"
        setUrlWithoutDomain(element.attr("href"))
        if (element.hasClass("ssl-item-filler")) {
            languages = "Filler Episode"
        }
    }

    // ============================ Video Links =============================


    override fun episodeSourcesRequest(url: String): Request {
        val id = url.substringAfterLast("?ep=")
        return GET(
            "$baseUrl/ajax$ajaxRoute/episode/servers?episodeId=$id",
            apiHeaders(baseUrl + url)
        )
    }

    data class VideoData(
        val type: String,
        val link: String,
        val name: String,
    )

    private fun customEpisodeSourcesParse(response: Response): List<VideoData> {
        val episodeReferer = response.request.header("referer")!!
        val videoTypeSelection = preferences.videoTypeSelection
        val jsonString = response.use { it.body.string() }
        val serversDoc = json.decodeFromString<HtmlResponse>(jsonString).getHtml()
        val embedLinks =
            listOf(
                "servers-sub",
                "servers-dub",
                "servers-mixed",
                "servers-raw"
            ).map { typeSelector ->
                if (typeSelector !in videoTypeSelection) return@map emptyList()
                serversDoc.select("div.$typeSelector div.item").parallelMap { server ->
                    runCatching {
                        val id = server.attr("data-id")
                        val type = server.attr("data-type")
                        val name = server.text()

                        val linkRes = client.newCall(
                            GET(
                                "$baseUrl/ajax$ajaxRoute/episode/sources?id=$id",
                                apiHeaders(episodeReferer)
                            ),
                        ).execute()

                        val bodyString = linkRes.use { it.body.string() }
                        val link = json.decodeFromString<SourcesResponse>(bodyString).link ?: ""

                        VideoData(type, link, name)
                    }.getOrNull()
                }.filterNotNull()
            }.flatten()
        return embedLinks
    }

    override fun fetchEpisodeSources(url: String): Observable<List<StreamSource>> {
        return client.newCall(episodeSourcesRequest(url))
            .asCancelableObservable()
            .map { response ->
                customEpisodeSourcesParse(response).parallelMap { server ->
                    runCatching {
                        extractVideo(server)
                    }.getOrNull()
                }.filterNotNull().flatten()
            }
    }

    abstract fun extractVideo(server: VideoData): List<StreamSource>

    override fun episodeSourcesSelector() = throw UnsupportedOperationException()

    override fun episodeSourcesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun episodeSourcesParse(response: Response) = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private fun Set<String>.contains(s: String, ignoreCase: Boolean): Boolean {
        return any { it.equals(s, ignoreCase) }
    }

    private fun apiHeaders(referer: String): Headers = headers.newBuilder().apply {
        add("Accept", "*/*")
        add("Host", baseUrl.toHttpUrl().host)
        add("Referer", referer)
        add("X-Requested-With", "XMLHttpRequest")
    }.build()

    private fun HttpUrl.Builder.addIfNotBlank(query: String, value: String): HttpUrl.Builder {
        if (value.isNotBlank()) {
            addQueryParameter(query, value)
        }
        return this
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        return this
    }
}
