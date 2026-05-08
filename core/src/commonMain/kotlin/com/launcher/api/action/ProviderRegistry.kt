package com.launcher.api.action

import kotlinx.coroutines.flow.Flow

/**
 * Port for querying which providers are usable on this device right now.
 * Drives wizard provider-list filtering (US-507) and dispatcher availability
 * checks (spec 005 §7.1 step 3).
 *
 * Real implementation (`AndroidProviderRegistry`) observes `AppIndex.snapshot`
 * and emits debounced (1s) deduplicated states per Clarification C3.
 *
 * Test double: [`FakeProviderRegistry`](commonTest) lets tests programmatically
 * pin per-provider availability and emit updates.
 */
interface ProviderRegistry {

    /** Synchronous probe of current availability for one provider. */
    fun availability(providerId: ProviderId): ProviderAvailability

    /** Snapshot of every known provider's current state. */
    fun snapshot(): List<ProviderState>

    /**
     * Hot stream of state updates. Emits a fresh full snapshot when the set of
     * *available* providers changes; debounced 1s, distinct per Clarification C3.
     * Source / frequency / threading / battery cost (Article VI §6) is documented
     * on the implementation, not on this port.
     */
    val updates: Flow<List<ProviderState>>
}

/** Per-provider record exposed to UI (wizard, settings, diagnostics). */
data class ProviderState(
    val providerId: ProviderId,
    val availability: ProviderAvailability,
    /** Resolved package, when applicable (whatsapp / telegram / youtube — null for `phone`, `sms`, `browser` umbrella providers). */
    val installedPackage: String? = null,
    /** Optional override; when null, UI uses `Res.string.provider_name_<id>` from strings_actions.xml. */
    val displayName: String? = null,
)

/** Three-state availability. UI dispatches accordingly: show / install / hide. */
sealed class ProviderAvailability {

    data object Available : ProviderAvailability()

    /** Not installed but installable. UI offers store install via [InstallHint]. */
    data class Missing(val installHint: InstallHint?) : ProviderAvailability()

    /** Cannot be made available on this device — no SIM, no browser, etc. UI greys out. */
    data class NotApplicable(val reason: NotApplicableReason) : ProviderAvailability()
}

data class InstallHint(
    /** Primary deep-link, e.g. `market://details?id=com.whatsapp`. */
    val storeUrl: String,
    /** Web fallback, e.g. `https://play.google.com/store/apps/details?id=...`. */
    val webStoreUrl: String,
    /** Recommended package name; UI may surface "install WhatsApp from Play". */
    val recommendedPackage: String,
)

enum class NotApplicableReason {
    /** Tablet without `FEATURE_TELEPHONY`. */
    NoTelephony,

    /** Device with no app responding to `ACTION_VIEW(http/https)`. */
    NoBrowser,

    /** Device with no default SMS handler (`Telephony.Sms.getDefaultSmsPackage`). */
    NoDefaultSmsApp,
}
