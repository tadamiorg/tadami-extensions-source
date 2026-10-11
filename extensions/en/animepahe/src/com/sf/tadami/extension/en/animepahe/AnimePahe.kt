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
import com.sf.tadami.source.model.SAnimeStatus
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

    /** Returns true when an update was migrated (not on a fresh install or an unchanged version). */
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
            return true
        }
        return false
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
                rememberSession(animeId, anime.session)
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
                rememberSession(animeId, anime.session)
                setUrlWithoutDomain("/anime/?anime_id=$animeId")
            }
        }
        return AnimesPage(animeList, false)
    }

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime = throw Exception("Not used !")

    override fun animeDetailsRequest(anime: Anime): Request {
        // We're using coroutines here to run it inside another thread and
        // prevent android.os.NetworkOnMainThreadException when trying to open
        // webview or share it.
        val session = runBlocking {
            withContext(Dispatchers.IO) {
                fetchSession(anime)
            }
        }
        return GET("$baseUrl/anime/$session")
    }

    private fun parseStatus(status: String?): SAnimeStatus {
        return when (status?.trim()?.lowercase()) {
            "currently airing" -> SAnimeStatus.ONGOING
            "finished airing" -> SAnimeStatus.COMPLETED
            else -> SAnimeStatus.UNKNOWN
        }
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        return SAnime.create().apply {
            title = document.selectFirst("div.title-wrapper > h1 > span")!!.text()
            status = parseStatus(document.selectFirst("div.col-sm-4.anime-info p:contains(Status:) a")?.text())
            thumbnailUrl = document.selectFirst("div.anime-poster a")!!.attr("href")
            genres = document.select("div.anime-genre ul li").map { it.text() }
            val synonyms = document.selectFirst("div.col-sm-4.anime-info p:contains(Synonyms:)")
                ?.text()
            description = document.select("div.anime-summary").text() +
                    if (synonyms.isNullOrEmpty()) "" else "\n\n$synonyms"
        }
    }

    // ============================== Episodes List ==============================

    override fun episodesListSelector(): String = throw Exception("Not used !")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used !")

    override fun episodesListRequest(anime: Anime): Request {
        val session = fetchSession(anime)
        return GET("$baseUrl/api?m=release&id=$session&sort=episode_desc&page=1")
    }

    override fun episodesListParse(response: Response): List<SEpisode> {
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

    override fun episodeSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("not used")

    override fun episodeSourcesSelector(): String = throw Exception("not used")

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val document = response.asJsoup()
        // Kwik's firewall only lets through pages embedded by the site: the referer must be the
        // origin the episode page was served from (after redirects), trailing slash included
        val referer = response.request.url.origin()
        val downloadLinks = document.select("div#pickDownload > a")
        val buttons = document.select("div#resolutionMenu > button")
        var error: Throwable? = null
        val sources = buttons.mapIndexedNotNull { index, btn ->
            val kwikLink = btn.attr("data-src")
            val quality = btn.text()
            val paheWinLink = downloadLinks.getOrNull(index)?.attr("href").orEmpty()
            // One quality failing must not hide the others
            runCatching { getVideo(paheWinLink, kwikLink, quality, referer) }
                .onFailure {
                    Log.e("AnimePahe", "Could not extract the $quality stream", it)
                    error = error ?: it
                }
                .getOrNull()
        }
        error?.let { if (sources.isEmpty()) throw it }
        return sources
    }

    private fun getVideo(paheUrl: String, kwikUrl: String, quality: String, referer: String): StreamSource {
        // The video host checks the referer too, it must be the player's origin
        val kwikOrigin = kwikUrl.toHttpUrl().origin()
        val videoUrl = if (preferences.useHlsLinks) {
            KwikExtractor(client).getHlsStreamUrl(kwikUrl, referer = referer)
        } else {
            KwikExtractor(client).getStreamUrlFromKwik(paheUrl)
        }
        return StreamSource(
            url = videoUrl,
            fullName = "Kwik - $quality",
            quality = quality,
            server = "Kwik",
            headers = Headers.headersOf("referer", kwikOrigin),
        )
    }

    private fun HttpUrl.origin(): String = "$scheme://$host/"

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
                        // e.g. "SubsPlease · 1080p BD": the fansub name may hold digits too
                        RESOLUTION_REGEX.find(source.quality)?.groupValues?.get(1)?.toInt()
                            ?: Int.MAX_VALUE
                    }
                ).reversed()
            }
    }

    // ============================= Utilities ==============================

    /** Anime id to its session and when it was resolved: details and episodes ask for it together. */
    private val sessions = ConcurrentHashMap<String, Pair<String, Long>>()

    // The latest and search lists hand the sessions out for free, the search API is rate limited
    private fun rememberSession(animeId: Int, session: String?) {
        if (!session.isNullOrBlank()) sessions[animeId.toString()] = session to System.currentTimeMillis()
    }

    /**
     * The pages and the episodes API take the anime's session rather than its id. The site now
     * blocks the /a/<id> redirect that used to give it (403), but its search results hold both.
     */
    private fun fetchSession(anime: Anime): String {
        val animeId = anime.getId()
        sessions[animeId]?.let { (session, resolvedAt) ->
            if (System.currentTimeMillis() - resolvedAt < SESSION_CACHE_MILLIS) return session
        }
        val session = searchQueries(anime.title).firstNotNullOfOrNull { searchSession(it, animeId) }
            ?: throw Exception(i18n.getString("anime_session_not_found"))
        sessions[animeId] = session to System.currentTimeMillis()
        return session
    }

    // The search only returns its 8 best matches: the exact title, then without the punctuation
    // the search may trip on
    private fun searchQueries(title: String): List<String> {
        val loose = title.replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        return listOf(title.trim(), loose).filter { it.isNotEmpty() }.distinct()
    }

    private fun searchSession(query: String, animeId: String): String? {
        val url = "$baseUrl/api".toHttpUrl().newBuilder()
            .addQueryParameter("m", "search")
            .addQueryParameter("q", query)
            .build()
        return client.newCall(GET(url.toString())).execute().use { response ->
            if (response.code == 429) throw IOException(i18n.getString("search_rate_limited"))
            if (!response.isSuccessful) throw IOException("AnimePahe search failed: HTTP ${response.code}")
            // No match gives a body without results
            val results = runCatching {
                json.decodeFromString<ResponseDto<SearchResultDto>>(response.body.string()).items
            }.getOrDefault(emptyList())
            results.forEach { rememberSession(it.id, it.session) }
            results.firstOrNull { it.id.toString() == animeId }?.session
        }
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
        private val RESOLUTION_REGEX = Regex("""(\d{3,4})p""")
        private const val SESSION_CACHE_MILLIS = 30 * 60 * 1000L
    }
}