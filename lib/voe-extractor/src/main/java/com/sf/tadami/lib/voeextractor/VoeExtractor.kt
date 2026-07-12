package com.sf.tadami.lib.voeextractor

import android.util.Base64
import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

class VoeExtractor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private val clientDdos by lazy { client.newBuilder().addInterceptor(DdosGuardInterceptor(client)).build() }

    private val playlistUtils by lazy { PlaylistUtils(clientDdos) }

    private val redirectRegex = Regex("""window\.location\.href\s*=\s*'([^']+)'""")

    fun videosFromUrl(url: String, prefix: String = ""): List<StreamSource> {
        val streamSources = mutableListOf<StreamSource>()

        var document = clientDdos.newCall(GET(url)).execute().asJsoup()

        // VOE now serves a redirect stub (window.location.href = 'https://<mirror>/e/<code>').
        val redirect = document.selectFirst("script")?.data()?.let { redirectRegex.find(it) }
        if (redirect != null) {
            document = clientDdos.newCall(GET(redirect.groupValues[1])).execute().asJsoup()
        }

        // The player config lives in a <script type="application/json">["<F7-encoded>"]</script> blob.
        val encoded = document.selectFirst("script[type=application/json]")?.data()
            ?.trim()
            ?.substringAfter("[\"")
            ?.substringBeforeLast("\"]")
            ?: return emptyList()

        val decrypted = decryptF7(encoded) ?: return emptyList()
        val m3u8 = decrypted["source"]?.jsonPrimitive?.content
        val mp4 = decrypted["direct_access_url"]?.jsonPrimitive?.content

        if (m3u8 != null) {
            streamSources.addAll(
                playlistUtils.extractFromHls(
                    m3u8,
                    videoNameGen = { quality -> "${prefix}Voe - $quality" },
                ).map { it.copy(server = "Voe") },
            )
        }
        if (mp4 != null) {
            streamSources.add(
                StreamSource(url = mp4, fullName = "${prefix}Voe - MP4", server = "Voe"),
            )
        }

        return streamSources
    }

    private fun decryptF7(p8: String): JsonObject? = try {
        val step1 = rot13(p8)
        val step2 = replacePatterns(step1)
        val step3 = step2.replace("_", "")
        val step4 = base64Decode(step3)
        val step5 = charShift(step4, 3)
        val step6 = step5.reversed()
        val decoded = base64Decode(step6)
        json.decodeFromString<JsonObject>(decoded)
    } catch (e: Exception) {
        Log.e("VoeExtractor", "Decryption error: ${e.message}")
        null
    }

    private fun rot13(input: String): String = input.map { c ->
        when (c) {
            in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
            in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
            else -> c
        }
    }.joinToString("")

    private val patternsRegex = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
        .joinToString("|") { Regex.escape(it) }.toRegex()

    private fun replacePatterns(input: String): String = input.replace(patternsRegex, "_")

    private fun charShift(input: String, shift: Int): String =
        input.map { (it.code - shift).toChar() }.joinToString("")

    private fun base64Decode(input: String): String =
        String(Base64.decode(input, Base64.DEFAULT), Charsets.ISO_8859_1)
}
