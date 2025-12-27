package com.sf.tadami.lib.lpayerextractor

import LpayerVideoData
import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.source.model.StreamSource
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class LpayerExtractor(private val client: OkHttpClient) {
    private val json: Json by injectLazy()

    private val playlistUtils by lazy { PlaylistUtils(client) }

    private val AES_KEY = "kiemtienmua911ca".toByteArray(Charsets.UTF_8)
    private val AES_IV = "1234567890oiuytr".toByteArray(Charsets.UTF_8)

    private fun decrypt(encryptedHex: String): String? {
        return runCatching {
            // Remove all whitespace and non-hex characters
            val cleanedHex = encryptedHex.replace(Regex("[^0-9A-Fa-f]"), "")

            if (cleanedHex.isEmpty() || cleanedHex.length % 2 != 0) {
               Log.d("LpayerExtractor", "Invalid hex string length: ${cleanedHex.length}")
                return null
            }

            val encryptedBytes = cleanedHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(AES_KEY, "AES")
            val ivParameterSpec = IvParameterSpec(AES_IV)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        }.getOrNull()
    }

    fun videosFromUrl(videoUrl: String, prefix: String = "LPayer - "): List<StreamSource> {
        return runCatching {
            Log.d("LpayerExtractor", "Extracting videos from $videoUrl")

            val sources = mutableListOf<StreamSource>()
            val url = videoUrl.toHttpUrl()

            // Extract video ID from hash (fragment)
            val videoId = url.fragment ?: return emptyList()
            if (videoId.isEmpty()) return emptyList()

            // Build API URL
            val apiUrl = "${url.scheme}://${url.host}/api/v1/video?id=$videoId&w=1366&h=768&r="

            val headers = Headers.Builder()
                .add("Referer", videoUrl)
                .build()

            // Get encrypted data
            val encryptedData = client.newCall(
                GET(apiUrl, headers)
            ).execute().use { it.body.string() }

            Log.d("LpayerExtractor", "Encrypted data: $encryptedData")

            // Decrypt data
            val decryptedData = decrypt(encryptedData) ?: return emptyList()

            Log.d("LpayerExtractor", "DecryptedData data: $decryptedData")

            // Parse JSON
            val videoData = json.decodeFromString<LpayerVideoData>(decryptedData)

            // Get HLS URL
            var hlsUrl = videoData.hls
                ?: videoData.source
                ?: videoData.url
                ?: videoData.file
                ?: return emptyList()

            // Handle relative URLs
            if (hlsUrl.startsWith("/")) {
                hlsUrl = "${url.scheme}://${url.host}$hlsUrl"
            }

            val isM3U8 = hlsUrl.contains(".m3u8")

            // Parse M3U8 playlist for additional qualities
            if (isM3U8) {
                sources.addAll(playlistUtils.extractFromHls(
                    playlistUrl = hlsUrl,
                    referer = videoUrl,
                    videoNameGen = { "$prefix$it" },
                ).map {
                    it.copy(server = "LPayer")
                })
            }

            sources
        }.getOrElse{ emptyList() }
    }
}