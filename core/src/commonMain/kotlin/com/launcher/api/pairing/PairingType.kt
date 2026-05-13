package com.launcher.api.pairing

/**
 * Discriminator for the wire-format field `/pairings/{token}.pairingType` so
 * one Firestore collection can host tokens for different trust-pairing
 * use-cases (admin-Managed link, trusted contact, call-trust-edge, …) without
 * a schema bump.
 *
 * Spec 007 ships the single subtype [AdminManagedLink]. Adding a new subtype
 * is **additive** — `schemaVersion` stays `1`; absent field defaults to
 * `AdminManagedLink` for backward-compat (contracts/pairing-token.md).
 */
enum class PairingType(val wireValue: String) {
    AdminManagedLink("admin-managed-link"),
    // TrustedContact("trusted-contact"),         // spec 011 — future
    // CallTrustEdge("call-trust-edge"),          // future jitsi spec
    // SubAdminLink("sub-admin-link"),            // future multi-admin spec
    // DeviceReplacement("device-replacement"),   // backlog config-portability
    ;

    companion object {
        /** Default applied when the field is absent in a v1 payload (backward-compat). */
        val DEFAULT: PairingType = AdminManagedLink

        fun fromWireOrNull(value: String?): PairingType? {
            if (value == null) return DEFAULT
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}
