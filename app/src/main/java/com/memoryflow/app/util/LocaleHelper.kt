package com.memoryflow.app.util

import android.content.Context
import android.content.res.Configuration
import com.memoryflow.app.data.SettingsStore
import java.util.Locale

/**
 * Applies the user's chosen interface language to any context (activity or notification builder).
 *
 * The `-u-nu-latn` Unicode extension pins the numbering system to Latin digits, so counters render
 * as 0 1 2 3 … even inside an Arabic interface. Without it, Java/Android would format `%d` with
 * Arabic-Indic digits (٠١٢٣).
 */
object LocaleHelper {

    private const val LATIN_DIGITS = "-u-nu-latn"

    fun wrap(base: Context, language: String): Context {
        val locale = appLocale(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Builds the app locale, keeping the language but forcing Western digits. */
    fun appLocale(language: String): Locale {
        val tag = if (language.contains("-u-")) language else language + LATIN_DIGITS
        return Locale.forLanguageTag(tag)
    }

    fun appContext(base: Context): Context = wrap(base, SettingsStore(base).language)
}
