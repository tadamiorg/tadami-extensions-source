package com.sf.tadami.extension.fr.otakufr

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.extension.fr.otakufr.extractors.UpstreamExtractor
import com.sf.tadami.extension.fr.otakufr.extractors.VidbmExtractor
import com.sf.tadami.lib.doodextractor.DoodExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.okruextractor.OkruExtractor
import com.sf.tadami.lib.sendvidextractor.SendvidExtractor
import com.sf.tadami.lib.sibnetextractor.SibnetExtractor
import com.sf.tadami.lib.streamwishextractor.StreamWishExtractor
import com.sf.tadami.lib.voeextractor.VoeExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.asCancelableObservable
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.online.ConfigurableParsedHttpAnimeSource
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent
import com.sf.tadami.ui.utils.parallelMap
import com.sf.tadami.utils.Lang
import com.sf.tadami.utils.editPreference
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class OtakuFr : ConfigurableParsedHttpAnimeSource<OtakuFrPreferences>(
    sourceId = 5,
    prefGroup = OtakuFrPreferences
) {
    override val name: String = "OtakuFr"

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.cloudflareClient

    override val supportRecent = true

    private val i18n = i18n(OtakuSamaTranslations)

    init {
        runBlocking {
            preferencesMigrations()
        }
    }

    private suspend fun preferencesMigrations() : Boolean {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(OtakuFrPreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return false
            }

            if(oldVersion < 3){
                dataStore.editPreference(
                    "https://otakufr.cc",
                    stringPreferencesKey(OtakuFrPreferences.BASE_URL.name)
                )
            }
        }
        return true
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getOtakuSamaPreferencesContent(i18n)
    }

    /* LATEST */
    override fun latestSelector(): String = "article.episode"

    override fun latestAnimeNextPageSelector(): String =
        "ul.pagination > li.page-item.active + li.page-item"

    override fun latestAnimeFromElement(element: Element): SAnime {
        val anime: SAnime = SAnime.create()
        anime.title = ""
        anime.setUrlWithoutDomain(element.select("div.text-center > a.episode-link").attr("href"))
        anime.thumbnailUrl = element.select("div.text-center > figure > a > img").attr("src")
        return anime
    }

    override fun fetchLatest(page: Int): Observable<AnimesPage> {
        return client.newCall(latestAnimesRequest(page))
            .asCancelableObservable()
            .flatMap { response ->
                val document = response.asJsoup()

                val animeList = document.select(latestSelector()).map { element ->
                    latestAnimeFromElement(element)
                }

                val hasNextPage = latestAnimeNextPageSelector().let { selector ->
                    document.select(selector).first()
                } != null

                val pageRequests = Observable.fromIterable(animeList).flatMap { anime ->
                    client.newCall(GET("$baseUrl${anime.url}"))
                        .asCancelableObservable()
                        .map { response ->
                            val doc = response.asJsoup()
                            val arianne = doc.select("ol.breadcrumb > li.breadcrumb-item:eq(1) a")
                            anime.setUrlWithoutDomain(arianne.attr("href"))
                            anime.title = arianne.text().trim()
                            anime
                        }
                }.toList().toObservable()

                pageRequests.map { pages ->
                    AnimesPage(pages, hasNextPage)
                }
            }
    }

    override fun latestAnimesRequest(page: Int): Request {
        return GET("${baseUrl}/page/$page/")
    }


    /* SEARCH */

    override fun searchSelector(): String = "div.list > article.card"

    override fun searchAnimeNextPageSelector(): String = "ul.pagination > li.active ~ li"

    override fun searchAnimeFromElement(element: Element): SAnime {
        val a = element.selectFirst("a.episode-name")!!

        return SAnime.create().apply {
            thumbnailUrl = element.selectFirst("img")!!.attr("src")
            setUrlWithoutDomain(a.attr("href"))
            title = a.text().trim()
        }
    }

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {
        return when {
            query.isNotBlank() -> GET(
                "$baseUrl/toute-la-liste-affiches/?q=$query".addPage(page),
                headers
            )

            else -> GET("$baseUrl/en-cours".addPage(page), headers)
        }
    }

    override fun fetchSearch(page: Int, query : String, filters: AnimeFilterList, noToasts : Boolean): Observable<AnimesPage> {
        return client.newCall(searchAnimeRequest(page,query,filters,noToasts))
            .asCancelableObservable()
            .map { response ->
                searchAnimeParse(response)
            }

    }

    override fun animeDetailsParse(document: Document): SAnime {
        val infoDiv = document.selectFirst("article.card div.episode")!!

        return SAnime.create().apply {
            status = infoDiv.selectFirst("li:contains(Statut)")?.ownText()
            genres = infoDiv.select("li:contains(Genre:) ul li").map { it.text() }
            description = buildString {
                append(
                    infoDiv.select("> p:not(:has(strong)):not(:empty)")
                        .joinToString("\n\n") { it.text() })
                append("\n")
                infoDiv.selectFirst("li:contains(Autre Nom)")?.let { append("\n${it.text()}") }
                infoDiv.selectFirst("li:contains(Auteur)")?.let { append("\n${it.text()}") }
                infoDiv.selectFirst("li:contains(Réalisateur)")?.let { append("\n${it.text()}") }
                infoDiv.selectFirst("li:contains(Type)")?.let { append("\n${it.text()}") }
                infoDiv.selectFirst("li:contains(Sortie initiale)")
                    ?.let { append("\n${it.text()}") }
                infoDiv.selectFirst("li:contains(Durée)")?.let { append("\n${it.text()}") }
            }
        }
    }

    override fun fetchAnimeDetails(anime: Anime): Observable<SAnime> {
        return client.newCall(animeDetailsRequest(anime))
            .asCancelableObservable()
            .map { response ->
                animeDetailsParse(response)
            }
    }

    override fun streamSourcesSelector(): String = throw Exception("Unused")

    override fun streamSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("Unused")

    override fun episodesSelector(): String = "div.list-episodes > a"

    override fun episodeFromElement(element: Element): SEpisode {
        val epText = element.ownText()

        return SEpisode.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = epText
            episodeNumber = Regex(" ([\\d.]+) (?:Vostfr|VF)").find(epText)
                ?.groupValues
                ?.get(1)
                ?.toFloatOrNull()
                ?: 1F
            dateUpload = element.selectFirst("span")
                ?.text()
                ?.let(::parseDate)
                ?: 0L
        }
    }

    override fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>> {
        return client.newCall(episodesRequest(anime))
            .asCancelableObservable()
            .map { response ->
                episodesParse(response)
            }
    }

    private val DATE_FORMATTER by lazy {
        SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
    }

    private fun parseDate(dateStr: String): Long {
        return runCatching { DATE_FORMATTER.parse(dateStr)?.time }
            .getOrNull() ?: 0L
    }

    private val streamwishExtractor by lazy { StreamWishExtractor(client, headers) }
    private val vidbmExtractor by lazy { VidbmExtractor(client, headers) }
    private val sendvidExtractor by lazy { SendvidExtractor(client, headers) }
    private val upstreamExtractor by lazy { UpstreamExtractor(client, headers) }
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val voeExtractor by lazy { VoeExtractor(client) }
    private val sibnetExtractor by lazy { SibnetExtractor(client) }

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val document = response.asJsoup()

        val serversList = document.select("div.tab-content iframe[src], div.tab-content iframe[data-src]").mapNotNull {
            val src = it.attr("abs:src")
            val dataSrc = it.attr("abs:data-src")

            val url = when {
                src.isNotBlank() && src != "about:blank" -> src
                dataSrc.isNotBlank() && dataSrc != "about:blank" -> dataSrc
                else -> src
            }

            if (url.contains("parisanime.com")) {
                val docHeaders = headers.newBuilder().apply {
                    add("Accept", "*/*")
                    add("Host", url.toHttpUrl().host)
                    add("Referer", url)
                    add("X-Requested-With", "XMLHttpRequest")
                }.build()

                val newDoc = client.newCall(
                    GET(url, headers = docHeaders),
                ).execute().asJsoup()
                newDoc.selectFirst("div[data-url]")?.attr("data-url")
            } else {
                url
            }
        }
        val streamSourcesList = mutableListOf<StreamSource>()
        streamSourcesList.addAll(
            serversList.parallelMap {
                runCatching {
                    getHosterVideos(it)
                }.getOrNull()
            }.filterNotNull().flatten()
        )

        return streamSourcesList.sort()
    }

    private fun getHosterVideos(host: String): List<StreamSource> {
        return when {
            host.startsWith("https://d00") || host.startsWith("https://doo") -> doodExtractor.videosFromUrl(
                url = host,
                quality = "Doodstream",
                redirect = false
            )

            host.contains("wish") -> streamwishExtractor.videosFromUrl(host)
            host.contains("sibnet.ru") -> sibnetExtractor.videosFromUrl(host)
            host.contains("vedbam") -> vidbmExtractor.videosFromUrl(host)
            host.contains("sendvid.com") -> {
                val fixedHost = if (!host.contains("https:")) "https:$host" else host
                sendvidExtractor.videosFromUrl(fixedHost)
            }

            host.contains("ok.ru") -> okruExtractor.videosFromUrl(host)
            host.contains("upstream") -> upstreamExtractor.videosFromUrl(host)
            host.startsWith("https://voe") -> voeExtractor.videosFromUrl(host)
            else -> emptyList()
        }
    }

    override fun fetchEpisode(url: String): Observable<List<StreamSource>> {
        return client.newCall(episodeRequest(url))
            .asCancelableObservable()
            .map {
                episodeSourcesParse(it)
            }
    }

    private fun String.addPage(page: Int): String {
        return if (page == 1) {
            this
        } else {
            this.toHttpUrl().newBuilder().apply {
                addPathSegment("page")
                addPathSegment(page.toString())
            }.build().toString()
        }
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        return this.groupBy { it.server.lowercase() }.entries
            .sortedWith(
                compareBy { (server, _) ->
                    preferences.playerStreamsOrder.split(",").indexOf(server)
                }
            ).flatMap { group ->
                group.value.sortedWith(
                    compareBy { source ->
                        when {
                            source.quality.isEmpty() -> Int.MAX_VALUE // Empty strings come last
                            else -> {
                                val matchResult = Regex("""(\d+)""").find(source.quality)
                                matchResult?.groupValues?.get(1)?.toInt() ?: Int.MAX_VALUE
                            }
                        }
                    }
                ).reversed()
            }
    }
}