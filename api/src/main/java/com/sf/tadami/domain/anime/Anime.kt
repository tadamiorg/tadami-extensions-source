package com.sf.tadami.domain.anime

import com.sf.tadami.source.model.SAnimeStatus

data class Anime(
    val id: Long,
    val source: Long,
    val url: String,
    val title: String,
    val rawTitle: String?,
    val thumbnailUrl: String?,
    val release: String?,
    val studio: String?,
    val author: String?,
    val status: SAnimeStatus,
    val description: String?,
    val genres: List<String>?,
    val favorite: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val fetchInterval: Int,
    val initialized: Boolean,
    val episodeFlags : Long,
    var dateAdded: Long
)

