package com.launcher.api.pairing

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.link.Link
import com.launcher.api.push.PushType
import com.launcher.api.result.Outcome
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.fake.identity.FakeDeviceIdProvider
import com.launcher.fake.identity.FakeIdentityProvider
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.push.FakePushSender
import com.launcher.fake.sync.FakeRemoteSyncBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Domain tests for [PairingService] (T083). Exercises the full pairing
 * FSM against shared [FakeRemoteSyncBackend] instances so the admin and
 * Managed sides talk to the same "Firestore" — matching the data flow
 * diagram in plan.md §Data flow — Pairing happy path.
 *
 *  - pair_happy_path: full Managed→admin handshake with consent allow.
 *  - expired_token_rejected: admin scans an expired QR.
 *  - already_claimed_token_rejected: second admin tries to claim used token.
 *  - decline_clears_all_data: Managed refuses consent → /pairings and
 *    /links subtree fully removed.
 *  - cancel_before_claim_idempotent: Managed toggles off before admin scans.
 */
class PairingServiceTest {

    private val sharedBackend: FakeRemoteSyncBackend = FakeRemoteSyncBackend()
    private val clockMillis = MutableClock(start = 100_000_000L)

    @Test
    fun pair_happy_path() = runTest {
        val managed = buildManagedSide(backend = sharedBackend)
        val admin = buildAdminSide(backend = sharedBackend)

        val token = managed.startPairingAsManaged().assertOk()

        // Admin scans → claim transaction.
        val bootstrap = admin.claimAsAdmin(token).assertOk()
        val link = assertIs<Link>(bootstrap)
        assertEquals("admin-uid", link.adminId.firebaseAuthUid)
        assertEquals(FakeDeviceIdProvider.DEFAULT_ID, link.managedDeviceId)

        // Pump the observer so the Managed FSM picks up claimed=true.
        yieldUntil { managed.state().firstOrNull() is PairingState.AwaitingConsent }

        val awaiting = assertIs<PairingState.AwaitingConsent>(managed.state().first())
        assertEquals(link.linkId, awaiting.linkId)
        assertEquals(link.adminId.firebaseAuthUid, awaiting.adminId)

        // Managed grants consent.
        val activated = managed.confirmConsentAsManaged().assertOk()
        assertEquals(link.linkId, activated.linkId)

        // State reaches Claimed.
        val finalState = managed.state().first()
        val claimed = assertIs<PairingState.Claimed>(finalState)
        assertEquals(link.linkId, claimed.link.linkId)

        // /state/current was written by Managed.
        assertNotNull(sharedBackend.peek(DocPath.LinkState(link.linkId)))
    }

    @Test
    fun expired_token_rejected() = runTest {
        val managed = buildManagedSide(backend = sharedBackend)
        val admin = buildAdminSide(backend = sharedBackend)
        try {
            val token = managed.startPairingAsManaged().assertOk()

            // Jump past the 5-minute TTL.
            clockMillis.advance(PairingService.TOKEN_TTL_MS + 1)

            val result = admin.claimAsAdmin(token)
            assertIs<Outcome.Failure<PairingError>>(result)
            assertEquals(PairingError.TokenExpired, result.error)
        } finally {
            managed.dispose()
        }
    }

    @Test
    fun already_claimed_token_rejected() = runTest {
        val managed = buildManagedSide(backend = sharedBackend)
        val admin1 = buildAdminSide(backend = sharedBackend, adminUid = "admin-1")
        val admin2 = buildAdminSide(backend = sharedBackend, adminUid = "admin-2")
        try {
            val token = managed.startPairingAsManaged().assertOk()
            admin1.claimAsAdmin(token).assertOk()

            val secondClaim = admin2.claimAsAdmin(token)
            assertIs<Outcome.Failure<PairingError>>(secondClaim)
            assertEquals(PairingError.TokenAlreadyClaimed, secondClaim.error)
        } finally {
            managed.dispose()
        }
    }

    @Test
    fun decline_clears_all_data() = runTest {
        val managed = buildManagedSide(backend = sharedBackend)
        val admin = buildAdminSide(backend = sharedBackend)

        val token = managed.startPairingAsManaged().assertOk()
        val link = admin.claimAsAdmin(token).assertOk() as Link

        yieldUntil { managed.state().firstOrNull() is PairingState.AwaitingConsent }

        managed.declineConsentAsManaged().assertOk()

        // Both /pairings/{token} and /links/{linkId} are gone.
        assertNull(sharedBackend.peek(DocPath.Pairings(token)))
        assertNull(sharedBackend.peek(DocPath.Links(link.linkId)))
        // State back to Idle.
        assertEquals(PairingState.Idle, managed.state().first())
    }

    @Test
    fun cancel_before_claim_clears_token() = runTest {
        val managed = buildManagedSide(backend = sharedBackend)

        val token = managed.startPairingAsManaged().assertOk()
        assertNotNull(sharedBackend.peek(DocPath.Pairings(token)))

        managed.cancelPairingAsManaged().assertOk()

        assertNull(sharedBackend.peek(DocPath.Pairings(token)))
        assertEquals(PairingState.Idle, managed.state().first())
    }

    // ---- helpers ---------------------------------------------------------

    private fun TestScope.buildManagedSide(
        backend: RemoteSyncBackend,
    ): PairingService = PairingService(
        backend = backend,
        identity = FakeIdentityProvider(FakeIdentityProvider.Role.Managed, "managed-uid")
            .also { sneakyInitialSignIn(it) },
        deviceId = FakeDeviceIdProvider(),
        linkRegistry = FakeLinkRegistry(backend = backend),
        pushSender = FakePushSender(),
        clock = { clockMillis.now() },
        scope = this,
        random = Random(seed = 1L),
    )

    private fun TestScope.buildAdminSide(
        backend: RemoteSyncBackend,
        adminUid: String = "admin-uid",
    ): PairingService = PairingService(
        backend = backend,
        identity = FakeIdentityProvider(FakeIdentityProvider.Role.Admin, adminUid)
            .also { sneakyInitialSignIn(it) },
        deviceId = FakeDeviceIdProvider(initialId = "admin-device-uuid"),
        // Admin side doesn't act on linkRegistry for spec 007 (spec 009 will).
        linkRegistry = FakeLinkRegistry(),
        pushSender = FakePushSender(),
        clock = { clockMillis.now() },
        scope = this,
        random = Random(seed = 2L),
    )

    /** PairingService.currentUid() reads from identity.currentIdentity(); the
     *  Fake exposes that after a signInAnonymous call. We pre-warm here so
     *  PairingService methods don't have to sign in inside the test. */
    private fun sneakyInitialSignIn(fake: FakeIdentityProvider) {
        // Trigger lazy init via a synchronous call. FakeIdentityProvider's
        // signInAnonymous is non-suspending in practice — it just sets state.
        kotlinx.coroutines.runBlocking { fake.signInAnonymous() }
    }

    /** Suspends until [predicate] is true, yielding to other coroutines. */
    private suspend fun yieldUntil(timeoutSteps: Int = 100, predicate: suspend () -> Boolean) {
        repeat(timeoutSteps) {
            if (predicate()) return
            yield()
        }
        fail("yieldUntil: predicate stayed false after $timeoutSteps yields")
    }

    private class MutableClock(start: Long) {
        private var current: Long = start
        fun now(): Long = current
        fun advance(deltaMs: Long) { current += deltaMs }
    }

    private fun <T, E> Outcome<T, E>.assertOk(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected Success, got Failure($error)")
    }

    @Suppress("unused")
    private val keepImportsAlive: List<Any> = listOf(AdminIdentity::class, PushType::class, CoroutineScope::class)
}
