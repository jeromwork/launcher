package com.launcher.api.config

import com.launcher.api.result.Outcome
import kotlinx.serialization.Serializable

/**
 * Contact element of `/config/current.contacts[]` (spec 008 §FR-003, contracts/config.md).
 *
 * Identity via [id] (UUID v4) — stable across renames для diff/merge (FR-051).
 *
 * [photoRef] reserved для спека 011 (e2e-encrypted media в Firebase Storage,
 * namespace `private:<uuid>`). Spec 008 leaves it null.
 *
 * Spec 009 (FR-026, FR-028): [fromRaw] is the **single** entry point used by
 * `SystemContactPicker`, `VCardImporter`, and the manual-entry form so all
 * three channels normalise input the same way. Pure (no I/O); idempotent.
 */
@Serializable
data class Contact(
    val id: ElementId,
    val displayName: String,
    val phoneNumber: String,
    val photoRef: String? = null,
) {
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        private val PHONE_REGEX = Regex("^\\+?\\d{5,20}$")
        private val PHONE_STRIP_REGEX = Regex("[\\s\\-().]")

        /**
         * Build a Contact from raw user / picker / vCard input.
         *
         * Pipeline:
         *  1. `rawName` — trim + strip ASCII control chars (U+0000..U+001F,
         *     U+007F) → non-empty → ≤ 100 Unicode code points.
         *     Unicode (Cyrillic, emoji) preserved verbatim.
         *  2. `rawPhone` — strip whitespace, dashes, parens, dots →
         *     match `^\+?\d{5,20}$`.
         */
        fun fromRaw(
            rawName: String,
            rawPhone: String,
            id: ElementId = ElementId.random(),
        ): Outcome<Contact, ValidationError> {
            val trimmed = rawName.trim()
            if (trimmed.isEmpty()) {
                return Outcome.Failure(ValidationError.NameEmpty)
            }
            val sanitisedName = stripControlChars(trimmed)
            if (sanitisedName.isEmpty()) {
                return Outcome.Failure(ValidationError.NameInvalid("only control chars"))
            }
            val codePointCount = sanitisedName.codePointCountCompat()
            if (codePointCount > MAX_NAME_LENGTH) {
                return Outcome.Failure(ValidationError.NameTooLong(actual = codePointCount))
            }

            val stripped = rawPhone.replace(PHONE_STRIP_REGEX, "")
            if (stripped.isEmpty()) {
                return Outcome.Failure(ValidationError.PhoneEmpty)
            }
            if (!PHONE_REGEX.matches(stripped)) {
                return Outcome.Failure(ValidationError.PhoneInvalid("does not match ^\\+?\\d{5,20}$"))
            }

            return Outcome.Success(
                Contact(
                    id = id,
                    displayName = sanitisedName,
                    phoneNumber = stripped,
                    photoRef = null,
                ),
            )
        }

        private fun stripControlChars(input: String): String {
            if (input.isEmpty()) return ""
            return buildString(input.length) {
                for (ch in input) {
                    val code = ch.code
                    val isControl = code in 0x00..0x1F || code == 0x7F
                    if (!isControl) append(ch)
                }
            }
        }

        private fun String.codePointCountCompat(): Int {
            var count = 0
            var i = 0
            while (i < length) {
                val ch = this[i]
                if (ch.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
                    i += 2
                } else {
                    i += 1
                }
                count += 1
            }
            return count
        }
    }
}

/**
 * Categorical error model for [Contact.fromRaw] failures (FR-026, FR-028).
 * UI maps each variant to a localised Russian message.
 */
sealed interface ValidationError {
    data object NameEmpty : ValidationError
    data class NameTooLong(val actual: Int, val max: Int = Contact.MAX_NAME_LENGTH) : ValidationError
    data class NameInvalid(val reason: String) : ValidationError
    data object PhoneEmpty : ValidationError
    data class PhoneInvalid(val reason: String) : ValidationError
}
