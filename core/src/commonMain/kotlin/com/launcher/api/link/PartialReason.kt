package com.launcher.api.link

import kotlinx.serialization.Serializable

/**
 * Reasons why apply на Managed could only partially succeed (spec 008 §FR-033,
 * contracts/state-applied.md).
 *
 * **Enum keys, not strings**: client maps these to localized user-facing text.
 * Per ux-quality CHK011: don't ship localized strings в wire format.
 *
 * Categorical → enables rate measurement (failure-recovery CHK017).
 */
@Serializable
enum class PartialReason {
    /** Provider for slot.kind not installed на Managed (e.g. dialer missing). */
    ProviderUnavailable,

    /** READ_CONTACTS permission revoked (specs 011 contacts). */
    ContactPermissionDenied,

    /** E2E-encrypted media (spec 011) couldn't be decrypted на Managed. */
    MediaDecryptFailed,

    /** Slot.kind unknown to current Managed app version (forward-compat). */
    UnknownSlotKind,
}
