package com.sf.tadami.multiexts.dooplay

import com.sf.tadami.network.GET
import com.sf.tadami.network.asCancelableObservable
import com.sf.tadami.network.asJsoup
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.AnimeFilter
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.online.ConfigurableParsedHttpAnimeSource
import io.reactivex.rxjava3.core.Observable
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Multisrc class for the DooPlay wordpress theme.
 * This class takes some inspiration from Tachiyomi's Madara multisrc class.
 */
abstract class DooPlay<T : DooPlayPreferences>(
    sourceId: Long,
    prefGroup: CustomPreferences<T>,
) : ConfigurableParsedHttpAnimeSource<T>(
    sourceId = sourceId,
    prefGroup = prefGroup
) {

    override val baseUrl: String
        get() = preferences.baseUrl

    override val supportRecent = true

    override fun headersBuilder() = super.headersBuilder().add("Referer", baseUrl)

    companion object {
        /**
         * Useful for the URL intent handler.
         */
        const val PREFIX_SEARCH = "path:"
    }

    // ============================== Custom Functions ==============================

    protected fun commonAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val img = element.selectFirst("img")!!
            val url = element.selectFirst("a")?.attr("href") ?: element.attr("href")
            setUrlWithoutDomain(url)
            title = img.attr("alt")
            thumbnailUrl = img.getImageUrl()
        }
    }

    // ============================== Episodes List ==============================

    override fun episodesListSelector() = "ul.episodios > li"

    protected open val episodeNumberRegex by lazy { "(\\d+)$".toRegex() }
    protected open val seasonListSelector = "div#seasons > div"
    protected open val episodeDateSelector = ".date"

    protected open val episodeMovieText = "Movie"

    protected open val episodeSeasonPrefix = "Season"

    override fun episodesListParse(response: Response): List<SEpisode> {
        val doc = getRealAnimeDoc(response.asJsoup())
        val seasonList = doc.select(seasonListSelector)
        return if (seasonList.size < 1) {
            SEpisode.create().apply {
                setUrlWithoutDomain(doc.location())
                episodeNumber = 1F
                name = episodeMovieText
            }.let(::listOf)
        } else {
            seasonList.flatMap(::getSeasonEpisodes).reversed()
        }
    }

    protected open fun getSeasonEpisodes(season: Element): List<SEpisode> {
        val seasonName = season.selectFirst("span.se-t")!!.text()
        return season.select(episodesListSelector()).mapNotNull { element ->
            runCatching {
                episodeFromElement(element, seasonName)
            }.onFailure { it.printStackTrace() }.getOrNull()
        }
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    protected open fun episodeFromElement(element: Element, seasonName: String): SEpisode {
        return SEpisode.create().apply {
            val epNum = element.selectFirst("div.numerando")!!.text()
                .trim()
                .let(episodeNumberRegex::find)
                ?.groupValues
                ?.last() ?: "0"
            val href = element.selectFirst("a[href]")!!
            val episodeName = href.ownText()
            episodeNumber = epNum.toFloatOrNull() ?: 0F
            dateUpload = element.selectFirst(episodeDateSelector)
                ?.text()
                ?.toDate() ?: 0L
            name = "$episodeSeasonPrefix $seasonName x $epNum - $episodeName"
            setUrlWithoutDomain(href.attr("href"))
        }
    }

    // ============================ Episodes Sources =============================

    override fun episodeSourcesParse(response: Response): List<StreamSource> = throw UnsupportedOperationException()

    override fun episodeSourcesSelector(): String = throw UnsupportedOperationException()

    override fun episodeSourcesFromElement(element: Element): List<StreamSource> = throw UnsupportedOperationException()

    // =============================== Search Animes ===============================

    private fun searchAnimeByPathParse(response: Response): AnimesPage {
        val details = animeDetailsParse(response).apply {
            setUrlWithoutDomain(response.request.url.toString())
            initialized = true
        }

        return AnimesPage(listOf(details), false)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val url = response.request.url.toString()

        val animes = when {
            "/?s=" in url -> { // Search by name.
                document.select(searchSelector()).map { element ->
                    searchAnimeFromElement(element)
                }
            }
            else -> { // Search by some kind of filter, like genres or popularity.
                document.select(latestSelector()).map { element ->
                   commonAnimeFromElement(element)
                }
            }
        }

        val hasNextPage = document.selectFirst(searchAnimeNextPageSelector()) != null
        return AnimesPage(animes, hasNextPage)
    }

    override fun fetchSearch(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Observable<AnimesPage> {
        return if (query.startsWith(PREFIX_SEARCH)) {
            val path = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/$path", headers))
                .asCancelableObservable()
                .map(::searchAnimeByPathParse)
        } else {
            super.fetchSearch(page, query, filters, noToasts)
        }
    }

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {
        return when {
            else -> GET("$baseUrl/page/$page/?s=$query", headers)
        }
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            val img = element.selectFirst("img")!!
            title = img.attr("alt")
            thumbnailUrl = img.getImageUrl()
        }
    }

    override fun searchAnimeNextPageSelector() = latestAnimeNextPageSelector()

    override fun searchSelector() = "div.result-item div.image a"

    // =========================== Anime Details ============================

    /**
     * Selector for the element on the anime details page that have some
     * additional info about the anime.
     *
     * @see [Element.getInfo]
     */
    protected open val additionalInfoSelector = "div#info"

    protected open val additionalInfoItems = listOf("Original", "First", "Last", "Seasons", "Episodes")

    protected open fun Document.getDescription(): String {
        return selectFirst("$additionalInfoSelector p")
            ?.let { it.text() + "\n" }
            ?: ""
    }
    override fun animeDetailsParse(document: Document): SAnime {
        val doc = getRealAnimeDoc(document)
        val sheader = doc.selectFirst("div.sheader")!!
        return SAnime.create().apply {
            setUrlWithoutDomain(doc.location())
            sheader.selectFirst("div.poster > img")!!.let {
                thumbnailUrl = it.getImageUrl()
                title = it.attr("alt").ifEmpty {
                    sheader.selectFirst("div.data > h1")!!.text()
                }
            }

            genres = sheader.select("div.data > div.sgeneros > a")
                .eachText()


            doc.selectFirst(additionalInfoSelector)?.let { info ->
                description = buildString {
                    append(doc.getDescription())
                    additionalInfoItems.forEach {
                        info.getInfo(it)?.let(::append)
                    }
                }
            }
        }
    }

    // =============================== Latest Animes ===============================

    override fun latestAnimeNextPageSelector() = "div.resppages > a > span.fa-chevron-right"

    override fun latestSelector() = "div.content article > div.poster"

    override fun latestAnimeFromElement(element: Element): SAnime = commonAnimeFromElement(element)

    protected open val latestUpdatesPath = "episodes"

    override fun latestAnimesRequest(page: Int) = GET("$baseUrl/$latestUpdatesPath/page/$page", headers)

    override fun latestAnimeParse(response: Response): AnimesPage {
        fetchGenresList()
        return super.latestAnimeParse(response)
    }

    // ============================== Filters ===============================

    /**
     * Disable it if you don't want the genres to be fetched.
     */
    protected open val fetchGenres = true

    /**
     * Automatically fetched genres from the source to be used in the filters.
     */
    protected open lateinit var genresArray: FilterItems

    override fun getFilterList(): AnimeFilterList {
        return if (this::genresArray.isInitialized) {
            AnimeFilterList(
                AnimeFilter.Header(genreFilterHeader),
                FetchedGenresFilter(genresListMessage, genresArray),
            )
        } else if (fetchGenres) {
            AnimeFilterList(AnimeFilter.Header(genresMissingWarning))
        } else {
            AnimeFilterList()
        }
    }

    /**
     * Fetch the genres from the source to be used in the filters.
     */
    protected open fun fetchGenresList() {
        if (!this::genresArray.isInitialized && fetchGenres) {
            runCatching {
                client.newCall(genresListRequest())
                    .execute()
                    .asJsoup()
                    .let(::genresListParse)
                    .let { items ->
                        if (items.isNotEmpty()) {
                            genresArray = items
                        }
                    }
            }.onFailure { it.printStackTrace() }
        }
    }

    /**
     * The request to the page that have the genres list.
     */
    protected open fun genresListRequest() = GET(baseUrl)

    /**
     * Get the genres from the document.
     */
    protected open fun genresListParse(document: Document): FilterItems {
        val items = document.select(genresListSelector()).map {
            val name = it.text()
            val value = it.attr("href").substringAfter("$baseUrl/")
            Pair(name, value)
        }.toTypedArray()

        return if (items.isEmpty()) {
            items
        } else {
            arrayOf(Pair(selectFilterText, "")) + items
        }
    }

    protected open val selectFilterText = "<Select>"

    protected open val genreFilterHeader = "NOTE: Filters are going to be ignored if using search text!"

    protected open val genresMissingWarning: String = "Press 'Reset' to attempt to show the genres"

    protected open val genresListMessage = "Genre"

    protected open fun genresListSelector() = "li:contains($genresListMessage) ul.sub-menu li > a"

    class FetchedGenresFilter(title: String, items: FilterItems) : UriPartFilter(title, items)

    open class UriPartFilter(
        displayName: String,
        private val vals: FilterItems,
    ) : AnimeFilter.Select(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].second
    }

    @Suppress("UNUSED")
    private inline fun <reified R> AnimeFilterList.asUriPart(): String {
        return this.first { it is R }.let { it as UriPartFilter }.toUriPart()
    }

    // ============================= Utilities ==============================

    /**
     * The selector to the item in the menu (in episodes page) with the
     * anime page url.
     */
    protected open val animeMenuSelector = "div.pag_episodes div.item a[href] i.fa-bars"

    /**
     * If the document comes from a episode page, this function will get the
     * real/expected document from the anime details page. else, it will return the
     * original document.
     *
     * @return A document from a anime details page.
     */
    protected open fun getRealAnimeDoc(document: Document): Document {
        val menu = document.selectFirst(animeMenuSelector)
        return if (menu != null) {
            val originalUrl = menu.parent()!!.attr("href")
            val req = client.newCall(GET(originalUrl, headers)).execute()
            req.asJsoup()
        } else {
            document
        }
    }

    /**
     * Tries to get additional info from an element at a anime details page,
     * like how many seasons it have, the year it was aired, etc.
     * Useful for anime description.
     */
    protected open fun Element.getInfo(substring: String): String? {
        val target = selectFirst("div.custom_fields:contains($substring)")
            ?: return null
        val key = target.selectFirst("b")!!.text()
        val value = target.selectFirst("span")!!.text()
        return "\n$key: $value"
    }

    /**
     * Tries to get the image url via various possible attributes.
     * Taken from Tachiyomi's Madara multisrc.
     */
    protected open fun Element.getImageUrl(): String? {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
       return this
    }

    protected open val dateFormatter by lazy {
        SimpleDateFormat("MMMM. dd, yyyy", Locale.ENGLISH)
    }

    protected open fun String.toDate(): Long {
        return runCatching { dateFormatter.parse(trim())?.time }
            .getOrNull() ?: 0L
    }
}

typealias FilterItems = Array<Pair<String, String>>