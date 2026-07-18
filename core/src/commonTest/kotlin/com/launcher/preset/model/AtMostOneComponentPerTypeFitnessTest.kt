package com.launcher.preset.model

import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.query.flatProfile
import com.launcher.preset.query.hierarchicalProfile
import com.launcher.preset.roundtrip.mvpPool
import com.launcher.preset.roundtrip.simpleLauncherPreset
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * TASK-136 T136-034 (FR-015d, CL-3) — at-most-one-component-per-type fitness.
 *
 * No entity may hold two components of the same slot ⇒ `entity.get<T>()` is
 * unambiguous. The slot is the concrete Kotlin type, except every [LifecycleState]
 * variant shares one slot (apply-state is mutually exclusive), so `Pending` +
 * `Failed` on the same entity is a violation (Risk R-2).
 */
class AtMostOneComponentPerTypeFitnessTest {

    private fun slotKey(c: Component): Any =
        if (c is LifecycleState) LifecycleState::class else c::class

    private fun assertNoDuplicateSlots(profile: Profile, label: String) {
        for (e in profile.entities) {
            val counts = e.components.groupingBy { slotKey(it) }.eachCount()
            val dupes = counts.filter { it.value > 1 }
            assertTrue(
                dupes.isEmpty(),
                "$label: entity '${e.id}' holds >1 component of the same slot: $dupes",
            )
        }
    }

    @Test
    fun fixturesHoldAtMostOnePerType() {
        assertNoDuplicateSlots(hierarchicalProfile(), "hierarchicalProfile")
        assertNoDuplicateSlots(flatProfile(), "flatProfile")
    }

    @Test
    fun profileFactoryOutputHoldsAtMostOnePerType() {
        val profile = ProfileFactory().create(simpleLauncherPreset(), mvpPool())
        assertTrue(profile.entities.isNotEmpty(), "factory produced no entities")
        assertNoDuplicateSlots(profile, "ProfileFactory output")
    }
}
