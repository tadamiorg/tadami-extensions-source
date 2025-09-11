package com.sf.tadami.extension.fr.franime

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.multiexts.dooplay.DooPlayPreferences
import com.sf.tadami.preferences.CommonKeys
import com.sf.tadami.preferences.model.CustomPreferences

data class FrAnimePreferences(
    override val baseUrl: String,
    val playerStreamsOrder : String,
    val userAgent: String,
    val lastVersionCode : Int,
    val tooltipAuto: Boolean
) : DooPlayPreferences {

    companion object : CustomPreferences<FrAnimePreferences> {
        const val DEFAULT_BASE_URL = "https://franime.fr"
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 OPR/120.0.0.0"
        val BASE_URL = stringPreferencesKey("base_url")
        val USER_AGENT = stringPreferencesKey("user_agent")
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "sibnet" to "Sibnet",
            "sendvid" to "Sendvid",
            "hdvid" to "Hdvid"
        )
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): FrAnimePreferences {
            return FrAnimePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                userAgent = preferences[USER_AGENT] ?: DEFAULT_USER_AGENT,
                playerStreamsOrder =  preferences[PLAYER_STREAMS_ORDER] ?: DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = "," ),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
                tooltipAuto = preferences[CommonKeys.SHOULD_SHOW_EPISODE_TOOLTIP] ?: true
            )
        }

        override fun setPrefs(newValue: FrAnimePreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
            preferences[USER_AGENT] = newValue.userAgent
            preferences[CommonKeys.SHOULD_SHOW_EPISODE_TOOLTIP] = newValue.tooltipAuto
        }
    }
}