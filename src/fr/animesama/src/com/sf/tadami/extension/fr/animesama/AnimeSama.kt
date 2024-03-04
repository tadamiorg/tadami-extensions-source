package com.sf.tadami.extension.fr.animesama

import android.text.Html
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.sendvidextractor.SendvidExtractor
import com.sf.tadami.lib.sibnetextractor.SibnetExtractor
import com.sf.tadami.lib.vkextractor.VkExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.POST
import com.sf.tadami.network.asCancelableObservable
import com.sf.tadami.network.asJsoup
import com.sf.tadami.network.shortTimeOutBuilder
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
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnimeSama : ConfigurableParsedHttpAnimeSource<AnimeSamaPreferences>(
    sourceId = 2,
    prefGroup = AnimeSamaPreferences
) {
    override val name: String = "AnimeSama"

    override val baseUrl: String = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.cloudflareClient

    private var episodeNumber: Int? = null

    override val supportRecent = false

    private val i18n = i18n(AnimeSamaTranslations)

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
                intPreferencesKey(AnimeSamaPreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return
            }
        }
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getAnimeSamaPreferencesContent(i18n)
    }

    override fun latestSelector(): String = throw Exception("Not used")

    override fun latestAnimeNextPageSelector(): String = throw Exception("Not used")

    override fun latestAnimeFromElement(element: Element): SAnime = throw Exception("Not used")

    override fun latestAnimesRequest(page: Int): Request = throw Exception("Not used")

    override fun searchSelector(): String =
        "div.cardListAnime"

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
                    client.newCall(GET("$baseUrl${anime.url}"))
                        .asCancelableObservable()
                        .map { response ->
                            val doc = response.asJsoup()
                            val seasonRegex = Regex(
                                "^\\s*panneauAnime\\(\"(.*)\", \"(.*)\"\\)",
                                RegexOption.MULTILINE
                            )
                            val scripts =
                                doc.select("h2 + p + div > script, h2 + div > script").toString()
                            val seasons = seasonRegex.findAll(scripts).map { match ->
                                val (seasonName, seasonUrl) = match.destructured
                                seasonName to seasonUrl
                            }.toMutableList()

                            seasons.map { (seasonName, seasonUrl) ->
                                val animeSeason = SAnime.create()
                                animeSeason.url = "${anime.url}/$seasonUrl"
                                animeSeason.thumbnailUrl = anime.thumbnailUrl
                                animeSeason.title = "$seasonName - ${anime.title}"
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

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {

        return when {
            query.isNotEmpty() -> {
                val formData = FormBody.Builder()
                    .add("query", query)
                    .build()
                POST("$baseUrl/catalogue/searchbar.php", headers, formData)
            }

            else -> {
                GET("$baseUrl/catalogue/index.php?page=$page", headers)
            }
        }
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime: SAnime = SAnime.create()
        anime.title = element.selectFirst("h1")?.text() ?: ""
        anime.thumbnailUrl = element.select("img").attr("src")
        anime.setUrlWithoutDomain(element.selectFirst("a")!!.attr("href").removeSuffix("/"))
        return anime
    }


    override fun searchAnimeNextPageSelector(): String = "div#nav_pages a.bg-sky-900 ~ a"

    override fun animeDetailsRequest(anime: Anime): Request {
        return GET(baseUrl + anime.url + "/../..", headers)
    }

    override fun fetchAnimeDetails(anime: Anime): Observable<SAnime> {
        return client.newCall(animeDetailsRequest(anime))
            .asCancelableObservable()
            .map { response ->
                animeDetailsParse(response.asJsoup(), anime)
            }
    }

    private fun animeDetailsParse(document: Document, dbAnime: Anime): SAnime {
        val anime = SAnime.create()
        val animeTitle = document.selectFirst("#titreOeuvre")?.text()
        val seasonRegex = Regex(
            "^\\s*panneauAnime\\(\"(.*)\", \"(.*)\"\\)",
            RegexOption.MULTILINE
        )
        val scripts = document.select("h2 + p + div > script, h2 + div > script").toString()
        val foundSeason = seasonRegex.findAll(scripts).find { match ->
            val (_, seasonUrl) = match.destructured
            dbAnime.url.contains(seasonUrl)
        }

        var seasonName = dbAnime.title
        if (foundSeason != null) {
            val (name, _) = foundSeason.destructured
            seasonName = "$name - $animeTitle"
        }

        anime.title = seasonName
        anime.description =
            document.selectFirst("h2:contains(Synopsis) ~ p.text-sm.text-gray-400.mt-2")?.text()
        anime.genres = document.selectFirst("h2:contains(Genres) ~ a")?.text()?.trim()
            ?.split(Regex("""( - )|,"""))
        anime.thumbnailUrl = document.selectFirst("meta[itemprop=image]")?.attr("content")
        return anime
    }

    override fun animeDetailsParse(document: Document): SAnime = throw Exception("Not used")

    override fun episodesSelector(): String = throw Exception("Not used")

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
        return client.newCall(episodesRequest(anime))
            .asCancelableObservable()
            .flatMap { response ->
                val document = response.asJsoup()
                val episodesUrl =
                    document.selectFirst("#sousBlocMilieu")?.selectFirst("script")?.attr("src")
                        ?.takeIf { it.contains("episodes.js") } ?: "episodes.js"
                val episodeScriptRequest = GET(baseUrl + anime.url + "/$episodesUrl", headers)
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

    override fun episodeSourcesParse(response: Response): List<StreamSource> {

        val document: Document = response.asJsoup()
        val javascriptCode = Html.fromHtml(document.html(), Html.FROM_HTML_MODE_LEGACY).toString()
        val pattern = Regex("""(?<!\/\*)var (.*?) = \[(.*?)\];""")
        val matches = pattern.findAll(javascriptCode)

        val variableArrays: Map<String, List<String>> = matches.associate { matchResult ->
            val variableName = matchResult.groupValues[1]
            val urls = matchResult.groupValues[2]
                .split(",")
                .map { it.trim('\'', '"', ' ', '\n', '\r') }
            variableName to urls
        }
        val streamSourcesList = mutableListOf<StreamSource>()
        val rawStreamSourceUrls = mutableListOf<String>()
        val newClient = client.shortTimeOutBuilder()
        streamSourcesList.addAll(
            variableArrays.values.parallelMap { urls ->
                runCatching {
                    val streamUrl = episodeNumber?.let { urls[it - 1] } ?: return@parallelMap null
                    if (rawStreamSourceUrls.contains(streamUrl)) return@parallelMap null
                    rawStreamSourceUrls.add(streamUrl)
                    when {
                        streamUrl.contains("sendvid.com") -> {
                            SendvidExtractor(newClient, headers).videosFromUrl(streamUrl)
                        }

                        streamUrl.contains("sibnet.ru") -> {
                            SibnetExtractor(newClient).videosFromUrl(streamUrl)
                        }

                        streamUrl.contains("anime-sama.fr") -> {
                            listOf(
                                StreamSource(
                                    url = streamUrl,
                                    fullName = "AnimeSama",
                                    server = "AnimeSama"
                                )
                            )
                        }

                        streamUrl.contains("vk.") -> {
                            VkExtractor(newClient, headers).videosFromUrl(streamUrl)
                        }

                        else -> null
                    }
                }.getOrNull()
            }.filterNotNull().flatten(),
        )

        return streamSourcesList.sort()
    }

    override fun streamSourcesSelector(): String = throw Exception("Not used")

    override fun streamSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("Not used")

    override fun episodeRequest(url: String): Request {
        episodeNumber = url.substringAfter("?number=").toInt()
        return GET(baseUrl + url.substringBeforeLast("?") + "/episodes.js?", headers)
    }

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