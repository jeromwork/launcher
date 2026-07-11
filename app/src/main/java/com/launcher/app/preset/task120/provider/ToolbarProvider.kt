package com.launcher.app.preset.task120.provider

import com.launcher.app.preset.task120.facade.HomeScreenFacade
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider

class ToolbarProvider(private val home: HomeScreenFacade) : Provider<Component.Toolbar> {

    override suspend fun check(component: Component.Toolbar, profile: Profile): Outcome =
        if (home.getToolbar() == component.items) Outcome.Ok else Outcome.NeedsApply

    override suspend fun apply(component: Component.Toolbar, profile: Profile): Outcome {
        home.setToolbar(component.items, component.layoutKey)
        return Outcome.Ok
    }
}
