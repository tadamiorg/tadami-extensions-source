package com.sf.tadami.extension.fr.vostfree

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class VostFreePreferences(
    val baseUrl: String,
    val playerStreamsOrder : String,
    val lastVersionCode : Int
) : CustomPreferencesIdentifier {

    companion object : CustomPreferences<VostFreePreferences> {
        const val DEFAULT_BASE_URL = "https://vostfree.in"
        val BASE_URL = stringPreferencesKey("base_url")

        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "voe" to "Voe",
            "sibnet" to "Sibnet",
            "doodstream" to "Doodstream",
            "okru" to "Okru",
            "vudeo" to "Vudeo",
            "uqload" to "Uqload",

        )
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): VostFreePreferences {
            return VostFreePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                playerStreamsOrder =  preferences[PLAYER_STREAMS_ORDER] ?: DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString (separator = "," ),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
            )
        }

        override fun setPrefs(newValue: VostFreePreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
        }
    }
}