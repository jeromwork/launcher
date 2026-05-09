package com.launcher.api.alerts

/**
 * Domain enumeration of crisis-banner types displayed in the launcher home
 * screen. Source of FR-026, FR-027, FR-031.
 *
 * Stack order when multiple banners visible (FR-031): [Airplane] above [Mute].
 * Multiple banners can coexist; user dismisses by fixing the underlying state,
 * not by gesture (FR-030).
 *
 * Reserved for spec 013: `NoInternet`. When added, slot below [Mute] in stack
 * order — losing connectivity is **less** disruptive to local interaction
 * than airplane mode or muted ringer.
 */
sealed class AlertBanner {
    data object Airplane : AlertBanner()
    data object Mute : AlertBanner()
    // data object NoInternet — claimed by spec 013, not implemented here.
}
