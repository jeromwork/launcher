package com.launcher.api.identity

import kotlinx.coroutines.flow.Flow

/**
 * Port over the stable per-install device id (UUIDv4) persisted in DataStore
 * (spec 007 §FR-001). Lives in `commonMain` so the domain (`PairingService`)
 * can read it without touching `androidx.datastore.*`.
 *
 *  - `DataStoreDeviceIdProvider` (androidMain) — backed by
 *    `com.launcher.pairing.identity_v1` per data-model.md §Persistence.
 *  - `FakeDeviceIdProvider` (commonTest) — fixed UUID with explicit override.
 *
 * The id is **generated once on first launch and never deleted** even on
 * revoke — it must survive across pairings.
 */
interface DeviceIdProvider {
    /** Hot Flow; emits the current id immediately and on every regeneration. */
    fun currentDeviceId(): Flow<String>

    /** Force a new UUID. Used only by tests or admin "reset device id" flow
     *  (not part of normal pairing). */
    suspend fun regenerate()
}
