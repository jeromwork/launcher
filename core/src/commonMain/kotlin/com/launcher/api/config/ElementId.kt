package com.launcher.api.config

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Stable identifier для элементов `/config` (Flow, Slot, Contact). UUID v4
 * string, client-generated at element creation time (spec 008 §FR-004).
 *
 * Per research.md §2: client-side UUID v4 was chosen because (a) elements may
 * be created offline (Managed-as-editor), no server round-trip allowed; (b)
 * collision probability is effectively zero at our scale; (c) content-hash IDs
 * would lose identity after rename.
 *
 * Validates UUID v4 format at construction to fail-fast on bad data
 * (corrupted local store, manual JSON injection, etc.).
 */
@JvmInline
@Serializable
value class ElementId(val value: String) {
    init {
        require(isUuid(value)) { "ElementId must be UUID format: $value" }
    }

    companion object {
        /** Generate fresh UUID v4 element id. Pure-Kotlin via stdlib (Kotlin 2.0.20+). */
        @OptIn(ExperimentalUuidApi::class)
        fun random(): ElementId = ElementId(Uuid.random().toString())

        /**
         * Permissive UUID check: 8-4-4-4-12 hex with dashes. Doesn't enforce
         * v4 variant bits — we accept v4 we generate and any structurally-valid
         * UUID for backward compat (in case a future spec needs v7 timestamps).
         */
        internal fun isUuid(s: String): Boolean {
            if (s.length != 36) return false
            for (i in s.indices) {
                val c = s[i]
                val expectDash = i == 8 || i == 13 || i == 18 || i == 23
                if (expectDash) {
                    if (c != '-') return false
                } else {
                    if (!c.isHexChar()) return false
                }
            }
            return true
        }

        private fun Char.isHexChar(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
