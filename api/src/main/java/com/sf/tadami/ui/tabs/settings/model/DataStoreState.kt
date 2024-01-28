package com.sf.tadami.ui.tabs.settings.model

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun <T> rememberDataStoreState(
    customPrefs: CustomPreferences<T>,
    customDataStore : DataStore<Preferences>? = null
): DataStoreState<T> {
    throw Exception("stub")
}

class DataStoreState<T>(
    private val dataStore: DataStore<Preferences>,
    private val customPrefs: CustomPreferences<T>,
) {

    private val _value : MutableStateFlow<T> = throw Exception("stub")
    val value: StateFlow<T> = throw Exception("stub")

    fun setValue(newValue: T) {
        throw Exception("stub")
    }

    init {

    }
}