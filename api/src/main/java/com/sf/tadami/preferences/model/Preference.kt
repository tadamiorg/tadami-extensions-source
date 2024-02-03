package com.sf.tadami.preferences.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Preference {
    abstract val title: String
    abstract val enabled : Boolean

    sealed class PreferenceItem<T> : Preference() {
        abstract val subtitle : String?
        abstract val icon : ImageVector?
        abstract val onValueChanged : (newValue : T) -> Boolean

        data class TogglePreference(
            var value : Boolean,
            override val title: String,
            override val subtitle: String? = throw Exception("Stub"),
            override val icon: ImageVector? = throw Exception("Stub"),
            override val enabled: Boolean = throw Exception("Stub"),
            override val onValueChanged: (newValue: Boolean) -> Boolean = throw Exception("Stub")
        ) : PreferenceItem<Boolean>()

        @Suppress("UNCHECKED_CAST")
        data class SelectPreference<S : Any>(
            var value : S,
            var items : Map<S,String>,
            override val title: String,
            override val subtitle: String? = throw Exception("Stub"),
            override val icon: ImageVector? = throw Exception("Stub"),
            override val enabled: Boolean = throw Exception("Stub"),
            override val onValueChanged: (newValue: S) -> Boolean = throw Exception("Stub"),
            val subtitleProvider : () -> String? = throw Exception("Stub")
        ) : PreferenceItem<S>(){
            fun castedOnValueChanged(newValue : Any) : Boolean = throw Exception("Stub")
        }

        data class MultiSelectPreference(
            var value : Set<String>,
            var items : Map<String,Pair<String,Boolean>>,
            override val title: String,
            override val subtitle: String? = throw Exception("Stub"),
            override val icon: ImageVector? = throw Exception("Stub"),
            override val enabled: Boolean = throw Exception("Stub"),
            val overrideOkButton : Boolean = throw Exception("Stub"),
            val subtitleProvider : @Composable () -> String? = throw Exception("Stub"),
            override val onValueChanged: (newValue: Set<String>) -> Boolean = throw Exception("Stub")
        ) : PreferenceItem<Set<String>>()

        data class MultiSelectPreferenceInt(
            var value : Set<Int>,
            var items : Map<Int,Pair<String,Boolean>>,
            override val title: String,
            override val subtitle: String? = throw Exception("Stub"),
            override val icon: ImageVector? = throw Exception("Stub"),
            override val enabled: Boolean = throw Exception("Stub"),
            val overrideOkButton : Boolean = throw Exception("Stub"),
            val subtitleProvider : @Composable () -> String? = throw Exception("Stub"),
            override val onValueChanged: (newValue: Set<Int>) -> Boolean = throw Exception("Stub")
        ) : PreferenceItem<Set<Int>>()

        data class TextPreference(
            override val title: String,
            override val subtitle: String? = throw Exception("Stub"),
            override val icon: ImageVector? = throw Exception("Stub"),
            override val onValueChanged: (newValue: String) -> Boolean = throw Exception("Stub"),
            override val enabled: Boolean = throw Exception("Stub"),
            val onClick : (() -> Unit)? = throw Exception("Stub")
        ) : PreferenceItem<String>()

        data class EditTextPreference(
            val value: String,
            val defaultValue : String? = throw Exception("Stub"),
            override val title: String,
            override val subtitle: String? = throw Exception("Stub"),
            override val icon: ImageVector? = throw Exception("Stub"),
            override val enabled: Boolean = throw Exception("Stub"),
            override val onValueChanged: (newValue: String) -> Boolean = throw Exception("Stub"),
        ) : PreferenceItem<String>()

        data class CustomPreference(
            override val title: String,
            val content: @Composable (PreferenceItem<String>) -> Unit,
        ) : PreferenceItem<String>() {
            override val enabled: Boolean = throw Exception("Stub")
            override val subtitle: String? = throw Exception("Stub")
            override val icon: ImageVector? = throw Exception("Stub")
            override val onValueChanged: (newValue: String) -> Boolean = throw Exception("Stub")
        }
    }

    data class PreferenceCategory(
        override val title: String,
        override val enabled: Boolean = throw Exception("Stub"),
        val preferenceItems : List<PreferenceItem<*>> = throw Exception("Stub")
    ) : Preference()

}
