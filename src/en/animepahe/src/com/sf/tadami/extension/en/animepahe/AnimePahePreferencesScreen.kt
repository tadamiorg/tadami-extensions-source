package com.sf.tadami.extension.en.animepahe

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getAnimePahePreferencesContent(
    i18n: i18n
) : SourcesPreferencesContent {

    return SourcesPreferencesContent(
        title = "AnimePahe",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = AnimePahePreferences.DEFAULT_BASE_URL,
                        key = AnimePahePreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = AnimePahePreferences.DEFAULT_BASE_URL,
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
                        value = AnimePahePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        key = AnimePahePreferences.PLAYER_STREAMS_ORDER,
                        items = AnimePahePreferences.DEFAULT_PLAYER_STREAMS_ORDER,
                        title = i18n.getString("sources_preferences_players_order"),
                        subtitle = i18n.getString("sources_preferences_players_order_subtitle"),
                        defaultValue = AnimePahePreferences.DEFAULT_PLAYER_STREAMS_ORDER.keys.joinToString(separator = ","),
                        onValueChanged = {
                            true
                        }
                    ),
                    SourcePreference.PreferenceItem.TogglePreference(
                        value = AnimePahePreferences.DEFAULT_ENGLISH_DUB,
                        key = AnimePahePreferences.ENG_DUB,
                        title = i18n.getString("sources_preferences_english_dub"),
                        subtitle = i18n.getString("sources_preferences_english_dub_subtitle"),
                        defaultValue = AnimePahePreferences.DEFAULT_ENGLISH_DUB,
                        onValueChanged = {
                            true
                        }
                    ),
                    SourcePreference.PreferenceItem.TogglePreference(
                        value = AnimePahePreferences.DEFAULT_USE_HLS_LINKS,
                        key = AnimePahePreferences.USE_HLS_LINKS,
                        title = i18n.getString("sources_preferences_use_hls_links"),
                        subtitle = i18n.getString("sources_preferences_use_hls_links_subtitle"),
                        defaultValue = AnimePahePreferences.DEFAULT_USE_HLS_LINKS,
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}
