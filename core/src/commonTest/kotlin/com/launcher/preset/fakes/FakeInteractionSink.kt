package com.launcher.preset.fakes

import com.launcher.preset.model.Component
import com.launcher.preset.model.ProfileComponent
import com.launcher.preset.port.InteractionSink

class FakeInteractionSink(
    private val canned: Map<String, Component?> = emptyMap(),
    private val defaultAnswer: (ProfileComponent) -> Component? = { it.component },
) : InteractionSink {
    override suspend fun askUser(component: ProfileComponent): Component? =
        if (canned.containsKey(component.id)) canned[component.id]
        else defaultAnswer(component)
}
