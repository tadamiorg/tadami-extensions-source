package com.sf.tadami.domain.anime

data class Anime(
    val id: Long,
    val source: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val release: String?,
    val status: String?,
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

