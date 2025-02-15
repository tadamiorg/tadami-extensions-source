package com.sf.tadami.extension.en.animepahe

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.extension.en.animepahe.dto.EpisodeDto
import com.sf.tadami.extension.en.animepahe.dto.LatestAnimeDto
import com.sf.tadami.extension.en.animepahe.dto.ResponseDto
import com.sf.tadami.extension.en.animepahe.dto.SearchResultDto
import com.sf.tadami.extension.en.animepahe.extractors.KwikExtractor
import com.sf.tadami.extension.en.animepahe.interceptors.DdosGuardInterceptor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.online.ConfigurableParsedHttpAnimeSource
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent
import com.sf.tadami.utils.Lang
import com.sf.tadami.utils.editPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Suppress("Unused")
class AnimePahe : ConfigurableParsedHttpAnimeSource<AnimePahePreferences>(
    sourceId = 7,
    prefGroup = AnimePahePreferences
) {
    override val name: String = "AnimePahe"

    private val interceptor = DdosGuardInterceptor(network.client)

    override val client = network.client.newBuilder()
        .addInterceptor(interceptor)
        .build()

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.ENGLISH

    private val i18n = i18n(AnimePaheTranslations)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if (migrated) {
            Log.i("AnimePahe", "Successfully migrated preferences")
        }
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getAnimePahePreferencesContent(i18n)
    }

    private suspend fun preferencesMigrations(): Boolean {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(AnimePahePreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return false
            }
        }
        return true
    }

    // Latest

    // =============================== Latest ===============================
    override fun latestSelector(): String = throw Exception("Not used !")

    override fun latestAnimeFromElement(element: Element): SAnime = throw Exception("Not used !")

    override fun latestAnimeNextPageSelector(): String = throw Exception("Not used !")

    override fun latestAnimesRequest(page: Int): Request = GET("$baseUrl/api?m=airing&page=$page")

    override fun latestAnimeParse(response: Response): AnimesPage {
        val jsonString = response.use { it.body.string() }
        val latestData = json.decodeFromString<ResponseDto<LatestAnimeDto>>(jsonString)
        val hasNextPage = latestData.currentPage < latestData.lastPage
        val animeList = latestData.items.map { anime ->
            SAnime.create().apply {
                title = anime.title
                thumbnailUrl = anime.snapshot
                val animeId = anime.id
                setUrlWithoutDomain("/anime/?anime_id=$animeId")
            }
        }
        return AnimesPage(animeList, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchAnimeNextPageSelector(): String = throw Exception("Not used !")

    override fun searchSelector(): String = throw Exception("Not used !")

    override fun searchAnimeFromElement(element: Element): SAnime = throw Exception("Not used !")

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {
        if (query.isBlank()) {
            throw Exception(i18n.getString("search_animes_blank_error_message"))
        }
        return GET("$baseUrl/api?m=search&l=8&q=$query")
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val jsonString = response.use { it.body.string() }
        val searchData = json.decodeFromString<ResponseDto<SearchResultDto>>(jsonString)
        val animeList = searchData.items.map { anime ->
            SAnime.create().apply {
                title = anime.title
                thumbnailUrl = anime.poster
                val animeId = anime.id
                setUrlWithoutDomain("/anime/?anime_id=$animeId")
            }
        }
        return AnimesPage(animeList, false)
    }

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime = throw Exception("Not used !")

    override fun animeDetailsRequest(anime: Anime): Request {
        val animeId = anime.getId()
        // We're using coroutines here to run it inside another thread and
        // prevent android.os.NetworkOnMainThreadException when trying to open
        // webview or share it.
        val session = runBlocking {
            withContext(Dispatchers.IO) {
                fetchSession(animeId)
            }
        }
        return GET("$baseUrl/anime/$session")
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        return SAnime.create().apply {
            title = document.selectFirst("div.title-wrapper > h1 > span")!!.text()
            status = document.selectFirst("div.col-sm-4.anime-info p:contains(Status:) a")!!.text()
            thumbnailUrl = document.selectFirst("div.anime-poster a")!!.attr("href")
            genres = document.select("div.anime-genre ul li").map { it.text() }
            val synonyms = document.selectFirst("div.col-sm-4.anime-info p:contains(Synonyms:)")
                ?.text()
            description = document.select("div.anime-summary").text() +
                    if (synonyms.isNullOrEmpty()) "" else "\n\n$synonyms"
        }
    }

    // ============================== Episodes ==============================

    override fun episodesSelector(): String = throw Exception("Not used !")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used !")

    override fun episodesRequest(anime: Anime): Request {
        val session = fetchSession(anime.getId())
        return GET("$baseUrl/api?m=release&id=$session&sort=episode_desc&page=1")
    }

    override fun episodesParse(response: Response): List<SEpisode> {
        val url = response.request.url.toString()
        val session = url.substringAfter("&id=").substringBefore("&")
        return recursivePages(response, session)
    }

    private fun parseEpisodePage(
        episodes: List<EpisodeDto>,
        animeSession: String
    ): MutableList<SEpisode> {
        return episodes.map { episode ->
            SEpisode.create().apply {
                dateUpload = episode.createdAt.toDate()
                val session = episode.session
                setUrlWithoutDomain("/play/$animeSession/$session")
                val epNum = episode.episodeNumber
                episodeNumber = epNum
                val epName = if (floor(epNum) == ceil(epNum)) {
                    epNum.toInt().toString()
                } else {
                    epNum.toString()
                }
                name = "Episode $epName"
            }
        }.toMutableList()
    }

    private fun recursivePages(response: Response, animeSession: String): List<SEpisode> {
        val jsonString = response.use { it.body.string() }
        val episodesData = json.decodeFromString<ResponseDto<EpisodeDto>>(jsonString)
        val page = episodesData.currentPage
        val hasNextPage = page < episodesData.lastPage
        val returnList = parseEpisodePage(episodesData.items, animeSession)
        if (hasNextPage) {
            val nextPage = nextPageRequest(response.request.url.toString(), page + 1)
            returnList += recursivePages(nextPage, animeSession)
        }
        return returnList
    }

    private fun nextPageRequest(url: String, page: Int): Response {
        val request = GET(url.substringBeforeLast("&page=") + "&page=$page")
        return client.newCall(request).execute()
    }


    // ============================ Video Links =============================

    override fun streamSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("not used")

    override fun streamSourcesSelector(): String = throw Exception("not used")

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val document = response.asJsoup()
        val downloadLinks = document.select("div#pickDownload > a")
        return document.select("div#resolutionMenu > button").mapIndexed { index, btn ->
            val kwikLink = btn.attr("data-src")
            val quality = btn.text()
            val paheWinLink = downloadLinks[index].attr("href")
            getVideo(paheWinLink, kwikLink, quality)
        }.sort()
    }

    private fun getVideo(paheUrl: String, kwikUrl: String, quality: String): StreamSource {
        return if (preferences.useHlsLinks) {
            val videoUrl = KwikExtractor(client).getHlsStreamUrl(kwikUrl, referer = baseUrl)
            StreamSource(
                url = videoUrl,
                fullName = "Kwik - $quality",
                quality = quality,
                server = "Kwik",
                headers = Headers.headersOf("referer", "https://kwik.cx"),
            )
        } else {
            val videoUrl = KwikExtractor(client).getStreamUrlFromKwik(paheUrl)
            StreamSource(
                url = videoUrl,
                fullName = "Kwik - $quality",
                server = "Kwik",
                quality = quality
            )
        }
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        return this.groupBy { it.server.lowercase() }.entries
            .sortedWith(
                compareBy { (server, _) ->
                    preferences.playerStreamsOrder.split(",").indexOf(server)
                }
            ).flatMap { group ->
                group.value.filter { source ->
                    if (!preferences.englishDub) !source.quality.contains(
                        "eng",
                        ignoreCase = true
                    ) else true
                }.sortedWith(
                    compareBy { source ->
                        when {
                            source.quality.isEmpty() -> Int.MAX_VALUE
                            else -> {
                                val matchResult = Regex("""(\d+)""").find(source.quality)
                                matchResult?.groupValues?.get(1)?.toInt() ?: Int.MAX_VALUE
                            }
                        }
                    }
                ).reversed()
            }
    }

    // ============================= Utilities ==============================
    private fun fetchSession(animeId: String): String {
        val resolveAnimeRequest = client.newCall(GET("$baseUrl/a/$animeId")).execute()
        val sessionId = resolveAnimeRequest.request.url.pathSegments.last()
        return sessionId
    }

    private fun Anime.getId() = url.substringAfterLast("?anime_id=").substringBefore("\"")

    private fun String.toDate(): Long {
        return runCatching {
            DATE_FORMATTER.parse(this)?.time ?: 0L
        }.getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        }
    }
}