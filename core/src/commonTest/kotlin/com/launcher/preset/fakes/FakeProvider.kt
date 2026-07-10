package com.launcher.preset.fakes

import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.CapabilityContract
import com.launcher.preset.port.CapabilityQuery
import com.launcher.preset.port.Provider
import kotlin.reflect.KClass

/**
 * Canned Provider — for engine + roundtrip tests. Configurable check/apply outcome per invocation.
 */
class FakeProvider<T : Component>(
    private val checkOutcome: (T) -> Outcome = { Outcome.NeedsApply },
    private val applyOutcome: (T) -> Outcome = { Outcome.Ok },
) : Provider<T> {
    override suspend fun check(component: T, profile: Profile): Outcome = checkOutcome(component)
    override suspend fun apply(component: T, profile: Profile): Outcome = applyOutcome(component)
}

class FakeCapabilityQuery(
    initial: Set<CapabilityFlag> = emptySet(),
) : CapabilityQuery {
    private val active = initial.toMutableSet()
    override suspend fun isActive(flag: CapabilityFlag): Boolean = active.any { it::class == flag::class }
    override suspend fun markActive(flag: CapabilityFlag, evidence: CapabilityQuery.Evidence) {
        active += flag
    }

    override suspend fun markInactive(flag: CapabilityFlag) {
        active.removeAll { it::class == flag::class }
    }
}

class FakeCapabilityContract(
    private val requires: Map<KClass<out Component>, Set<CapabilityFlag>> = emptyMap(),
    private val provides: Map<KClass<out Component>, Set<CapabilityFlag>> = emptyMap(),
) : CapabilityContract {
    override fun requires(componentType: KClass<out Component>): Set<CapabilityFlag> =
        requires[componentType] ?: emptySet()

    override fun provides(componentType: KClass<out Component>): Set<CapabilityFlag> =
        provides[componentType] ?: emptySet()
}
