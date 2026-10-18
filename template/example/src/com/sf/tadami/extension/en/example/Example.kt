package com.sf.tadami.extension.en.example

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SAnimeStatus
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.online.AnimeHttpSource
import com.sf.tadami.utils.Lang
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

/**
 * A minimal source to start from: replace the URLs and the selectors with the site's.
 * The release reads the three declarations below as written (id, name and DEFAULT_BASE_URL): keep them literal.
 */
class Example : AnimeHttpSource() {

    // Generated from the repo, the package and the class name (see common.gradle): never change it once
    // released, the users' library entries are keyed by it
    override val id: Long = BuildConfig.SOURCE_ID

    override val name: String = "Example"

    override val baseUrl: String = DEFAULT_BASE_URL

    override val lang: Lang = Lang.ENGLISH

    // A site without a list of the latest episodes: false, and the latest* members throw
    override val supportRecent = true

    // =============================== Latest ===============================

    override fun latestAnimesRequest(page: Int): Request = GET("$baseUrl/latest?page=$page", headers)

    override fun latestAnimeParse(response: Response): AnimesPage = animesPage(response.asJsoup())

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList, noToasts: Boolean): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url.toString(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = animesPage(response.asJsoup())

    private fun animesPage(document: Document): AnimesPage {
        val animes = document.select("div.anime-card").map(::animeFromCard)
        return AnimesPage(animes, hasNextPage = document.selectFirst("a.next-page") != null)
    }

    private fun animeFromCard(card: Element): SAnime = SAnime.create().apply {
        val link = card.selectFirst("a[href]") ?: throw IOException("Anime card without a link")
        // Relative links resolved first: setUrlWithoutDomain keeps the path and query of what it's given
        setUrlWithoutDomain(link.absUrl("href"))
        title = card.selectFirst(".title")?.text() ?: link.text()
        thumbnailUrl = card.selectFirst("img")?.absUrl("src")
    }

    // =============================== Details ==============================

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        return SAnime.create().apply {
            title = document.selectFirst("h1")?.text() ?: throw IOException("Anime page without a title")
            description = document.selectFirst("div.synopsis")?.text()
            genres = document.select("div.genres a").map { it.text() }
            status = when (document.selectFirst("span.status")?.text()?.lowercase()) {
                "ongoing" -> SAnimeStatus.ONGOING
                "completed" -> SAnimeStatus.COMPLETED
                else -> SAnimeStatus.UNKNOWN
            }
        }
    }

    // =============================== Episodes =============================

    // Newest first: the app numbers the episodes in this order
    override fun episodesListParse(response: Response): List<SEpisode> {
        return response.asJsoup().select("ul.episodes a[href]").map { link ->
            SEpisode.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                episodeNumber = Regex("""\d+(\.\d+)?""").find(name)?.value?.toFloat() ?: -1f
            }
        }
    }

    // =============================== Streams ==============================

    override fun episodeSourcesParse(response: Response): List<StreamSource> {
        // Most sites embed players: map each one to an extractor of lib/ (see claude.md, "Video Extractors")
        throw UnsupportedOperationException("Not implemented yet")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://example.com"
    }
}
