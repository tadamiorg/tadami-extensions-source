package com.sf.tadami.extension.fr.vostfree

import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.lib.doodextractor.DoodExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.okruextractor.OkruExtractor
import com.sf.tadami.lib.sibnetextractor.SibnetExtractor
import com.sf.tadami.lib.uqloadextractor.UqloadExtractor
import com.sf.tadami.lib.voeextractor.VoeExtractor
import com.sf.tadami.lib.vudeoextractor.VudeoExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.POST
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.AnimeFilter
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.online.ConfigurableParsedHttpAnimeSource
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent
import com.sf.tadami.ui.utils.UiToasts
import com.sf.tadami.ui.utils.parallelMap
import com.sf.tadami.utils.Lang
import com.sf.tadami.utils.editPreference
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class VostFree : ConfigurableParsedHttpAnimeSource<VostFreePreferences>(
    sourceId = 3,
    prefGroup = VostFreePreferences
) {
    override val name: String = "VostFree"

    override val baseUrl: String = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.cloudflareClient


    private val i18n = i18n(VostFreeTranslations)

    init {
        runBlocking {
            preferencesMigrations()
        }
    }
    private suspend fun preferencesMigrations() {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(VostFreePreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return
            }
        }
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getVostFreePreferencesContent(i18n)
    }

    override fun latestSelector(): String = "div.last-episode"

    override fun latestAnimeNextPageSelector(): String = "div.navigation > a:has(span.next-page)"

    override fun latestAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.title = element.selectFirst("div.title a")!!.text()
        anime.setUrlWithoutDomain(element.selectFirst("div.title a")!!.attr("href"))
        anime.thumbnailUrl = baseUrl + element.selectFirst("span.image img")?.attr("src")
        anime.release = element.selectFirst("ul.additional li:eq(5) > a")?.text()

        return anime
    }

    override fun latestAnimesRequest(page: Int): Request =
        GET("$baseUrl/last-episode.html/page/$page")


    override fun searchSelector(): String = "div.search-result, div.movie-poster"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList, noToasts : Boolean): Request {

        val genreFilter = filters.find { it is GenreList } as GenreList
        val typeFilter = filters.find { it is TypeList } as TypeList

        val formData = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("search_start", "$page")
            .add("story", query)
            .build()

        return when {
            query.isNotBlank() -> {
                if (query.length < 4) {
                    if(!noToasts){
                        UiToasts.showToast(i18n.getString("vostfree_search_length_error"))
                    }
                }
                return POST("$baseUrl/index.php?do=search", headers, formData)
            }
            genreFilter.state != 0 -> GET("$baseUrl/genre/${genreFilters[genreFilter.state].second}/page/$page/")
            typeFilter.state != 0 -> GET("$baseUrl/${typeFilters[typeFilter.state].second}/page/$page/")
            else -> GET("$baseUrl/animes-vostfr/page/$page/")
        }
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        return when {
            element.select("div.search-result").toString() != "" -> latestAnimeFromElement(element)
            else -> searchPopularAnimeFromElement(element)
        }
    }

    private fun searchPopularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(
            element.select("div.play a").attr("href"),
        )
        anime.title = element.select("div.info.hidden div.title").text()
        anime.thumbnailUrl = baseUrl + element.select("div.movie-poster span.image img").attr("src")
        return anime
    }

    override fun searchAnimeNextPageSelector(): String = latestAnimeNextPageSelector()

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.title = document.selectFirst("div.slide-middle h1")!!.text()
        anime.description = document.selectFirst("div.slide-desc")?.text()
        anime.genres = document.select("div.slide-middle ul.slide-top li.right a").map { it.text() }
        anime.thumbnailUrl = baseUrl + document.selectFirst("div.slide-poster img")?.attr("src")
        anime.release = document.selectFirst("div.slide-info p a")?.text()

        return anime
    }

    override fun episodesSelector(): String = throw Exception("Not used")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used")

    override fun episodesParse(response: Response): List<SEpisode> {
        val episodes = mutableListOf<SEpisode>()
        val jsoup = response.asJsoup()
        jsoup.select("select.new_player_selector option").forEachIndexed { index, it ->
            val epNum = it.text().replace("Episode", "").drop(2)

            if (it.text() == "Film") {
                val episode = SEpisode.create().apply {
                    episodeNumber = "1".toFloat()
                    name = "Film"
                }
                episode.url = ("?episode:${0}/${response.request.url}")
                episodes.add(episode)
            } else {
                val episode = SEpisode.create().apply {
                    episodeNumber = epNum.toFloat()
                    name = "Épisode $epNum"
                }
                episode.setUrlWithoutDomain("?episode:$index/${response.request.url}")
                episodes.add(episode)
            }
        }

        return episodes.reversed()
    }

    override fun streamSourcesSelector(): String = throw Exception("Not used")

    override fun streamSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("Not used")

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val epNum = response.request.url.toString().substringAfter("$baseUrl/?episode:")
            .substringBefore("/")
        val realUrl = response.request.url.toString().replace("$baseUrl/?episode:$epNum/", "")

        val document = client.newCall(GET(realUrl)).execute().asJsoup()
        val videoList = mutableListOf<StreamSource>()
        val allPlayerIds = document.select("div.tab-content div div.new_player_top div.new_player_bottom div.button_box")[epNum.toInt()]
        videoList.addAll(
            allPlayerIds.select("div").parallelMap { serverDiv ->
                runCatching {
                    val server = serverDiv.text().lowercase()
                    val playerId = serverDiv.attr("id")
                    val playerFragmentUrl =  document.select("div#player-tabs div.tab-blocks div.tab-content div div#content_$playerId").text()
                    when (server) {
                        "vudeo" -> {
                            val headers = headers.newBuilder()
                                .set("referer", "https://vudeo.io/")
                                .build()
                            VudeoExtractor(client).videosFromUrl(playerFragmentUrl, headers)
                        }
                        "ok" -> {
                            val url = "https://ok.ru/videoembed/$playerFragmentUrl"
                            OkruExtractor(client).videosFromUrl(url, "", false)
                        }
                        "doodstream" -> {
                            DoodExtractor(client).videosFromUrl(playerFragmentUrl, "DoodStream", false)
                        }
                        "sibnet" -> {
                            val url = "https://video.sibnet.ru/shell.php?videoid=$playerFragmentUrl"
                            SibnetExtractor(client).videosFromUrl(url)
                        }
                        "uqload" -> {
                            val url = "https://uqload.io/embed-$playerFragmentUrl.html"
                            UqloadExtractor(client).videosFromUrl(url)
                        }
                        "voe" -> {
                            VoeExtractor(client).videosFromUrl(playerFragmentUrl)
                        }
                        else -> null
                    }
                }.getOrNull()
            }.filterNotNull().flatten(),
        )

        return videoList.sort()
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header(i18n.getString("discover_search_filters_independent")),
            GenreList(arrayOf(i18n.getString("discover_search_screen_filters_group_selected_text") to "") + genreFilters),
            TypeList(arrayOf(i18n.getString("discover_search_screen_filters_group_selected_text") to "") + typeFilters)
        )
    }

    private class GenreList(values: Array<Pair<String, String>>) :
        AnimeFilter.Select("Genre", values.map { it.first }.toTypedArray())


    private val genreFilters =
        arrayOf(
            Pair("Action", "Action"),
            Pair("Comédie", "Comédie"),
            Pair("Drame", "Drame"),
            Pair("Surnaturel", "Surnaturel"),
            Pair("Shonen", "Shonen"),
            Pair("Romance", "Romance"),
            Pair("Tranche de vie", "Tranche+de+vie"),
            Pair("Fantasy", "Fantasy"),
            Pair("Mystère", "Mystère"),
            Pair("Psychologique", "Psychologique"),
            Pair("Sci-Fi", "Sci-Fi"),
        )

    private class TypeList(values: Array<Pair<String, String>>) :
        AnimeFilter.Select("Type", values.map { it.first }.toTypedArray())

    private val typeFilters =
        arrayOf(
            Pair("Animes VOSTFR", "animes-vostfr"),
            Pair("Animes VF", "animes-vf"),
            Pair("Films", "films-vf-vostfr"),
        )


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