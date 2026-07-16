package com.launcher.preset.port

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity

interface InteractionSink {
    suspend fun askUser(component: Entity): Component?
}
