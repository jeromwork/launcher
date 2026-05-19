package com.launcher.util

import android.os.Build
import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * Spec 010 T057 — locale-aware phone-number formatter (FR-011, CHK-localization-008).
 *
 * Uses [PhoneNumberUtils.formatNumber] (Android's libphonenumber-backed
 * formatter) — available API 21+. The 2nd argument is the default ISO-3166
 * country code, derived from the system [Locale].
 *
 * Examples:
 *  - «+79161234567» (RU locale) → «+7 916 123-45-67» (Android may add hyphens
 *    or spaces — exact format depends on libphonenumber bundled in the OS).
 *  - «+14155550123» (US locale) → «+1 415-555-0123».
 *
 * On failure (libphonenumber rejects the input) — returns the original string
 * unchanged so the UI always shows *something*.
 */
object PhoneNumberFormatter {

    fun format(rawNumber: String, locale: Locale = Locale.getDefault()): String {
        val country = locale.country.takeIf { it.length == 2 } ?: "US"
        return try {
            // PhoneNumberUtils.formatNumber(String, String) is API 21+ (overload).
            PhoneNumberUtils.formatNumber(rawNumber, country)
                ?: rawNumber
        } catch (_: Exception) {
            rawNumber
        }
    }

    /**
     * Sanity check used by [CallConfirmationDialog] to grey out the CALL button
     * (FR-015). Requires a leading `+` или digit, then mostly digits / spaces /
     * dashes / parens, at least 5 digits total.
     */
    fun isLikelyValid(rawNumber: String): Boolean {
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.length < 5 || digits.length > 20) return false
        // Allowed chars: digits, +, spaces, dashes, parens, dots.
        return rawNumber.all { it.isDigit() || it in " +-(). " }
    }
}
