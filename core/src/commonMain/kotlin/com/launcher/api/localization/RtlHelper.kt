package com.launcher.api.localization

/** Reading direction independent of Compose's androidx.compose.ui.unit.LayoutDirection. */
enum class ReadingDirection { Ltr, Rtl }

/**
 * Returns the appropriate reading direction for the given BCP-47 tag.
 * FR-032.
 *
 * RTL languages currently in scope: Arabic (ar), Hebrew (he), Persian (fa),
 * Urdu (ur), Pashto (ps). Hindi (hi) is LTR (Devanagari), kept here only
 * because it sits next to AR/HE in many tag lists.
 */
fun readingDirectionFor(localeTag: String): ReadingDirection {
    val primary = localeTag.substringBefore('-').lowercase()
    return when (primary) {
        "ar", "he", "fa", "ur", "ps", "iw", "yi" -> ReadingDirection.Rtl
        else -> ReadingDirection.Ltr
    }
}
