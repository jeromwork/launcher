package com.launcher.preset.fakes

import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.port.InteractionSink

class FakeInteractionSink(
    private val canned: Map<String, Component?> = emptyMap(),
    private val defaultAnswer: (Entity) -> Component? = { e -> e.components.firstOrNull { it !is LifecycleState } },
) : InteractionSink {
    override suspend fun askUser(component: Entity): Component? =
        if (canned.containsKey(component.id)) canned[component.id]
        else defaultAnswer(component)
}
