package com.launcher.preset.model

import com.launcher.wire.WireVersion

/**
 * Typed validation error surface (FR-019, CL-8).
 *
 * T018 (TASK-126) added [RequiresOrderViolation], [UnknownComponentId], and [NullLocale]
 * variants. [CapabilityMissing], [UnknownPoolRef], [CircularOrdering] were introduced in
 * TASK-120 and are preserved for backwards compatibility with the capability-flag path.
 *
 * Domain never throws exceptions; all validator outcomes are values.
 */
sealed class ValidationError {
    data class CapabilityMissing(
        val componentId: String,
        val missing: Set<CapabilityFlag>,
    ) : ValidationError()

    data class UnknownPoolRef(val ref: String) : ValidationError()
    data class SchemaVersionUnsupported(val required: WireVersion, val readerLevel: WireVersion) : ValidationError()
    data class CircularOrdering(val cycle: List<String>) : ValidationError()

    /** T018 (FR-006): [offenderId] declares `requires = [..., missingId, ...]` but
     *  `missingId` does not appear earlier in `Preset.wizardFlow`. */
    data class RequiresOrderViolation(
        val offenderId: String,
        val missingId: String,
    ) : ValidationError()

    /** T018 (FR-019): a Preset entry references a Pool ID absent from the pool. */
    data class UnknownComponentId(val id: String) : ValidationError()

    /** T018 (FR-004): `Component.Language.locale` deserialized as null. */
    object NullLocale : ValidationError()

    // ---- hierarchy validation (T127-010, FR-016) ----

    /** [entityId] declares `parentId = missingParentId`, but no such entity exists. */
    data class DanglingParentRef(
        val entityId: String,
        val missingParentId: String,
    ) : ValidationError()

    /** `parentId` chain loops back on itself (A → B → A). [cycle] lists the ids involved. */
    data class CircularParentRef(val cycle: List<String>) : ValidationError()

    /** [buttonId] is a `ToolbarButton` whose `targetFlowId` resolves to no Flow entity. */
    data class DanglingTargetRef(
        val buttonId: String,
        val missingFlowId: String,
    ) : ValidationError()

    fun toI18nKey(): String = when (this) {
        is CapabilityMissing -> "validator.error.capability_missing"
        is UnknownPoolRef -> "validator.error.unknown_pool_ref"
        is SchemaVersionUnsupported -> "validator.error.schema_version_unsupported"
        is CircularOrdering -> "validator.error.circular_ordering"
        is RequiresOrderViolation -> "validator.error.requires_order_violation"
        is UnknownComponentId -> "validator.error.unknown_component_id"
        is NullLocale -> "validator.error.null_locale"
        is DanglingParentRef -> "validator.error.dangling_parent_ref"
        is CircularParentRef -> "validator.error.circular_parent_ref"
        is DanglingTargetRef -> "validator.error.dangling_target_ref"
    }
}
