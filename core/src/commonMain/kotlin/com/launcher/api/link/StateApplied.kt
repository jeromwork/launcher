package com.launcher.api.link

import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

import com.launcher.api.config.ElementId
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.config.SlotKind
import kotlinx.serialization.Serializable

/**
 * Wire-format root для `/links/{linkId}/state/current` (spec 008 §FR-031..033,
 * contracts/state-applied.md).
 *
 * **Additive extension** to [LinkBootstrap] from спека 007 — schemaVersion stays
 * at 1 (FR-032; per state-bootstrap.md §Backward compatibility «spec 008 расширит
 * schema добавлением `flows`, `slots`, `appliedCapabilities` и т.д. Расширение —
 * **additive**»).
 *
 * Field origins:
 *  - [schemaVersion], [appliedAt], [presetId], [fcmToken], [updatedAt]: from spec 007
 *    `LinkBootstrap` — kept identical, spec 007 readers ignore the new fields.
 *  - [appliedConfigUpdatedAt]: NEW в 008 — mirrors `/config/current.serverUpdatedAt`
 *    at the moment apply succeeded. Nullable: prior to first apply, no value.
 *    Admin UI uses this to show «Применено ✓» indicator (SC-001b).
 *  - [flowsApplied], [contactsApplied], [partialApplyReasons]: NEW в 008 — what
 *    Managed really applied (may differ from /config if provider missing etc.).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StateApplied(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = MIN_WRITER_VERSION,
    val appliedAt: Long,
    val presetId: String,
    val fcmToken: String?,
    val updatedAt: Long,
    // Spec 008 extension (additive — FR-032):
    val appliedConfigUpdatedAt: ServerTimestamp? = null,
    val flowsApplied: List<FlowApplied>? = null,
    val contactsApplied: List<ContactApplied>? = null,
    val partialApplyReasons: List<PartialReason> = emptyList(),
) : WireVersionHeader {
    companion object {
        /** What this build writes. Was the integer 1 before the conversion — never lowered (I3). */
        val SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

        /** Applied-state fields are additive per the spec-007 to spec-008 extension. */
        val MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

        /** Rewritten wholesale by the managed device on every apply. */
        val MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)
    }
}

@Serializable
data class FlowApplied(
    val id: ElementId,
    val title: String,
    val slots: List<SlotApplied>,
)

@Serializable
data class SlotApplied(
    val id: ElementId,
    val kind: SlotKind,
    val appliedSuccessfully: Boolean,
)

@Serializable
data class ContactApplied(
    val id: ElementId,
    val displayName: String,
    val appliedSuccessfully: Boolean,
)
