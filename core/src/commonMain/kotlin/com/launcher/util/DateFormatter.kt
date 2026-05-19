package com.launcher.util

/**
 * Spec 010 T089 — locale-aware date formatter for «дата привязки» rendering
 * в PairedDevicesScreen (FR-030, CHK-localization-007).
 *
 * KMP `expect` so each platform plugs in its native locale-aware formatter:
 *  - androidMain → `java.text.DateFormat.getDateInstance(SHORT, Locale.getDefault())`
 *  - iosMain → `NSDateFormatter` with `.shortStyle` (когда iOS таргет
 *    активируется в будущем спеке).
 *
 * The senior-safe rendering rule (CHK-elderly-007): short format only —
 * «20.05.2026» rather than «May 20, 2026 12:34:56» — to keep cognitive load
 * low. Locale flips between «20.05.2026» (ru) and «5/20/26» (en-US).
 *
 * Implementations MUST never crash on out-of-range epoch values; instead
 * return an empty string, since «дата привязки» is supplementary copy,
 * not load-bearing.
 */
expect object DateFormatter {
    /** Formats epoch millis as a short-locale date string. */
    fun formatShortDate(epochMillis: Long): String
}

/**
 * Spec 010 T094 — platform `currentTimeMillis` helper. Lives next to
 * DateFormatter because both lean on epoch-millis platform APIs.
 *
 *  - androidMain → `System.currentTimeMillis()`
 *  - iosMain → `(NSDate().timeIntervalSince1970 * 1000).toLong()` stub.
 *
 * Used by the 7-tap detector / Modifier where a [Long] timestamp is needed
 * but `kotlinx.datetime` is not on the dependency list (keeps the core
 * artefact lean).
 */
expect fun nowEpochMillis(): Long
