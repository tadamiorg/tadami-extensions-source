package com.sf.tadami.extension.en.kickassanime.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PopularResponseDto(
    val page_count: Int = 0,
    val result: List<PopularItemDto> = emptyList(),
)

@Serializable
data class RecentsResponseDto(
    val hadNext: Boolean = false,
    val result: List<PopularItemDto> = emptyList(),
)

@Serializable
data class SearchResponseDto(
    val result: List<PopularItemDto> = emptyList(),
    val maxPage: Int = 1,
)

@Serializable
data class PopularItemDto(
    val title: String = "",
    val title_en: String? = null,
    val slug: String = "",
    val poster: PosterDto? = null,
)

@Serializable
data class PosterDto(@SerialName("hq") val slug: String = "") {
    val url get() = "image/poster/$slug.webp"
}

@Serializable
data class AnimeInfoDto(
    val genres: List<String> = emptyList(),
    val poster: PosterDto? = null,
    val season: String? = null,
    val slug: String = "",
    val status: String = "",
    val synopsis: String? = null,
    val title: String = "",
    val title_en: String? = null,
    val year: Int? = null,
)

@Serializable
data class LanguagesDto(
    val result: List<String> = emptyList(),
)

@Serializable
data class EpisodeResponseDto(
    // We only care about its size (number of pages), not the contents.
    val pages: List<JsonObject> = emptyList(),
    val result: List<EpisodeDto> = emptyList(),
) {
    @Serializable
    data class EpisodeDto(
        val slug: String = "",
        val title: String? = null,
        val episode_string: String = "",
    )
}

@Serializable
data class ServersDto(val servers: List<Server> = emptyList()) {
    @Serializable
    data class Server(
        val name: String = "",
        val src: String = "",
    )
}

@Serializable
data class VideoDto(
    val hls: String = "",
    val dash: String = "",
    val subtitles: List<SubtitlesDto> = emptyList(),
) {
    @Serializable
    data class SubtitlesDto(
        val name: String = "",
        val language: String = "",
        val src: String = "",
    )
}
