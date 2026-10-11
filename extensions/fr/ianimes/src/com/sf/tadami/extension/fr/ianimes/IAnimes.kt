package com.sf.tadami.extension.fr.ianimes

import android.util.Log
import com.sf.tadami.extension.fr.ianimes.extractors.LuluStreamExtractor
import com.sf.tadami.extension.fr.ianimes.extractors.OdyseeExtractor
import com.sf.tadami.extension.fr.ianimes.extractors.VidaraExtractor
import com.sf.tadami.extension.fr.ianimes.extractors.VidsonicExtractor
import com.sf.tadami.lib.doodextractor.DoodExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.mailruextractor.MailRuExtractor
import com.sf.tadami.lib.streamtapeextractor.StreamTapeExtractor
import com.sf.tadami.lib.vidhide.VidHideExtractor
import com.sf.tadami.lib.vidmolyextractor.VidmolyExtractor
import com.sf.tadami.lib.voeextractor.VoeExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.AnimeFilter
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
import kotlinx.coroutines.flow.first
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
import org.jsoup.parser.Parser
import uy.kohesive.injekt.injectLazy
import java.io.IOException

@Suppress("UNUSED")
class IAnimes : ConfigurableParsedHttpAnimeSource<IAnimesPreferences>(
    sourceId = 102,
    prefGroup = IAnimesPreferences
) {
    override val name: String = "IAnimes"

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.client

    override val supportRecent = true

    private val json: Json by injectLazy()

    private val i18n = i18n(IAnimesTranslations)

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if (migrated) {
            Log.i(TAG, "Successfully migrated preferences")
        }
    }

    // ============================== Preferences ===============================

    /** Returns true when an update was migrated (not on a fresh install or an unchanged version). */
    private suspend fun preferencesMigrations(): Boolean {
        // Read the store itself: `preferences` may still hold the defaults this early
        val oldVersion = dataStore.data.first()[IAnimesPreferences.LAST_VERSION_CODE] ?: 0
        if (oldVersion >= BuildConfig.VERSION_CODE) return false

        dataStore.editPreference(BuildConfig.VERSION_CODE, IAnimesPreferences.LAST_VERSION_CODE)

        // Fresh install: the defaults apply, nothing to migrate
        if (oldVersion == 0) return false

        // Future migrations go here, gated on the version they ship in:
        // if (oldVersion in 1 until N) { ... }
        return true
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getIAnimesPreferencesContent(i18n)
    }

    // =============================== Latest ===============================

    // One page listing the episodes released over the last days, newest first
    override fun latestAnimesRequest(page: Int): Request = GET("$baseUrl/nouveautees.php", headers)

    override fun latestSelector(): String = CARD_SELECTOR

    override fun latestAnimeFromElement(element: Element): SAnime =
        animeFromCard(element) ?: throw IOException("IAnimes: unreadable card in the latest releases")

    override fun latestAnimeNextPageSelector(): String? = null

    override fun latestAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        // Several episodes of a same anime are listed separately
        val animes = document.select(latestSelector())
            .mapNotNull { animeFromCard(it) }
            .distinctBy { it.url }
        return AnimesPage(animes, false)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selectedSlug()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            when {
                query.isNotBlank() -> {
                    addPathSegment("result.php")
                    addQueryParameter("s", query.trim())
                }
                genre != null -> {
                    addPathSegment("categorie.php")
                    addQueryParameter("genre", genre)
                }
                else -> addPathSegment("animes.php")
            }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url.toString(), headers)
    }

    override fun searchSelector(): String = CARD_SELECTOR

    override fun searchAnimeFromElement(element: Element): SAnime? = animeFromCard(element)

    // The page links are numbered, the current one is `a.active`
    override fun searchAnimeNextPageSelector(): String = "div.cat-pagination a.active + a"

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header(i18n.getString("filters_independent")),
            GenreFilter(
                i18n.getString("filters_genre"),
                arrayOf(i18n.getString("filters_genre_all") to "") + GENRES
            ),
        )
    }

    private class GenreFilter(name: String, private val genres: Array<Pair<String, String>>) :
        AnimeFilter.Select(name, genres.map { it.first }.toTypedArray()) {
        fun selectedSlug(): String? = genres.getOrNull(state)?.second?.takeIf { it.isNotEmpty() }
    }

    /**
     * Cards of the latest releases, search results, genre and A-Z lists all share this markup:
     * `<table width="214">` with a `td.card-title`, a cover in a CSS background around the anime
     * link, and `td.card-info` cells ending with the language (VOSTFR, VF).
     */
    private fun animeFromCard(titleCell: Element): SAnime? {
        val card = titleCell.closest("table") ?: return null
        val link = card.selectFirst("a[href*=\"liste.php?manga=\"]") ?: return null
        val siteTitle = (titleCell.selectFirst("span") ?: titleCell).text().unescaped().trim()
        if (siteTitle.isEmpty()) return null
        val href = link.absUrl("href").ifEmpty { link.attr("href") }
        // The card's label is sometimes wrong (a "-vostfr" anime shown as VF), the slug isn't
        val language = LANGUAGE_SLUG_REGEX.find(href)?.groupValues?.get(1)?.uppercase()
            ?: card.select("td.card-info").lastOrNull()?.text()?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val (name, entryLanguage) = splitLanguage(siteTitle, language)

        return SAnime.create().apply {
            title = name
            this.language = entryLanguage
            setUrlWithoutDomain(href)
            thumbnailUrl = card.selectFirst("div[style*=\"url(\"]")?.attr("style")
                ?.let { BACKGROUND_URL_REGEX.find(it)?.groupValues?.get(1) }
                ?.trim()
                ?.takeIf { it.startsWith("http") }
        }
    }

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        val info = informations(document)
        val language = info["langue"]?.uppercase()?.takeIf { it.isNotEmpty() }
        val heading = document.selectFirst("div.header-title h1")?.text()?.unescaped()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: info["titre original"]
            ?: throw IOException("IAnimes: anime title not found on the page")
        val (name, entryLanguage) = splitLanguage(heading, language)

        return SAnime.create().apply {
            title = name
            this.language = entryLanguage
            thumbnailUrl = document.selectFirst("fieldset table.bordures img[src]")?.absUrl("src")
                ?.takeIf { it.startsWith("http") }
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
                    ?.takeIf { it.startsWith("http") }
            genres = document.select("a.genre").map { it.text().trim() }.filter { it.isNotEmpty() }
                .ifEmpty { info["genre"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } }
            status = parseStatus(info["statut"])
            release = info["année"]
            description = buildDescription(document, info)
        }
    }

    /** "Label : Value" pairs of the Informations block, keyed by the lowercased label. */
    private fun informations(document: Document): Map<String, String> {
        return document.select("fieldset table.bordures font[color=\"#FFA07A\"]").mapNotNull { label ->
            val key = label.text().substringBefore(":").trim().lowercase()
            val value = label.nextElementSibling()?.text()?.replace(' ', ' ')
                ?.replace(SPACES_REGEX, " ")?.trim()
            if (key.isEmpty() || value.isNullOrEmpty()) null else key to value
        }.toMap()
    }

    private fun buildDescription(document: Document, info: Map<String, String>): String? {
        val synopsis = document.selectFirst("fieldset:has(legend:contains(Synopsis)) > div")
            ?.wholeText()
            ?.lines()
            ?.map { it.replace(' ', ' ').trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotEmpty() }
        val extras = listOf(
            "Titre alternatif" to info["titre alternatif"],
            "Titre original" to info["titre original"],
            "Format" to info["format"],
            "Pays" to info["pays"],
        ).mapNotNull { (label, value) -> value?.let { "$label : $it" } }

        return listOfNotNull(synopsis, extras.joinToString("\n").takeIf { it.isNotEmpty() })
            .joinToString("\n\n")
            .takeIf { it.isNotEmpty() }
    }

    private fun parseStatus(status: String?): SAnimeStatus {
        return when (status?.lowercase()?.trim()) {
            "en cours", "en cours de diffusion", "ongoing" -> SAnimeStatus.ONGOING
            "terminé", "termine", "fini", "completed" -> SAnimeStatus.COMPLETED
            "en pause", "on hold", "hiatus" -> SAnimeStatus.ON_HIATUS
            "annulé", "annule", "abandonné", "abandonne", "cancelled" -> SAnimeStatus.CANCELLED
            else -> SAnimeStatus.UNKNOWN
        }
    }

    // ============================== Episodes ==============================

    override fun episodesListSelector(): String = "div.ep-box ul.ep-list li > a[href]"

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException("Not used")

    override fun episodesListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val language = informations(document)["langue"]?.uppercase()
        // One `ul.ep-list` per season, each after its "Saison NN" button
        val seasons = document.select("div.ep-box ul.ep-list")
        val multiSeason = seasons.size > 1

        var index = 0
        val episodes = seasons.flatMap { list ->
            val season = list.previousElementSibling()?.selectFirst("a.ep-season-btn")?.text()
                ?.let { NUMBER_REGEX.find(it)?.value?.toIntOrNull() }
            list.select("li > a[href]").map { link ->
                index++
                episodeFromLink(link, if (multiSeason) season else null, index, language)
            }
        }
        // The site lists the oldest first, the app expects the newest first
        return episodes.reversed()
    }

    private fun episodeFromLink(link: Element, season: Int?, index: Int, language: String?): SEpisode {
        val href = link.absUrl("href").ifEmpty { link.attr("href") }
        val number = NUMBER_REGEX.find(link.text())?.value?.toIntOrNull()
        // "bleach-vostfr-film-vostfr.htm"; not "...-film--ep1-vf.htm", an episode of an anime named "... Film 1"
        val file = href.substringAfterLast("/")
        val isFilm = FILM_REGEX.containsMatchIn(file) && !EPISODE_FILE_REGEX.containsMatchIn(file)

        return SEpisode.create().apply {
            setUrlWithoutDomain(href)
            name = when {
                isFilm -> i18n.getString("episode_film")
                number == null -> link.text().trim()
                season != null -> i18n.getString("episode_season_name").format(season, number)
                else -> i18n.getString("episode_name").format(number)
            }
            // Seasons restart at 1, so a running index keeps the numbers increasing
            episodeNumber = when {
                season != null || number == null -> index.toFloat()
                else -> number.toFloat()
            }
            languages = language
        }
    }

    // ============================ Video Links =============================

    override fun episodeSourcesSelector(): String = throw UnsupportedOperationException("Not used")

    override fun episodeSourcesFromElement(element: Element): List<StreamSource> =
        throw UnsupportedOperationException("Not used")

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val document = response.asJsoup()
        val players = document.select("div.player-frame iframe[src], div.player-wrap iframe[src]")
            .mapNotNull { it.absUrl("src").ifEmpty { it.attr("src") }.toHttpUrlOrNull() }
            .distinct()
        if (players.isEmpty()) throw IOException(i18n.getString("error_no_players"))

        // A VOE code plays on any VOE domain. The site links voe.sx and mirrors with random names,
        // some blocked by DNS or broken, so each code is also tried on the page's other VOE hosts
        val voeHosts = (players.filter { hostOf(it) == Host.VOE }.map { it.host } + VOE_HOST).distinct()

        val sources = players.parallelMap { url ->
            runCatching { sourcesFromPlayer(url, voeHosts) }
                .onFailure { Log.e(TAG, "Could not extract the streams of $url", it) }
                .getOrNull()
                .orEmpty()
                .also { if (it.isEmpty()) Log.w(TAG, "No stream from $url") }
        }.flatten()

        if (sources.isEmpty()) throw IOException(i18n.getString("error_no_streams"))
        return sources
    }

    private enum class Host { VIDMOLY, VOE, STREAMTAPE, MAILRU, DOOD, LULUSTREAM, VIDARA, VIDSONIC, ODYSEE, HGCLOUD, UNKNOWN }

    private fun hostOf(url: HttpUrl): Host {
        val host = url.host.lowercase().removePrefix("www.")
        return when {
            "vidmoly" in host -> Host.VIDMOLY
            "voe" in host -> Host.VOE
            "streamtape" in host || host in STREAMTAPE_HOSTS -> Host.STREAMTAPE
            host.endsWith("mail.ru") -> Host.MAILRU
            DOOD_HOSTS.any { it in host } -> Host.DOOD
            host.startsWith("lulu") -> Host.LULUSTREAM
            "vidara" in host -> Host.VIDARA
            "vidsonic" in host -> Host.VIDSONIC
            host.endsWith("odysee.com") -> Host.ODYSEE
            host == "hgcloud.to" || host in HGCLOUD_MIRRORS -> Host.HGCLOUD
            // Dead or encrypted hosts without an extractor (savefiles.com also uses /e/<code>)
            UNSUPPORTED_HOSTS.any { host == it || host.endsWith(".$it") } -> Host.UNKNOWN
            // VOE mirrors have random names (jessicayeahcatch.com, ...), all with /e/<code>
            VOE_MIRROR_REGEX.matches(host) && url.encodedPath.startsWith("/e/") -> Host.VOE
            else -> Host.UNKNOWN
        }
    }

    private fun sourcesFromPlayer(url: HttpUrl, voeHosts: List<String>): List<StreamSource> {
        val link = url.toString()
        return when (hostOf(url)) {
            Host.VIDMOLY -> VidmolyExtractor(client, headers).videosFromUrl(vidmolyUrl(url))
            Host.VOE -> voeSources(url, voeHosts)
            Host.STREAMTAPE -> StreamTapeExtractor(client).videosFromUrl(link)
            Host.MAILRU -> MailRuExtractor(client, headers).videosFromUrl(link)
            Host.DOOD -> DoodExtractor(client).videosFromUrl(link, "DoodStream")
            Host.LULUSTREAM -> LuluStreamExtractor(client, headers).videosFromUrl(link)
            Host.VIDARA -> VidaraExtractor(client, headers, json).videosFromUrl(link)
            Host.VIDSONIC -> VidsonicExtractor(client, headers).videosFromUrl(link)
            Host.ODYSEE -> OdyseeExtractor(client, headers, json).videosFromUrl(link)
            Host.HGCLOUD -> hgCloudSources(url)
            Host.UNKNOWN -> {
                Log.w(TAG, "Unsupported player $link")
                emptyList()
            }
        }
    }

    /** The code on its own host first, then on the other VOE hosts until one serves it. */
    private fun voeSources(url: HttpUrl, voeHosts: List<String>): List<StreamSource> {
        val code = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return emptyList()
        var lastError: Throwable? = null
        for (host in (listOf(url.host) + voeHosts).distinct()) {
            val result = runCatching { VoeExtractor(client, json).videosFromUrl("https://$host/e/$code") }
            result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            lastError = result.exceptionOrNull() ?: lastError
            Log.w(TAG, "VOE $code: nothing on $host" + (result.exceptionOrNull()?.let { " (${it.message})" } ?: ""))
        }
        lastError?.let { throw it }
        return emptyList()
    }

    /**
     * hgcloud.to only serves a script that sends the browser to a random mirror (vibuxer.com,
     * hanerix.com) with the same /e/<code>, a VidHide player. The code is read on the mirrors directly.
     */
    private fun hgCloudSources(url: HttpUrl): List<StreamSource> {
        val code = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return emptyList()
        val mirrors = (listOf(url.host).filter { it in HGCLOUD_MIRRORS } + HGCLOUD_MIRRORS).distinct()
        var lastError: Throwable? = null
        for (mirror in mirrors) {
            val result = runCatching {
                VidHideExtractor(client, "https://$mirror")
                    .videosFromUrl("https://$mirror/e/$code", prefix = HGCLOUD_SERVER, server = HGCLOUD_SERVER)
            }
            result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            lastError = result.exceptionOrNull() ?: lastError
            Log.w(TAG, "HgCloud $code: nothing on $mirror" + (result.exceptionOrNull()?.let { " (${it.message})" } ?: ""))
        }
        lastError?.let { throw it }
        return emptyList()
    }

    /** Old episodes use vidmoly.biz (blocked by some DNS), the same codes play on vidmoly.org. */
    private fun vidmolyUrl(url: HttpUrl): String {
        return if (url.host == VIDMOLY_HOST) url.toString() else url.newBuilder().host(VIDMOLY_HOST).build().toString()
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        val order = preferences.playerStreamsOrder.split(",")
        return this.groupBy { it.server.lowercase() }.entries
            .sortedBy { (server, _) -> order.indexOf(server).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .flatMap { (_, sources) ->
                sources.sortedByDescending { source ->
                    NUMBER_REGEX.find(source.quality)?.value?.toIntOrNull() ?: -1
                }
            }
    }

    // ================================ Utils ===============================

    /**
     * The title without its language marker, and the entry's language (shown by the app as a badge):
     * ("Kyoufu Collector VOSTFR", "VOSTFR") -> ("Kyoufu Collector", "VOSTFR"). A language left in the
     * site's title ("X Vostfr", "X (VF)", "X - VF") is stripped, and used when the page gave none.
     */
    private fun splitLanguage(siteTitle: String, language: String?): Pair<String, String?> {
        var name = siteTitle
        var nameLanguage: String? = null
        repeat(2) {
            val match = TRAILING_LANGUAGE_REGEX.find(name) ?: return@repeat
            val stripped = name.substring(0, match.range.first).trim()
            if (stripped.isEmpty()) return@repeat
            nameLanguage = nameLanguage ?: (match.groupValues[1].ifEmpty { match.groupValues[2] }).uppercase()
            name = stripped
        }
        return name to (language ?: nameLanguage)
    }

    /** Some titles are escaped twice by the site ("Paws &amp;amp; Palaces"). */
    private fun String.unescaped(): String = Parser.unescapeEntities(this, false)

    companion object {
        private const val TAG = "IAnimes"
        private const val CARD_SELECTOR = "td.card-title"
        private const val VIDMOLY_HOST = "vidmoly.org"
        private const val VOE_HOST = "voe.sx"

        private val NUMBER_REGEX = Regex("""\d+""")
        private val SPACES_REGEX = Regex("""\s+""")
        private val FILM_REGEX = Regex("""-film(-|\.htm)""")
        private val EPISODE_FILE_REGEX = Regex("""-ep\d+""")
        /** "X VOSTFR", "X (VF)", "X - Vostfr", "X [VO]" (a separator before the language is required) */
        private val TRAILING_LANGUAGE_REGEX = Regex(
            """[\s\-–—:|,]*[(\[]\s*(VOSTFR|VF|VO|DUB)\s*[)\]]\s*$|[\s\-–—:|,]+(VOSTFR|VF|VO|DUB)\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val LANGUAGE_SLUG_REGEX = Regex("""manga=[^&]*-(vostfr|vf)(?:&|$)""", RegexOption.IGNORE_CASE)
        private val BACKGROUND_URL_REGEX = Regex("""url\(\s*['"]?([^'")]*)['"]?\s*\)""")

        private val STREAMTAPE_HOSTS = setOf("streamta.pe", "strtape.cloud", "strcloud.link", "stape.fun", "shavetape.cash")
        private val DOOD_HOSTS = listOf("dood", "dsvplay", "ds2play", "d000d", "d0000d", "playmogo")
        private val UNSUPPORTED_HOSTS = setOf("savefiles.com", "up4fun.top", "upns.pro")
        private val VOE_MIRROR_REGEX = Regex("""[a-z]{12,}\.com""")
        private const val HGCLOUD_SERVER = "HgCloud"
        private val HGCLOUD_MIRRORS = listOf("vibuxer.com", "hanerix.com")

        private val GENRES = arrayOf(
            "Action" to "action",
            "Amour-Amitié" to "amour-amitie",
            "Aventure" to "aventure",
            "Combat" to "combat",
            "Comédie" to "comedie",
            "Cyber-Mecha" to "cyber-mecha",
            "Drame" to "drame",
            "Ecchi" to "ecchi",
            "Enigmes" to "enigmes",
            "Fantastique" to "fantastique",
            "Horreur" to "horreur",
            "Magical Girl" to "magical-girl",
            "Romance" to "romance",
            "Science-Fiction" to "science-fiction",
            "Sport" to "sport",
        )
    }
}
