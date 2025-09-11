package com.sf.tadami.extension.fr.franime.interceptors

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Headers
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebViewResolver(private val globalHeaders: Headers) {
    private val context: Application by injectLazy()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val tag by lazy { javaClass.simpleName }

    class JsInterface(private val latch: CountDownLatch) {
        var result: String = ""

        @JavascriptInterface
        fun setResponse(response: String) {
            result = response
            latch.countDown()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getPlayerUrl(url: String): String {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        val jsi = JsInterface(latch)
        var hasFinished = false

        handler.post {
            val webview = WebView(context)
            webView = webview
            with(webview.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = false
                loadWithOverviewMode = false
                userAgentString = globalHeaders["User-Agent"]
            }

            webview.addJavascriptInterface(jsi, "jsinterface")

            webview.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false // Allow the WebView to handle the redirect
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    if (!hasFinished) {
                        hasFinished = true

                        // Wait a bit longer to ensure all JavaScript and redirects are complete
                        handler.postDelayed({
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    // Wait for document to be fully loaded
                                    if (document.readyState === 'complete') {
                                        return document.querySelector('iframe').src;
                                    } else {
                                        return new Promise(function(resolve) {
                                            window.addEventListener('load', function() {
                                                setTimeout(function() {
                                                    resolve(document.querySelector('iframe').src);
                                                }, 1000);
                                            });
                                        });
                                    }
                                })();
                                """.trimIndent()
                            ) { currentUrl ->
                                val cleanUrl = currentUrl?.let {
                                    if (it.startsWith("\"") && it.endsWith("\"")) {
                                        it.substring(1, it.length - 1)
                                    } else it
                                }
                                jsi.setResponse(cleanUrl ?: "")
                            }
                        }, 2000) // Wait 2 seconds after page finished to ensure all redirects are complete
                    }
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.d(tag, "WebView error: $errorCode - $description for URL: $failingUrl")
                    super.onReceivedError(view, errorCode, description, failingUrl)
                }
            }

            // Create headers map for loadUrl
            val headers = mutableMapOf<String, String>()
            globalHeaders.forEach { header ->
                headers[header.first] = header.second
            }

            webView?.loadUrl(url, headers)
        }

        // Wait for the result with a longer timeout to account for redirects
        val success = latch.await(TIMEOUT_SEC, TimeUnit.SECONDS)

        if (!success) {
            Log.d(tag, "Timeout waiting for page to load completely")
        }

        handler.post {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }

        return jsi.result
    }

    companion object {
        const val TIMEOUT_SEC: Long = 30 // Increased timeout for redirects
    }
}