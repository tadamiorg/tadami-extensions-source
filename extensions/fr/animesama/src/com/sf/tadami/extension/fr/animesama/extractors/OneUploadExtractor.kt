package com.sf.tadami.extension.fr.animesama.extractors

import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.util.Locale
import java.util.regex.Pattern

class OneUploadExtractor(private val client: OkHttpClient, private val headers: Headers) {
    private fun extractM3u8Url(doc: Document): String? {
        // Select the <script> tag that contains the .m3u8 URL
        val scriptTag = doc.selectFirst("script:containsData(jwplayer):containsData(setup)") ?: return null

        // Extract the data (JavaScript) from the script tag
        val scriptContent = scriptTag.data()

        // Define a regex pattern to match the .m3u8 URL
        val pattern = Pattern.compile("""file:\s*"(https?://[^\s]+\.m3u8[^"]*)"""")
        val matcher = pattern.matcher(scriptContent)

        // Find and return the first .m3u8 URL found in the script
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    private fun getSystemAcceptLanguage(): String {
        val locale = Locale.getDefault()
        return locale.toLanguageTag() + "," + locale.language + ";q=0.9"
    }

    fun videosFromUrl(url: String): List<StreamSource> {

        val baseHeaders = headers.newBuilder()
            .set("Accept-Language", getSystemAcceptLanguage())
            .build()

        val animesamaHeaders = baseHeaders.newBuilder()
            .set("Referer", "https://anime-sama.fr/")
            .build()

        val videoHeaders = baseHeaders.newBuilder()
            .set("Origin","https://oneupload.to")
            .set("Referer","https://oneupload.to/")
            .build()

        val doc = client.newCall(GET(url,animesamaHeaders)).execute().asJsoup()
        val masterUrl = extractM3u8Url(doc) ?: return emptyList()
        return PlaylistUtils(client,videoHeaders).extractFromHls(masterUrl,videoNameGen = { qual -> "OneUpload - $qual" }).map {
            it.copy(server = "OneUpload")
        }
    }
}