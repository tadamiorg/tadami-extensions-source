package com.sf.tadami.extension.fr.anisama

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.extension.fr.anisama.extractors.VidCdnExtractor
import com.sf.tadami.lib.doodextractor.DoodExtractor
import com.sf.tadami.lib.filemoonextractor.FileMoonExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.sendvidextractor.SendvidExtractor
import com.sf.tadami.lib.sibnetextractor.SibnetExtractor
import com.sf.tadami.lib.streamhidevid.StreamHideVidExtractor
import com.sf.tadami.lib.voeextractor.VoeExtractor
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.online.ConfigurableParsedHttpAnimeSource
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent
import com.sf.tadami.ui.utils.parallelMap
import com.sf.tadami.utils.Lang
import com.sf.tadami.utils.editPreference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import uy.kohesive.injekt.injectLazy

class AniSama : ConfigurableParsedHttpAnimeSource<AnisamaPreferences>(
    sourceId = 6,
    prefGroup = AnisamaPreferences
) {
    private val json: Json by injectLazy()

    override val name: String = "AniSama"

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.cloudflareClient

    override val supportRecent = true

    private val i18n = i18n(AnisamaTranslations)

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if(migrated){
            Log.i("AniSama","Successfully migrated preferences")
        }
    }

    private suspend fun preferencesMigrations() : Boolean {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(AnisamaPreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return false
            }

            if (oldVersion < 4) {
                val streamOrder = preferences.playerStreamsOrder.split(",").toMutableList()
                if (!streamOrder.contains("vidcdn")) {
                    streamOrder.add("vidcdn")
                }

                dataStore.editPreference(
                    streamOrder.joinToString(separator = ","),
                    AnisamaPreferences.PLAYER_STREAMS_ORDER
                )
            }
            if (oldVersion < 5) {
                val baseUrl = preferences.baseUrl
                if (baseUrl !== "https://animesz.xyz") {
                    dataStore.editPreference(
                        "https://animesz.xyz",
                        AnisamaPreferences.BASE_URL
                    )
                }
            }
        }
        return true
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getAnisamaPreferencesContent(i18n)
    }

    // LATEST

    override fun latestAnimesRequest(page: Int): Request = GET("$baseUrl/recently-updated/?page=$page")

    override fun latestSelector(): String = ".film_list-wrap article"

    override fun latestAnimeNextPageSelector(): String = ".ap__-btn-next a:not(.disabled)"

    override fun latestAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        title = element.select(".dynamic-name").text()
        thumbnailUrl = element.select(".film-poster-img").attr("data-src")
        setUrlWithoutDomain(element.select(".film-poster-ahref").attr("href"))
    }

    // SEARCH

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun searchSelector() = latestSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = latestAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = latestAnimeNextPageSelector()

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Request {
        val url = "$baseUrl/filter".toHttpUrl().newBuilder()
        url.addQueryParameter("keyword", query)
        url.addQueryParameter("page", page.toString())
        return GET(url.build().toString())
    }

    // ANIME DETAILS

    private fun Elements.getMeta(name: String) = select(".item:has(.item-title:contains($name)) > .item-content").text()

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()

        val details = document.select(".anime-detail")

        anime.title = details.select(".dynamic-name").text().substringBeforeLast(" ")
        anime.thumbnailUrl = details.select(".film-poster-img").attr("src")
        anime.url = document.select("link[rel=canonical]").attr("href")
        anime.release = details.getMeta("Créé")
        anime.status = details.getMeta("Status")
        anime.description = details.select(".shorting").text()
        anime.genres = details.getMeta("Genre").split(",")

        return anime
    }

    // EPISODES
    override fun episodesSelector(): String = ".ep-item"

    private fun Anime.getId(): String = url.substringAfterLast("-")

    override fun episodesRequest(anime: Anime): Request {
        return GET(
            "$baseUrl/ajax/episode/list/${anime.getId()}",
            headers.newBuilder().set("Referer", "$baseUrl${anime.url}").build(),
        )
    }

    @Serializable
    data class EpisodeListDTO(val html: String, val status : Boolean, val totalItems : Int)

    override fun episodesParse(response: Response): List<SEpisode> {
        val jsonString = response.use { it.body.string() }
        val data = json.decodeFromString<EpisodeListDTO>(jsonString).html
        val document = Jsoup.parse(data)
        return document.select(episodesSelector()).parallelMap(::episodeFromElement).reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            episodeNumber = element.attr("data-number").toFloat()
            name = element.attr("title")
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun getEpisodeUrl(episode: SEpisode): String {
        return baseUrl + episode.url
    }

    // VIDEO STREAMS

    override fun streamSourcesSelector(): String = ".server-item"

    override fun streamSourcesFromElement(element: Element): List<StreamSource> = throw Exception("Unused")

    override fun episodeRequest(url: String): Request {
        return GET(
            "$baseUrl/ajax/episode/servers?episodeId=${url.substringAfter("?ep=")}",
            headers.newBuilder().set("Referer", "$baseUrl/").build(),
        )
    }

    @Serializable
    data class EpisodeStreamsDTO(val html: String, val status : Boolean)
    @Serializable
    data class StreamInfoDTO(val link: String)

    private val filemoonExtractor by lazy { FileMoonExtractor(client) }
    private val sibnetExtractor by lazy { SibnetExtractor(client) }
    private val sendvidExtractor by lazy { SendvidExtractor(client, headers) }
    private val voeExtractor by lazy { VoeExtractor(client,json) }
    private val vidCdnExtractor by lazy { VidCdnExtractor(client,json) }
    private val doodExtractor by lazy { DoodExtractor(client) }
    private val streamHideVidExtractor by lazy { StreamHideVidExtractor(client) }

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val jsonString = response.use { it.body.string() }
        val data = json.decodeFromString<EpisodeStreamsDTO>(jsonString).html
        val document = Jsoup.parse(data)
        val epid = response.request.url.toString().substringAfterLast("=")
        val streamSourcesList = mutableListOf<StreamSource>()
        val deduplicatedData = document.select(streamSourcesSelector()).distinctBy { it.attr("data-id") }
        streamSourcesList.addAll(
            deduplicatedData.parallelMap { server ->
                runCatching {
                    val playerRequest = GET("$baseUrl/ajax/episode/sources?id=${server.attr("data-id")}&epid=$epid")
                    val playerResponse = client.newCall(playerRequest).execute().use { pRes -> pRes.body.string() }
                    val playerUrl = json.decodeFromString<StreamInfoDTO>(playerResponse).link
                    val prefix = server.attr("data-type").uppercase()
                    with(playerUrl) {
                        when {
                            prefix == "VF" -> null
                            contains("toonanime.xyz") -> vidCdnExtractor.videosFromUrl(playerUrl)
                            contains("filemoon.sx") -> filemoonExtractor.videosFromUrl(this, "Filemoon - ",headers)
                            contains("sibnet.ru") -> sibnetExtractor.videosFromUrl(this)
                            contains("sendvid.com") -> sendvidExtractor.videosFromUrl(this)
                            contains("voe.sx") -> voeExtractor.videosFromUrl(this)
                            contains(Regex("(d000d|dood)")) -> doodExtractor.videosFromUrl(this, "DoodStream")
                            contains("vidhide") -> streamHideVidExtractor.videosFromUrl(this)
                            else -> null
                        }
                    }
                }.getOrNull()
            }.filterNotNull().flatten(),
        )
        return streamSourcesList.sort()
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