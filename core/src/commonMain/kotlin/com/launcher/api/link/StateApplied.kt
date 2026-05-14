package com.launcher.api.link

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
@Serializable
data class StateApplied(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appliedAt: Long,
    val presetId: String,
    val fcmToken: String?,
    val updatedAt: Long,
    // Spec 008 extension (additive — FR-032):
    val appliedConfigUpdatedAt: ServerTimestamp? = null,
    val flowsApplied: List<FlowApplied>? = null,
    val contactsApplied: List<ContactApplied>? = null,
    val partialApplyReasons: List<PartialReason> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
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
