package com.sf.tadami.extension.fr.voiranime.recaptcha.interceptor

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import com.sf.tadami.network.NetworkHelper
import com.sf.tadami.ui.utils.UiToasts
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class WebViewInterceptor(private val context: Context) : Interceptor {

    /**
     * When this is called, it initializes the WebView if it wasn't already. We use this to avoid
     * blocking the main thread too much. If used too often we could consider moving it to the
     * Application class.
     */
    private val initWebView by lazy {
        // Crashes on some devices. We skip this in some cases since the only impact is slower
        // WebView init in those rare cases.
        // See https://bugs.chromium.org/p/chromium/issues/detail?id=1279562
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            return@lazy
        }

        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // Avoid some crashes like when Chrome/WebView is being updated.
        }
    }

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }

        if (!WebViewUtil.supportsWebView(context)) {
            UiToasts.showToast("Webview is required to use Voiranime", Toast.LENGTH_LONG)

            return response
        }
        initWebView

        return intercept(chain, request, response)
    }

    fun parseHeaders(headers: Headers): Map<String, String> {
        return headers
            // Keeping unsafe header makes webview throw [net::ERR_INVALID_ARGUMENT]
            .filter { (name, value) ->
                isRequestHeaderSafe(name, value)
            }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.getOrNull(0).orEmpty() }
    }

    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    fun displayWebViewOnTop(webView: WebView, context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Define layout parameters for the WebView
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // Full width
            WindowManager.LayoutParams.MATCH_PARENT, // Full height
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY // For Android O and above
            else
                WindowManager.LayoutParams.TYPE_PHONE, // For below Android O
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // Add WebView to the WindowManager
        windowManager.addView(webView, layoutParams)
    }


    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Avoid sending empty User-Agent, Chromium WebView will reset to default if empty
            settings.userAgentString = request.header("User-Agent") ?: NetworkHelper.DEFAULT_USER_AGENT
        }
    }
}

// Based on [IsRequestHeaderSafe] in https://source.chromium.org/chromium/chromium/src/+/main:services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}
private val unsafeHeaderNames = listOf("content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie")
