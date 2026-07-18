package com.launcher.preset.engine

import com.launcher.preset.model.ChangeItem
import com.launcher.preset.model.Component
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset

class PresetDiff {

    /**
     * Compute the change set to move a device from [current] to [incoming].
     * Same-version-different-content → reject (throws IllegalStateException per FR-011).
     */
    fun diff(current: Preset, incoming: Preset, pool: Pool): List<ChangeItem> {
        require(incoming.presetId == current.presetId) {
            "Diff across preset ids is unsupported (current=${current.presetId}, incoming=${incoming.presetId})"
        }
        if (incoming.version <= current.version && incoming != current) {
            error("Same-version-different-content preset rejected (version=${incoming.version})")
        }

        val currentRefs = current.activeComponents.associateBy { it.poolRef }
        val incomingRefs = incoming.activeComponents.associateBy { it.poolRef }

        val added = incomingRefs.keys - currentRefs.keys
        val removed = currentRefs.keys - incomingRefs.keys
        val stable = incomingRefs.keys.intersect(currentRefs.keys)

        val factory = ProfileFactory()
        val changes = mutableListOf<ChangeItem>()

        for (ref in added) {
            val decl = pool.byId(ref) ?: continue
            val single = incomingRefs[ref]!!
            val resolved = factory.resolveSingle(incoming, single, pool)
                ?: decl.components.firstOrNull() ?: continue
            changes += ChangeItem.Added(ref, resolved)
        }

        for (ref in removed) {
            changes += ChangeItem.Removed(ref)
        }

        for (ref in stable) {
            val a = currentRefs[ref]!!
            val b = incomingRefs[ref]!!
            if (a.paramsOverride != b.paramsOverride) {
                val decl = pool.byId(ref) ?: continue
                val resolved = factory.resolveSingle(incoming, b, pool)
                    ?: decl.components.firstOrNull() ?: continue
                changes += ChangeItem.ParamsChanged(ref, resolved)
            }
        }
        return changes
    }

    /**
     * Spawn a single preset entry and return its resolved data component (the
     * bundle's one domain-data component with `paramsOverride` merged), stripped of
     * the [LifecycleState] marker. Null when the entry resolves to no entity.
     */
    private fun ProfileFactory.resolveSingle(
        incoming: Preset,
        entry: com.launcher.preset.model.ActiveComponentEntry,
        pool: Pool,
    ): Component? = create(
        incoming.copy(
            wizardFlow = emptyList(),
            settingsMap = emptyList(),
            activeComponents = listOf(entry),
        ),
        pool,
    ).entities.firstOrNull()
        ?.components?.firstOrNull { it !is LifecycleState }
}
