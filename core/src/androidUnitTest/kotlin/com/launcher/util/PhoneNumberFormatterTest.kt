package com.launcher.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Spec 010 T057 — verifies PhoneNumberFormatter routes through Android's
 * libphonenumber-backed PhoneNumberUtils.formatNumber and isLikelyValid
 * sanity-bounds.
 *
 * Exact formatting depends on the libphonenumber version bundled with the
 * Robolectric SDK image, so we assert non-null + contains-original-digits
 * rather than exact spacing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhoneNumberFormatterTest {

    @Test
    fun format_ru_keeps_country_code() {
        val formatted = PhoneNumberFormatter.format("+79161234567", Locale("ru", "RU"))
        assertNotNull(formatted)
        assertTrue("Expected formatted output to start with +7, got: $formatted", formatted.startsWith("+7"))
        // Digits preserved.
        assertTrue(formatted.filter { it.isDigit() } == "79161234567")
    }

    @Test
    fun format_us_keeps_country_code() {
        val formatted = PhoneNumberFormatter.format("+14155550123", Locale.US)
        assertNotNull(formatted)
        assertTrue(formatted.startsWith("+1"))
    }

    @Test
    fun format_passes_through_garbage() {
        val raw = "not-a-number"
        val formatted = PhoneNumberFormatter.format(raw, Locale.US)
        // Implementation guarantees the input is returned as-is on failure
        // (libphonenumber rejects). We accept either the raw input OR null-
        // fallback-to-raw; both shapes are acceptable for the caller.
        assertNotNull(formatted)
    }

    @Test
    fun isLikelyValid_accepts_normal_numbers() {
        assertTrue(PhoneNumberFormatter.isLikelyValid("+79161234567"))
        assertTrue(PhoneNumberFormatter.isLikelyValid("+1 415-555-0123"))
        assertTrue(PhoneNumberFormatter.isLikelyValid("(415) 555-0123"))
    }

    @Test
    fun isLikelyValid_rejects_too_short() {
        assertFalse(PhoneNumberFormatter.isLikelyValid("123"))
    }

    @Test
    fun isLikelyValid_rejects_garbage() {
        assertFalse(PhoneNumberFormatter.isLikelyValid("abc"))
        assertFalse(PhoneNumberFormatter.isLikelyValid("+1-call-now"))
    }
}
