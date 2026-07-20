package com.launcher.preset.model

import com.launcher.wire.WireVersion
import com.launcher.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

import kotlinx.serialization.Serializable

/**
 * Blueprint — a **Bundle** (canonical ECS, TASK-136): a named component set +
 * tags used as a **spawn-time template only**. Verified against Bevy `Bundle`:
 * "zero runtime significance after creation" — after `ProfileFactory` spawns an
 * [Entity] from it, the bundle identity is discarded; the entity is a free bag
 * (add/remove components independently).
 */
@Serializable
data class Blueprint(
    val id: String,
    val components: List<Component> = emptyList(),
    /** Tags the bundle stamps onto the spawned entity (CL-4 — explicit). */
    val tags: Set<Tag> = emptySet(),
    val wizardBehavior: WizardBehavior = WizardBehavior.AutoApply,
    val critical: Boolean = false,
    val descriptionKey: String? = null,
    // T017 (FR-006, FR-014): v2 additions. Defaults keep v1 pool.json deserializing.
    /** IDs that must appear earlier in `Preset.wizardFlow`. null = no dependencies. */
    val requires: List<String>? = null,
    /** Wizard complete only when all `required=true` declarations reach Applied. */
    val required: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Pool(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val declarations: List<Blueprint>,
) : WireVersionHeader {
    fun byId(id: String): Blueprint? =
        declarations.firstOrNull { it.id == id }

    companion object {
        /** v2: adds `requires` + `required` to Blueprint (TASK-126). */
        /** What this build writes. Was the integer 2 before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(2, 0)

        /** Blueprints and entries are additive; an old reader ignores what it does not know. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** Pools are authored by us and shipped as bundled assets, never merged by two writers. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}
