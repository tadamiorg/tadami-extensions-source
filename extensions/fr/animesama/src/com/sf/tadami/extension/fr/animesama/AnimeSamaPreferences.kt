package com.sf.tadami.extension.fr.animesama

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class AnimeSamaPreferences(
    val baseUrl: String,
    val playerStreamsOrder: String,
    val userAgent: String,
    val lastVersionCode: Int
) : CustomPreferencesIdentifier {

    companion object : CustomPreferences<AnimeSamaPreferences> {
        const val DEFAULT_BASE_URL = "https://anime-sama.to"
        val BASE_URL = stringPreferencesKey("base_url")
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.7827.116 Mobile Safari/537.36"
        val USER_AGENT = stringPreferencesKey("user_agent")
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "sibnet" to "Sibnet",
            "vidmoly" to "VidMoly",
            "lpayer" to "LPayer",
            "callistanise" to "Callistanise",
            "sendvid" to "Sendvid",
            "vidhide" to "VidHide",
            "smoothpre" to "SmoothPre",
            "vk" to "Vk",
            "oneupload" to "OneUpload",
            "yourupload" to "YourUpload",
            "uqload" to "Uqload",
            "ansembed" to "AnsEmbed",
        )
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE =
            intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): AnimeSamaPreferences {
            return AnimeSamaPreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                playerStreamsOrder = preferences[PLAYER_STREAMS_ORDER]
                    ?: DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                userAgent = preferences[USER_AGENT] ?: DEFAULT_USER_AGENT,
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
            )
        }

        override fun setPrefs(newValue: AnimeSamaPreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[USER_AGENT] = newValue.userAgent
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
        }
    }
}