package com.launcher.preset.port

import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile

interface Provider<T : Component> {
    suspend fun check(component: T, profile: Profile): Outcome
    suspend fun apply(component: T, profile: Profile): Outcome

    // TODO(capability-registry): check()/apply() will be exposed as domain-verbs through
    // future Capability Registry (F-2). Each Provider implementation becomes an exposure
    // point. Provider-side rollback (`suspend fun rollback(...): Outcome = Outcome.Unsupported`)
    // — additive extension when needed per FR-029.
}
