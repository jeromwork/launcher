package com.launcher.api.edit

import com.launcher.wire.WireVersion

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compile-time gate: every [EditError] sealed variant must be handled
 * exhaustively. If a future variant is added без presentation handler, this
 * test fails to compile (missing `else` branch) — forcing the developer to
 * decide UX handling per failure-recovery.md CHK001.
 *
 * Trace: spec 014 T038, failure-recovery.md CHK001.
 */
class EditErrorExhaustivenessTest {

    @Test
    fun every_EditError_variant_is_pattern_matched() {
        val cases: List<EditError> = listOf(
            EditError.InvalidPosition,
            EditError.SlotNotFound("dummy"),
            EditError.FlowNotFound("dummy"),
            EditError.ConcurrentEditConflict,
            EditError.NotAuthorized,
            EditError.ProfileSelectionRequiresCapabilityRegistry,
        )

        // Exhaustive `when` без `else` — compile error if a variant is missed.
        cases.forEach { err ->
            val handled: String = when (err) {
                is EditError.InvalidPosition -> "invalid-position"
                is EditError.SlotNotFound -> "slot-not-found:${err.slotId}"
                is EditError.FlowNotFound -> "flow-not-found:${err.flowId}"
                is EditError.ConcurrentEditConflict -> "conflict"
                is EditError.NotAuthorized -> "not-authorized"
                is EditError.ProfileSelectionRequiresCapabilityRegistry -> "needs-capability-registry"
            }
            assertEquals(true, handled.isNotEmpty())
        }
    }

    @Test
    fun every_StoreError_variant_is_pattern_matched() {
        val cases: List<StoreError> = listOf(
            StoreError.LimitReached,
            StoreError.InvalidName("dummy"),
            StoreError.NameAlreadyExists("dummy"),
            StoreError.NotFound,
            StoreError.DefaultMustExist,
            StoreError.UnsupportedSchemaVersion(required = WireVersion(2, 0), readerLevel = WireVersion(1, 0)),
        )

        cases.forEach { err ->
            val handled: String = when (err) {
                is StoreError.LimitReached -> "limit-reached"
                is StoreError.InvalidName -> "invalid-name:${err.reason}"
                is StoreError.NameAlreadyExists -> "name-exists:${err.name}"
                is StoreError.NotFound -> "not-found"
                is StoreError.DefaultMustExist -> "default-must-exist"
                is StoreError.UnsupportedSchemaVersion -> "unsupported:${err.required}/${err.readerLevel}"
            }
            assertEquals(true, handled.isNotEmpty())
        }
    }
}
