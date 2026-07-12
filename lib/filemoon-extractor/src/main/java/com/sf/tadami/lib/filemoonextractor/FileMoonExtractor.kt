package com.sf.tadami.lib.filemoonextractor

import android.util.Base64
import android.util.Log
import com.sf.tadami.lib.playlistutils.PlaylistUtils
import com.sf.tadami.network.GET
import com.sf.tadami.network.POST
import com.sf.tadami.network.asJsoup
import com.sf.tadami.source.model.StreamSource
import com.sf.tadami.source.model.Track
import dev.datlag.jsunpacker.JsUnpacker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import uy.kohesive.injekt.injectLazy

/**
 * Extractor for Filemoon / "Byse" players.
 *
 * Modern hosts protect the stream behind a challenge flow (ECDSA attest + proof-of-work
 * captcha) before returning the playback config (plain `sources` or an AES-256-GCM encrypted
 * `playback` blob). Older mirrors still expose a packed-eval script; that path is kept as a
 * fallback. Ported from skoruppa/docchi-players `filemoon.py` (proxy/domain lists omitted).
 */
class FileMoonExtractor(private val client: OkHttpClient) {

    private val playlistUtils by lazy { PlaylistUtils(client) }
    private val json: Json by injectLazy()
    private val parser by lazy { Json { ignoreUnknownKeys = true } }

    fun videosFromUrl(
        url: String,
        prefix: String = "Filemoon - ",
        headers: Headers? = null,
        embedOrigin: String? = null,
    ): List<StreamSource> {
        Log.d(TAG, "videosFromUrl: url=$url embedOrigin=$embedOrigin")
        val apiSources = runCatching { sourcesFromApi(url, headers, embedOrigin) }.getOrElse {
            Log.e(TAG, "API flow failed: ${it.message}", it)
            null
        }
        if (!apiSources.isNullOrEmpty()) {
            return buildStreams(apiSources, url, prefix, headers)
        }
        Log.d(TAG, "API flow yielded nothing, falling back to packed-eval")
        return runCatching { videosFromPacked(url, prefix, headers) }.getOrElse {
            Log.e(TAG, "Packed flow failed: ${it.message}", it)
            emptyList()
        }
    }

    // ================================ Modern challenge API ================================

    private fun sourcesFromApi(url: String, headers: Headers?, embedOrigin: String?): List<VideoSource> {
        val httpUrl = url.toHttpUrl()
        val host = httpUrl.host
        val mediaId = MEDIA_ID_REGEX.find(httpUrl.encodedPath)?.groupValues?.get(1)
            ?: httpUrl.pathSegments.lastOrNull { it.isNotEmpty() }
            ?: return emptyList()
        val ua = headers?.get("User-Agent") ?: DEFAULT_UA
        Log.d(TAG, "[api] host=$host mediaId=$mediaId")

        // Legacy flow first: POST /api/videos/{id}/playback with a self-signed fingerprint.
        val legacy = postJson(
            "https://$host/api/videos/$mediaId/playback",
            baseApiHeaders(host, url, ua),
            legacyFingerprint(),
        )
        Log.d(TAG, "[api] legacy /playback -> ${legacy.first}")

        val data: JsonObject = when {
            legacy.first == 428 -> {
                Log.d(TAG, "[api] captcha required -> running challenge flow")
                challengeFlow(host, mediaId, url, ua, embedOrigin) ?: return emptyList()
            }
            legacy.first in 200..299 -> parser.parseToJsonElement(legacy.second).jsonObject
            else -> {
                Log.d(TAG, "[api] legacy playback rejected (${legacy.first}): ${legacy.second.take(160)}")
                return emptyList()
            }
        }

        return extractSources(data)
    }

    private fun challengeFlow(
        host: String,
        mediaId: String,
        embedPageUrl: String,
        ua: String,
        embedOrigin: String?,
    ): JsonObject? {
        val base = "https://$host"
        val apiHeaders = baseApiHeaders(host, embedPageUrl, ua)

        // 1. Challenge
        val challenge = postJson("$base/api/videos/access/challenge", apiHeaders, "{}")
        if (challenge.first !in 200..299) {
            Log.d(TAG, "[challenge] failed ${challenge.first}: ${challenge.second.take(160)}")
            return null
        }
        val challengeObj = parser.parseToJsonElement(challenge.second).jsonObject
        val challengeId = challengeObj.str("challenge_id") ?: return null
        val nonce = challengeObj.str("nonce") ?: return null

        // 2. Attest (ECDSA P-256 signature of the nonce + fake client fingerprint)
        val (jwk, signature) = signChallenge(nonce)
        val attestBody = buildJsonObject {
            put("viewer_id", "")
            put("device_id", "")
            put("challenge_id", challengeId)
            put("nonce", nonce)
            put("signature", signature)
            put("public_key", jwk)
            put("client", clientFingerprint(ua))
            putJsonObject("storage") {}
            putJsonObject("attributes") { put("entropy", "low") }
        }
        val attest = postJson("$base/api/videos/access/attest", apiHeaders, attestBody.toString())
        if (attest.first !in 200..299) {
            Log.d(TAG, "[challenge] attest failed ${attest.first}: ${attest.second.take(160)}")
            return null
        }
        val attestObj = parser.parseToJsonElement(attest.second).jsonObject
        val token = attestObj.str("token") ?: return null
        val viewerId = attestObj.str("viewer_id") ?: return null
        val deviceId = attestObj.str("device_id") ?: return null
        val confidence = attestObj["confidence"] ?: JsonPrimitive(0.5)

        val fingerprint = buildJsonObject {
            putJsonObject("fingerprint") {
                put("token", token)
                put("viewer_id", viewerId)
                put("device_id", deviceId)
                put("confidence", confidence)
            }
        }
        val embedHeaders = embedHeaders(host, embedPageUrl, ua, embedOrigin, viewerId, deviceId)

        // 3. Captcha (proof-of-work challenge)
        val captcha = postJson("$base/api/videos/$mediaId/embed/captcha", embedHeaders, fingerprint.toString())
        if (captcha.first !in 200..299) {
            Log.d(TAG, "[challenge] captcha failed ${captcha.first}: ${captcha.second.take(160)}")
            return null
        }
        val captchaObj = parser.parseToJsonElement(captcha.second).jsonObject
        val powNonce = captchaObj.str("pow_nonce") ?: return null
        val powDifficulty = captchaObj["pow_difficulty"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val powToken = captchaObj.str("pow_token") ?: return null

        // 4. Solve PoW + verify
        Log.d(TAG, "[challenge] solving PoW (difficulty=$powDifficulty)")
        val solution = solvePow(powNonce, powDifficulty) ?: run {
            Log.e(TAG, "[challenge] PoW unsolved")
            return null
        }
        Log.d(TAG, "[challenge] PoW solution=$solution")
        val verifyBody = buildJsonObject {
            put("pow_token", powToken)
            put("solution", solution)
            put("fingerprint", fingerprint["fingerprint"]!!)
        }
        val verify = postJson("$base/api/videos/$mediaId/embed/captcha/verify", embedHeaders, verifyBody.toString())
        val verifyObj = runCatching { parser.parseToJsonElement(verify.second).jsonObject }.getOrNull()
        val captchaToken = verifyObj?.str("token")
        if (verify.first !in 200..299 || captchaToken == null) {
            Log.d(TAG, "[challenge] verify failed ${verify.first}: ${verify.second.take(160)}")
            return null
        }

        // 5. Playback with the captcha token
        val playbackHeaders = embedHeaders.newBuilder().set("X-Captcha-Token", captchaToken).build()
        val playback = postJson("$base/api/videos/$mediaId/embed/playback", playbackHeaders, fingerprint.toString())
        Log.d(TAG, "[challenge] playback -> ${playback.first}")
        if (playback.first !in 200..299) return null
        return parser.parseToJsonElement(playback.second).jsonObject
    }

    private fun extractSources(data: JsonObject): List<VideoSource> {
        (data["sources"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "[api] plaintext sources: ${it.size}")
            return parser.decodeFromJsonElement(SOURCE_LIST_SERIALIZER, it)
        }
        val playback = data["playback"] as? JsonObject ?: return emptyList()
        Log.d(TAG, "[api] encrypted playback -> decrypting")
        val decrypted = decryptPlayback(playback)
        val sources = parser.parseToJsonElement(decrypted).jsonObject["sources"] as? JsonArray ?: return emptyList()
        Log.d(TAG, "[api] decrypted ${sources.size} source(s)")
        return parser.decodeFromJsonElement(SOURCE_LIST_SERIALIZER, sources)
    }

    private fun buildStreams(sources: List<VideoSource>, url: String, prefix: String, headers: Headers?): List<StreamSource> {
        val host = url.toHttpUrl().host
        val ua = headers?.get("User-Agent") ?: DEFAULT_UA
        val videoHeaders = Headers.Builder()
            .set("Referer", "https://$host/")
            .set("User-Agent", ua)
            .build()

        return sources.flatMap { source ->
            var streamUrl = source.url ?: source.file ?: return@flatMap emptyList<StreamSource>()
            if (streamUrl.startsWith("/")) streamUrl = "https://$host$streamUrl"
            val quality = source.label ?: "Unknown"
            Log.d(TAG, "[api] extracting HLS ${streamUrl.take(110)} (q=$quality)")
            playlistUtils.extractFromHls(
                streamUrl,
                masterHeaders = videoHeaders,
                videoHeaders = videoHeaders,
                videoNameGen = { "$prefix$quality" },
            )
        }.map { it.copy(server = "Filemoon") }
            .also { Log.d(TAG, "[api] produced ${it.size} playable stream(s)") }
    }

    // ================================ Crypto / PoW helpers ================================

    /** Generate an EC P-256 keypair, return (JWK public key, base64url raw ECDSA signature of nonce). */
    private fun signChallenge(nonce: String): Pair<JsonObject, String> {
        val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val kp = kpg.generateKeyPair()
        val pub = kp.public as ECPublicKey
        val x = b64Url(to32(pub.w.affineX))
        val y = b64Url(to32(pub.w.affineY))
        val jwk = buildJsonObject {
            put("alg", "ES256")
            put("crv", "P-256")
            put("ext", true)
            put("key_ops", buildJsonArray { add("verify") })
            put("kty", "EC")
            put("x", x)
            put("y", y)
        }
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(kp.private)
            update(nonce.toByteArray())
        }.sign()
        return jwk to b64Url(derToRaw(sig))
    }

    private fun clientFingerprint(ua: String): JsonObject = buildJsonObject {
        put("user_agent", ua)
        put("pixel_ratio", 1)
        put("screen_width", 1920)
        put("screen_height", 1080)
        put("color_depth", 24)
        put("languages", buildJsonArray { add("fr-FR"); add("fr"); add("en") })
        put("timezone", "Europe/Paris")
        put("hardware_concurrency", 8)
        put("touch_points", 0)
        put("webgl_vendor", "Intel")
        put("webgl_renderer", "ANGLE (Intel, Intel(R) UHD Graphics 630, OpenGL 4.5)")
        put("canvas_hash", b64Url(randomBytes(32)))
        put("audio_hash", b64Url(randomBytes(32)))
        put("webgl_params_hash", b64Url(randomBytes(32)))
        put("fonts_hash", b64Url(randomBytes(32)))
        put("codecs_hash", b64Url(randomBytes(32)))
        put("media_devices", "ai0ao0vi0")
        put("pointer_type", "fine,hover")
        putJsonObject("extra") { put("vendor", ""); put("appVersion", "5.0 (X11)") }
    }

    /** Self-signed fingerprint used by the legacy /playback endpoint. */
    private fun legacyFingerprint(): String {
        val vId = randomBytes(16).toHex()
        val dId = randomBytes(16).toHex()
        val now = System.currentTimeMillis() / 1000
        val tData = buildJsonObject {
            put("viewer_id", vId)
            put("device_id", dId)
            put("confidence", 0.75)
            put("iat", now)
            put("exp", now + 600)
        }
        val tbData = b64Url(tData.toString().toByteArray())
        val tSig = b64Url(sha256(tbData.toByteArray()))
        val token = "$tbData.$tSig"
        return buildJsonObject {
            putJsonObject("fingerprint") {
                put("viewer_id", vId)
                put("device_id", dId)
                put("confidence", 0.75)
                put("token", token)
            }
        }.toString()
    }

    private fun decryptPlayback(pb: JsonObject): String {
        val iv = ftBytes(pb.str("iv")!!)
        val version = pb.str("version")
        val partsJson = pb["key_parts"] as JsonArray
        val parts = partsJson.map { it.jsonPrimitive.content }
        val selected = if (version != null) {
            val v = version.toInt()
            listOf(parts[v - 1], parts[parts.size - v])
        } else {
            parts
        }
        val key = selected.fold(ByteArray(0)) { acc, p -> acc + ftBytes(p) }
        val payload = ftBytes(pb.str("payload")!!)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    /**
     * Byse proof-of-work: custom memory-hard hash of `nonce + ":" + counter`; accept when the
     * 32-bit digest has at least [difficulty] leading zero bits. Ported from the reference solver.
     */
    private fun solvePow(nonce: String, difficulty: Int, maxIter: Int = 4_000_000): String? {
        val prefix = "$nonce:".toByteArray(Charsets.ISO_8859_1)
        val buf = IntArray(BUF)
        for (counter in 0 until maxIter) {
            val input = prefix + counter.toString().toByteArray(Charsets.ISO_8859_1)
            var s0 = 0x6A09E667
            var s1 = 0xBB67AE85.toInt()
            var s2 = 0x3C6EF372
            var s3 = 0xA54FF53A.toInt()

            for (bb in input) {
                s0 += (bb.toInt() and 0xFF); s0 = rl(s0, 7)
                s0 += s1; s3 = rl(s3 xor s0, 16); s2 += s3; s1 = rl(s1 xor s2, 12)
                s0 += s1; s3 = rl(s3 xor s0, 8); s2 += s3; s1 = rl(s1 xor s2, 7)
            }
            for (i in 0 until 8) {
                s0 += s1; s3 = rl(s3 xor s0, 16); s2 += s3; s1 = rl(s1 xor s2, 12)
                s0 += s1; s3 = rl(s3 xor s0, 8); s2 += s3; s1 = rl(s1 xor s2, 7)
            }
            for (i in 0 until BUF) {
                s0 += s1; s3 = rl(s3 xor s0, 16); s2 += s3; s1 = rl(s1 xor s2, 12)
                s0 += s1; s3 = rl(s3 xor s0, 8); s2 += s3; s1 = rl(s1 xor s2, 7)
                buf[i] = s0 xor s2
            }
            for (rep in 0 until 2) {
                for (si in 0 until BUF) {
                    val a = buf[si] and BMASK
                    var c = buf[si] + buf[a]
                    c = rl(c, 13)
                    c = c xor (buf[(si + 1) and BMASK] * IC)
                    buf[si] = c
                    s0 = s0 xor c
                    s0 += s1; s3 = rl(s3 xor s0, 16); s2 += s3; s1 = rl(s1 xor s2, 12)
                    s0 += s1; s3 = rl(s3 xor s0, 8); s2 += s3; s1 = rl(s1 xor s2, 7)
                }
            }
            s0 += s1; s3 = rl(s3 xor s0, 16); s2 += s3; s1 = rl(s1 xor s2, 12)
            s0 += s1; s3 = rl(s3 xor s0, 8); s2 += s3; s1 = rl(s1 xor s2, 7)

            var out = s0
            for (ci in 0 until 64) {
                val d = buf[ci]
                out += d; out = rl(out, 5); out = out xor (d * FC)
            }
            out = out xor s2

            if (Integer.numberOfLeadingZeros(out) >= difficulty) return counter.toString()
        }
        return null
    }

    // ================================ HTTP helpers ================================

    private fun postJson(url: String, headers: Headers, body: String): Pair<Int, String> {
        val request = POST(url, headers, body.toRequestBody(JSON_MEDIA))
        client.newCall(request).execute().use { resp ->
            return resp.code to resp.body.string()
        }
    }

    private fun baseApiHeaders(host: String, referer: String, ua: String): Headers = Headers.Builder()
        .set("User-Agent", ua)
        .set("Accept", "*/*")
        .set("Referer", referer)
        .set("Origin", "https://$host")
        .build()

    private fun embedHeaders(
        host: String,
        embedPageUrl: String,
        ua: String,
        embedOrigin: String?,
        viewerId: String,
        deviceId: String,
    ): Headers = baseApiHeaders(host, embedPageUrl, ua).newBuilder().apply {
        set("X-Embed-Parent", embedPageUrl)
        set("Cookie", "byse_viewer_id=$viewerId; byse_device_id=$deviceId")
        if (embedOrigin != null) {
            set("X-Embed-Origin", embedOrigin)
            set("X-Embed-Referer", "https://$embedOrigin/")
        }
    }.build()

    // ================================ Legacy packed-eval fallback ================================

    private fun videosFromPacked(url: String, prefix: String, headers: Headers?): List<StreamSource> {
        var httpUrl = url.toHttpUrl()
        val videoHeaders = (headers?.newBuilder() ?: Headers.Builder())
            .set("Referer", url)
            .set("Origin", "https://${httpUrl.host}")
            .build()

        val doc = client.newCall(GET(url, videoHeaders)).execute().asJsoup()
        val jsEval = doc.selectFirst("script:containsData(eval):containsData(m3u8)")?.data() ?: run {
            val iframeUrl = doc.selectFirst("iframe[src]")?.attr("src") ?: run {
                Log.d(TAG, "[packed] no eval/iframe found")
                return emptyList()
            }
            httpUrl = iframeUrl.toHttpUrl()
            val iframeDoc = client.newCall(GET(iframeUrl, videoHeaders)).execute().asJsoup()
            iframeDoc.selectFirst("script:containsData(eval):containsData(m3u8)")?.data() ?: return emptyList()
        }
        val unpacked = JsUnpacker.unpackAndCombine(jsEval).orEmpty()
        val masterUrl = unpacked.takeIf(String::isNotBlank)
            ?.substringAfter("{file:\"", "")
            ?.substringBefore("\"}", "")
            ?.takeIf(String::isNotBlank)
            ?: return emptyList()

        val subtitleTracks = buildList {
            val subUrl = httpUrl.queryParameter("sub.info")
                ?: unpacked.substringAfter("fetch('", "").substringBefore("').").takeIf(String::isNotBlank)
            if (subUrl != null) {
                runCatching {
                    client.newCall(GET(subUrl, videoHeaders)).execute()
                        .body.string()
                        .let { json.decodeFromString<List<SubtitleDto>>(it) }
                        .forEach { add(Track.SubtitleTrack(url = it.file, lang = it.label, mimeType = "text/vtt")) }
                }
            }
        }

        return playlistUtils.extractFromHls(
            masterUrl,
            referer = "https://${httpUrl.host}/",
            videoNameGen = { "$prefix$it" },
            videoHeadersGen = { _, referer, _ -> playlistUtils.generateMasterHeaders(videoHeaders, referer) },
            subtitleList = subtitleTracks,
        ).map { it.copy(server = "Filemoon") }
    }

    // ================================ Small utils ================================

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private fun rl(v: Int, s: Int): Int = (v shl s) or (v ushr (32 - s))

    private fun to32(bi: BigInteger): ByteArray {
        val raw = bi.toByteArray()
        val out = ByteArray(32)
        val src = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
        System.arraycopy(src, 0, out, 32 - src.size, src.size)
        return out
    }

    /** Convert a DER-encoded ECDSA signature to raw r||s (64 bytes). */
    private fun derToRaw(der: ByteArray): ByteArray {
        var idx = 2 // skip 0x30, total-length
        require(der[idx].toInt() == 0x02) { "bad DER" }
        val rLen = der[idx + 1].toInt()
        idx += 2
        val r = der.copyOfRange(idx, idx + rLen)
        idx += rLen
        require(der[idx].toInt() == 0x02) { "bad DER" }
        val sLen = der[idx + 1].toInt()
        idx += 2
        val s = der.copyOfRange(idx, idx + sLen)
        return pad32(r) + pad32(s)
    }

    private fun pad32(b: ByteArray): ByteArray {
        val out = ByteArray(32)
        val src = if (b.size > 32) b.copyOfRange(b.size - 32, b.size) else b
        System.arraycopy(src, 0, out, 32 - src.size, src.size)
        return out
    }

    private fun b64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun ftBytes(input: String): ByteArray {
        val b64 = input.replace('-', '+').replace('_', '/')
        val padding = when (b64.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }
        return Base64.decode(b64 + padding, Base64.DEFAULT)
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { java.security.SecureRandom().nextBytes(it) }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    @Serializable
    data class VideoSource(
        val file: String? = null,
        val url: String? = null,
        val label: String? = "Default",
    )

    @Serializable
    data class SubtitleDto(val file: String, val label: String)

    private companion object {
        const val TAG = "FileMoonExtractor"
        const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
        val MEDIA_ID_REGEX = Regex("""/(?:e|eyi|d|download|j\d+)/([0-9a-zA-Z]+)""")
        val JSON_MEDIA = "application/json".toMediaType()
        val SOURCE_LIST_SERIALIZER = kotlinx.serialization.builtins.ListSerializer(VideoSource.serializer())

        // PoW constants
        const val BUF = 512
        const val BMASK = 511
        val IC = 0x9E3779B1.toInt()
        val FC = 0x85EBCA77.toInt()
    }
}
