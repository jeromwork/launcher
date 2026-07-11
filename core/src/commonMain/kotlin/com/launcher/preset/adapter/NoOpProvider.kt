package com.launcher.preset.adapter

import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider

object NoOpProvider : Provider<Component> {
    override suspend fun check(component: Component, profile: Profile): Outcome = Outcome.Unsupported
    override suspend fun apply(component: Component, profile: Profile): Outcome = Outcome.Unsupported
}
