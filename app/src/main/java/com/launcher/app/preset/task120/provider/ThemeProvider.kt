package com.launcher.app.preset.task120.provider

import com.launcher.app.preset.task120.facade.AppThemeController
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider

/**
 * T032 — ThemeProvider (FR-003, US-1).
 *
 * `check()` reads current theme from [AppThemeController]; `apply()` writes the
 * new flat [Component.Theme] via the same facade. `ThemeRef` sugar (write-time)
 * has already been expanded before the Component reaches the provider — see
 * `ThemeCatalog` (T036).
 */
class ThemeProvider(
    private val controller: AppThemeController,
) : Provider<Component.Theme> {

    override suspend fun check(component: Component.Theme, profile: Profile): Outcome =
        if (controller.current() == component) Outcome.Ok else Outcome.NeedsApply

    override suspend fun apply(component: Component.Theme, profile: Profile): Outcome {
        controller.set(component)
        return Outcome.Ok
    }
}
