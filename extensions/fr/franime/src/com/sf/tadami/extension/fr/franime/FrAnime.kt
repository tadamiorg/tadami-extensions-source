package com.sf.tadami.extension.fr.franime

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.extension.fr.franime.dto.CustomAnime
import com.sf.tadami.extension.fr.franime.interceptors.WebViewResolver
import com.sf.tadami.lib.filemoonextractor.FileMoonExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.sendvidextractor.SendvidExtractor
import com.sf.tadami.lib.sibnetextractor.SibnetExtractor
import com.sf.tadami.network.GET
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
import com.sf.tadami.utils.editPreferences
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

@Suppress("UNUSED")
class FrAnime : ConfigurableParsedHttpAnimeSource<FrAnimePreferences>(
    sourceId = 15,
    prefGroup = FrAnimePreferences
) {
    override val name: String = "FrAnime"

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.client

    private val i18n = i18n(FrAnimeTranslations)

    override val supportEpisodeTooltip: Boolean = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", preferences.userAgent)

    private val json: Json by injectLazy()
    private val baseApiUrl = "https://api.${baseUrl.toHttpUrl().host}/api"
    private val baseApiAnimeUrl = "$baseApiUrl/anime"

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if (migrated) {
            Log.i("FrAnime", "Successfully migrated preferences")
        }
    }


    // ============================== Preferences ===============================
    private suspend fun preferencesMigrations(): Boolean {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(FrAnimePreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                dataStore.editPreferences(
                    dataStore.data.map { prefs ->
                        FrAnimePreferences.transform(prefs)
                    }.first(),
                    FrAnimePreferences
                )
                return false
            }
        }
        return true
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getFrAnimePreferencesContent(i18n)
    }

    private val database by lazy {
        client.newCall(GET("$baseApiUrl/animes/", headers)).execute()
            .body.string()
            .let { json.decodeFromString<List<CustomAnime>>(it) }
    }

    // =============================== Latest ===============================

    override fun latestSelector(): String = throw UnsupportedOperationException()

    override fun latestAnimeFromElement(element: Element): SAnime =
        throw UnsupportedOperationException()

    override fun latestAnimeNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun fetchLatest(page: Int) =
        Observable.just(pagesToAnimesPage(database.sortedByDescending { anime ->
            anime.updateTime
        }, page))

    override fun latestAnimeParse(response: Response): AnimesPage =
        throw UnsupportedOperationException()

    override fun latestAnimesRequest(page: Int): Request = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchSelector(): String = throw UnsupportedOperationException()

    override fun searchAnimeFromElement(element: Element): SAnime =
        throw UnsupportedOperationException()

    override fun searchAnimeNextPageSelector(): String = throw UnsupportedOperationException()

    override fun fetchSearch(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Observable<AnimesPage> {
        val pages = database.filter {
            it.title.contains(query, true) ||
                    it.originalTitle.contains(query, true) ||
                    it.titlesAlt.en?.contains(query, true) == true ||
                    it.titlesAlt.enJp?.contains(query, true) == true ||
                    it.titlesAlt.jaJp?.contains(query, true) == true ||
                    titleToUrl(it.originalTitle).contains(query)
        }
        return Observable.just(pagesToAnimesPage(pages, page))
    }

    override fun searchAnimeParse(response: Response): AnimesPage =
        throw UnsupportedOperationException()

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request =
        throw UnsupportedOperationException()

    // =========================== Anime Details ============================

    override fun fetchAnimeDetails(anime: Anime): Observable<SAnime> {
        val sanime = SAnime.create().apply {
            url = anime.url
            title = anime.title
            description = anime.description
            genres = anime.genres
            status = anime.status
            release = anime.release
            thumbnailUrl = anime.thumbnailUrl
            initialized = anime.initialized
        }
        return Observable.just(sanime)
    }


    override fun animeDetailsParse(document: Document): SAnime =
        throw UnsupportedOperationException()


    // ============================== Episodes ==============================
    override fun getEpisodeTooltip(): String = i18n.getString("source_episode_tooltip_content")

    override fun episodesListSelector(): String = throw UnsupportedOperationException()

    override fun episodeFromElement(element: Element): SEpisode =
        throw UnsupportedOperationException()

    override fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>> {
        val url = (baseUrl + anime.url).toHttpUrl()
        val stem = url.encodedPathSegments.last()
        val language = url.queryParameter("lang") ?: "vo"
        val season = url.queryParameter("s")?.toIntOrNull() ?: 1
        val animeData = database.first { titleToUrl(it.originalTitle) == stem }
        val episodes = animeData.seasons[season - 1].episodes
            .mapIndexedNotNull { index, episode ->
                val players = when (language) {
                    "vo" -> episode.languages.vo
                    else -> episode.languages.vf
                }.players

                if (players.isEmpty()) return@mapIndexedNotNull null

                SEpisode.create().apply {
                    setUrlWithoutDomain(anime.url + "&ep=${index + 1}")
                    name = episode.title ?: "Episode ${index + 1}"
                    episodeNumber = (index + 1).toFloat()
                }
            }
        return Observable.just(episodes.sortedByDescending { it.episodeNumber })
    }

    override fun episodesListParse(response: Response): List<SEpisode> =
        throw UnsupportedOperationException()


    // ============================ Episode sources =============================

    override fun episodeSourcesSelector(): String = throw UnsupportedOperationException()

    override fun episodeSourcesFromElement(element: Element): List<StreamSource> =
        throw UnsupportedOperationException()

    override fun fetchEpisodeSources(url: String): Observable<List<StreamSource>> {
        val episodeSourcesUrl = (baseUrl + url).toHttpUrl()
        val seasonNumber = episodeSourcesUrl.queryParameter("s")?.toIntOrNull() ?: 1
        val episodeNumber = episodeSourcesUrl.queryParameter("ep")?.toIntOrNull() ?: 1
        val episodeLang = episodeSourcesUrl.queryParameter("lang") ?: "vo"
        val stem = episodeSourcesUrl.encodedPathSegments.last()
        val animeData = database.first { titleToUrl(it.originalTitle) == stem }
        val episodeData = animeData.seasons[seasonNumber - 1].episodes[episodeNumber - 1]
        val videoBaseUrl =
            "$baseApiAnimeUrl/${animeData.id}/${seasonNumber - 1}/${episodeNumber - 1}"

        val players =
            if (episodeLang == "vo") episodeData.languages.vo.players else episodeData.languages.vf.players

        val sendvidExtractor by lazy { SendvidExtractor(client, headers) }
        val sibnetExtractor by lazy { SibnetExtractor(client) }
        val fileMoonExtractor by lazy { FileMoonExtractor(client) }
        val streamSourcesList = mutableListOf<StreamSource>()
        val webViewResolver = WebViewResolver(headers)
        val sameSourceCount = mutableMapOf<String,Int>()

        streamSourcesList.addAll(
            players.withIndex().parallelMap { (index, playerName) ->
                runCatching {
                    sameSourceCount[playerName] = sameSourceCount.getOrDefault(playerName, 0) + 1
                    val apiUrl = "$videoBaseUrl/$episodeLang/$index"
                    val frAnimePlayerUrl = client.newCall(GET(apiUrl, headers)).execute().body.string()
                    when (playerName) {
                        "sendvid" -> sendvidExtractor.videosFromUrl(url = webViewResolver.getPlayerUrl(frAnimePlayerUrl),suffix = "#${sameSourceCount[playerName]}")
                        "sibnet" -> sibnetExtractor.videosFromUrl(url = webViewResolver.getPlayerUrl(frAnimePlayerUrl), suffix = "#${sameSourceCount[playerName]}")
                        "filemoon" -> fileMoonExtractor.videosFromUrl(url = webViewResolver.getPlayerUrl(frAnimePlayerUrl), headers = headers)
                        "hd" -> listOf(StreamSource(url = frAnimePlayerUrl, fullName = "Hdvid #${sameSourceCount[playerName]}", server = "Hdvid", headers = headers))
                        else -> emptyList()
                    }
                }.getOrNull()
            }.filterNotNull().flatten()

        )

        return Observable.just(streamSourcesList)
    }

    override fun getEpisodeUrl(episode: SEpisode): String {
        return baseUrl + episode.url
    }

    // ============================= Utilities ==============================
    private fun pagesToAnimesPage(pages: List<CustomAnime>, page: Int): AnimesPage {
        val chunks = pages.chunked(50)
        val hasNextPage = chunks.size > page
        val entries = pageToSAnimes(chunks.getOrNull(page - 1) ?: emptyList())
        return AnimesPage(entries, hasNextPage)
    }

    private val titleRegex by lazy { Regex("[^A-Za-z0-9 ]") }
    private fun titleToUrl(title: String) =
        titleRegex.replace(title, "").replace(" ", "-").lowercase()

    private fun pageToSAnimes(page: List<CustomAnime>): List<SAnime> {
        return page.flatMap { anime ->
            anime.seasons.flatMapIndexed { index, season ->
                val seasonTitle = anime.title + if (anime.seasons.size > 1) " S${index + 1}" else ""
                val hasVostfr = season.episodes.any { ep -> ep.languages.vo.players.isNotEmpty() }
                val hasVf = season.episodes.any { ep -> ep.languages.vf.players.isNotEmpty() }

                // I want to die for writing this
                val languages = listOfNotNull(
                    if (hasVostfr) Triple("VOSTFR", "vo", hasVf) else null,
                    if (hasVf) Triple("VF", "vf", hasVostfr) else null,
                )

                languages.map { lang ->
                    SAnime.create().apply {
                        title = seasonTitle + if (lang.third) " (${lang.first})" else ""
                        thumbnailUrl = anime.poster
                        genres = anime.genres
                        status = anime.status
                        description = anime.description
                        setUrlWithoutDomain("/anime/${titleToUrl(anime.originalTitle)}?lang=${lang.second}&s=${index + 1}")
                        initialized = true
                    }
                }
            }
        }
    }
}