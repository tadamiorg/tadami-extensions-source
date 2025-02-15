package com.sf.tadami.extension.en.animepahe

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class AnimePahePreferences(
    val baseUrl: String,
    val playerStreamsOrder : String,
    val lastVersionCode : Int,
    val useHlsLinks: Boolean,
    val englishDub: Boolean
) : CustomPreferencesIdentifier {

    companion object : CustomPreferences<AnimePahePreferences> {
        const val DEFAULT_BASE_URL = "https://animepahe.com"
        const val DEFAULT_USE_HLS_LINKS = true
        const val DEFAULT_ENGLISH_DUB = true
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "kwik" to "Kwik"
        )
        val BASE_URL = stringPreferencesKey("base_url")
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))
        val USE_HLS_LINKS = booleanPreferencesKey("use_hls_links")
        val ENG_DUB = booleanPreferencesKey("english_dub")

        override fun transform(preferences: Preferences): AnimePahePreferences {
            return AnimePahePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                playerStreamsOrder =  preferences[PLAYER_STREAMS_ORDER] ?: DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString (separator = "," ),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
                useHlsLinks = preferences[USE_HLS_LINKS] ?: DEFAULT_USE_HLS_LINKS,
                englishDub = preferences[ENG_DUB] ?: DEFAULT_ENGLISH_DUB
            )
        }

        override fun setPrefs(newValue: AnimePahePreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
            preferences[USE_HLS_LINKS] = newValue.useHlsLinks
            preferences[ENG_DUB] = newValue.englishDub
        }
    }
}