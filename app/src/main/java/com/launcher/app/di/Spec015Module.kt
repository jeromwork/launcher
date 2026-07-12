package com.launcher.app.di

import com.launcher.adapters.localization.AndroidLocaleProvider
import com.launcher.adapters.localization.AndroidStringResolver
import com.launcher.api.localization.LocaleProvider
import com.launcher.api.localization.StringResolver
import com.launcher.app.locale.LocaleOverrideStore
import com.launcher.ui.senior.util.AnimationPreferenceProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Residual spec 015 Koin module — trimmed down after TASK-126 Phase 7
 * delete pass. The legacy `WizardEngine` / `SystemSettingPort` /
 * `CheckHandler` / `ApplyHandler` bindings are gone with the deleted
 * `com.launcher.api.wizard.*` package.
 *
 * What still lives here:
 *  - `LocaleProvider` + `StringResolver` (localization primitives used by
 *    every UI surface).
 *  - `AnimationPreferenceProvider` (reduce-motion source — a small
 *    Android-System-Settings wrapper).
 *  - `LocaleOverrideStore` (TASK-126 Phase 7 wave B carve-out from the
 *    deleted `UserPreferencesStore`).
 *  - Settings ViewModels (`PendingChecklistViewModel`,
 *    `LocaleDivergenceViewModel`) — small, no wizard deps.
 *
 * Rename to `PresetModule` (or fold into it) is a follow-up
 * housekeeping commit; kept as a separate module for now to preserve
 * git-history readability of the delete pass.
 */
val spec015Module = module {

    single<LocaleProvider> { AndroidLocaleProvider(androidContext()) }
    single<StringResolver> { AndroidStringResolver(androidContext()) }
    single<AnimationPreferenceProvider> {
        object : AnimationPreferenceProvider {
            override fun durationScale(): Float {
                val ctx = androidContext()
                return try {
                    android.provider.Settings.Global.getFloat(
                        ctx.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        1.0f,
                    )
                } catch (_: Throwable) {
                    1.0f
                }
            }
        }
    }

    single { LocaleOverrideStore(androidContext()) }

    factory {
        com.launcher.app.settings.PendingChecklistViewModel(
            profileStore = get(),
            presetSource = get(),
            stringResolver = get(),
        )
    }
    factory {
        com.launcher.app.settings.LocaleDivergenceViewModel(
            localeProvider = get(),
            localeOverrideStore = get(),
        )
    }
}
