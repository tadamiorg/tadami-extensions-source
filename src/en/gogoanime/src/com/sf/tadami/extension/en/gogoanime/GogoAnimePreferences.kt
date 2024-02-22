package com.sf.tadami.extension.en.gogoanime

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class GogoAnimePreferences(
    val baseUrl: String,
    val playerStreamsOrder : String,
    val lastVersionCode : Int
) : CustomPreferencesIdentifier {

    companion object : CustomPreferences<GogoAnimePreferences> {
        const val DEFAULT_BASE_URL = "https://anitaku.to"
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "gogostream" to "GogoStream",
            "vidstreaming" to "VidStreaming",
            "doodstream" to "DoodStream",
            "streamwish" to "StreamWish",
            "mp4upload" to "Mp4Upload",
            "filelions" to "FileLions"
        )
        val BASE_URL = stringPreferencesKey("base_url")
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): GogoAnimePreferences {
            return GogoAnimePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                playerStreamsOrder =  preferences[PLAYER_STREAMS_ORDER] ?: DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString (separator = "," ),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
            )
        }

        override fun setPrefs(newValue: GogoAnimePreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
        }
    }
}