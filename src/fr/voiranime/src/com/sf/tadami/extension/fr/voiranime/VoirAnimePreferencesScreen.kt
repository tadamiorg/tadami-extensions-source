package com.sf.tadami.extension.fr.voiranime

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getVoirAnimePreferencesContent(
    i18n: i18n
) : SourcesPreferencesContent {

    return SourcesPreferencesContent(
        title = "AnimeSama",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = VoirAnimePreferences.DEFAULT_BASE_URL,
                        key = VoirAnimePreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = VoirAnimePreferences.DEFAULT_BASE_URL,
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
                        value = VoirAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        key = VoirAnimePreferences.PLAYER_STREAMS_ORDER,
                        items = VoirAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER,
                        title = i18n.getString("sources_preferences_players_order"),
                        subtitle = i18n.getString("sources_preferences_players_order_subtitle"),
                        defaultValue = VoirAnimePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}