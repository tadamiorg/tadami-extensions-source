package com.sf.tadami.multiexts.zoro

import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

interface ZoroPreferences : CustomPreferencesIdentifier {
    val baseUrl: String
    val videoTypeSelection: Set<String>
}