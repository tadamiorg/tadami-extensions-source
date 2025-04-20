package com.sf.tadami.extension.en.gogoanime

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getGogoAnimePreferencesContent(
    i18n: i18n
) : SourcesPreferencesContent {

    return SourcesPreferencesContent(
        title = "GogoAnime",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = GogoAnimePreferences.DEFAULT_BASE_URL,
                        key = GogoAnimePreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = GogoAnimePreferences.DEFAULT_BASE_URL,
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
                        value = GogoAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        key = GogoAnimePreferences.PLAYER_STREAMS_ORDER,
                        items = GogoAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER,
                        title = i18n.getString("sources_preferences_players_order"),
                        subtitle = i18n.getString("sources_preferences_players_order_subtitle"),
                        defaultValue = GogoAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}
