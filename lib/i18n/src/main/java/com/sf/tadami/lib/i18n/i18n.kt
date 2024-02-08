package com.sf.tadami.lib.i18n

import java.util.Locale

typealias Translations = Map<String, Map<Language, String>>

class i18n(
    private val translations: Translations
) {
    private fun getLocale(): String {
        return Locale.getDefault().language
    }

    fun getString(stringKey: String): String {
        val translation = translations[stringKey] ?: return stringKey
        val language = getLanguage() ?: return translation.values.first()
        return translation[language] ?: translation.values.first()
    }

    private fun getLanguage(): Language? {
        val locale = getLocale()
        return valueOfOrNull(locale)
    }

    private fun valueOfOrNull(value: String?): Language? {
        return try {
            if (value == null) throw Exception("Null value")
            Language.valueOf(value)
        } catch (e: Exception) {
            null
        }
    }
}
