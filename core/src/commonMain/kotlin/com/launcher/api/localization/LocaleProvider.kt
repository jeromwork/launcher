package com.launcher.api.localization

/** Returns BCP-47 locale tag (e.g. "en", "ru", "kk-Latn", "ar-SA"). FR-028. */
interface LocaleProvider {
    fun currentLocaleTag(): String
}
