package com.launcher.preset.port

import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import kotlin.reflect.KClass

interface CapabilityContract {
    fun requires(componentType: KClass<out Component>): Set<CapabilityFlag>
    fun provides(componentType: KClass<out Component>): Set<CapabilityFlag>
}
