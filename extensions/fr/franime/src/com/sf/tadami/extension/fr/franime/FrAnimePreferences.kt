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
        /** Requests use the app's user agent, the one its WebView solves challenges with. */
        const val DEFAULT_USER_AGENT = "default"
        /** The default of versions before 2, stored by their fresh installs. */
        private const val LEGACY_DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 OPR/120.0.0.0"
        val BASE_URL = stringPreferencesKey("base_url")
        val USER_AGENT = stringPreferencesKey("user_agent")
        /** Keys are the lowercased `server` of the stream sources built by the extractors. */
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "sibnet" to "Sibnet",
            "sendvid" to "Sendvid",
            "vidmoly" to "Vidmoly",
            "filemoon" to "Filemoon",
            "uqload" to "Uqload",
            "vk" to "Vk",
            "lpayer" to "LPayer",
            "smoothpre" to "SmoothPre",
            "vidhide" to "VidHide",
            "yourupload" to "YourUpload",
        )
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): FrAnimePreferences {
            return FrAnimePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                userAgent = preferences[USER_AGENT]
                    ?.takeUnless { it.isBlank() || it == LEGACY_DEFAULT_USER_AGENT }
                    ?: DEFAULT_USER_AGENT,
                playerStreamsOrder = normalizedStreamsOrder(preferences[PLAYER_STREAMS_ORDER]),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
                tooltipAuto = preferences[CommonKeys.SHOULD_SHOW_EPISODE_TOOLTIP] ?: true
            )
        }

        /** Keeps the user's order of the known servers and appends the ones added since. */
        private fun normalizedStreamsOrder(stored: String?): String {
            val known = DEFAULT_PLAYER_STREAMS_ORDER.keys
            val order = stored.orEmpty().split(",").filter { it in known }.distinct()
            return (order + known.filterNot { it in order }).joinToString(separator = ",")
        }

        /** The user agent the user set for this source, or null to keep the app's one. */
        fun userAgentOverride(userAgent: String): String? {
            return userAgent.trim().takeUnless { it.isEmpty() || it.equals(DEFAULT_USER_AGENT, ignoreCase = true) }
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