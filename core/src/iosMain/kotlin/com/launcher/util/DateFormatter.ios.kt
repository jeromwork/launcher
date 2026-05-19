package com.launcher.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * iOS `actual` of [DateFormatter] (spec 010 T089) — placeholder until iOS
 * lands in a future spec. Uses NSDateFormatter с current locale.
 */
@Suppress("CAST_NEVER_SUCCEEDS")
actual object DateFormatter {
    actual fun formatShortDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val formatter = NSDateFormatter().apply { dateStyle = NSDateFormatterShortStyle }
        val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
        return formatter.stringFromDate(date)
    }
}
