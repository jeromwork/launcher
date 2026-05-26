package com.launcher.api.media

import com.launcher.api.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 012 — label sanitisation + validation per FR-016.
 *
 * Task: T1224 (Phase 3).
 *
 * Mirrors Contact.fromRaw label rules для consistency:
 *  - trim → strip control chars → ≤ 40 graphemes.
 */
class DocumentLabelTest {

    @Test
    fun empty_label_rejected() {
        val result = DocumentLabel.sanitiseAndValidate("")
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is DocumentLabelError.Empty)
    }

    @Test
    fun whitespace_only_label_rejected() {
        val result = DocumentLabel.sanitiseAndValidate("   ")
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is DocumentLabelError.Empty)
    }

    @Test
    fun control_chars_only_rejected_after_strip() {
        val result = DocumentLabel.sanitiseAndValidate("")
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is DocumentLabelError.OnlyControlChars)
    }

    @Test
    fun single_char_accepted() {
        val result = DocumentLabel.sanitiseAndValidate("П")
        assertTrue(result is Outcome.Success)
        assertEquals("П", result.value)
    }

    @Test
    fun exactly_40_chars_accepted() {
        val label = "А".repeat(40)
        val result = DocumentLabel.sanitiseAndValidate(label)
        assertTrue(result is Outcome.Success)
        assertEquals(40, result.value.length)
    }

    @Test
    fun forty_one_chars_rejected_as_too_long() {
        val label = "А".repeat(41)
        val result = DocumentLabel.sanitiseAndValidate(label)
        assertTrue(result is Outcome.Failure)
        val err = result.error
        assertTrue(err is DocumentLabelError.TooLong)
        assertEquals(41, err.actual)
        assertEquals(40, err.max)
    }

    @Test
    fun control_chars_stripped_from_middle() {
        val raw = "ПаспортРФ"
        val result = DocumentLabel.sanitiseAndValidate(raw)
        assertTrue(result is Outcome.Success)
        assertEquals("ПаспортРФ", result.value)
    }

    @Test
    fun leading_trailing_whitespace_trimmed() {
        val result = DocumentLabel.sanitiseAndValidate("  Медкарта  ")
        assertTrue(result is Outcome.Success)
        assertEquals("Медкарта", result.value)
    }

    @Test
    fun emoji_counted_as_one_code_point() {
        // Single emoji = 2 UTF-16 chars but 1 code point.
        val label = "📋" // clipboard emoji
        val result = DocumentLabel.sanitiseAndValidate(label)
        assertTrue(result is Outcome.Success)
        // String.length = 2 (surrogate pair), but should be counted as 1 code point.
        assertEquals(label, result.value)
    }

    @Test
    fun truncate_preserves_emoji_at_boundary() {
        // 39 chars + emoji = 40 code points exactly.
        val under = "А".repeat(39) + "📋"
        val truncated = DocumentLabel.truncate(under)
        // Should retain all 40 code points (truncate is no-op at exactly 40).
        assertEquals(under, truncated)
    }

    @Test
    fun truncate_long_label() {
        val raw = "А".repeat(100)
        val truncated = DocumentLabel.truncate(raw)
        // codePointCount of result should be 40.
        val codePoints = truncated.codePointCountCompat()
        assertEquals(40, codePoints)
    }

    // Test helper — mirrors private extension.
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
