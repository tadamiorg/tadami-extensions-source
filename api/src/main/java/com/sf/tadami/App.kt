package com.sf.tadami

import android.app.Application
import android.content.Context

open class App : Application() {

    companion object {
        private var appContext: Context? = null
        fun getAppContext(): Context? {
            throw Exception("Stub")
        }

        fun getLocale() : String?{
            throw Exception("Stub!")
        }
    }
}