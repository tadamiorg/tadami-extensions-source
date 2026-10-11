package com.sf.tadami.extension.fr.franime

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.extension.fr.franime.dto.CustomAnime
import com.sf.tadami.lib.filemoonextractor.FileMoonExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.lpayerextractor.LpayerExtractor
import com.sf.tadami.lib.sendvidextractor.SendvidExtractor
import com.sf.tadami.lib.sibnetextractor.SibnetExtractor
import com.sf.tadami.lib.smoothpreextractor.SmoothPreExtractor
import com.sf.tadami.lib.uqloadextractor.UqloadExtractor
import com.sf.tadami.lib.vidhide.VidHideExtractor
import com.sf.tadami.lib.vidmolyextractor.VidmolyExtractor
import com.sf.tadami.lib.vkextractor.VkExtractor
import com.sf.tadami.lib.youruploadextractor.YourUploadExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.shortTimeOutBuilder
import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SAnimeStatus
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.util.Base64

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

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Referer", "$baseUrl/")
        // Without an override, the app's user agent (already set) matches its WebView, which a
        // Cloudflare clearance is bound to
        FrAnimePreferences.userAgentOverride(preferences.userAgent)?.let { set("User-Agent", it) }
    }

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
    /** Returns true when an update was migrated (not on a fresh install or an unchanged version). */
    private suspend fun preferencesMigrations(): Boolean {
        // Read the store itself: on older apps `preferences` still holds the defaults this early
        val oldVersion = dataStore.data.first()[FrAnimePreferences.LAST_VERSION_CODE] ?: 0
        if (oldVersion >= BuildConfig.VERSION_CODE) return false

        dataStore.editPreference(
            BuildConfig.VERSION_CODE,
            intPreferencesKey(FrAnimePreferences.LAST_VERSION_CODE.name)
        )

        // Writes the defaults on a fresh install. On updates, transform() also brings the
        // stored server order up to date (servers added in v2, Hdvid removed)
        dataStore.editPreferences(
            dataStore.data.map { prefs ->
                FrAnimePreferences.transform(prefs)
            }.first(),
            FrAnimePreferences
        )

        // Fresh install: nothing was migrated
        return oldVersion != 0
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

        // "#n" suffix of each player among the players giving the same server, computed up front
        // since the players are resolved in parallel. It keeps mirrors apart (casting identifies a
        // source by its name)
        val servers = players.map { serverOf(it.lowercase()) }
        val sameSourceCount = servers.mapIndexed { index, server -> servers.take(index + 1).count { it == server } }

        val streamSources = players.withIndex()
            // Hosts without an extractor are skipped before calling the (rate limited) API
            .filter { (_, playerName) -> playerName.lowercase() in SUPPORTED_PLAYERS }
            .parallelMap { (index, playerName) ->
                runCatching {
                    val watchUrl = fetchWatchUrl("$videoBaseUrl/$episodeLang/$index")
                        ?: return@runCatching null
                    val playerUrl = decodePlayerUrl(watchUrl) ?: run {
                        Log.w("FrAnime", "Could not decode the $playerName player of $watchUrl")
                        return@runCatching null
                    }
                    videosFromPlayer(playerName.lowercase(), playerUrl)
                        .map { it.copy(fullName = "${it.fullName.trim()} #${sameSourceCount[index]}") }
                        .also { Log.d("FrAnime", "The $playerName player $playerUrl gave ${it.size} stream(s)") }
                }.onFailure {
                    Log.w("FrAnime", "The $playerName player failed: ${it.message}")
                }.getOrNull()
            }
            .filterNotNull()
            .flatten()

        return Observable.just(streamSources.sort())
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        val order = preferences.playerStreamsOrder.split(",")
        return sortedBy { source ->
            order.indexOf(source.server.lowercase()).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }

    private val extractorClient by lazy { client.shortTimeOutBuilder(15) }

    /** Players served by the same extractor, which names their streams the same way. */
    private fun serverOf(playerName: String): String {
        return when (playerName) {
            "vkru" -> "vk"
            "movearnpre", "minochinos", "dingtezuni" -> "vidhide"
            else -> playerName
        }
    }

    private fun videosFromPlayer(playerName: String, url: String): List<StreamSource> {
        return when (playerName) {
            "sibnet" -> SibnetExtractor(extractorClient).videosFromUrl(url = url)
            "sendvid" -> SendvidExtractor(extractorClient, headers).videosFromUrl(url = url)
            "vidmoly" -> VidmolyExtractor(extractorClient, headers).videosFromUrl(url.replace("vidmoly.to", "vidmoly.net"))
            "filemoon" -> FileMoonExtractor(extractorClient).videosFromUrl(url = url, headers = headers)
            "uqload" -> UqloadExtractor(extractorClient).videosFromUrl(url)
            "vk", "vkru" -> VkExtractor(extractorClient, headers).videosFromUrl(url)
            "lpayer" -> LpayerExtractor(extractorClient).videosFromUrl(url)
            "smoothpre" -> SmoothPreExtractor(extractorClient).videosFromUrl(url)
            "yourupload" -> YourUploadExtractor(extractorClient).videosFromUrl(url, headers)
            "movearnpre" -> VidHideExtractor(extractorClient, MOVEARNPRE_BASE_URL).videosFromUrl(url)
            "minochinos", "dingtezuni" -> VidHideExtractor(extractorClient, url.toHttpUrl().origin()).videosFromUrl(url)
            else -> emptyList()
        }
    }

    /** The player API answers with a watch page URL, retrying once when it is rate limited. */
    private fun fetchWatchUrl(apiUrl: String): String? {
        repeat(2) { attempt ->
            client.newCall(GET(apiUrl, headers)).execute().use { response ->
                if (response.isSuccessful) return response.body.string().trim()
                if (response.code != 429) {
                    Log.w("FrAnime", "The player API answered ${response.code} for $apiUrl")
                    return null
                }
            }
            if (attempt == 0) Thread.sleep(RATE_LIMIT_RETRY_DELAY_MS)
        }
        Log.w("FrAnime", "The player API is still rate limited for $apiUrl")
        return null
    }

    /**
     * The watch page only embeds the host's player, but it sits behind a Cloudflare challenge. The
     * player URL travels in one of its query parameters (the others are decoys) as
     * base64(hex(url xor key)), so it is decoded here instead of loading the page in a WebView.
     */
    private fun decodePlayerUrl(watchUrl: String): String? {
        val url = watchUrl.toHttpUrlOrNull() ?: return null
        return url.queryParameterNames.firstNotNullOfOrNull { name ->
            url.queryParameter(name)?.let(::decodeObfuscatedUrl)
        }
    }

    private fun decodeObfuscatedUrl(value: String): String? {
        val unpadded = value.trimEnd('=')
        val hex = runCatching {
            String(Base64.getDecoder().decode(unpadded + "=".repeat((4 - unpadded.length % 4) % 4)), Charsets.US_ASCII)
        }.getOrNull() ?: return null
        if (hex.length < 16 || hex.length % 2 != 0) return null
        val bytes = hex.chunked(2).map { it.toIntOrNull(16) ?: return null }
        // Every player URL starts with "http", which gives the single byte key away
        val key = bytes.first() xor 'h'.code
        val decoded = buildString { bytes.forEach { append((it xor key).toChar()) } }
        return decoded.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.takeIf { it.all { char -> char.code in 0x21..0x7E } }
    }

    private fun HttpUrl.origin(): String {
        val defaultPort = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
        return "$scheme://$host" + if (defaultPort) "" else ":$port"
    }

    override fun getEpisodeUrl(episode: SEpisode): String {
        return baseUrl + episode.url
    }

    private fun parseStatus(status: String): SAnimeStatus {
        val normalized = status.lowercase()
        return when {
            "cours" in normalized || "ongoing" in normalized -> SAnimeStatus.ONGOING
            "termin" in normalized || "fini" in normalized || "complet" in normalized -> SAnimeStatus.COMPLETED
            "pause" in normalized || "hiatus" in normalized -> SAnimeStatus.ON_HIATUS
            "annul" in normalized || "cancel" in normalized -> SAnimeStatus.CANCELLED
            else -> SAnimeStatus.UNKNOWN
        }
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
                        title = seasonTitle
                        // Shown by the app as a badge, so it stays visible however long the title is
                        language = lang.first
                        thumbnailUrl = anime.poster
                        genres = anime.genres
                        status = parseStatus(anime.status)
                        description = anime.description
                        setUrlWithoutDomain("/anime/${titleToUrl(anime.originalTitle)}?lang=${lang.second}&s=${index + 1}")
                        initialized = true
                    }
                }
            }
        }
    }

    companion object {
        /** FrAnime player names (lowercased) that [videosFromPlayer] can extract. */
        private val SUPPORTED_PLAYERS = setOf(
            "sibnet", "sendvid", "vidmoly", "filemoon", "uqload", "vk", "vkru", "lpayer",
            "smoothpre", "yourupload", "movearnpre", "minochinos", "dingtezuni",
        )
        private const val MOVEARNPRE_BASE_URL = "https://HI3THh5OxxWw.ovaltinecdn.com"
        private const val RATE_LIMIT_RETRY_DELAY_MS = 1500L
    }
}
