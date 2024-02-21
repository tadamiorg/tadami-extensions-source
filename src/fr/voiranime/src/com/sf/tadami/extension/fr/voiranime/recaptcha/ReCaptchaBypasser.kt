package com.sf.tadami.extension.fr.voiranime.recaptcha

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sf.tadami.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class RecaptchaV3Bypasser(private val client: OkHttpClient, private val headers: Headers) {
    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val defaultUA : String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private fun <K, V> MutableMap<K, V>.putOrReplace(key: K, value: V) {
        if (containsKey(key)) {
            this[key] = value
        } else {
            put(key, value)
        }
    }

    class AndroidJSI(private val latch: CountDownLatch) {
        var token = ""

        @JavascriptInterface
        fun sendResponse(response: String) {
            token = response.lines().joinToString("").substringAfter("uvresp\",\"").substringBefore("\"")
            latch.countDown()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getRecaptchaToken(targetUrl: String): Pair<String, String> {
        val latch = CountDownLatch(1)
        var webView: WebView? = null

        var token = ""
        val androidjsi = AndroidJSI(latch)

        handler.post {
            val webview = WebView(context)
            webView = webview
            with(webview.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = false
                loadWithOverviewMode = false
            }

            webview.addJavascriptInterface(androidjsi, "androidjsi")

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript("document.querySelector('input[name=_token]').value") {
                        token = it.trim('"')
                    }
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url.toString()
                    val reqHeaders = request?.requestHeaders.orEmpty()
                    val mutableHeaders = reqHeaders.toMutableMap()
                    mutableHeaders.putOrReplace("User-Agent", headers["User-Agent"] ?: defaultUA)
                    // Our beloved token
                    if (url.contains("/recaptcha/api2/anchor")) {
                        // Injects the script to click on the captcha box
                        return injectScripts(url, mutableHeaders, CLICK_BOX_SCRIPT, INTERCEPTOR_SCRIPT)
                    } else if (mutableHeaders["Accept"].orEmpty().contains("text/html")) {
                        // Injects the XMLHttpRequest hack
                        return injectScripts(url, mutableHeaders, INTERCEPTOR_SCRIPT)
                    }

                    return super.shouldInterceptRequest(view, request)
                }
            }

            webview.loadUrl(targetUrl)
        }

        latch.await(20, TimeUnit.SECONDS)

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }

        return Pair(token, androidjsi.token)
    }

    private fun Headers.toWebViewHeaders() = toMultimap()
        .mapValues { it.value.getOrNull(0) ?: "" }
        .toMutableMap()
        .apply {
            remove("cross-origin-embedder-policy")
            remove("content-security-policy")
            remove("report-to")
        }

    private fun injectScripts(
        url: String,
        reqHeaders: Map<String, String>,
        vararg scripts: String,
    ): WebResourceResponse {
        val headers = Headers.Builder().apply {
            reqHeaders.entries.forEach { (key, value) -> add(key, value) }
        }.build()
        val res = client.newCall(GET(url, headers)).execute()
        val newHeaders = res.headers.toWebViewHeaders()
        val body = res.body.string()
        val newBody = if (res.isSuccessful) {
            body.substringBeforeLast("</body>") + scripts.joinToString("\n") + "</body></html>"
        } else {
            body
        }
        return WebResourceResponse(
            "text/html", // mimeType
            "utf-8", // encoding
            res.code, // status code
            res.message.ifEmpty { "ok" }, // reason phrase
            newHeaders, // response headers
            ByteArrayInputStream(newBody.toByteArray()), // data
        )
    }
}

private const val INTERCEPTOR_SCRIPT = """
<script type="text/javascript">
const originalOpen = window.XMLHttpRequest.prototype.open
window.XMLHttpRequest.prototype.open = function(_unused_method, url, _unused_arg) {
    if (url.includes('/api2/userverify')) {
        originalOpen.apply(this, arguments) // call the original open method
        this.onreadystatechange = function() {
            if (this.readyState === 4 && this.status === 200) {
                const responseBody = this.responseText
                window.androidjsi.sendResponse(responseBody)
            }
        }
    } else {
        originalOpen.apply(this, arguments)
    }

}
</script>
"""

private const val CLICK_BOX_SCRIPT = """
<script type="text/javascript">
setInterval(async () => {
    const items = document.querySelectorAll("#recaptcha-anchor, .recaptcha-checkbox, #rc-anchor-container span[role=checkbox]")
    items.forEach(x => {try { x.click() } catch (e) {} })
}, 500)
</script>"""