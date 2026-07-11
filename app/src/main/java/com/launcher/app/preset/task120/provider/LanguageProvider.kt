package com.launcher.app.preset.task120.provider

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider

/**
 * T033 — LanguageProvider (FR-004, FR-022, SC-11).
 *
 * ACL wrapper around [AppCompatDelegate.setApplicationLocales]. Domain never
 * sees `LocaleListCompat`.
 *
 * Sentinel `"system"` maps to [LocaleListCompat.getEmptyLocaleList] — i.e.
 * follow the OS locale.
 */
class LanguageProvider : Provider<Component.Language> {

    override suspend fun check(component: Component.Language, profile: Profile): Outcome {
        val current = AppCompatDelegate.getApplicationLocales()
        val target = component.toLocaleList()
        return if (current == target) Outcome.Ok else Outcome.NeedsApply
    }

    override suspend fun apply(component: Component.Language, profile: Profile): Outcome {
        AppCompatDelegate.setApplicationLocales(component.toLocaleList())
        return Outcome.Ok
    }

    private fun Component.Language.toLocaleList(): LocaleListCompat =
        if (locale == SYSTEM_SENTINEL) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(locale)
        }

    private companion object {
        const val SYSTEM_SENTINEL = "system"
    }
}
