package com.sf.tadami.source.online

import com.sf.tadami.source.AnimesPage
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ParsedAnimeHttpSource : AnimeHttpSource() {

    // Search
    protected abstract fun searchSelector(): String
    protected abstract fun searchAnimeFromElement(element: Element): SAnime?
    protected abstract fun searchAnimeNextPageSelector(): String?
    override fun searchAnimeParse(response: Response): AnimesPage = throw Exception("Stub!")

    // Latest
    protected abstract fun latestSelector(): String
    protected abstract fun latestAnimeFromElement(element: Element): SAnime
    protected abstract fun latestAnimeNextPageSelector(): String?

    override fun latestAnimeParse(response: Response): AnimesPage = throw Exception("Stub!")

    // Details

    override fun animeDetailsParse(response: Response): SAnime = throw Exception("Stub!")

    protected abstract fun animeDetailsParse(document: Document): SAnime

    // Episodes

    protected abstract fun episodesSelector(): String
    protected abstract fun episodeFromElement(element: Element): SEpisode

    override fun episodesParse(response: Response): List<SEpisode> = throw Exception("Stub!")

    // VideoList

    protected abstract fun streamSourcesSelector(): String
    protected abstract fun streamSourcesFromElement(element: Element): List<StreamSource>

    override fun episodeSourcesParse(response: Response): List<StreamSource> = throw Exception("Stub!")
}