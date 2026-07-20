package com.launcher.preset.model

import com.launcher.wire.WireVersion
import com.launcher.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Preset — shareable JSON template describing WHICH components to use, WHEN, and
 * WHERE on the screen.
 *
 * The model has **three orthogonal axes** — do NOT conflate them:
 *
 *  1. **Lifecycle** — the three list fields below (`wizardFlow` / `settingsMap` /
 *     `activeComponents`): *when* a component appears (first-run wizard, Settings
 *     screen, applied at runtime).
 *  2. **Semantic** — `Entity.tags`: *what* a component is about (Presentation,
 *     Safety, Accessibility, …). Tags live on the entity (TASK-136).
 *  3. **Structural** — `ActiveComponentEntry.parentRef` → `Entity.parentId`:
 *     *where* it hangs in the screen tree (Workspace → Flow → Tile, Toolbar →
 *     ToolbarButton). Storage stays flat; the tree is computed by queries.
 *
 * A component may sit in `wizardFlow`, carry `[Presentation, Communication]`, and
 * live under `flow-calls` — all three facets are independently useful.
 *
 * See `docs/architecture/preset-model.md` (AI TL;DR first) for the full model and
 * the ECS ≈ database-table mental model; TASK-127 Decision block for the
 * tag + query + hierarchy extension of TASK-120.
 */
@Serializable
data class WizardFlowEntry(
    val poolRef: String,
    val order: Int,
    val wizardTitleKey: String,
    val wizardIntroKey: String? = null,
    val behavior: WizardBehavior,
    val paramsOverride: JsonObject? = null,
    val visibleIf: JsonElement? = null,
)

@Serializable
data class SettingsMapEntry(
    val poolRef: String,
    val categoryKey: String,
    val settingsIcon: String? = null,
    val sensitivity: Sensitivity = Sensitivity.Normal,
    val paramsOverride: JsonObject? = null,
)

@Serializable
data class ActiveComponentEntry(
    val poolRef: String,
    val paramsOverride: JsonObject? = null,
    // TASK-136 (T136-046): `status` removed — a preset declares WHAT to spawn
    // (poolRef + paramsOverride + parentRef); the initial apply-state is injected
    // by ProfileFactory as a LifecycleState.Pending component in the entity bag,
    // never carried in the preset. Preset JSON wire-format change; pre-MVP
    // clean-in-place (Article XX) — no migrator, schemaVersion stays 2, a stray
    // legacy `"status"` key is dropped by ignoreUnknownKeys=true.
    /**
     * T127-026 (FR-011/FR-013) — id of the parent entity in the assembled Profile
     * (`null` = root). This is how a preset expresses the screen tree:
     * `Workspace → Flow → Tile`, `Toolbar → ToolbarButton`.
     *
     * Additive optional field: presets written before TASK-127 omit it and produce
     * a flat, all-roots profile (the simple-launcher case, US-1).
     * Validated at assembly — see [ValidationError.DanglingParentRef].
     */
    val parentRef: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Preset(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val presetId: String,
    val version: Int,
    val layoutKey: String,
    val wizardFlow: List<WizardFlowEntry> = emptyList(),
    val settingsMap: List<SettingsMapEntry> = emptyList(),
    val activeComponents: List<ActiveComponentEntry> = emptyList(),
    // T015 (FR-007, FR-003): v2 additions. Nullable defaults keep v1 fixtures deserializing.
    val hintFlow: List<HintFlowEntry>? = null,
    val wizardPresentation: WizardPresentation? = null,
) : WireVersionHeader {
    companion object {
        /** v2: adds `hintFlow` + `wizardPresentation` (TASK-126). */
        /** What this build writes. Was the integer 2 before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(2, 0)

        /** Component entries are additive; a stray unknown entry is dropped, not misread. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** Presets are shipped assets, rewritten wholesale rather than merged. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}
