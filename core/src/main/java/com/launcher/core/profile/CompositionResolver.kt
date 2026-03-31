package com.launcher.core.profile

import com.launcher.api.DegradationReason
import com.launcher.api.DegradationRecord
import com.launcher.api.EffectiveProfile
import com.launcher.api.ProfileSnapshot
import com.launcher.core.modules.ModuleResolutionState

/**
 * Applies conflict resolution order from data-model.md (canonical list).
 */
object CompositionResolver {

    fun resolve(
        raw: ProfileSnapshot,
        profileGeneration: Int,
        modules: List<ModuleResolutionState>,
    ): EffectiveProfile {
        val effectiveFlags = LinkedHashMap<String, Boolean>()
        val reasonCodes = mutableListOf<DegradationReason>()
        val degradedModules = mutableSetOf<String>()

        for (m in modules) {
            val profileWants = raw.moduleFlags[m.moduleId] ?: false
            when {
                !m.contractSatisfied -> {
                    effectiveFlags[m.moduleId] = false
                    if (profileWants) {
                        degradedModules.add(m.moduleId)
                        reasonCodes.add(DegradationReason.CONTRACT_INCOMPATIBLE)
                    }
                }
                !m.presentInBuild -> {
                    effectiveFlags[m.moduleId] = false
                    if (profileWants) {
                        degradedModules.add(m.moduleId)
                        reasonCodes.add(DegradationReason.MODULE_UNAVAILABLE)
                    }
                }
                else -> {
                    effectiveFlags[m.moduleId] = profileWants
                }
            }
        }

        for ((id, wants) in raw.moduleFlags) {
            if (id !in effectiveFlags && wants) {
                effectiveFlags[id] = false
                degradedModules.add(id)
                reasonCodes.add(DegradationReason.MODULE_UNAVAILABLE)
            }
        }

        val degradation = DegradationRecord(
            activeProfileId = raw.id,
            degradedModules = degradedModules.toList(),
            reasonCodes = reasonCodes.distinct(),
        )

        return EffectiveProfile(
            snapshot = raw,
            profileGeneration = profileGeneration,
            effectiveModuleFlags = effectiveFlags.toMap(),
            degradation = degradation,
        )
    }
}
