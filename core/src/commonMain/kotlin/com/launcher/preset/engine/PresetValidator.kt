package com.launcher.preset.engine

import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.ValidationError
import com.launcher.preset.port.CapabilityContract

/**
 * Static preset validator — runs before Wizard start.
 * Returns empty list = valid. Non-empty → Wizard MUST NOT start.
 */
class PresetValidator(
    private val contract: CapabilityContract,
    private val supportedSchemaVersion: Int = Preset.CURRENT_SCHEMA_VERSION,
) {

    fun validate(preset: Preset, pool: Pool): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (preset.schemaVersion > supportedSchemaVersion) {
            errors += ValidationError.SchemaVersionUnsupported(preset.schemaVersion, supportedSchemaVersion)
            return errors
        }

        val allRefs = (
            preset.wizardFlow.map { it.poolRef } +
                preset.settingsMap.map { it.poolRef } +
                preset.activeComponents.map { it.poolRef }
            ).distinct()

        val known = HashSet<String>()
        for (ref in allRefs) {
            if (pool.byId(ref) == null) {
                errors += ValidationError.UnknownPoolRef(ref)
            } else {
                known += ref
            }
        }

        val available = mutableSetOf<CapabilityFlag>()
        for (entry in preset.wizardFlow.sortedWith(compareBy({ it.order }, { it.poolRef }))) {
            val decl = pool.byId(entry.poolRef) ?: continue
            val type = decl.component::class
            val requires = contract.requires(type)
            val missing = requires - available
            if (missing.isNotEmpty()) {
                errors += ValidationError.CapabilityMissing(entry.poolRef, missing)
            }
            available += contract.provides(type)
        }

        return errors
    }
}
