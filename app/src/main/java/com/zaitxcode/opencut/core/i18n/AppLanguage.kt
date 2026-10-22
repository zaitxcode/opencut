package com.zaitxcode.opencut.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * The languages the interface is available in.
 *
 * The choice is a local preference: nothing is sent anywhere and the app keeps
 * working offline in either language.
 */
enum class AppLanguage(val tag: String?) {
    /** Follow the language of the device. */
    SYSTEM(null),

    /** English. */
    ENGLISH("en"),

    /** Arabic, with a right to left layout. */
    ARABIC("ar"),
    ;

    companion object {
        private const val PREFS = "opencut_settings"
        private const val KEY_LANGUAGE = "language"

        /** The language the user picked, or [SYSTEM]. */
        fun current(context: Context): AppLanguage {
            val stored = preferences(context).getString(KEY_LANGUAGE, null) ?: return SYSTEM
            return entries.firstOrNull { it.tag == stored } ?: SYSTEM
        }

        /** Stores the language and returns the locale that has to be applied. */
        fun set(context: Context, language: AppLanguage): Locale {
            preferences(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
            return localeOf(context, language)
        }

        /** The locale the app has to render with. */
        fun localeOf(context: Context, language: AppLanguage = current(context)): Locale {
            val tag = language.tag ?: return Locale.getDefault()
            val locale = Locale.forLanguageTag(tag)
            // Arabic needs the generic locale so that the fonts and the numerals
            // follow the region of the device.
            return if (language == AppLanguage.ARABIC && locale.country.isEmpty()) {
                Locale.forLanguageTag("$tag-${Locale.getDefault().country.ifEmpty { "EG" }}")
            } else {
                locale
            }
        }

        private fun preferences(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

/**
 * Applies the chosen language to a context.
 *
 * The resources of the base context are re-created with the locale of the user,
 * which makes the string resources and the layout direction follow the choice on
 * every Android version.
 */
object LocaleContext {

    /** Returns a context whose resources follow the chosen language. */
    fun wrap(base: Context): Context {
        val language = AppLanguage.current(base)
        val locale = AppLanguage.localeOf(base, language)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(android.os.LocaleList.forLanguageTags(locale.toLanguageTag()))
        return base.createConfigurationContext(configuration)
    }
}

/** Tells the system about the per app language on the versions that support it. */
fun publishSystemLocale(context: Context, language: AppLanguage) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val manager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
    runCatching {
        manager.applicationLocales = if (language.tag == null) {
            android.os.LocaleList.getEmptyLocaleList()
        } else {
            android.os.LocaleList.forLanguageTags(AppLanguage.localeOf(context, language).toLanguageTag())
        }
    }
}
