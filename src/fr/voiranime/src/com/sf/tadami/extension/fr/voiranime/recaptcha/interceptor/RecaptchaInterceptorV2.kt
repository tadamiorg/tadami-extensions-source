package com.sf.tadami.extension.fr.voiranime.recaptcha.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

@Serializable
data class Coordinates(val x: Float,val y: Float)

class RecaptchaInterceptorV2(
    val animeId: String,
    val episodeId: String,
    val host: String,
    val ajaxUrl: String,
    private val latch: CountDownLatch,
    context: Context,
) : WebViewInterceptor(context) {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        return true
    }

    class AndroidJSI(
        private val latch: CountDownLatch,
        var webview: WebView?
    ) {
        var recaptchaToken: String? = null

        fun simulateTap(x: Float, y: Float) {

            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()

            val randomClickSalt = Random.nextInt(5, 35 + 1)

            // Simulate touch down
            val touchDown = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_DOWN, x + randomClickSalt, y + randomClickSalt, 0
            )

            val randomInterval = Random.nextInt(100, 200 + 1)

            // Simulate touch up
            val touchUp = MotionEvent.obtain(
                downTime, eventTime + randomInterval, MotionEvent.ACTION_UP, x + randomClickSalt, y + randomClickSalt, 0
            )

            webview?.dispatchTouchEvent(touchDown)
            webview?.dispatchTouchEvent(touchUp)

            // Recycle MotionEvent objects
            touchDown.recycle()
            touchUp.recycle()
        }

        @JavascriptInterface
        fun onIframeCoordinates(left: Float, top: Float, width: Float, height: Float) {
            val density = webview?.resources?.displayMetrics?.density ?: 1f
            val scrollX = webview?.scrollX ?: 0
            val scrollY = webview?.scrollY ?: 0

            // Adjust coordinates for scroll and density
            val x = (left + (width / 2)) * density + scrollX
            val y = (top + (height / 2)) * density + scrollY

            Log.e("Calculated Tap", "x=$x, y=$y")

            simulateTap(x, y)
        }


        @JavascriptInterface
        fun onCaptchaSolved(token: String) {
            recaptchaToken = token
            latch.countDown()
        }
    }

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response
    ): Response {
        try {
            response.close()
            val captchaToken = resolveWithWebView(request)

            if (captchaToken === null) {
                throw IOException("Failed to bypass recaptcha")
            }

            val formBody: RequestBody = FormBody.Builder()
                .add("action", "get_video_chapter_content")
                .add("grecaptcha", captchaToken)
                .add("manga", animeId)
                .add("chapter", episodeId)
                .add("host", host)
                .build()

            val newRequest = request.newBuilder()
                .url(ajaxUrl)
                .post(formBody)
                .build()

            return chain.proceed(newRequest)
        } catch (e: Exception) {
            throw IOException("Failed to bypass recaptcha")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request): String? {

        var webview: WebView? = null
        val androidjsi = AndroidJSI(latch, webview)

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        fun injectCaptchaScript() {
            webview?.evaluateJavascript(
                """
                    (function() {
                         const iframe = document.querySelector('iframe[title="reCAPTCHA"]');
                         if(!iframe){
                            RecaptchaSolver.onCaptchaSolved(null);
                         }
                         const rect = iframe.getBoundingClientRect();
                         const min = Math.ceil(1000);
                         const max = Math.floor(2000);
                         const randomTimeout = Math.floor(Math.random() * (max - min + 1)) + min
                         setTimeout(() => { RecaptchaSolver.onIframeCoordinates(rect.left, rect.top, rect.width, rect.height) }, randomTimeout)
                         var interval = setInterval(function() {
                                const recaptchaResponse = document.getElementById('g-recaptcha-response');
                                if (recaptchaResponse && recaptchaResponse.value) {
                                    clearInterval(interval);
                                    RecaptchaSolver.onCaptchaSolved(recaptchaResponse.value);
                                }
                        }, 500);
                    })();
            """.trimIndent(), null
            )
        }


        executor.execute {
            webview = createWebView(originalRequest)

            androidjsi.webview = webview

            webview?.webViewClient = object : WebViewClientCompat() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    injectCaptchaScript()
                }
            }

            webview?.addJavascriptInterface(androidjsi, "RecaptchaSolver")
            webview?.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        return androidjsi.recaptchaToken
    }
}