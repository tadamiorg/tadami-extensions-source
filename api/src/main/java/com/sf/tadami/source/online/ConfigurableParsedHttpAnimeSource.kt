package com.sf.tadami.source.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.NavHostController
import com.sf.tadami.ui.tabs.settings.components.PreferenceScreen
import com.sf.tadami.ui.tabs.settings.model.CustomPreferences
import com.sf.tadami.ui.tabs.settings.model.CustomPreferencesIdentifier

abstract class ConfigurableParsedHttpAnimeSource<T : CustomPreferencesIdentifier>(prefGroup: CustomPreferences<T>) :
    ParsedAnimeHttpSource() {

    // Preferences
    private val PREFERENCES_FILE_NAME : String = throw Exception("Stub!")

    val dataStore: DataStore<Preferences> = throw Exception("Stub!")

    val preferences : T = throw Exception("Stub")

    abstract fun getPreferenceScreen(navController: NavHostController): PreferenceScreen
}