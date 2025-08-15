package com.sf.tadami.extension.fr.jetanime

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.extension.fr.jetanime.extractors.MilvayoExtractor
import com.sf.tadami.lib.filemoonextractor.FileMoonExtractor
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.streamhidevid.StreamHideVidExtractor
import com.sf.tadami.multiexts.dooplay.DooPlay
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent
import com.sf.tadami.ui.utils.parallelMap
import com.sf.tadami.utils.Lang
import com.sf.tadami.utils.editPreference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element

@Suppress("UNUSED")
class JetAnime : DooPlay<JetAnimePreferences>(
    sourceId = 11,
    prefGroup = JetAnimePreferences
) {
    override val name: String = "JetAnime"

    override val baseUrl: String
        get() = preferences.baseUrl

    override val lang: Lang = Lang.FRENCH

    override val client: OkHttpClient = network.cloudflareClient

    private val i18n = i18n(JetAnimeTranslations)

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if(migrated){
            Log.i("JetAnime","Successfully migrated preferences")
        }
    }


    // ============================== Preferences ===============================
    private suspend fun preferencesMigrations() : Boolean{
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(JetAnimePreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return false
            }
        }
        return true
    }

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getJetAnimePreferencesContent(i18n)
    }

    // =============================== Latest ===============================

    override fun latestAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val img = element.selectFirst("img")!!
            val url = element.selectFirst("a")?.attr("href") ?: element.attr("href")
            val slug = url.substringAfter("/episodes/")
            setUrlWithoutDomain("/serie/${slug.substringBeforeLast("-episode").substringBeforeLast("-saison")}")
            title = img.attr("alt")
            thumbnailUrl = img.getImageUrl()
        }
    }

    override fun latestAnimeNextPageSelector(): String = "div.pagination > span.current + a"

    // =============================== Search ===============================

    override fun searchAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val url = response.request.url.toString()

        val animeList = when {
            "/?s=" in url -> { // Search by name.
                document.select(customSearchSelector())
                    .map(::searchAnimeFromElement)
            }
            "/annee/" in url -> { // Search by year
                document.select(searchYearSelector())
                    .map(::commonAnimeFromElement)
            }
            else -> { // Search by some kind of filter, like genres or popularity.
                document.select(searchSelector())
                    .map(::commonAnimeFromElement)
            }
        }

        val hasNextPage = document.selectFirst(searchAnimeNextPageSelector()) != null
        return AnimesPage(animeList, hasNextPage)
    }

    private fun customSearchSelector() = "div.search-page > div.result-item div.image a"

    private fun searchYearSelector() = "div.content > div.items > article div.poster"

    override fun searchSelector() = "div#archive-content > article > div.poster"

    // ============================== Filters ===============================

    override val fetchGenres = false

    // ============================ Video Links =============================

    private val noRedirects = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        val players = response.asJsoup().select("ul#playeroptionsul li")
        val streamSourcesList = mutableListOf<StreamSource>()

        streamSourcesList.addAll(
            players.parallelMap { player ->
                runCatching {
                    val url = getPlayerUrl(player).ifEmpty { throw Exception("No url found !") }
                    with(url) {
                        when {
                            contains("mivalyo") -> MilvayoExtractor(client,headers).videosFromUrl(this)
                            contains("movearnpre") -> MilvayoExtractor(client,headers).videosFromUrl(this,"Movearnpre","Movearnpre")
                            contains("hdsplay") -> FileMoonExtractor(client).videosFromUrl("https://26efp.com/bkg/${this.split("/").last()}?ref=on.jetanimes.com")
                            else -> null
                        }
                    }
                }.getOrNull()
            }.filterNotNull().flatten(),
        )

        return streamSourcesList
    }

    private fun getPlayerUrl(player: Element): String {
        val type = player.attr("data-type")
        val id = player.attr("data-post")
        val num = player.attr("data-nume")
        if (num == "trailer") return ""
        return client.newCall(GET("$baseUrl/wp-json/dooplayer/v1/post/$id?type=$type&source=$num"))
            .execute()
            .body.string()
            .substringAfter("\"embed_url\":\"")
            .substringBefore("\",")
            .replace("\\", "")
    }

    override fun List<StreamSource>.sort(): List<StreamSource> {
        return this.groupBy { it.server.lowercase() }.entries
            .sortedWith(
                compareBy { (server, _) ->
                    preferences.playerStreamsOrder.split(",").indexOf(server)
                }
            ).flatMap { group ->
                group.value.sortedWith(
                    compareByDescending<StreamSource> { source ->
                        // Extract numerical quality
                        Regex("""(\d+)""").find(source.quality)?.value?.toIntOrNull() ?: Int.MIN_VALUE
                    }.thenBy { source ->
                        // Extract the "Source #N" number if present
                        val sourceMatch = Regex("""\s+#(\d+)""").find(source.fullName)
                        sourceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    }
                )
            }
    }
}