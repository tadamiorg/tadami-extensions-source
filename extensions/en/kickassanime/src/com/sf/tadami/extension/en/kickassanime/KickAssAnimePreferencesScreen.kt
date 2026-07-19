package com.sf.tadami.extension.en.kickassanime

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getKickAssAnimePreferencesContent(i18n: i18n): SourcesPreferencesContent {
    return SourcesPreferencesContent(
        title = "KickAssAnime",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = KickAssAnimePreferences.DEFAULT_BASE_URL,
                        key = KickAssAnimePreferences.BASE_URL,
                        title = i18n.getString("pref_base_url_title"),
                        subtitle = i18n.getString("pref_base_url_subtitle"),
                        defaultValue = KickAssAnimePreferences.DEFAULT_BASE_URL,
                        onValueChanged = { true }
                    )
                )
            ),
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_titles"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.TogglePreference(
                        value = false,
                        key = KickAssAnimePreferences.USE_ENGLISH_TITLES,
                        defaultValue = false,
                        title = i18n.getString("pref_use_english_title"),
                        subtitle = i18n.getString("pref_use_english_subtitle"),
                        onValueChanged = { true }
                    )
                )
            ),
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_player"),
                videoCategory = true,
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.ReorderStringPreference(
                        value = KickAssAnimePreferences.DEFAULT_SERVER_ORDER.keys.joinToString(","),
                        key = KickAssAnimePreferences.SERVER_ORDER,
                        items = KickAssAnimePreferences.DEFAULT_SERVER_ORDER,
                        title = i18n.getString("pref_server_order_title"),
                        subtitle = i18n.getString("pref_server_order_subtitle"),
                        defaultValue = KickAssAnimePreferences.DEFAULT_SERVER_ORDER.keys.joinToString(","),
                        onValueChanged = { true }
                    ),
                    SourcePreference.PreferenceItem.ReorderStringPreference(
                        value = KickAssAnimePreferences.DEFAULT_AUDIO_ORDER.keys.joinToString(","),
                        key = KickAssAnimePreferences.AUDIO_ORDER,
                        // Keys stay the English language tokens (matched against the stream's audio track
                        // names in KickAssAnime.sort()); only the displayed labels are localized.
                        items = mapOf(
                            "Japanese" to i18n.getString("audio_lang_japanese"),
                            "English" to i18n.getString("audio_lang_english"),
                            "French" to i18n.getString("audio_lang_french"),
                        ),
                        title = i18n.getString("pref_audio_order_title"),
                        subtitle = i18n.getString("pref_audio_order_subtitle"),
                        defaultValue = KickAssAnimePreferences.DEFAULT_AUDIO_ORDER.keys.joinToString(","),
                        onValueChanged = { true }
                    )
                )
            ),
        )
    )
}
