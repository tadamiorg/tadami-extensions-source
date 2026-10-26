package com.sf.tadami.extension.fr.animesama

import android.os.SystemClock
import android.text.Html
import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.lpayerextractor.LpayerExtractor
import com.sf.tadami.lib.sendvidextractor.SendvidExtractor
import com.sf.tadami.lib.sibnetextractor.SibnetExtractor
import com.sf.tadami.lib.uqloadextractor.UqloadExtractor
import com.sf.tadami.lib.vidhide.VidHideExtractor
import com.sf.tadami.lib.vidmolyextractor.VidmolyExtractor
import com.sf.tadami.lib.vkextractor.VkExtractor
import com.sf.tadami.lib.voeextractor.VoeExtractor
import com.sf.tadami.lib.youruploadextractor.YourUploadExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.HEAD
import com.sf.tadami.network.asCancelableObservable
import com.sf.tadami.network.asJsoup
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
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.util.Locale

@Suppress("UNUSED")
class AnimeSama : ConfigurableParsedHttpAnimeSource<AnimeSamaPreferences>(
    sourceId = 2,
    prefGroup = AnimeSamaPreferences
) {
    override val name: String = "AnimeSama"

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", preferences.userAgent)

    private val VOICES_VALUES = listOf("vostfr", "vf", "vj", "va")

    private val i18n = i18n(AnimeSamaTranslations)

    private val json: Json by injectLazy()

    private fun getMainUrl() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mainUrl = client.newCall(Request.Builder().url(baseUrl).build()).execute()
                    .use { response ->
                        val finalUrl = response.request.url
                        "${finalUrl.scheme}://${finalUrl.host}"
                    }

                if (mainUrl == baseUrl) return@launch

                dataStore.editPreference(
                    mainUrl,
                    stringPreferencesKey(AnimeSamaPreferences.BASE_URL.name)
                )
            } catch (e: Exception) {
                Log.e("AnimeSama", "Error getting main url", e)
            }
        }
    }

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if (migrated) {
            Log.i("AnimeSama", "Successfully migrated preferences")
        }
        getMainUrl()
    }


    // ============================== Preferences ===============================

    /** Returns true when an update was migrated (not on a fresh install or an unchanged version). */
    private suspend fun preferencesMigrations(): Boolean {
        // Read the store itself: on older apps `preferences` still holds the defaults this early
        val stored = dataStore.data.first()
        val oldVersion = stored[AnimeSamaPreferences.LAST_VERSION_CODE] ?: 0
        if (oldVersion >= BuildConfig.VERSION_CODE) return false

        dataStore.editPreference(
            BuildConfig.VERSION_CODE,
            intPreferencesKey(AnimeSamaPreferences.LAST_VERSION_CODE.name)
        )

        // Fresh install: the defaults apply
        if (oldVersion == 0) return false

        // v31: an order the user never changed follows the new default (AnsEmbed and VOE first, Sibnet last)
        if (oldVersion in 1 until 31 &&
            stored[AnimeSamaPreferences.PLAYER_STREAMS_ORDER] in AnimeSamaPreferences.LEGACY_DEFAULT_PLAYER_STREAMS_ORDERS
        ) {
            dataStore.editPreference(
                AnimeSamaPreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                AnimeSamaPreferences.PLAYER_STREAMS_ORDER
            )
        }

        // transform() brings a stored order up to date: merged servers take the place of the first of them,
        // removed ones are dropped and new ones appended. Written back so the preference screen shows it
        val order = dataStore.data.map { AnimeSamaPreferences.transform(it).playerStreamsOrder }.first()
        dataStore.editPreference(order, AnimeSamaPreferences.PLAYER_STREAMS_ORDER)

        return true
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getAnimeSamaPreferencesContent(i18n)
    }


    // ============================== Latest Animes ===============================

    override fun latestSelector(): String = "#containerAjoutsAnimes div a:first-of-type"

    override fun latestAnimeNextPageSelector(): String = throw Exception("Not used")

    override fun latestAnimeParse(response: Response): AnimesPage {
        val cards = response.asJsoup().select(latestSelector())
        val animes = cards.map { latestAnimeFromElement(it) }

        // A card only names the anime: its season comes from the anime's page, as in search and details,
        // so that every screen gives the same title (one request per anime)
        val pages = animes.map { animePath(it.url) }.distinct().parallelMap { path ->
            path to runCatching { client.newCall(GET("$baseUrl$path", headers)).execute().use { it.asJsoup() } }
                .onFailure { Log.w(TAG, "Could not load $path", it) }
                .getOrNull()
        }.toMap()

        animes.zip(cards).forEach { (anime, card) ->
            val page = pages[animePath(anime.url)]
            val animeName = page?.selectFirst("h1")?.text()
            val seasonName = page?.let { seasonNameOf(it, anime.url) }
            if (animeName != null && seasonName != null) {
                anime.title = seasonTitle(seasonName, animeName)
                anime.rawTitle = animeName
            } else {
                // Page unreachable or season not listed yet: the card says "Saison 2 Episode 5"
                card.selectFirst("span.info-text")?.text()?.substringBefore(" Episode")?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { anime.title = seasonTitle(it, anime.title) }
            }
        }

        return AnimesPage(animes, false)
    }

    override fun latestAnimeFromElement(element: Element): SAnime {
        val titleText = element.selectFirst("h2.card-title")?.text() ?: ""
        val anime: SAnime = SAnime.create()
        anime.title = titleText
        anime.rawTitle = titleText.ifBlank { null }
        anime.thumbnailUrl = element.selectFirst("img")?.attr("src")
        anime.setUrlWithoutDomain(element.attr("href").removeSuffix("/"))
        // Shown by the app as a badge, so it stays visible however long the title is. From the url, as in
        // search and details (the card's flag says "VASTFR" for /va)
        anime.language = languageOf(anime.url)
        return anime
    }

    override fun latestAnimesRequest(page: Int): Request = GET(baseUrl, headers)


    // ============================== Search Animes ===============================

    override fun searchSelector(): String =
        "#list_catalog > div"

    override fun fetchSearch(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Observable<AnimesPage> {
        return client.newCall(searchAnimeRequest(page, query.trim(), filters, noToasts))
            .asCancelableObservable()
            .flatMap { response ->

                val document = response.asJsoup()

                val animeList = document.select(searchSelector()).map { element ->
                    searchAnimeFromElement(element)
                }

                val hasNextPage = searchAnimeNextPageSelector().let { selector ->
                    document.select(selector).first()
                } != null

                val pageRequests = Observable.fromIterable(animeList).flatMap { anime ->
                    client.newCall(GET("$baseUrl${anime.url}", headers))
                        .asCancelableObservable()
                        .map { response ->
                            val doc = response.asJsoup()
                            val animeName = doc.selectFirst("h1")?.text() ?: anime.title

                            val seasons = seasonPanels(doc).flatMap { (seasonName, seasonUrl) ->
                                VOICES_VALUES.parallelMap { voice ->
                                    getLangUrl(
                                        anime = anime,
                                        seasonName = seasonName,
                                        seasonUrl = seasonUrl,
                                        lang = voice
                                    )
                                }.filterNotNull()
                            }

                            seasons.map { (seasonName, seasonUrl, language) ->
                                val animeSeason = SAnime.create()
                                animeSeason.url = "${anime.url}/$seasonUrl"
                                animeSeason.thumbnailUrl = anime.thumbnailUrl
                                animeSeason.title = seasonTitle(seasonName, animeName)
                                animeSeason.rawTitle = animeName
                                animeSeason.language = language
                                animeSeason
                            }
                        }
                }.toList().toObservable()

                pageRequests.map { pages ->
                    val animeSeasons = pages.flatten()
                    AnimesPage(animeSeasons, hasNextPage)
                }

            }
    }

    private fun getLangUrl(
        anime: SAnime,
        seasonName: String,
        seasonUrl: String,
        lang: String
    ): Triple<String, String, String>? {
        try {
            val langUrlStem = "${seasonUrl.substringBeforeLast("/")}/$lang"
            val langRes =
                client.newCall(HEAD("$baseUrl${anime.url}/$langUrlStem", headers)).execute()
            if (langRes.isSuccessful) {
                return Triple(seasonName, langUrlStem, lang.uppercase(Locale.getDefault()))
            }
            return null
        } catch (_: Exception) {
            return null
        }

    }

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {

        return when {
            query.isNotEmpty() -> {
                GET("$baseUrl/catalogue/?type[]=Anime&search=$query&page=$page", headers)
            }

            else -> {
                GET("$baseUrl/catalogue/?type[]=Anime&page=$page", headers)
            }
        }
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime: SAnime = SAnime.create()
        anime.title = element.selectFirst("h2.card-title")?.text() ?: ""
        anime.thumbnailUrl = element.select("img").attr("src")
        anime.setUrlWithoutDomain(element.selectFirst("a")!!.attr("href").removeSuffix("/"))
        return anime
    }


    override fun searchAnimeNextPageSelector(): String = "div#list_pagination a.bg-sky-900 ~ a"


    // ============================== Anime Details ===============================

    override fun animeDetailsRequest(anime: Anime): Request {
        return GET(baseUrl + anime.url + "/../..", headers)
    }

    override fun fetchAnimeDetails(anime: Anime): Observable<SAnime> {
        return client.newCall(animeDetailsRequest(anime))
            .asCancelableObservable()
            .map { response ->
                Log.d("AnimeSama", "fetchAnimeDetails: ${response.request.url.toString()}")
                animeDetailsParse(response.asJsoup(), anime)
            }
    }

    private fun animeDetailsParse(document: Document, dbAnime: Anime): SAnime {
        val anime = SAnime.create()
        val animeTitle = document.selectFirst("h1")?.text()
        val seasonName = seasonNameOf(document, dbAnime.url)

        if (animeTitle != null && seasonName != null) {
            anime.title = seasonTitle(seasonName, animeTitle)
            // The untouched anime name, for search and migration (the title starts with the season)
            anime.rawTitle = animeTitle
            anime.language = languageOf(dbAnime.url)
        } else {
            // No panel for this url (yet), or not the anime's page (a 404 "Page introuvable"): keep what we had
            anime.title = dbAnime.title
        }
        anime.description =
            document.selectFirst("#synopsisText")?.text()
        anime.genres = document.select(".oe-genres a.genre-pill").map { it.text().trim() }
        anime.thumbnailUrl = document.selectFirst("meta[itemprop=image]")?.attr("content")

        // The anime's list comes first, before the source manga's one (which has its own "Statut")
        val statusText = infoOf(document, "Statut") ?: ""

        anime.status = when {
            statusText.contains("En cours", true) -> SAnimeStatus.ONGOING
            statusText.contains("Terminé", true) -> SAnimeStatus.COMPLETED
            else -> SAnimeStatus.UNKNOWN
        }

        // "20 oct. 1999"; films have no airing date, only the year in "2016 · Film · CoMix Wave · Terminé"
        anime.release = infoOf(document, "Diffusion")
            ?: document.selectFirst("p.oe-meta")?.text()?.let { YEAR_REGEX.find(it)?.value }

        // The anime's creator or the manga's author(s), else the staff credited with the original work
        anime.author = infoOf(document, "Créateur", "Auteur", "Auteurs")
            ?: document.select(".oe-staff")
                .filter { it.selectFirst(".oe-perso-detail")?.text() in ORIGINAL_AUTHOR_ROLES }
                .mapNotNull { it.selectFirst(".oe-perso-nom")?.text() }
                .distinct()
                .joinToString(", ")
                .ifEmpty { null }

        anime.studio = infoOf(document, "Studio", "Studios")

        return anime
    }

    /** The first value of the "Informations" lists (<dt>Studio</dt><dd>Toei Animation</dd>) labelled one of [labels]. */
    private fun infoOf(document: Document, vararg labels: String): String? =
        document.select("dl.oe-infos dt")
            .firstOrNull { it.text() in labels }
            ?.nextElementSibling()?.text()?.takeIf { it.isNotBlank() }

    override fun animeDetailsParse(document: Document): SAnime = throw Exception("Not used")


    // ============================== Seasons ===============================

    /** The season panels of an anime page: panneauAnime("Saison 2", "saison2/vostfr") gives ("Saison 2", "saison2/vostfr"). */
    private fun seasonPanels(document: Document): List<Pair<String, String>> {
        val scripts = document.select("h2 + p + div > script, h2 + div > script").toString()
        return SEASON_REGEX.findAll(scripts).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    /** The name of the season an entry (/catalogue/<anime>/<season>/<lang>) is about, from its anime's page. */
    private fun seasonNameOf(document: Document, entryUrl: String): String? {
        val season = entryUrl.removeSuffix("/").substringBeforeLast("/").substringAfterLast("/")
        return seasonPanels(document)
            .firstOrNull { (_, url) -> url.removeSuffix("/").substringBeforeLast("/") == season }
            ?.first
    }

    /** The season first, on every screen: "Saison 2 - Overgeared". */
    private fun seasonTitle(seasonName: String, animeName: String) = "$seasonName - $animeName"

    /** /catalogue/<anime>/<season>/<lang> to /catalogue/<anime> */
    private fun animePath(entryUrl: String) =
        entryUrl.removeSuffix("/").substringBeforeLast("/").substringBeforeLast("/")

    /** /catalogue/<anime>/<season>/vostfr to "VOSTFR" */
    private fun languageOf(entryUrl: String) =
        entryUrl.removeSuffix("/").substringAfterLast("/").uppercase(Locale.ROOT).ifBlank { null }

    // ============================== Anime Episodes ===============================
    override fun episodesListSelector(): String = throw Exception("Not used")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used")

    private fun getEpisodesTrueNames(document: Document): List<Pair<String, List<String>>> {
        val resultList = mutableListOf<Pair<String, List<String>>>()
        val scriptRegex = Regex("""<script>([^<]*(?:(?!<\/script>)<[^<]*)*)<\/script>""")
        val scripts = scriptRegex.findAll(document.html())
        scripts.forEach { script ->
            val code = script.groupValues.getOrNull(1)?.substringAfter(">")
                ?.takeIf { !it.contains("#avOeuvre") && it.contains("resetListe();") }
            if (code != null) {
                val commentsRegex = Regex("""\/\*.*?\*\/""", RegexOption.DOT_MATCHES_ALL)
                val codeWithoutComments = code.replace(commentsRegex, "").trimIndent()
                val functionCalls =
                    codeWithoutComments
                        .substringAfter("resetListe();")
                        .substringBefore("});")
                        .substringBeforeLast(";")
                        .split(";")

                functionCalls.forEach { call ->
                    val functionName = call.substringBefore("(").trim()
                    val parameters = call.substringAfter("(").substringBefore(")")
                        .trim()
                        .split(Regex(""",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*${'$'})"""))
                        .map { it.removeSurrounding("\"").trim() }
                    resultList.add(functionName to parameters)
                }
                return resultList
            }
        }
        return resultList
    }

    private fun parseEpisodesTrueNames(
        names: List<Pair<String, List<String>>>,
        totalEpisodes: Int?
    ): List<String> {
        if (totalEpisodes == null) return emptyList()
        val episodesNames = mutableListOf<String>()
        try {
            names.forEach { (function, parameters) ->
                when (function) {
                    "newSPF" -> {
                        episodesNames.add(parameters[0])
                    }

                    "newSP" -> {
                        episodesNames.add("Episode ${parameters[0]}")
                    }

                    "creerListe" -> {
                        val debut = parameters[0].toInt()
                        val fin = parameters[1].toInt()
                        for (i in debut..fin) {
                            episodesNames.add("Episode $i")
                        }
                    }

                    "finirListe", "finirListeOP" -> {
                        val baseEpNumber = parameters[0].toInt()
                        for (i in 0 until (totalEpisodes - episodesNames.size) - 1) {
                            episodesNames.add("Episode ${baseEpNumber + i}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return episodesNames
    }

    override fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>> {
        return client.newCall(episodesListRequest(anime))
            .asCancelableObservable()
            .flatMap { response ->
                val document = response.asJsoup()
                val episodesUrl =
                    document.selectFirst("script[src*=episodes.js]")?.attr("src") ?: "episodes.js"
                val episodeScriptRequest = GET(baseUrl + anime.url + "/$episodesUrl", seasonHeaders(anime.url))
                client.newCall(episodeScriptRequest)
                    .asCancelableObservable()
                    .map { res ->
                        val doc = res.asJsoup()
                        val pattern = Regex("""var (.*?) = \[(.*?)\];""")
                        val matches = pattern.findAll(doc.html())

                        val variableLinkCounts: MutableMap<String, Int> = mutableMapOf()

                        for (matchResult in matches) {
                            val variableName = matchResult.groupValues[1]
                            val urls = matchResult.groupValues[2]
                                .split(",")
                                .map { it.trim('\'', '"', ' ', '\n', '\r') }

                            val linkCount = urls.size
                            variableLinkCounts[variableName] = linkCount
                        }
                        val episodesList: MutableList<SEpisode> = mutableListOf()
                        val variableWithMostLinks = variableLinkCounts.maxByOrNull { it.value }
                        val trueNames = try {
                            parseEpisodesTrueNames(
                                getEpisodesTrueNames(document),
                                variableWithMostLinks?.value
                            )
                        } catch (e: Exception) {
                            emptyList()
                        }
                        for (i in 1..(variableWithMostLinks?.value?.minus(1) ?: 0)) {
                            val episode = SEpisode.create()
                            episode.episodeNumber = i.toFloat()
                            episode.name = if (trueNames.getOrNull(i - 1) != null) {
                                trueNames[i - 1]
                            } else {
                                "Episode ${i.toFloat()}"
                            }
                            episode.url = "${anime.url}?number=$i"
                            episodesList.add(episode)
                        }
                        episodesList.reversed()
                    }
            }
    }

    // ============================== Episode Sources ===============================

    override fun episodeSourcesParse(response: Response): List<StreamSource> =
        throw Exception("Unused")

    private fun episodeSourcesParse(response: Response, originUrl: String): List<StreamSource> {

        val document: Document = response.asJsoup()
        val javascriptCode = Html.fromHtml(document.html(), Html.FROM_HTML_MODE_LEGACY).toString()
        val pattern = Regex("""(?<!\/\*)var (.*?) = \[(.*?)\];""")

        // One array per player, indexed by episode
        val players = pattern.findAll(javascriptCode).map { matchResult ->
            matchResult.groupValues[2]
                .split(",")
                .map { it.trim('\'', '"', ' ', '\n', '\r', '\t') }
        }.toList()

        val episodeNumber = originUrl.substringAfter("?number=").toInt()
        // The same link is often in two players
        val episodePlayers = players
            .mapNotNull { it.getOrNull(episodeNumber - 1)?.toHttpUrlOrNull() }
            .distinct()

        // A VOE code plays on any VOE domain. The site links mirrors with random names that come and go,
        // so each code is also tried on the other VOE hosts of the season
        val voeHosts = (players.flatten().mapNotNull { it.toHttpUrlOrNull() }
            .filter { hostOf(it) == Host.VOE }
            .map { it.host } + VOE_HOSTS).distinct()

        val newClient = client.shortTimeOutBuilder(PLAYER_CALL_TIMEOUT_S)

        return streamsWithinDeadline(episodePlayers) { url ->
            runCatching { sourcesFromPlayer(newClient, url, voeHosts) }
                .onFailure { Log.e(TAG, "Could not extract the streams of $url", it) }
                .getOrNull()
                .orEmpty()
                .also { if (it.isEmpty()) Log.w(TAG, "No stream from $url") }
        }
    }

    /**
     * The streams of each player, extracted in parallel, in the players' order, without waiting for the slow
     * ones: a broken player tries its mirrors one call timeout after the other. Once a player gives streams, the
     * others have [PLAYERS_GRACE_MS] more, [PLAYERS_MAX_WAIT_MS] at most in all. A player left behind runs on in
     * the background, its streams dropped.
     */
    private fun streamsWithinDeadline(
        players: List<HttpUrl>,
        extract: (HttpUrl) -> List<StreamSource>,
    ): List<StreamSource> = runBlocking {
        val done = Channel<Pair<Int, List<StreamSource>>>(Channel.UNLIMITED)
        // Out of runBlocking's scope, which would wait for the players left behind
        val scope = CoroutineScope(Dispatchers.IO)
        players.forEachIndexed { index, url ->
            scope.launch { done.send(index to extract(url)) }
        }

        val results = arrayOfNulls<List<StreamSource>>(players.size)
        var deadline = SystemClock.elapsedRealtime() + PLAYERS_MAX_WAIT_MS
        var received = 0
        while (received < players.size) {
            val (index, streams) = withTimeoutOrNull(deadline - SystemClock.elapsedRealtime()) {
                done.receive()
            } ?: break
            results[index] = streams
            received++
            if (streams.isNotEmpty()) deadline = minOf(deadline, SystemClock.elapsedRealtime() + PLAYERS_GRACE_MS)
        }
        if (received < players.size) {
            Log.w(TAG, "Left behind, too slow: ${players.filterIndexed { index, _ -> results[index] == null }}")
        }
        results.filterNotNull().flatten()
    }

    private enum class Host { ANSEMBED, VIDMOLY, VIDHIDE, VOE, UQLOAD, SENDVID, VK, LPAYER, SIBNET, YOURUPLOAD, UNKNOWN }

    private fun hostOf(url: HttpUrl): Host {
        // HttpUrl lowercases the host: the site writes "Smoothpre.com"
        val host = url.host.removePrefix("www.")
        return when {
            host == ANSEMBED_HOST -> Host.ANSEMBED
            "vidmoly" in host -> Host.VIDMOLY
            host in VIDHIDE_HOSTS -> Host.VIDHIDE
            "uqload" in host -> Host.UQLOAD
            host == "sendvid.com" -> Host.SENDVID
            host in VK_HOSTS -> Host.VK
            host.endsWith("embed4me.com") -> Host.LPAYER
            host.endsWith("sibnet.ru") -> Host.SIBNET
            host == "yourupload.com" -> Host.YOURUPLOAD
            "voe" in host -> Host.VOE
            // VOE mirrors have random names (teresapoliticallearn.com, ...), all with /e/<code>
            VOE_MIRROR_REGEX.matches(host) && VOE_PATH_REGEX.matches(url.encodedPath) -> Host.VOE
            else -> Host.UNKNOWN
        }
    }

    private fun sourcesFromPlayer(client: OkHttpClient, url: HttpUrl, voeHosts: List<String>): List<StreamSource> {
        val link = url.toString()
        return when (hostOf(url)) {
            Host.ANSEMBED -> ansEmbedSources(client, url)
            Host.VIDMOLY -> vidmolySources(client, url)
            Host.VIDHIDE -> vidHideSources(client, url)
            Host.VOE -> voeSources(client, url, voeHosts)
            Host.UQLOAD -> UqloadExtractor(client).videosFromUrl(uqloadUrl(url))
            // Empty players: "sendvid.com/embed/", "video_ext.php?oid=&hd=3"
            Host.SENDVID -> if (SENDVID_PATH_REGEX.matches(url.encodedPath)) {
                SendvidExtractor(client, headers).videosFromUrl(link)
            } else {
                emptyList()
            }

            Host.VK -> if (url.queryParameter("id").isNullOrBlank()) {
                emptyList()
            } else {
                VkExtractor(client, headers).videosFromUrl(link)
            }

            Host.LPAYER -> LpayerExtractor(client).videosFromUrl(link, siteUrl = baseUrl)
            Host.SIBNET -> SibnetExtractor(client).videosFromUrl(link)
            Host.YOURUPLOAD -> YourUploadExtractor(client).videosFromUrl(link, headers)
            Host.UNKNOWN -> {
                Log.w(TAG, "Unsupported player $link")
                emptyList()
            }
        }
    }

    /** AnsEmbed is Vidmoly's white label: when ansembed.net can't be reached, the same code plays on vidmoly.org. */
    private fun ansEmbedSources(client: OkHttpClient, url: HttpUrl): List<StreamSource> {
        // "embed-.html" placeholders have no code
        val code = VIDMOLY_PATH_REGEX.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return emptyList()
        val extractor = VidmolyExtractor(client, headers)
        return runCatching {
            extractor.videosFromUrl("https://$ANSEMBED_HOST/embed-$code.html", server = ANSEMBED_SERVER)
        }.getOrElse { error ->
            Log.w(TAG, "AnsEmbed $code: ${error.message} on $ANSEMBED_HOST, trying $VIDMOLY_HOST")
            extractor.videosFromUrl("https://$VIDMOLY_HOST/embed-$code.html", server = ANSEMBED_SERVER)
        }
    }

    /** vidmoly.to is parked and vidmoly.net/.biz are DNS-blocked by some ISPs: the codes play on vidmoly.org and ansembed.net. */
    private fun vidmolySources(client: OkHttpClient, url: HttpUrl): List<StreamSource> {
        val code = VIDMOLY_PATH_REGEX.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return emptyList()
        return mirrorSources("Vidmoly", code, listOf(VIDMOLY_HOST, ANSEMBED_HOST)) { host ->
            VidmolyExtractor(client, headers).videosFromUrl("https://$host/embed-$code.html")
        }
    }

    /**
     * Every domain of the VidHide network serves the same /embed/<code>, most of them by redirecting to
     * callistanise.com, which some ISPs block like minochinos.com. The mirrors serving the codes themselves
     * come first, the link's own host last.
     */
    private fun vidHideSources(client: OkHttpClient, url: HttpUrl): List<StreamSource> {
        val code = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return emptyList()
        return mirrorSources("VidHide", code, VIDHIDE_MIRRORS + url.host) { host ->
            VidHideExtractor(client, "https://$host")
                .videosFromUrl("https://$host/embed/$code", prefix = VIDHIDE_SERVER, server = VIDHIDE_SERVER)
        }
    }

    /** The code on its own host first, then on the other VOE hosts until one serves it. */
    private fun voeSources(client: OkHttpClient, url: HttpUrl, voeHosts: List<String>): List<StreamSource> {
        val code = url.pathSegments.lastOrNull { it.isNotBlank() } ?: return emptyList()
        return mirrorSources("VOE", code, listOf(url.host) + voeHosts) { host ->
            VoeExtractor(client, json).videosFromUrl("https://$host/e/$code")
        }
    }

    /** Tries a code on each mirror of its host until one gives streams. */
    private fun mirrorSources(
        server: String,
        code: String,
        hosts: List<String>,
        extract: (host: String) -> List<StreamSource>,
    ): List<StreamSource> {
        var lastError: Throwable? = null
        for (host in hosts.distinct()) {
            val result = runCatching { extract(host) }
            result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
            lastError = result.exceptionOrNull() ?: lastError
            Log.w(TAG, "$server $code: nothing on $host" + (result.exceptionOrNull()?.let { " (${it.message})" } ?: ""))
        }
        lastError?.let { throw it }
        return emptyList()
    }

    /** uqload.vc/embed/<code> answers 404 (on the site too), the same code plays at /embed-<code>.html. */
    private fun uqloadUrl(url: HttpUrl): String {
        val code = UQLOAD_PATH_REGEX.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return url.toString()
        return "https://$UQLOAD_HOST/embed-$code.html"
    }

    override fun fetchEpisodeSources(url: String): Observable<List<StreamSource>> {
        return client.newCall(episodeSourcesRequest(url))
            .asCancelableObservable()
            .map {
                episodeSourcesParse(it, url).sort()
            }
    }

    override fun episodeSourcesSelector(): String = throw Exception("Not used")

    override fun episodeSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("Not used")

    override fun episodeSourcesRequest(url: String): Request {
        return GET(baseUrl + url.substringBeforeLast("?") + "/episodes.js?", seasonHeaders(url.substringBeforeLast("?")))
    }

    /** episodes.js answers 403 (a Cloudflare firewall rule) without a Referer from the site: send the season page's, as a browser does. */
    private fun seasonHeaders(seasonUrl: String) =
        headers.newBuilder().set("Referer", "$baseUrl$seasonUrl/").build()

    override fun getEpisodeUrl(episode: SEpisode): String {
        return baseUrl + episode.url.substringBeforeLast("?")
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        val order = preferences.playerStreamsOrder.split(",")
        return this.groupBy { it.server.lowercase() }.entries
            .sortedBy { (server, _) -> order.indexOf(server).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .flatMap { (_, sources) ->
                // Best quality first, sources without one (a single MP4) last
                sources.sortedByDescending { source ->
                    NUMBER_REGEX.find(source.quality)?.value?.toIntOrNull() ?: -1
                }
            }
    }

    companion object {
        private const val TAG = "AnimeSama"

        private val SEASON_REGEX = Regex("^\\s*panneauAnime\\(\"(.*)\", \"(.*)\"\\)", RegexOption.MULTILINE)

        private val YEAR_REGEX = Regex("\\b(19|20)\\d{2}\\b")

        private val ORIGINAL_AUTHOR_ROLES = setOf("Auteur original", "Histoire originale")

        private const val ANSEMBED_HOST = "ansembed.net"
        private const val ANSEMBED_SERVER = "AnsEmbed"
        private const val VIDMOLY_HOST = "vidmoly.org"
        private const val UQLOAD_HOST = "uqload.vc"
        private const val VIDHIDE_SERVER = "VidHide"

        /** A player's page and playlists answer in about a second; a dead mirror isn't waited on longer. */
        private const val PLAYER_CALL_TIMEOUT_S = 10L

        /** How long the other players have once one gives streams: the working ones finish within a few seconds. */
        private const val PLAYERS_GRACE_MS = 5_000L

        /** How long the players have in all when none gives streams. */
        private const val PLAYERS_MAX_WAIT_MS = 30_000L

        /**
         * VidHide network domains linked by the site, which calls them "Earnvids" (movearnpre.com and mivalyo.com
         * are gone, earnvids.com is down, their codes live on).
         */
        private val VIDHIDE_HOSTS = setOf(
            "minochinos.com", "callistanise.com", "dingtezuni.com", "morencius.com", "smoothpre.com",
            "movearnpre.com", "mivalyo.com", "bingezove.com", "vidhidevip.com", "vidhidepre.com", "earnvids.com",
        )
        /** VidHide mirrors that serve the codes themselves, not blocked by the ISPs that block callistanise.com. */
        private val VIDHIDE_MIRRORS = listOf("smoothpre.com", "morencius.com", "callistanise.com")

        /** voe.sx redirects to the current player mirror (blocked by some ISPs, the site's mirrors are tried first). */
        private val VOE_HOSTS = listOf("voe.sx")
        private val VOE_MIRROR_REGEX = Regex("""[a-z]{12,}\.com""")
        private val VOE_PATH_REGEX = Regex("""/e/[A-Za-z0-9]+/?""")

        private val VK_HOSTS = setOf("vk.com", "m.vk.com", "vk.ru", "vkvideo.ru", "m.vkvideo.ru")

        private val VIDMOLY_PATH_REGEX = Regex("""/embed-([A-Za-z0-9]+)\.html""")
        private val UQLOAD_PATH_REGEX = Regex("""/embed(?:/|-)([A-Za-z0-9]+)(?:\.html)?/?""")
        private val SENDVID_PATH_REGEX = Regex("""/embed/[A-Za-z0-9]+/?""")
        private val NUMBER_REGEX = Regex("""\d+""")
    }
}
