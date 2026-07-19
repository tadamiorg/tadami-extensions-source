package com.sf.tadami.extension.en.kickassanime

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class KickAssAnimePreferences(
    val baseUrl: String,
    val useEnglishTitles: Boolean,
    val serverOrder: String,
    val audioOrder: String,
    val lastVersionCode: Int
) : CustomPreferencesIdentifier {

    companion object : CustomPreferences<KickAssAnimePreferences> {
        const val DEFAULT_BASE_URL = "https://kaa.lt"
        val BASE_URL = stringPreferencesKey("base_url")

        val USE_ENGLISH_TITLES = booleanPreferencesKey("use_english_titles")

        val DEFAULT_SERVER_ORDER = mapOf(
            "VidStreaming" to "VidStreaming",
            "BirdStream" to "BirdStream",
            "DuckStream" to "DuckStream",
            "CatStream" to "CatStream",
        )
        val SERVER_ORDER = stringPreferencesKey("server_order")

        val DEFAULT_AUDIO_ORDER = mapOf(
            "Japanese" to "Japanese",
            "English" to "English",
            "French" to "French",
        )
        val AUDIO_ORDER = stringPreferencesKey("audio_order")

        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): KickAssAnimePreferences {
            return KickAssAnimePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                useEnglishTitles = preferences[USE_ENGLISH_TITLES] ?: false,
                serverOrder = preferences[SERVER_ORDER] ?: DEFAULT_SERVER_ORDER.keys.joinToString(","),
                audioOrder = preferences[AUDIO_ORDER] ?: DEFAULT_AUDIO_ORDER.keys.joinToString(","),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0
            )
        }

        override fun setPrefs(newValue: KickAssAnimePreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[USE_ENGLISH_TITLES] = newValue.useEnglishTitles
            preferences[SERVER_ORDER] = newValue.serverOrder
            preferences[AUDIO_ORDER] = newValue.audioOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
        }
    }
}
