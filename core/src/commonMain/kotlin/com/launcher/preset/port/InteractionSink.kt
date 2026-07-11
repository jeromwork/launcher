package com.launcher.preset.port

import com.launcher.preset.model.Component
import com.launcher.preset.model.ProfileComponent

interface InteractionSink {
    suspend fun askUser(component: ProfileComponent): Component?
}
