package com.sf.tadami.extension.fr.voiranime.recaptcha

import com.sf.tadami.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

internal class ReCapatchaInterceptor(
    private val client: OkHttpClient,
    private val baseUrl : String,
    private val url: String,
    private val host : String,
    private val animeId : String,
    private val episodeId : String,
    private val headers: Headers,
) : Interceptor {
    private val recapBypasser by lazy { RecaptchaV3Bypasser(client, headers) }

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val (token, reCaptchaToken) = recapBypasser.getRecaptchaToken(baseUrl + url.replace(" ","%20"))

        if (reCaptchaToken.isBlank()) throw IOException(FAILED_RECAPTCHA)

        val formBody = FormBody.Builder()
            .add("action", "get_video_chapter_content")
            .add("grecaptcha", reCaptchaToken)
            .add("manga", animeId)
            .add("chapter", episodeId)
            .add("host", host)
            .build()

        val newRequest = POST(url = "$baseUrl/wp-admin/admin-ajax.php", body = formBody, headers = headers)

        return chain.proceed(newRequest)
    }

    companion object {
        private const val FAILED_RECAPTCHA = "Recaptcha verification failed"
    }
}