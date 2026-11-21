package com.memoryflow.app

import com.memoryflow.app.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The UI must always show Western digits (0 1 2 3), even in the Arabic interface.
 * The app locale keeps the language but pins the numbering system to `latn`.
 */
class LocaleDigitsTest {

    @Test
    fun `arabic keeps its language and pins latin digits`() {
        val locale = LocaleHelper.appLocale("ar")
        assertEquals("ar", locale.language)
        assertEquals("latn", locale.getUnicodeLocaleType("nu"))
        assertEquals("523", String.format(locale, "%d", 523))
        assertEquals("500", String.format(locale, "%d", 500))
        assertEquals("25", String.format(locale, "%d", 25))
    }

    @Test
    fun `english and german are unchanged apart from the numbering tag`() {
        assertEquals("en", LocaleHelper.appLocale("en").language)
        assertEquals("de", LocaleHelper.appLocale("de").language)
        assertEquals("latn", LocaleHelper.appLocale("de").getUnicodeLocaleType("nu"))
    }
}
