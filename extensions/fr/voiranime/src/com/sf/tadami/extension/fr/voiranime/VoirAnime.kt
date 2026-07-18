package com.sf.tadami.extension.fr.voiranime

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.lib.filemoonextractor.FileMoonExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.mailruextractor.MailRuExtractor
import com.sf.tadami.lib.streamtapeextractor.StreamTapeExtractor
import com.sf.tadami.lib.vidmolyextractor.VidmolyExtractor
import com.sf.tadami.lib.voeextractor.VoeExtractor
import com.sf.tadami.lib.youruploadextractor.YourUploadExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.network.asCancelableObservable
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
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class VoirAnime : ConfigurableParsedHttpAnimeSource<VoirAnimePreferences>(
    sourceId = 4,
    prefGroup = VoirAnimePreferences
) {
    private val json: Json by injectLazy()

    override val name: String = "VoirAnime"

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.cloudflareClient

    override val supportRecent = true

    private val i18n = i18n(VoirAnimeTranslations)

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if (migrated) {
            Log.i("VoirAnime", "Successfully migrated preferences")
        }
    }

    private suspend fun preferencesMigrations() : Boolean {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(VoirAnimePreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return false;
            }

            if (oldVersion < 4) {
                dataStore.editPreference(
                    VoirAnimePreferences.DEFAULT_BASE_URL,
                    VoirAnimePreferences.BASE_URL
                )
            }
        }
        return true
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getVoirAnimePreferencesContent(i18n)
    }

    override fun latestSelector(): String = "div.page-listing-item > div > div > .page-item-detail"

    override fun latestAnimeNextPageSelector(): String = ".wp-pagenavi .nextpostslink"

    override fun latestAnimeFromElement(element: Element): SAnime {
        val anime: SAnime = SAnime.create()
        val imgRef = element.select("div[id*=\"manga-item\"] > a").first()
        anime.title = element.select("div.item-summary > div > h3.h5 > a").first()!!.text()
        anime.setUrlWithoutDomain(imgRef!!.attr("href"))
        val imgDiv = imgRef.select("img")
        val imageUrl = when {
            imgDiv.hasClass("img-responsive") && imgDiv.hasAttr("srcset") -> {
                val srcSet = imgDiv.attr("srcset").split(",").map {
                    it.trim().split(" ").first()
                }
                srcSet.last()
            }

            else -> {
                imgDiv.attr("src")
            }
        }
        anime.thumbnailUrl = imageUrl
        return anime
    }

    override fun latestAnimesRequest(page: Int): Request = GET("$baseUrl/page/$page")

    override fun searchSelector(): String = throw Exception("Unused")

    override fun searchAnimeNextPageSelector(): String = ".wp-pagenavi .nextpostslink"

    override fun fetchSearch(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Observable<AnimesPage> {
        return client.newCall(searchAnimeRequest(page, query.trim(), filters, noToasts))
            .asCancelableObservable()
            .map { response ->

                val document = response.asJsoup()

                val animeList = if (query.isNotEmpty()) {
                    document.select("div.c-tabs-item__content").map { searchAnimeFromQueryElement(it) }
                } else {
                    document.select("div.page-item-detail.video").map { searchAnimeFromElement(it) }
                }

                val hasNextPage = document.select(searchAnimeNextPageSelector()).first() != null

                AnimesPage(animeList, hasNextPage)
            }
    }

    private fun searchAnimeFromQueryElement(element: Element): SAnime {
        val anime: SAnime = SAnime.create()
        val titleUrl = element.selectFirst("div.post-title h3 a")
        anime.title = titleUrl?.text().orEmpty()
        anime.setUrlWithoutDomain(titleUrl?.attr("href").orEmpty())
        val imgDiv = element.select("div.tab-thumb img")
        anime.thumbnailUrl = if (imgDiv.hasAttr("srcset")) {
            imgDiv.attr("srcset").split(",").map { it.trim().split(" ").first() }.last()
        } else {
            imgDiv.attr("src")
        }
        return anime
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime: SAnime = SAnime.create()
        val titleUrl = element.select("div.post-title > h3 > a")
        anime.title = titleUrl.text()
        anime.setUrlWithoutDomain(titleUrl.attr("href"))
        val imgDiv = element.select("div.item-thumb > a > img")
        val imageUrl = when {
            imgDiv.hasClass("img-responsive") && imgDiv.hasAttr("srcset") -> {
                val srcSet = imgDiv.attr("srcset").split(",").map {
                    it.trim().split(" ").first()
                }
                srcSet.last()
            }

            else -> {
                imgDiv.attr("src")
            }
        }
        anime.thumbnailUrl = imageUrl
        return anime
    }

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {

        return when {
            query.isNotEmpty() -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    if (page > 1) addPathSegments("page/$page")
                    addQueryParameter("s", query)
                    addQueryParameter("post_type", "wp-manga")
                    listOf("op", "author", "artist", "release", "adult", "type", "language")
                        .forEach { addQueryParameter(it, "") }
                }.build()
                GET(url.toString(), headers)
            }

            else -> {
                GET("$baseUrl/liste-danimes/page/$page", headers)
            }
        }
    }

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        anime.title = document.select("div.post-title h1").text()
        anime.genres = document.select("div.genres-content a").map { it.text() }
        anime.description = document.selectFirst("div.description-summary p")?.text()
        val rightColumnValues = document.select("div.summary_content div.post-content_item")

        fun valueForHeading(vararg headings: String): String? {
            return rightColumnValues.find { item ->
                val heading = item.select("div.summary-heading > h5").text().trim().lowercase()
                headings.any { heading == it }
            }?.select("div.summary-content")?.text()?.trim()?.takeIf { it.isNotBlank() }
        }

        anime.status = parseStatus(valueForHeading("status"))
        anime.release = valueForHeading("start date")
        anime.studio = valueForHeading("studio", "studios", "studio(s)")
        anime.author = valueForHeading("author", "authors", "author(s)", "auteur", "auteurs")
        return anime
    }

    private fun parseStatus(status: String?): SAnimeStatus {
        return when (status?.lowercase()?.trim()) {
            "en cours", "ongoing", "en cours de diffusion", "airing", "publishing" -> SAnimeStatus.ONGOING
            "terminé", "termine", "completed", "complete", "fini" -> SAnimeStatus.COMPLETED
            "en pause", "on hold", "hiatus", "on hiatus" -> SAnimeStatus.ON_HIATUS
            "annulé", "annule", "canceled", "cancelled", "abandonné", "abandonne", "dropped" -> SAnimeStatus.CANCELLED
            else -> SAnimeStatus.UNKNOWN
        }
    }

    override fun episodesListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        return document.select(episodesListSelector())
            .mapIndexed { index, element -> episodeFromElementCustom(element, index + 1) }
    }

    override fun episodeSourcesSelector(): String = throw Exception("Unused")

    override fun episodeSourcesFromElement(element: Element): List<StreamSource> =
        throw Exception("Unused")

    override fun episodesListSelector(): String = "li.wp-manga-chapter"

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Unused")

    private fun episodeFromElementCustom(element: Element, index: Int): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.select("a").attr("href").removeSuffix("/"))
        episode.name = element.select("a").text().trim()
        episode.episodeNumber = if (element.select("a").text().contains("film", true)) {
            index.toFloat()
        } else {
            try {
                element.select("a").text().trim().substringAfterLast("-")
                    .substringBeforeLast("V").trim().toFloat()
            }catch (err : Exception){
                Log.d("VoirAnime", "Error parsing episode number: ${err.message}")
                index.toFloat()
            }
        }

        return episode
    }

    override fun fetchEpisodeSources(url: String): Observable<List<StreamSource>> {
        return client.newCall(GET(baseUrl + url, headers)).asCancelableObservable().map { res ->
            val document = res.asJsoup()

            // Extract iframe URLs directly from the thisChapterSources JavaScript object
            val scriptContent = document.select("script").firstOrNull {
                it.html().contains("var thisChapterSources")
            }?.html() ?: ""

            val streamSourcesList = mutableListOf<StreamSource>()

            // Parse the thisChapterSources object to extract iframe src URLs
            val iframeRegex = Regex("""\"(LECTEUR [^\"]+)\":\s*\"<iframe[^>]*src=\\\"([^\"]+)\\\"""")
            val matches = iframeRegex.findAll(scriptContent)

            matches.forEach { match ->
                try {
                    val serverName = match.groupValues[1]
                    val iframeUrl = match.groupValues[2].replace("\\", "")

                    Log.d("VoirAnime",iframeUrl)

                    val sources = when (serverName) {
                        "LECTEUR VOE" -> {
                            VoeExtractor(client, json).videosFromUrl(url = iframeUrl)
                        }
                        "LECTEUR myTV" -> {
                            VidmolyExtractor(client, headers).videosFromUrl(url = iframeUrl)
                        }
                        "LECTEUR YU" -> {
                            YourUploadExtractor(client).videosFromUrl(
                                url = iframeUrl,
                                headers = headers
                            )
                        }
                        "LECTEUR MOON" -> {
                            FileMoonExtractor(client).videosFromUrl(
                                url = iframeUrl,
                                embedOrigin = baseUrl.substringAfter("://").substringBefore("/")
                            )
                        }
                        "LECTEUR Stape" -> {
                            StreamTapeExtractor(client).videosFromUrl(url = iframeUrl)
                        }
                        "LECTEUR FHD1" -> {
                            MailRuExtractor(client, headers).videosFromUrl(url = iframeUrl)
                        }
                        else -> null
                    }

                    sources?.let { streamSourcesList.addAll(it) }
                } catch (e: Exception) {
                    Log.e("VoirAnime", "Error extracting from server: ${e.message}")
                }
            }

            streamSourcesList.sort()
        }
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