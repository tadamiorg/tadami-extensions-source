package com.sf.tadami.extension.fr.ianimes

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getIAnimesPreferencesContent(
    i18n: i18n
): SourcesPreferencesContent {
    val defaultOrder = IAnimesPreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ",")

    return SourcesPreferencesContent(
        title = "IAnimes",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = IAnimesPreferences.DEFAULT_BASE_URL,
                        key = IAnimesPreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = IAnimesPreferences.DEFAULT_BASE_URL,
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
                        value = defaultOrder,
                        key = IAnimesPreferences.PLAYER_STREAMS_ORDER,
                        items = IAnimesPreferences.DEFAULT_PLAYER_STREAMS_ORDER,
                        title = i18n.getString("sources_preferences_players_order"),
                        subtitle = i18n.getString("sources_preferences_players_order_subtitle"),
                        defaultValue = defaultOrder,
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}
