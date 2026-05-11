package com.launcher.api.identity

/**
 * The Managed device (e.g. бабушкин телефон). Do not confuse with
 * `managedDeviceId` (stable UUIDv4 in DataStore, FR-001) — `firebaseAuthUid`
 * here is the short-lived Firebase Auth UID and may rotate on reinstall
 * (research.md §Identity rotation).
 *
 * TODO(exit-ramp): on migration to named auth (OWD-2), `linkWithCredential`
 * preserves the UID across reinstalls; `managedDeviceId` remains the stable id.
 */
@JvmInline
value class ManagedIdentity(override val firebaseAuthUid: String) : Identity {
    companion object {
        fun anonymous(uid: String): ManagedIdentity = ManagedIdentity(uid)
    }
}
