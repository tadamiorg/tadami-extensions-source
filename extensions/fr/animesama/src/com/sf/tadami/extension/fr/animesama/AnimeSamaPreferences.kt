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
        /** Keys are the lowercased `server` of the stream sources built by the extractors. */
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "ansembed" to "AnsEmbed",
            "voe" to "Voe",
            "uqload" to "Uqload",
            "vidhide" to "VidHide",
            "vk" to "Vk",
            "vidmoly" to "VidMoly",
            "lpayer" to "LPayer",
            "sendvid" to "Sendvid",
            "yourupload" to "YourUpload",
            // 403 everywhere since October 2026, kept in case it comes back
            "sibnet" to "Sibnet",
        )
        /** Servers merged into another one (v31: one VidHide network), which takes the place of the first of them. */
        private val MERGED_PLAYERS = mapOf(
            "callistanise" to "vidhide",
            "smoothpre" to "vidhide",
        )
        /** The defaults of earlier versions, also as their migrations completed them: such an order was never changed. */
        val LEGACY_DEFAULT_PLAYER_STREAMS_ORDERS = setOf(
            "sibnet,vidmoly,lpayer,callistanise,sendvid,vidhide,smoothpre,vk,oneupload,yourupload,uqload,ansembed",
            "sibnet,vidmoly,lpayer,callistanise,sendvid,vidhide,smoothpre,vk,oneupload,yourupload",
            "sibnet,vidmoly,vidhide,smoothpre,sendvid,vk,oneupload,yourupload,lpayer,callistanise,uqload,ansembed",
            "sibnet,vidmoly,smoothpre,sendvid,vk,oneupload,yourupload,lpayer,callistanise,uqload,ansembed",
            "sibnet,vidmoly,sendvid,vk,oneupload,yourupload,lpayer,callistanise,uqload,ansembed",
        )
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE =
            intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): AnimeSamaPreferences {
            return AnimeSamaPreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                playerStreamsOrder = normalizedStreamsOrder(preferences[PLAYER_STREAMS_ORDER]),
                userAgent = preferences[USER_AGENT] ?: DEFAULT_USER_AGENT,
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
            )
        }

        /**
         * Keeps the user's order of the known servers: merged ones become their new server, removed ones
         * (oneupload) are dropped, the ones added since (voe) are appended.
         */
        private fun normalizedStreamsOrder(stored: String?): String {
            val known = DEFAULT_PLAYER_STREAMS_ORDER.keys
            val order = stored.orEmpty().split(",")
                .map { MERGED_PLAYERS[it] ?: it }
                .filter { it in known }
                .distinct()
            return (order + known.filterNot { it in order }).joinToString(separator = ",")
        }

        override fun setPrefs(newValue: AnimeSamaPreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[USER_AGENT] = newValue.userAgent
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
        }
    }
}