package com.sf.tadami.network

import android.content.Context
import okhttp3.OkHttpClient

class NetworkHelper(private val context : Context) {

    val client: OkHttpClient = throw Exception("Stub!")

    val nonCloudflareClient: OkHttpClient = throw Exception("Stub!")

    val cloudflareClient: OkHttpClient = throw Exception("Stub!")
    companion object{
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.150 Safari/537.36 Edg/88.0.705.63"
    }
}