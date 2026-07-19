package com.sf.tadami.extension.en.kickassanime

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.extension.en.kickassanime.dto.AnimeInfoDto
import com.sf.tadami.extension.en.kickassanime.dto.EpisodeResponseDto
import com.sf.tadami.extension.en.kickassanime.dto.PopularItemDto
import com.sf.tadami.extension.en.kickassanime.dto.PopularResponseDto
import com.sf.tadami.extension.en.kickassanime.dto.RecentsResponseDto
import com.sf.tadami.extension.en.kickassanime.dto.SearchResponseDto
import com.sf.tadami.extension.en.kickassanime.dto.ServersDto
import com.sf.tadami.extension.en.kickassanime.extractors.KickAssAnimeExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.network.GET
import com.sf.tadami.network.POST
import com.sf.tadami.network.asCancelableObservable
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
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Suppress("Unused")
class KickAssAnime : ConfigurableParsedHttpAnimeSource<KickAssAnimePreferences>(
    sourceId = 101,
    prefGroup = KickAssAnimePreferences
) {
    override val name: String = "KickAssAnime"
    override val baseUrl: String get() = preferences.baseUrl
    private val apiUrl: String get() = "$baseUrl/api/show"
    override val lang: Lang = Lang.ENGLISH
    override val client: OkHttpClient = network.client
    override val supportRecent: Boolean = true

    private val parser: Json by lazy { Json { ignoreUnknownKeys = true; isLenient = true } }
    private val i18n = i18n(KickAssAnimeTranslations)

    private val extractor by lazy { KickAssAnimeExtractor(client, parser, headers) }

    init {
        runBlocking { preferencesMigrations() }
    }

    private suspend fun preferencesMigrations() {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(KickAssAnimePreferences.LAST_VERSION_CODE.name)
            )
            if (oldVersion == 0) return
        }
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getKickAssAnimePreferencesContent(i18n)
    }

    // ================================ Latest =================================

    override fun fetchLatest(page: Int): Observable<AnimesPage> {
        return client.newCall(GET("$apiUrl/recent?type=all&page=$page", headers))
            .asCancelableObservable()
            .map { response ->
                val data = parser.decodeFromString<RecentsResponseDto>(response.body.string())
                AnimesPage(data.result.map { it.toSAnime() }, data.hadNext)
            }
    }

    // ================================ Search =================================

    override fun fetchSearch(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Observable<AnimesPage> {
        val call = if (query.isBlank()) {
            // Browse: trending doesn't require the search-specific headers.
            client.newCall(GET("$apiUrl/trending?page=$page", headers))
        } else {
            val body = buildJsonObject {
                put("page", page)
                put("query", query)
            }.toString().toRequestBody("application/json".toMediaType())
            val searchHeaders = headers.newBuilder()
                .set("Accept", "application/json, text/plain, */*")
                .set("Content-Type", "application/json")
                .build()
            client.newCall(POST("$baseUrl/api/fsearch", searchHeaders, body))
        }

        return call.asCancelableObservable().map { response ->
            val bodyStr = response.body.string()
            if (query.isBlank()) {
                val data = parser.decodeFromString<PopularResponseDto>(bodyStr)
                AnimesPage(data.result.map { it.toSAnime() }, page < data.page_count)
            } else {
                val data = parser.decodeFromString<SearchResponseDto>(bodyStr)
                AnimesPage(data.result.map { it.toSAnime() }, page < data.maxPage)
            }
        }
    }

    // ================================ Details ================================

    override fun fetchAnimeDetails(anime: Anime): Observable<SAnime> {
        return client.newCall(GET("$apiUrl${anime.url}", headers))
            .asCancelableObservable()
            .map { response ->
                val data = parser.decodeFromString<AnimeInfoDto>(response.body.string())
                SAnime.create().apply {
                    title = data.displayTitle()
                    setUrlWithoutDomain("/${data.slug}")
                    data.poster?.let { thumbnailUrl = "$baseUrl/${it.url}" }
                    genres = data.genres
                    status = data.status.parseStatus()
                    release = data.year?.toString()
                    description = buildString {
                        data.synopsis?.let { append(it) }
                        data.season?.let { append("\n\nSeason: ${it.replaceFirstChar(Char::uppercase)}") }
                        data.year?.let { append("\nYear: $it") }
                    }.ifBlank { null }
                }
            }
    }

    // =============================== Episodes ================================

    override fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>> {
        val slug = anime.url.trim('/')
        return client.newCall(GET("$apiUrl/$slug/episodes?page=1&lang=$AUDIO_LANG", headers))
            .asCancelableObservable()
            .map { response ->
                val first = parser.decodeFromString<EpisodeResponseDto>(response.body.string())
                val episodes = first.result.toMutableList()
                // pages[] lists every page; page 1 is already fetched.
                for (page in 2..first.pages.size) {
                    val body = client.newCall(
                        GET("$apiUrl/$slug/episodes?page=$page&lang=$AUDIO_LANG", headers)
                    ).execute().body.string()
                    episodes += parser.decodeFromString<EpisodeResponseDto>(body).result
                }
                episodes.map { ep ->
                    SEpisode.create().apply {
                        url = "/$slug/ep-${ep.episode_string}-${ep.slug}"
                        name = "Ep. ${ep.episode_string}" + (ep.title?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: "")
                        episodeNumber = ep.episode_string.toFloatOrNull() ?: 0f
                        languages = "Japanese"
                    }
                }.reversed()
            }
    }

    // ============================= Video Sources =============================

    override fun fetchEpisodeSources(url: String): Observable<List<StreamSource>> {
        val serversUrl = apiUrl + url.replace("/ep-", "/episode/ep-")
        return client.newCall(GET(serversUrl, headers))
            .asCancelableObservable()
            .map { response ->
                val servers = parser.decodeFromString<ServersDto>(response.body.string()).servers
                servers.flatMap { server ->
                    try {
                        val proute = extractor.videosFromUrl(server.src, server.name)
                        Log.d("KickAssAnime", "Extractor success for ${server.name}: $proute")
                        proute
                    } catch (e: Exception) {
                        Log.e("KickAssAnime", "Extractor error for ${server.name}: ${e.message}")
                        emptyList()
                    }
                }.sort()
            }
    }

    // ================================ Utils ==================================

    private fun PopularItemDto.toSAnime(): SAnime {
        val romaji = title
        val english = title_en
        val posterDto = poster
        val animeSlug = slug
        return SAnime.create().apply {
            title = if (preferences.useEnglishTitles && !english.isNullOrBlank()) english else romaji
            rawTitle = romaji
            setUrlWithoutDomain("/$animeSlug")
            posterDto?.let { thumbnailUrl = "$baseUrl/${it.url}" }
        }
    }

    private fun AnimeInfoDto.displayTitle(): String =
        if (preferences.useEnglishTitles && !title_en.isNullOrBlank()) title_en!! else title

    private fun String.parseStatus(): SAnimeStatus = when (this) {
        "finished_airing" -> SAnimeStatus.COMPLETED
        "currently_airing" -> SAnimeStatus.ONGOING
        else -> SAnimeStatus.UNKNOWN
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        val order = preferences.serverOrder.split(",")
        val audioPriority = preferences.audioOrder.split(",")
        return sortedWith(
            compareBy(
                { order.indexOf(it.server).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { -(Regex("""(\d+)""").find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0) },
            )
        ).map { source ->
            // Reorder each source's audio tracks so the user's preferred language is first (the app picks
            // index 0 by default). Stable sort: languages not in the priority list keep their manifest order.
            if (source.audioTracks.size <= 1) {
                source
            } else {
                source.copy(
                    audioTracks = source.audioTracks.sortedBy { track ->
                        audioPriority.indexOfFirst { it.equals(track.lang, ignoreCase = true) }
                            .let { i -> if (i < 0) Int.MAX_VALUE else i }
                    }
                )
            }
        }
    }

    // ===================== Unused parser/selector stubs ======================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList, noToasts: Boolean): Request =
        throw UnsupportedOperationException("Not used")
    override fun latestAnimesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")
    override fun searchSelector(): String = throw UnsupportedOperationException("Not used")
    override fun searchAnimeFromElement(element: Element): SAnime = throw UnsupportedOperationException("Not used")
    override fun searchAnimeNextPageSelector(): String? = throw UnsupportedOperationException("Not used")
    override fun latestSelector(): String = throw UnsupportedOperationException("Not used")
    override fun latestAnimeFromElement(element: Element): SAnime = throw UnsupportedOperationException("Not used")
    override fun latestAnimeNextPageSelector(): String? = throw UnsupportedOperationException("Not used")
    override fun animeDetailsParse(document: Document): SAnime = throw UnsupportedOperationException("Not used")
    override fun episodesListSelector(): String = throw UnsupportedOperationException("Not used")
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException("Not used")
    override fun episodeSourcesSelector(): String = throw UnsupportedOperationException("Not used")
    override fun episodeSourcesFromElement(element: Element): List<StreamSource> = throw UnsupportedOperationException("Not used")

    companion object {
        private const val AUDIO_LANG = "ja-JP"
    }
}
