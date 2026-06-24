package com.launcher.app.settings

import androidx.appcompat.app.AppCompatDelegate
import com.launcher.api.localization.LocaleProvider
import com.launcher.api.wizard.UserPreferencesStore

/**
 * Computes whether the app's per-app locale (managed via
 * [AppCompatDelegate.getApplicationLocales]) diverges from the system
 * default. Used by [LocaleDivergenceIndicator] in Settings.
 *
 * Per data-model.md §6.2 + FR-017a.
 *
 * The `localeProvider.currentLocaleTag()` is the effective in-app locale
 * (already overridden if a wizard answer is persisted). For the **system**
 * locale we go through `LocaleListCompat.getAdjustedDefault()` after
 * clearing the app override; cleaner path on API 33+ is
 * `LocaleManager.getSystemLocales()`, but AppCompat shim covers both.
 */
class LocaleDivergenceViewModel(
    private val localeProvider: LocaleProvider,
    private val userPreferencesStore: UserPreferencesStore,
) {
    suspend fun state(): LocaleDivergenceState {
        val override = userPreferencesStore.current().languageOverride
        val appTag = override ?: localeProvider.currentLocaleTag()
        val systemTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .ifEmpty { localeProvider.currentLocaleTag() }
        // Если override отсутствует — app- и system- совпадают по построению.
        val systemDefault = if (override == null) appTag else readSystemDefault(localeProvider)
        return LocaleDivergenceState(appLocale = appTag, systemLocale = systemDefault)
    }

    private fun readSystemDefault(localeProvider: LocaleProvider): String {
        // Без override `localeProvider.currentLocaleTag()` уже равен системному.
        // Этот метод вызывается только при наличии override, и тогда системный
        // язык приходит из Configuration напрямую через LocaleListCompat.
        val defaultList = androidx.core.os.LocaleListCompat.getAdjustedDefault()
        val first = if (!defaultList.isEmpty) defaultList.get(0) else null
        return first?.toLanguageTag() ?: localeProvider.currentLocaleTag()
    }
}

data class LocaleDivergenceState(
    val appLocale: String,
    val systemLocale: String,
) {
    val diverges: Boolean get() = appLocale != systemLocale
}
