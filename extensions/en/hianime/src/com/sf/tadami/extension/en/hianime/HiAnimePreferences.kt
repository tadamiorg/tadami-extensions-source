package com.sf.tadami.extension.en.hianime

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sf.tadami.multiexts.zoro.ZoroPreferences
import com.sf.tadami.preferences.model.CustomPreferences


data class HiAnimePreferences(
    override val baseUrl: String,
    override val videoTypeSelection: Set<String>,
    val playerStreamsOrder : String,
    val lastVersionCode : Int,
) : ZoroPreferences {

    companion object : CustomPreferences<HiAnimePreferences> {
        const val DEFAULT_BASE_URL = "https://hianime.to"
        val DEFAULT_PLAYER_STREAMS_ORDER = mapOf(
            "hd-1" to "HD-1",
            "hd-2" to "HD-2",
            "sreamtape" to "StreamTape"
        )
        val DEFAULT_VIDEO_TYPES = mapOf(
            "servers-sub" to "Sub", "servers-dub" to "Dub", "servers-mixed" to "Mixed", "servers-raw" to "Raw"
        )
        val BASE_URL = stringPreferencesKey("base_url")
        val PLAYER_STREAMS_ORDER = stringPreferencesKey("player_streams_order")
        val LAST_VERSION_CODE = intPreferencesKey(CustomPreferences.appStateKey("last_version_code"))
        val VIDEO_TYPES_SELECTION = stringSetPreferencesKey("video_types_selection")

        override fun transform(preferences: Preferences): HiAnimePreferences {
            return HiAnimePreferences(
                baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
                playerStreamsOrder =  preferences[PLAYER_STREAMS_ORDER] ?: DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString (separator = "," ),
                lastVersionCode = preferences[LAST_VERSION_CODE] ?: 0,
                videoTypeSelection = preferences[VIDEO_TYPES_SELECTION] ?: DEFAULT_VIDEO_TYPES.keys
            )
        }

        override fun setPrefs(newValue: HiAnimePreferences, preferences: MutablePreferences) {
            preferences[BASE_URL] = newValue.baseUrl
            preferences[PLAYER_STREAMS_ORDER] = newValue.playerStreamsOrder
            preferences[LAST_VERSION_CODE] = newValue.lastVersionCode
            preferences[VIDEO_TYPES_SELECTION] = newValue.videoTypeSelection
        }
    }
}