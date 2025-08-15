package com.sf.tadami.extension.en.hianime

import android.util.Log
import androidx.datastore.preferences.core.intPreferencesKey
import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.lib.megacloudextractor.MegaCloudExtractor
import com.sf.tadami.lib.streamtapeextractor.StreamTapeExtractor
import com.sf.tadami.multiexts.zoro.Zoro
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent
import com.sf.tadami.utils.Lang
import com.sf.tadami.utils.editPreference
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.jsoup.nodes.Element

@Suppress("Unused")
class HiAnime : Zoro<HiAnimePreferences>(
    sourceId = 8,
    prefGroup = HiAnimePreferences
) {
    override val name: String = "HiAnime"

    override val lang: Lang = Lang.ENGLISH

    private val i18n = i18n(HiAnimeTranslations)

    override val ajaxRoute = "/v2"

    init {
        val migrated = runBlocking {
            preferencesMigrations()
        }
        if(migrated){
            Log.i("HiAnime","Successfully migrated preferences")
        }
    }

    // =============================== Preferences ===============================

    override fun getPreferenceScreen(): SourcesPreferencesContent {
        return getHiAnimePreferencesContent(i18n)
    }

    private suspend fun preferencesMigrations() : Boolean {
        val oldVersion = preferences.lastVersionCode
        if (oldVersion < BuildConfig.VERSION_CODE) {
            dataStore.editPreference(
                BuildConfig.VERSION_CODE,
                intPreferencesKey(HiAnimePreferences.LAST_VERSION_CODE.name)
            )

            // Fresh install
            if (oldVersion == 0) {
                return false
            }
        }
        return true
    }

    // =============================== Latest ===============================

    override fun latestAnimesRequest(page: Int): Request = GET("$baseUrl/recently-updated?page=$page", docHeaders)

    override fun latestAnimeFromElement(element: Element): SAnime {
        return super.latestAnimeFromElement(element).apply {
            url = url.substringBefore("?")
        }
    }

    // =============================== Zoro extract streams ===============================

    private val streamtapeExtractor by lazy { StreamTapeExtractor(client) }
    private val megaCloudExtractor by lazy { MegaCloudExtractor(client, headers, MEGACLOUD_API) }

    override fun extractVideo(server: VideoData): List<StreamSource> {
        return when (server.name) {
            "StreamTape" -> {
                streamtapeExtractor.videoFromUrl(server.link, "Streamtape - ${server.type}")
                    ?.let(::listOf)
                    ?: emptyList()
            }
            "HD-1", "HD-2", "HD-3" -> megaCloudExtractor.getVideosFromUrl(server.link, server.type, server.name)
            else -> emptyList()
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

    companion object {
        const val MEGACLOUD_API = "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"
    }
}