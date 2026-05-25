package com.launcher.api.link

import com.launcher.api.result.Outcome
import com.launcher.api.sync.BackendError
import kotlinx.coroutines.flow.Flow

/**
 * Port for the **admin-side** view of paired devices. While [LinkRegistry]
 * holds the single current link of the Managed side, this port surfaces every
 * `/links/{linkId}` document where the current Firebase Auth uid equals
 * `adminId` — that is, every device the admin has paired.
 *
 * Why a separate port from [LinkRegistry]:
 *  - LinkRegistry is keyed by *me as a Managed device* (one link at most).
 *  - ManagedDevicesRegistry is keyed by *me as the admin* (potentially many).
 *  - The lifetimes differ (Managed: activate/revoke; Admin: claim/revoke).
 *  - Combining them would muddy single-link Managed invariants.
 *
 * **Implementations**:
 *  - `FirestoreManagedDevicesRegistry` (androidRealBackend) — backed by a
 *    Firestore listener on `links` collection filtered by `adminId == uid`.
 *  - `FakeManagedDevicesRegistry` (commonTest / mockBackend) — in-memory list
 *    mutated by [recordClaim] and [forgetLink], no real backend.
 *
 * Doctrine: this port is admin-side mutation surface for the pairing flow.
 * The `claimAsAdmin` transaction in PairingService notifies this registry on
 * success via [recordClaim] so observers get a Flow update without waiting on
 * the cloud listener round-trip. The Firestore listener stays as the source
 * of truth for cross-device sync (other admin sessions, revoke from Managed).
 */
interface ManagedDevicesRegistry {

    /** Hot Flow of all currently paired devices for the **current** admin uid.
     *  Emits `emptyList()` while not authenticated or while the listener is
     *  still attaching. Updates as documents are added or removed in Firestore. */
    fun observeAll(): Flow<List<Link>>

    /** Optimistic local insert — called by [com.launcher.api.pairing.PairingService]
     *  right after a successful claim transaction so the UI updates before
     *  the Firestore listener round-trip. Idempotent: a second call with the
     *  same `linkId` is a no-op. */
    fun recordClaim(link: Link)

    /** Optimistic local removal — called when the admin revokes a link from
     *  this device. Backend deletion is the source of truth; this just keeps
     *  the local Flow snappy. */
    fun forgetLink(linkId: String)

    /** Admin-side delete: removes `/links/{linkId}` on the backend AND from
     *  local state. Used by the «Удалить» button on the paired-devices screen
     *  and by reconnect-dedup when an orphan link from a prior pair must be
     *  pruned. Returns Success even if the doc was already absent. */
    suspend fun removeLinkOnServer(linkId: String): Outcome<Unit, BackendError>

    /** Look up an existing link for [managedDeviceId] under the current admin
     *  uid. Used by [com.launcher.api.pairing.PairingService.claimAsAdmin] to
     *  dedupe reconnects of the same managed device (issuing a new linkId would
     *  orphan the previous one). Returns null when no prior link exists. */
    suspend fun findByManagedDeviceId(managedDeviceId: String): Outcome<Link?, BackendError>

    /** Diagnostic stream of last ~10 events for on-screen debug banner.
     *  MVP-only — remove once the admin-side flow is verified end-to-end. */
    fun debugEvents(): Flow<List<String>>
}
