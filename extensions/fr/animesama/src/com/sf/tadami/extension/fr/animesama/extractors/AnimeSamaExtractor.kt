package com.sf.tadami.extension.fr.animesama.extractors

import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient

class AnimeSamaExtractor(private val client: OkHttpClient){
    fun videosFromUrl(url : String,originUrl : String,headers: Headers): List<StreamSource> {
        val videoList = mutableListOf<StreamSource>()

        val document = client.newCall(
            GET(originUrl,headers),
        ).execute().asJsoup()

        document.getElementById("selectLecteursAS") ?: return emptyList()

        videoList.add(
            StreamSource(url = url, fullName = "AnimeSama", server = "AnimeSama"),
        )
        return videoList
    }
}