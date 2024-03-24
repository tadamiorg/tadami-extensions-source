package com.sf.tadami.extension.fr.animesama

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class AnimeSamaPreferences(
    val baseUrl: String,
    val playerStreamsOrder : String,
    val lastVersionCode : Int
) : CustomPreferencesIdentifier {

    companion object : CustomPreferences<AnimeSamaPreferences> {
        const val DEFAULT_BASE_URL = "https://anime-sama.fr"
        val BASE_URL = stringPreferencesKey("base_url")
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            /*"animesama" to "AnimeSama",*/
            "sibnet" to "Sibnet",
            "vidmoly" to "VidMoly",
            "sendvid" to "Sendvid",
            "vk" to "Vk",
            "yourupload" to "YourUpload"
        )
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): AnimeSamaPreferences {
            return AnimeSamaPreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                playerStreamsOrder =  preferences[PLAYER_STREAMS_ORDER] ?: DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = "," ),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
            )
        }

        override fun setPrefs(newValue: AnimeSamaPreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
        }
    }
}