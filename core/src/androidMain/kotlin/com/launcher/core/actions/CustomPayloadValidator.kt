package com.launcher.core.actions

import com.launcher.api.action.ActionPayload

/**
 * Bounds-checker for [ActionPayload.Custom]. The "custom" payload is the
 * deliberate forward-compat escape hatch (research R2) — older clients can
 * receive `Custom` payloads from a newer config and surface them as
 * `ProviderUnavailable(UnknownInThisVersion)` rather than crashing.
 *
 * Without bounds, a malicious or buggy producer could submit unbounded
 * key/value sizes that bloat memory or persistence. This validator caps:
 *
 *  - max 16 keys per params map,
 *  - keys: regex `[a-z][a-z0-9_.-]{1,63}` — lowercase, alphanumeric + `_`, `.`, `-`,
 *  - values: ≤ 1024 chars each.
 *
 * It does NOT block JSON-as-string in values — only warns via [Result.warnings].
 * Real defense against nested-JSON injection lives in the provider handler that
 * eventually consumes the `Custom` payload (none in spec 005 — `Custom` is just
 * the future pipe).
 *
 * Bounds taken verbatim from spec 005 security CHK-011 / contracts/action-wire-format.md §Custom.
 */
class CustomPayloadValidator {

    private val keyRegex = Regex("[a-z][a-z0-9_.-]{1,63}")

    fun validate(payload: ActionPayload.Custom): Result {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (payload.params.size > MAX_KEYS) {
            errors += "params has ${payload.params.size} keys; max is $MAX_KEYS"
        }

        for ((key, value) in payload.params) {
            if (!keyRegex.matches(key)) {
                errors += "param key '$key' does not match $keyRegex"
            }
            if (value.length > MAX_VALUE_LENGTH) {
                errors += "param '$key' value length ${value.length} exceeds $MAX_VALUE_LENGTH"
            }
            if (looksLikeNestedJson(value)) {
                warnings += "param '$key' value looks like nested JSON; future decoders may reject"
            }
        }

        return if (errors.isEmpty()) Result.Ok(warnings) else Result.Invalid(errors, warnings)
    }

    private fun looksLikeNestedJson(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
            (trimmed.startsWith('[') && trimmed.endsWith(']'))
    }

    sealed class Result {
        abstract val warnings: List<String>

        data class Ok(override val warnings: List<String> = emptyList()) : Result()

        data class Invalid(
            val errors: List<String>,
            override val warnings: List<String> = emptyList(),
        ) : Result()
    }

    companion object {
        const val MAX_KEYS = 16
        const val MAX_KEY_LENGTH = 64
        const val MAX_VALUE_LENGTH = 1024
    }
}
