package com.sf.tadami.extension.fr.animesama

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getAnimeSamaPreferencesContent(
    i18n: i18n
) : SourcesPreferencesContent {

    return SourcesPreferencesContent(
        title = "AnimeSama",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = AnimeSamaPreferences.DEFAULT_BASE_URL,
                        key = AnimeSamaPreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = AnimeSamaPreferences.DEFAULT_BASE_URL,
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
                        value = AnimeSamaPreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        key = AnimeSamaPreferences.PLAYER_STREAMS_ORDER,
                        items = AnimeSamaPreferences.DEFAULT_PLAYER_STREAMS_ORDER,
                        title = i18n.getString("sources_preferences_players_order"),
                        subtitle = i18n.getString("sources_preferences_players_order_subtitle"),
                        defaultValue = AnimeSamaPreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}