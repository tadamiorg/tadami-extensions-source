package com.sf.tadami.source

import com.sf.tadami.domain.anime.Anime
import com.sf.tadami.source.model.AnimeFilterList
import com.sf.tadami.source.model.SAnime
import com.sf.tadami.source.model.SEpisode
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.utils.Lang
import io.reactivex.rxjava3.core.Observable

class StubSource(override val id: String) : AnimeCatalogueSource {
    override val name: String = throw Exception("Stub")
    override val lang: Lang = throw Exception("Stub")
    override fun fetchSearch(
        page: Int,
        query: String,
        filters: AnimeFilterList,
        noToasts: Boolean
    ): Observable<AnimesPage> {
        throw Exception("Stub")
    }

    override fun fetchLatest(page: Int): Observable<AnimesPage> {
        throw Exception("Stub")
    }

    override fun getFilterList(): AnimeFilterList {
        throw Exception("Stub")
    }

    override fun fetchAnimeDetails(anime: Anime): Observable<SAnime> {
        throw Exception("Stub")
    }

    override fun fetchEpisodesList(anime: Anime): Observable<List<SEpisode>> {
        throw Exception("Stub")
    }

    override fun fetchEpisode(url: String): Observable<List<StreamSource>> {
        throw Exception("Stub")
    }

    private fun getSourceNotInstalledException(): SourceNotInstalledException {
        throw Exception("Stub")
    }

    inner class SourceNotInstalledException : Exception(id)
}