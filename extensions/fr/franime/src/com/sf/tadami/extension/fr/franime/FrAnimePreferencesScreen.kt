package com.sf.tadami.extension.fr.franime

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getFrAnimePreferencesContent(
    i18n: i18n
) : SourcesPreferencesContent {

    return SourcesPreferencesContent(
        title = "FrAnime",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = FrAnimePreferences.DEFAULT_BASE_URL,
                        key = FrAnimePreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = FrAnimePreferences.DEFAULT_BASE_URL,
                        onValueChanged = {
                            true
                        }
                    ),
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = FrAnimePreferences.DEFAULT_USER_AGENT,
                        key = FrAnimePreferences.USER_AGENT,
                        title = i18n.getString("sources_preferences_user_agent"),
                        subtitle = i18n.getString("sources_preferences_user_agent_subtitle"),
                        defaultValue = FrAnimePreferences.DEFAULT_USER_AGENT,
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
                        value = FrAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        key = FrAnimePreferences.PLAYER_STREAMS_ORDER,
                        items = FrAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER,
                        title = i18n.getString("sources_preferences_players_order"),
                        subtitle = i18n.getString("sources_preferences_players_order_subtitle"),
                        defaultValue = FrAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}