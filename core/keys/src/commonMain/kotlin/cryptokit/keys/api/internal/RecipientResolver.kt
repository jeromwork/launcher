package cryptokit.keys.api.internal

import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecipientPubKey

/**
 * Internal port: resolves the set of [RecipientPubKey] who must be able to
 * decrypt an entry written at `(namespace, key)`.
 *
 * Semantics — **resolved from the owner's perspective**, not the caller's:
 *  - All of [ownerNamespace] owner's own devices (own UID self-edit access).
 *  - All UIDs who hold an access-grant from the owner; for each such helper UID,
 *    all of *their* devices (so the helper can decrypt on any of their phones /
 *    tablets / TVs).
 *
 * Caller's UID never appears explicitly — if the caller is the owner, their
 * devices are in the first bucket; if the caller is a helper, their devices
 * are in the second bucket via the grant.
 *
 * Lookup data lives in the backend (Firestore today, own server later) under:
 *  - `/users/{ownerNamespace}/devices/{deviceId}/pub-key` for own devices,
 *  - `/users/{ownerNamespace}/access-grants/{helperUid}` for grants,
 *  - `/users/{helperUid}/devices/{deviceId}/pub-key` for helper devices.
 *
 * F-5b MVP: same `RecipientResolver` shape covers self-edit, multi-device
 * fan-out, and cross-user delegation. No code change when grants are added /
 * removed — only the resolver's output list changes.
 */
interface RecipientResolver {

    suspend fun resolveFor(
        ownerNamespace: String,
        key: String
    ): Outcome<List<RecipientPubKey>, ResolverError>
}

sealed class ResolverError {
    /** Backend round-trip failed. */
    data class Network(val cause: Throwable? = null) : ResolverError()

    /** Owner has zero published devices — cannot encrypt without at least one recipient. */
    data object OwnerHasNoDevices : ResolverError()

    /** Caller is not the owner and has no grant in the owner's namespace. */
    data object NoGrant : ResolverError()

    /** Backend rejected lookup (security rules). */
    data object Unauthorized : ResolverError()

    /** Stored grant or device document was malformed. */
    data class Malformed(val message: String) : ResolverError()
}
