package com.launcher.preset.engine

import com.launcher.wire.CorruptWireFormatException
import com.launcher.wire.UnknownWireVersionException
import com.launcher.wire.WireVersion
import com.launcher.wire.accessFor

import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.model.Pool
import com.launcher.preset.model.Preset
import com.launcher.preset.model.ValidationError
import com.launcher.preset.port.CapabilityContract

/**
 * Typed validation result carrying either a validated [Preset] or a list of
 * [ValidationError]s. Domain-pure — no exceptions cross the boundary (FR-019, CL-8).
 *
 * T020 (TASK-126) — introduced to give callers a typed Result<Preset, ValidationError>
 * shape without leaning on `kotlin.Result` (which is single-error and Throwable-typed).
 */
sealed class PresetValidationResult {
    data class Success(val preset: Preset) : PresetValidationResult()
    data class Failure(val errors: List<ValidationError>) : PresetValidationResult() {
        init {
            require(errors.isNotEmpty()) { "Failure must carry at least one error" }
        }
    }
}

/**
 * Static preset validator — runs before Wizard start.
 *
 * TASK-120 API (`validate` returning `List<ValidationError>`) is preserved for existing
 * callers ([com.launcher.app.preset.task120.PresetBootstrap]).
 * TASK-126 T020 adds [validateToResult] which returns a typed [PresetValidationResult]
 * and additionally checks: requires-order (FR-006), unknown component id (FR-019),
 * null Language.locale (FR-004).
 */
class PresetValidator(
    private val contract: CapabilityContract,
    private val readerLevel: WireVersion = Preset.SCHEMA_VERSION,
) {

    fun validate(preset: Preset, pool: Pool): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Version header gate (wire-format.md §3). Previously this compared schemaVersion, which
        // refused presets authored by a newer build even when every addition was one we could
        // safely ignore. Only a raised minReaderVersion refuses now.
        try {
            preset.accessFor(readerLevel)
        } catch (_: UnknownWireVersionException) {
            errors += ValidationError.SchemaVersionUnsupported(preset.minReaderVersion, readerLevel)
            return errors
        } catch (_: CorruptWireFormatException) {
            errors += ValidationError.SchemaVersionUnsupported(preset.minReaderVersion, readerLevel)
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
                // Backwards-compat: TASK-120 emits UnknownPoolRef; TASK-126 T020 additionally
                // surfaces UnknownComponentId via validateToResult().
                errors += ValidationError.UnknownPoolRef(ref)
            } else {
                known += ref
            }
        }

        val available = mutableSetOf<CapabilityFlag>()
        for (entry in preset.wizardFlow.sortedWith(compareBy({ it.order }, { it.poolRef }))) {
            val decl = pool.byId(entry.poolRef) ?: continue
            // A bundle may declare several components; each contributes its own
            // capability requires/provides (TASK-136 free bag; MVP bundles hold one).
            for (comp in decl.components) {
                val type = comp::class
                val requires = contract.requires(type)
                val missing = requires - available
                if (missing.isNotEmpty()) {
                    errors += ValidationError.CapabilityMissing(entry.poolRef, missing)
                }
                available += contract.provides(type)
            }
        }

        // T020 additions — requires-order + null-locale + unknown-component-id.
        errors += extraChecks(preset, pool)

        return errors
    }

    /**
     * T020 — typed Result<Preset, ValidationError> surface.
     *
     * Returns [PresetValidationResult.Success] with the preset unchanged when validation
     * passes; returns [PresetValidationResult.Failure] with the full error list otherwise.
     */
    fun validateToResult(preset: Preset, pool: Pool): PresetValidationResult {
        val errors = validate(preset, pool)
        return if (errors.isEmpty()) PresetValidationResult.Success(preset)
        else PresetValidationResult.Failure(errors)
    }

    // ---- T020 helpers --------------------------------------------------------

    private fun extraChecks(preset: Preset, pool: Pool): List<ValidationError> {
        val out = mutableListOf<ValidationError>()

        // Unknown component id: pool.declarations referenced by wizardFlow but absent.
        // TASK-120 already reports UnknownPoolRef for missing refs; T020 additionally emits
        // UnknownComponentId so callers that consume the typed variant get an unambiguous
        // signal. (Both are surfaced — callers may pattern-match on either.)
        val poolIds = pool.declarations.map { it.id }.toHashSet()
        val refs = preset.wizardFlow.map { it.poolRef } +
            preset.settingsMap.map { it.poolRef } +
            preset.activeComponents.map { it.poolRef }
        for (ref in refs.distinct()) {
            if (ref !in poolIds) out += ValidationError.UnknownComponentId(ref)
        }

        // Requires-order (FR-006): for each wizardFlow entry, every id in the resolved
        // declaration's `requires` must appear earlier in the wizardFlow.
        val ordered = preset.wizardFlow.sortedWith(compareBy({ it.order }, { it.poolRef }))
        val seenEarlier = mutableSetOf<String>()
        for (entry in ordered) {
            val decl = pool.byId(entry.poolRef)
            if (decl != null) {
                for (dep in decl.requires.orEmpty()) {
                    if (dep !in seenEarlier) {
                        out += ValidationError.RequiresOrderViolation(
                            offenderId = entry.poolRef,
                            missingId = dep,
                        )
                    }
                }
            }
            seenEarlier += entry.poolRef
        }

        // Null-locale (FR-004): Component.Language with blank locale value.
        // (Kotlin-level `locale: String` cannot be `null`; a JSON `"locale": null` fails
        // deserialization before reaching here. We still guard against sentinel emptiness.)
        for (decl in pool.declarations) {
            for (comp in decl.components) {
                if (comp is Component.Language && comp.locale.isBlank()) {
                    out += ValidationError.NullLocale
                }
            }
        }

        return out
    }
}
