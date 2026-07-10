package com.launcher.app.preset.task120.provider

import com.launcher.app.preset.task120.facade.UiPrefsFacade
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider
import kotlin.math.abs

class FontSizeProvider(private val ui: UiPrefsFacade) : Provider<Component.FontSize> {

    override suspend fun check(component: Component.FontSize, profile: Profile): Outcome =
        if (abs(ui.fontScale() - component.scale) < 0.01f) Outcome.Ok else Outcome.NeedsApply

    override suspend fun apply(component: Component.FontSize, profile: Profile): Outcome {
        ui.setFontScale(component.scale)
        return Outcome.Ok
    }
}
