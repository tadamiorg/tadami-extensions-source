package com.sf.tadami.extension.fr.ianimes

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sf.tadami.preferences.model.CustomPreferences
import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

data class IAnimesPreferences(
    val baseUrl: String,
    val playerStreamsOrder: String,
    val lastVersionCode: Int
) : CustomPreferencesIdentifier {

    companion object : CustomPreferences<IAnimesPreferences> {
        const val DEFAULT_BASE_URL = "https://www.ianimes.eu"
        val BASE_URL = stringPreferencesKey("base_url")

        /** Keys are the lowercased `server` of the stream sources built by the extractors. */
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "vidmoly" to "Vidmoly",
            "voe" to "Voe",
            "vidara" to "Vidara",
            "lulustream" to "LuluStream",
            "hgcloud" to "HgCloud",
            "odysee" to "Odysee",
            "streamtape" to "StreamTape",
            "mailru" to "MailRu",
            "vidsonic" to "Vidsonic",
            "doodstream" to "DoodStream",
        )
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))

        override fun transform(preferences: Preferences): IAnimesPreferences {
            return IAnimesPreferences(
                baseUrl = preferences[BASE_URL]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
                    ?: DEFAULT_BASE_URL,
                playerStreamsOrder = normalizedStreamsOrder(preferences[PLAYER_STREAMS_ORDER]),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
            )
        }

        /** Keeps the user's order of the known servers and appends the ones added since. */
        private fun normalizedStreamsOrder(stored: String?): String {
            val known = DEFAULT_PLAYER_STREAMS_ORDER.keys
            val order = stored.orEmpty().split(",").filter { it in known }.distinct()
            return (order + known.filterNot { it in order }).joinToString(separator = ",")
        }

        override fun setPrefs(newValue: IAnimesPreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
        }
    }
}
