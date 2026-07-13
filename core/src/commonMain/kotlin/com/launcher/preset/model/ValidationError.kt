package com.launcher.preset.model

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
    data class SchemaVersionUnsupported(val actual: Int, val expected: Int) : ValidationError()
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

    fun toI18nKey(): String = when (this) {
        is CapabilityMissing -> "validator.error.capability_missing"
        is UnknownPoolRef -> "validator.error.unknown_pool_ref"
        is SchemaVersionUnsupported -> "validator.error.schema_version_unsupported"
        is CircularOrdering -> "validator.error.circular_ordering"
        is RequiresOrderViolation -> "validator.error.requires_order_violation"
        is UnknownComponentId -> "validator.error.unknown_component_id"
        is NullLocale -> "validator.error.null_locale"
    }
}
