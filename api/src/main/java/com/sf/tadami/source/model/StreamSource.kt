package com.sf.tadami.source.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import okhttp3.Headers

@Serializable
data class StreamSource(
    val url : String = "",
    val quality : String = "",
    @Serializable(with = KSerializer::class)
    val headers: Headers? = null
)