package com.launcher.api.media

import com.launcher.api.result.Outcome

/**
 * Spec 012 — sanitisation + validation для `Slot(kind=Document).args.label`.
 *
 * Rules (per FR-016):
 *  - Trim whitespace.
 *  - Strip ASCII control chars (U+0000..U+001F, U+007F).
 *  - After strip — must be non-empty and ≤ 40 Unicode code points.
 *
 * Mirrors Contact.fromRaw pipeline (consistent UX across all user-entered labels).
 *
 * Task: T1224 (Phase 3). FR-016.
 */
object DocumentLabel {
    const val MAX_LENGTH: Int = 40

    fun sanitiseAndValidate(raw: String): Outcome<String, DocumentLabelError> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Outcome.Failure(DocumentLabelError.Empty)

        val stripped = buildString(trimmed.length) {
            for (ch in trimmed) {
                val code = ch.code
                if (!(code in 0x00..0x1F || code == 0x7F)) {
                    append(ch)
                }
            }
        }
        if (stripped.isEmpty()) return Outcome.Failure(DocumentLabelError.OnlyControlChars)

        val codePoints = stripped.codePointCountCompat()
        if (codePoints > MAX_LENGTH) {
            return Outcome.Failure(DocumentLabelError.TooLong(actual = codePoints, max = MAX_LENGTH))
        }
        return Outcome.Success(stripped)
    }

    /**
     * Hard-truncate variant — used при чтении из untrusted /config (бабушка получила
     * config с too-long label из-за admin bug). Graceful: truncate без error.
     */
    fun truncate(raw: String): String {
        val trimmed = raw.trim()
        val stripped = buildString(trimmed.length) {
            for (ch in trimmed) {
                val code = ch.code
                if (!(code in 0x00..0x1F || code == 0x7F)) {
                    append(ch)
                }
            }
        }
        return stripped.takeCodePoints(MAX_LENGTH)
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

    private fun String.takeCodePoints(maxCount: Int): String {
        if (isEmpty() || maxCount <= 0) return ""
        var count = 0
        var i = 0
        val sb = StringBuilder()
        while (i < length && count < maxCount) {
            val ch = this[i]
            if (ch.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
                sb.append(ch).append(this[i + 1])
                i += 2
            } else {
                sb.append(ch)
                i += 1
            }
            count += 1
        }
        return sb.toString()
    }
}

sealed interface DocumentLabelError {
    data object Empty : DocumentLabelError
    data object OnlyControlChars : DocumentLabelError
    data class TooLong(val actual: Int, val max: Int) : DocumentLabelError
}
