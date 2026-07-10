package com.launcher.preset.model

sealed class ValidationError {
    data class CapabilityMissing(
        val componentId: String,
        val missing: Set<CapabilityFlag>,
    ) : ValidationError()

    data class UnknownPoolRef(val ref: String) : ValidationError()
    data class SchemaVersionUnsupported(val version: Int, val supported: Int) : ValidationError()
    data class CircularOrdering(val cycle: List<String>) : ValidationError()

    fun toI18nKey(): String = when (this) {
        is CapabilityMissing -> "validator.error.capability_missing"
        is UnknownPoolRef -> "validator.error.unknown_pool_ref"
        is SchemaVersionUnsupported -> "validator.error.schema_version_unsupported"
        is CircularOrdering -> "validator.error.circular_ordering"
    }
}
