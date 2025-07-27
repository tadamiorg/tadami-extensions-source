package com.sf.tadami.extension.en.hianime

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getHiAnimePreferencesContent(
    i18n: i18n
) : SourcesPreferencesContent {

    return SourcesPreferencesContent(
        title = "HiAnime",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = HiAnimePreferences.DEFAULT_BASE_URL,
                        key = HiAnimePreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = HiAnimePreferences.DEFAULT_BASE_URL,
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_player"),
                videoCategory = true,
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.ReorderStringPreference(
                        value = HiAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        key = HiAnimePreferences.PLAYER_STREAMS_ORDER,
                        items = HiAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER,
                        title = i18n.getString("sources_preferences_players_order"),
                        subtitle = i18n.getString("sources_preferences_players_order_subtitle"),
                        defaultValue = HiAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        onValueChanged = {
                            true
                        }
                    ),
                    SourcePreference.PreferenceItem.MultiSelectPreference(
                        value = HiAnimePreferences.DEFAULT_VIDEO_TYPES.keys,
                        key = HiAnimePreferences.VIDEO_TYPES_SELECTION,
                        items = HiAnimePreferences.DEFAULT_VIDEO_TYPES.mapValues { (_,value)->
                            value to true
                        },
                        title = i18n.getString("sources_preferences_video_types_selection"),
                        defaultValue = HiAnimePreferences.DEFAULT_VIDEO_TYPES.keys,
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}


