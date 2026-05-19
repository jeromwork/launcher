package com.launcher.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Android `actual` of [DateFormatter] (spec 010 T089).
 *
 * Uses [DateFormat.SHORT] which under en-US renders «5/20/26» and под ru
 * «20.05.26»; both senior-safely terse.
 */
actual object DateFormatter {
    actual fun formatShortDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        return runCatching {
            DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
                .format(Date(epochMillis))
        }.getOrElse { "" }
    }
}
