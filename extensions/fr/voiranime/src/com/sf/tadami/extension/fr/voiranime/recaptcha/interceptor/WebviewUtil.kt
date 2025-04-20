package com.sf.tadami.extension.fr.voiranime.recaptcha.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewUtil {

    const val MINIMUM_WEBVIEW_VERSION = 114

    fun supportsWebView(context: Context): Boolean {
        try {
            // May throw android.webkit.WebViewFactory$MissingWebViewPackageException if WebView
            // is not installed
            CookieManager.getInstance()
        } catch (e: Throwable) {
            Log.e("WebViewUtil",e.stackTraceToString())
            return false
        }

        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    }
}

fun WebView.isOutdated(): Boolean {
    return getWebViewMajorVersion() < WebViewUtil.MINIMUM_WEBVIEW_VERSION
}
private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
}

private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString

    // Next call to getUserAgentString() will get us the default
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString

    // Revert to original UA string
    settings.userAgentString = originalUA

    return defaultUserAgentString
}


@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = false

        // Allow zooming
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
}