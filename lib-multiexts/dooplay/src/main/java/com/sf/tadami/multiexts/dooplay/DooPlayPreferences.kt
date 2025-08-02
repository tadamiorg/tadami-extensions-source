package com.sf.tadami.multiexts.dooplay

import com.sf.tadami.preferences.model.CustomPreferencesIdentifier

interface DooPlayPreferences : CustomPreferencesIdentifier {
    val baseUrl: String
}