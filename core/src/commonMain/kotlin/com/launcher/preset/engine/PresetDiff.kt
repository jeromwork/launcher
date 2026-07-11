package com.launcher.preset.engine

import com.launcher.preset.model.ChangeItem
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
            val resolved = factory.create(
                incoming.copy(
                    wizardFlow = emptyList(),
                    settingsMap = emptyList(),
                    activeComponents = listOf(single),
                ),
                pool,
            ).components.firstOrNull()?.component ?: decl.component
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
                val resolved = factory.create(
                    incoming.copy(
                        wizardFlow = emptyList(),
                        settingsMap = emptyList(),
                        activeComponents = listOf(b),
                    ),
                    pool,
                ).components.firstOrNull()?.component ?: decl.component
                changes += ChangeItem.ParamsChanged(ref, resolved)
            }
        }
        return changes
    }
}
