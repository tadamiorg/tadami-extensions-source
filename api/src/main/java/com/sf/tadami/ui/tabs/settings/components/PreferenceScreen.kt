package com.sf.tadami.ui.tabs.settings.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sf.tadami.ui.components.data.Action
import com.sf.tadami.ui.tabs.settings.model.Preference

interface PreferenceScreen {

    @get:StringRes
    val title: Int

    @Composable
    fun getTitle(): String {
        throw Exception("Stub")
    }

    @Composable
    fun getPreferences() : List<Preference>

    val backHandler: (() -> Unit)?

    val getCustomDataStore: (() -> DataStore<Preferences>)?
        get() = throw Exception("Stub")

    val topBarActions: List<Action>
        get() = throw Exception("Stub")


    @Composable
    fun Content() {
        throw Exception("Stub")
    }
}