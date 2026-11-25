package com.forensim.app.core.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * App languages. Stored by AppCompat's per-app locale support (Android 13+ also shows it under
 * Settings > Apps > Forensim > Language), so the choice survives restarts without our own key.
 */
enum class AppLanguage(val tag: String, val nativeName: String, val englishName: String) {
    ENGLISH("en", "English", "English"),
    FILIPINO("fil", "Filipino", "Filipino");

    companion object {
        /** The language currently in effect (an explicit app choice, else the phone's language). */
        fun current(): AppLanguage {
            val chosen = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
            return if (chosen.language == "fil" || chosen.language == "tl") FILIPINO else ENGLISH
        }

        /** Switches the app language; AppCompat recreates the activity with the new strings. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
