package cryptokit.keys.api

/**
 * Provider of a stable, device-local [StableId]-shaped namespace used by
 * [KeyRegistry] when no [AuthIdentity] is available (US-4 local-only mode,
 * spec.md §Local-only mode).
 *
 * **Why a separate port from [cryptokit.keys.api.internal.DeviceIdentity]**:
 * `DeviceIdentity` owns the device's X25519 keypair for envelope sharing
 * (F-5b). This provider owns only a string-shaped namespace for key
 * derivation. They share device-local lifecycle but distinct purposes —
 * fold them and a future spec that wants to rotate one without the other
 * would have to choose a side. Keeping them split is consistent with
 * CLAUDE.md rule 1 (each port owns one external surface).
 *
 * **Lifecycle**:
 *  - First call: generate a random UUID v4, persist locally (Keystore +
 *    DataStore on Android; in-memory on test fakes).
 *  - Subsequent calls: return the cached value.
 *  - App uninstall / data clear: namespace is regenerated next launch.
 *    Any data encrypted under the old namespace becomes unrecoverable —
 *    this is the accepted residual for local-only mode (no recovery
 *    surface without identity, per spec.md US-4).
 *
 * **Returned value** has the same shape as [StableId] (UUID v4 string)
 * so it can be passed to [KeyRegistry.derive] without ceremony. It is
 * **not** a [StableId] in the type system because semantically it is
 * device-local, not identity-provider-issued.
 */
interface DeviceKeyNamespaceProvider {
    /**
     * Returns the device-local namespace. Idempotent: subsequent calls
     * return the same value for the lifetime of the install.
     */
    suspend fun namespace(): StableId
}
