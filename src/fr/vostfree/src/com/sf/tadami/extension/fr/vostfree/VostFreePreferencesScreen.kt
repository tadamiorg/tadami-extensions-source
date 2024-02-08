package com.sf.tadami.extension.fr.vostfree

import com.sf.tadami.lib.i18n.i18n
import com.sf.tadami.preferences.model.SourcePreference
import com.sf.tadami.ui.tabs.browse.tabs.sources.preferences.SourcesPreferencesContent

fun getVostFreePreferencesContent(
    i18n: i18n
) : SourcesPreferencesContent {

    return SourcesPreferencesContent(
        title = "VostFree",
        preferences = listOf(
            SourcePreference.PreferenceCategory(
                title = i18n.getString("category_network"),
                preferenceItems = listOf(
                    SourcePreference.PreferenceItem.EditTextPreference(
                        value = VostFreePreferences.DEFAULT_BASE_URL,
                        key = VostFreePreferences.BASE_URL,
                        title = i18n.getString("sources_preferences_base_url"),
                        subtitle = i18n.getString("sources_preferences_base_url_subtitle"),
                        defaultValue = VostFreePreferences.DEFAULT_BASE_URL,
                        onValueChanged = {
                            true
                        }
                    ),
                )
            ),
        )
    )
}