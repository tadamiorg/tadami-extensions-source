package com.sf.tadami.extension.en.gogoanime

import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.extension.en.gogoanime.filters.GogoAnimeFilters
import com.sf.tadami.lib.doodextractor.DoodExtractor
import com.sf.tadami.lib.gogostreamextractor.GogoStreamExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.mp4uploadextractor.Mp4uploadExtractor
import com.sf.tadami.lib.streamwishextractor.StreamWishExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.asCancelableObservable
import com.sf.tadami.network.asJsoup
import com.sf.tadami.network.asObservable
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GogoAnime : ConfigurableParsedHttpAnimeSource<GogoAnimePreferences>(
    sourceId = 1,
    prefGroup = GogoAnimePreferences
) {
    override val name: String = "GogoAnime"
    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.ENGLISH

    private val ajaxBaseUrl: String = "https://ajax.gogocdn.net/ajax"

    override val client: OkHttpClient = network.cloudflareClient

    private val i18n = i18n(GogoAnimeTranslations)

    init {
        runBlocking {
            preferencesMigrations()
        }
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getGogoAnimePreferencesContent(i18n)
    }

    private suspend fun preferencesMigrations() : Boolean {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(GogoAnimePreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return false
            }
        }
        return true
    }

    // Latest

    override fun latestSelector(): String {
        return "ul.items li"
    }

    override fun latestAnimeNextPageSelector(): String =
        "ul.pagination-list li:last-child:not(.selected)"

    override fun latestAnimeFromElement(element: Element): SAnime {
        val anime: SAnime = SAnime.create()
        val imgRef = element.select("div.img a").first()
        anime.title = element.select("p.name").first()!!.text()
        anime.url = getDetailsURL(imgRef?.select("img")?.first()?.attr("src"))
        anime.thumbnailUrl = imgRef!!.select("img").first()?.attr("src")
        return anime
    }

    private fun getDetailsURL(episodeURL: String?): String {
        return "/category/" + episodeURL?.split("/")?.last()
            ?.replace(Regex("(-[0-9]{5,}\\..*\$)|(\\..*\$)"), "")
    }

    override fun latestAnimesRequest(page: Int): Request {
        return GET("${ajaxBaseUrl}/page-recent-release.html?page=$page&type=1", headers)
    }

    // Search

    override fun searchSelector(): String = "ul.items > li"

    override fun searchAnimeNextPageSelector(): String =
        "ul.pagination-list li:last-child:not(.selected)"

    override fun searchAnimeFromElement(element: Element): SAnime? {
        val anime: SAnime = SAnime.create()
        anime.title = element.select("p.name").text()
        anime.thumbnailUrl = element.select("div.img > a > img").attr("src")
        val url = element.selectFirst("p.name > a")?.attr("href") ?: return null
        anime.setUrlWithoutDomain(url)
        return anime
    }

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {
        val params = GogoAnimeFilters.getSearchParameters(filters)
        val request = when {
            params.genre.isNotEmpty() -> GET("$baseUrl/genre/${params.genre}?page=$page", headers)
            params.recent.isNotEmpty() -> GET(
                "https://ajax.gogo-load.com/ajax/page-recent-release.html?page=$page&type=${params.recent}",
                headers
            )

            params.season.isNotEmpty() -> GET("$baseUrl/${params.season}?page=$page", headers)
            else -> GET("$baseUrl/filter.html?keyword=$query&${params.filter}&page=$page", headers)
        }
        return request
    }

    // Details

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        anime.title = document.selectFirst("div.anime_info_body_bg > h1")!!.text()
        anime.genres = document.getInfo("Genre:")?.select("a")?.map { it.attr("title") } ?: emptyList()
        anime.description = document.select("div.description").text()
        anime.status = document.getInfo("Status:")?.select("a")?.text()
        anime.release = document.getInfo("Released:")?.ownText()

        return anime
    }

    private fun Document.getInfo(text: String): Element? {
        return selectFirst("p.type:has(span:containsOwn($text))") ?: null
    }

    // Episodes List

    override fun episodesSelector(): String = "li > a"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        val ep = element.selectFirst("div.name")?.ownText()?.substringAfter(" ") ?: ""
        episode.setUrlWithoutDomain(baseUrl + element.attr("href").substringAfter(" "))
        episode.name = "Episode $ep"
        episode.episodeNumber = ep.toFloat()
        return episode
    }

    private fun getGogoEpisodesRequest(response: Response): Request {
        val document = response.asJsoup()

        val lastEp = document.select("ul#episode_page li a").last()?.attr("ep_end")
        val animeId = document.select("input#movie_id").attr("value")

        return GET(
            "$ajaxBaseUrl/load-list-episode?ep_start=0&ep_end=$lastEp&id=$animeId",
            headers
        )
    }

    override fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>> {
        val episodesListRequest = client.newCall(episodesRequest(anime))
            .asObservable()
            .map { response ->
                getGogoEpisodesRequest(response)
            }
        return episodesListRequest.flatMap { request ->
            client.newCall(request)
                .asCancelableObservable().map { response ->
                    episodesParse(response)
                }
        }
    }

    // Episode Source Stream

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

    override fun streamSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("not used")

    override fun streamSourcesSelector(): String = throw Exception("not used")

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val document = response.asJsoup()
        val gogoExtractor = GogoStreamExtractor(network.client)
        val streamwishExtractor = StreamWishExtractor(client, headers)
        val streamSourcesList = mutableListOf<StreamSource>()
        streamSourcesList.addAll(
            document.select("div.anime_muti_link > ul > li").parallelMap { server ->
                runCatching {
                    val className = server.className()
                    val serverUrl = server.selectFirst("a")
                        ?.attr("data-video")
                        ?.replace(Regex("^//"), "https://")
                        ?: return@runCatching null
                    when (className) {
                        "anime", "vidcdn" -> {
                            gogoExtractor.videosFromUrl(serverUrl)
                        }

                        "streamwish" -> streamwishExtractor.videosFromUrl(serverUrl)
                        "doodstream" -> {
                            DoodExtractor(client).videosFromUrl(serverUrl)
                        }

                        "mp4upload" -> {
                            val headers =
                                headers.newBuilder().set("Referer", "https://mp4upload.com/")
                                    .build()
                            Mp4uploadExtractor(client).videosFromUrl(serverUrl, headers)
                        }

                        "filelions" -> {
                            streamwishExtractor.videosFromUrl(
                                serverUrl,
                                videoNameGen = { quality -> "FileLions - $quality" }).map {
                                    it.copy(server="FileLions")
                            }
                        }

                        else -> null
                    }
                }.getOrNull()
            }.filterNotNull().flatten(),
        )
        return streamSourcesList.sort()
    }

    // Filters

    override fun getFilterList(): AnimeFilterList = GogoAnimeFilters.FILTER_LIST(i18n)
}