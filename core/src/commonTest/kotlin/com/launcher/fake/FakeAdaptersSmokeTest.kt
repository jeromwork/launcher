package com.launcher.fake

import family.wire.WireVersion

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.identity.ManagedIdentity
import com.launcher.api.link.Link
import com.launcher.api.pairing.PairingToken
import com.launcher.api.pairing.PairingType
import com.launcher.api.pairing.PairingWireFormat
import com.launcher.api.push.PushType
import com.launcher.api.result.Outcome
import com.launcher.api.sync.DocPath
import com.launcher.fake.identity.FakeDeviceIdProvider
import com.launcher.fake.identity.FakeIdentityProvider
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.push.FakePushSender
import com.launcher.fake.sync.FakeRemoteSyncBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Smoke test wiring all Fake adapters into one process to simulate the full
 * "admin device + managed device" pairing handshake against a shared
 * [FakeRemoteSyncBackend] (the in-memory equivalent of "both devices talk
 * to the same Firestore project").
 *
 * Verifies that the Fakes compose into a coherent system — the real
 * `PairingService` orchestration arrives in Phase 7, so this test exercises
 * the raw port surface end-to-end without that orchestrator.
 */
class FakeAdaptersSmokeTest {

    @Test
    fun two_sides_share_state_via_one_backend() = runTest {
        // Shared "Firestore" between admin and Managed.
        val backend = FakeRemoteSyncBackend()

        // Managed side
        val managedIdentity = FakeIdentityProvider(FakeIdentityProvider.Role.Managed, "uid-managed")
        val managedDeviceId = FakeDeviceIdProvider()
        val managedLinkRegistry = FakeLinkRegistry()

        // Admin side
        val adminIdentity = FakeIdentityProvider(FakeIdentityProvider.Role.Admin, "uid-admin")
        val pushSender = FakePushSender()

        // 1. Both sign in anonymously.
        val managedUid = managedIdentity.signInAnonymous().assertOk().firebaseAuthUid
        val adminUid = adminIdentity.signInAnonymous().assertOk().firebaseAuthUid
        assertTrue(managedIdentity.currentIdentity() is ManagedIdentity)
        assertTrue(adminIdentity.currentIdentity() is AdminIdentity)

        // 2. Managed creates a pairing token and writes /pairings/{token}.
        val token = PairingToken("A3KX9B")
        val managedDeviceUuid = managedDeviceId.currentDeviceId().first()
        val pairingDoc = PairingWireFormat.serialize(
            token = token,
            managedDeviceId = managedDeviceUuid,
            managedDeviceFirebaseUid = managedUid,
            expiresAt = 1_000_000_000_000L,
            claimed = false,
            pairingType = PairingType.AdminManagedLink,
        )
        backend.writeDoc(DocPath.Pairings(token), pairingDoc, WireVersion(1, 0))

        // 3. Admin reads the pairing doc (simulates QR scan + Firestore read).
        val read = backend.readDoc(DocPath.Pairings(token))
        val snapshot = (read as Outcome.Success).value
        assertNotNull(snapshot)
        val parsed = (PairingWireFormat.deserialize(snapshot.data) as Outcome.Success).value
        assertEquals(managedDeviceUuid, parsed.managedDeviceId)

        // 4. Admin seeds the local LinkRegistry (in real flow Phase 7 PairingService
        //    runs a Firestore transaction; here we model the post-claim state).
        val link = Link(
            linkId = "link-001",
            adminId = AdminIdentity(adminUid),
            managedDeviceId = managedDeviceUuid,
            managedDeviceFirebaseUid = managedUid,
            createdAt = 2_000_000_000_000L,
        )
        managedLinkRegistry.seedLink(link)
        assertEquals(link, managedLinkRegistry.currentLink().first())

        // 5. Admin pushes "config-changed" through the FakePushSender.
        pushSender.notify(link.linkId, PushType.ConfigChanged).assertOk()
        assertEquals(1, pushSender.countFor(link.linkId))

        // 6. Managed revokes — registry clears.
        managedLinkRegistry.revoke()
        assertEquals(null, managedLinkRegistry.currentLink().first())
    }

    private fun <T, E> Outcome<T, E>.assertOk(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected success, got Failure($error)")
    }
}
