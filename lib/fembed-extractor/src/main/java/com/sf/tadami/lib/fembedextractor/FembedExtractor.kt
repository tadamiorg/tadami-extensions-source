package com.sf.tadami.lib.fembedextractor

import com.sf.tadami.network.GET
import com.sf.tadami.network.POST
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class FembedExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, prefix: String = "", redirect: Boolean = false): List<StreamSource> {
        val videoApi = if (redirect) {
            (runCatching { client.newCall(GET(url)).execute().request.url.toString()
                .replace("/v/", "/api/source/") }.getOrNull() ?: return emptyList())
        } else {
            url.replace("/v/", "/api/source/")
        }
        val body = runCatching {
            client.newCall(POST(videoApi)).execute().body.string()
        }.getOrNull() ?: return emptyList()

        val jsonResponse = try{ Json { ignoreUnknownKeys = true }.decodeFromString(body) } catch (e: Exception) { FembedResponse(false, emptyList()) }

        return if (jsonResponse.success) {
            jsonResponse.data.map {
                val fullName = ("Fembed - ${it.label}").let {
                    if (prefix.isNotBlank()) "$prefix $it"
                    else it
                }
                StreamSource(url = it.file, fullName = fullName, server = "Fembed")
            }
        } else { emptyList() }
    }
}
