package com.launcher.api.config

import com.launcher.api.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 009 T034 — Contact.fromRaw domain validation (FR-026, FR-028).
 */
class ContactFromRawTest {

    @Test
    fun trim_strips_control_chars_preserves_unicode() {
        val r = Contact.fromRaw("  Маша  ", "+71234567890")
        assertTrue(r is Outcome.Success, "expected Success, got $r")
        assertEquals("Маша", r.value.displayName)
    }

    @Test
    fun empty_after_trim_returns_NameEmpty() {
        val r = Contact.fromRaw("   ", "+71234567890")
        assertTrue(r is Outcome.Failure)
        assertEquals(ValidationError.NameEmpty, r.error)
    }

    @Test
    fun only_control_chars_returns_NameInvalid() {
        // U+0001..U+001F are not whitespace — trim() leaves them — then
        // stripControlChars produces empty → NameInvalid (distinct from
        // NameEmpty for "only whitespace" per FR-026).
        val onlyControl = ""
        val r = Contact.fromRaw(onlyControl, "+71234567890")
        assertTrue(r is Outcome.Failure)
        assertTrue(r.error is ValidationError.NameInvalid, "got ${r.error}")
    }

    @Test
    fun empty_string_returns_NameEmpty() {
        val r = Contact.fromRaw("", "+71234567890")
        assertTrue(r is Outcome.Failure)
        assertEquals(ValidationError.NameEmpty, r.error)
    }

    @Test
    fun over_100_codepoints_returns_NameTooLong() {
        val longName = "А".repeat(101)
        val r = Contact.fromRaw(longName, "+71234567890")
        assertTrue(r is Outcome.Failure)
        val err = r.error
        assertTrue(err is ValidationError.NameTooLong, "got $err")
        assertEquals(101, err.actual)
    }

    @Test
    fun phone_strips_whitespace_dashes_parens() {
        val r = Contact.fromRaw("Маша", "+7 (123) 456-78.90")
        assertTrue(r is Outcome.Success, "expected Success, got $r")
        assertEquals("+71234567890", r.value.phoneNumber)
    }

    @Test
    fun phone_invalid_format_returns_PhoneInvalid() {
        val r = Contact.fromRaw("Маша", "12abc")
        assertTrue(r is Outcome.Failure)
        assertTrue(r.error is ValidationError.PhoneInvalid, "got ${r.error}")
    }

    @Test
    fun phone_empty_after_strip_returns_PhoneEmpty() {
        val r = Contact.fromRaw("Маша", "  - ( ) . ")
        assertTrue(r is Outcome.Failure)
        assertEquals(ValidationError.PhoneEmpty, r.error)
    }

    @Test
    fun phone_too_short_returns_PhoneInvalid() {
        val r = Contact.fromRaw("Маша", "1234")
        assertTrue(r is Outcome.Failure)
        assertTrue(r.error is ValidationError.PhoneInvalid)
    }

    @Test
    fun emoji_in_displayName_preserved() {
        val r = Contact.fromRaw("Маша 😍", "+71234567890")
        assertTrue(r is Outcome.Success, "expected Success, got $r")
        assertEquals("Маша 😍", r.value.displayName)
    }

    @Test
    fun string_of_only_emoji_is_valid() {
        val r = Contact.fromRaw("😍", "+71234567890")
        assertTrue(r is Outcome.Success, "expected Success, got $r")
    }
}
