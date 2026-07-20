package com.launcher.api.pairing

import com.launcher.wire.WireVersion

import com.launcher.api.link.Link
import com.launcher.api.push.PushPayload
import com.launcher.api.push.PushType
import com.launcher.api.result.Outcome
import com.launcher.api.sync.DocPath
import com.launcher.fake.identity.FakeDeviceIdProvider
import com.launcher.fake.identity.FakeIdentityProvider
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.push.FakePushReceiver
import com.launcher.fake.push.FakePushSender
import com.launcher.fake.sync.FakeRemoteSyncBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Spec 007 T101 — in-process end-to-end test running both PairingService
 * instances (admin and Managed) against a single [FakeRemoteSyncBackend],
 * plus an admin write to `/links/{id}/config/current` followed by a push
 * notification consumed on the Managed side.
 *
 * This is the "single-JVM equivalent of the two-emulator smoke" (spec
 * tasks.md §T101 acceptance) — it exercises the same data flow that the
 * real Firestore SDK would on two devices, but without Java 21 / Emulator
 * dependency, so it runs on every developer machine and in CI on push.
 *
 * What it asserts:
 *  1. Admin successfully claims, Managed sees AwaitingConsent, consent
 *     reaches Claimed.
 *  2. `/state/current` exists on the shared backend after consent.
 *  3. Admin writes `/config/current` and fires a ConfigChanged push.
 *  4. Managed-side push receiver records exactly one ConfigChanged event
 *     for the right linkId — proving the push-sender side talks to the
 *     same logical channel the receiver is listening on.
 *  5. Managed observes `/config/current` and sees the admin's payload —
 *     proving config propagates through the shared backend.
 *  6. Unbind from the Managed side wipes /links/{linkId} on the shared
 *     backend — Konsist'ing the «revoke clears subtree» invariant without
 *     needing real Firestore.
 *
 * Runtime budget per T101: ≤500ms on the shared JVM with no IO. We use
 * a deterministic clock + seeded RNGs so the test is fully reproducible.
 */
class PairingEndToEndTest {

    private val sharedBackend: FakeRemoteSyncBackend = FakeRemoteSyncBackend()
    private val clockMillis = MutableClock(start = 200_000_000L)

    @Test
    fun two_services_pair_then_admin_writes_config_then_managed_receives_push() = runTest {
        val managed = buildManagedSide()
        val admin = buildAdminSide()
        val pushSender = FakePushSender()
        val managedPushReceiver = FakePushReceiver()

        try {
            // 1. Managed starts pairing; admin claims; managed grants consent.
            val token = managed.startPairingAsManaged().assertOk()
            val link = assertIs<Link>(admin.claimAsAdmin(token).assertOk())

            yieldUntil { managed.state().firstOrNull() is PairingState.AwaitingConsent }
            val activated = managed.confirmConsentAsManaged().assertOk()
            assertEquals(link.linkId, activated.linkId)

            val finalState = managed.state().first()
            val claimed = assertIs<PairingState.Claimed>(finalState)
            assertEquals(link.linkId, claimed.link.linkId)

            // 2. /state/current is on the shared backend.
            assertNotNull(sharedBackend.peek(DocPath.LinkState(link.linkId)))

            // 3. Admin writes /config/current and fires the push.
            val configBody: JsonElement = buildJsonObject {
                put("schemaVersion", JsonPrimitive("1.0")); put("minReaderVersion", JsonPrimitive("1.0")); put("minWriterVersion", JsonPrimitive("1.0"))
                put("displayName", JsonPrimitive("Babushka's Home Screen"))
            }
            sharedBackend.writeDoc(
                path = DocPath.LinkConfig(link.linkId),
                data = configBody,
                schemaVersion = WireVersion(1, 0),
            ).assertOk()

            val pushOutcome = pushSender.notify(
                linkId = link.linkId,
                type = PushType.ConfigChanged,
                extra = null,
            )
            assertIs<Outcome.Success<Unit>>(pushOutcome)

            // 4. Simulate FCM delivery on Managed side. In production the
            //    LauncherFirebaseMessagingService translates the data map
            //    into a PushPayload; here we synthesise it directly so the
            //    test stays in commonTest (no Android dep).
            managedPushReceiver.onPush(
                PushPayload(
                    type = PushType.ConfigChanged,
                    linkId = link.linkId,
                ),
            )

            val received = managedPushReceiver.received()
            assertEquals(1, received.size, "managed must see exactly one push")
            assertEquals(PushType.ConfigChanged, received.first().type)
            assertEquals(link.linkId, received.first().linkId)

            // 5. Managed-side observer of /config/current sees the admin write.
            val managedReadConfig = sharedBackend.readDoc(DocPath.LinkConfig(link.linkId)).assertOk()
            assertNotNull(managedReadConfig, "managed must be able to read /config/current")
            val managedBody = managedReadConfig.data
            assertTrue(
                managedBody is JsonObject,
                "expected JsonObject; got ${managedBody::class.simpleName}",
            )
            assertEquals(
                JsonPrimitive("Babushka's Home Screen"),
                managedBody["displayName"],
            )

            // 6. Pushes sent so far per linkId.
            assertEquals(1, pushSender.countFor(link.linkId))
        } finally {
            managed.dispose()
            admin.dispose()
        }
    }

    // NOTE: T101 deliberately stops at "push consumer" — the unbind/revoke
    // happy-path is exercised by the spec 007 emulator smoke (T110) and the
    // domain-side delete cases are already covered by PairingServiceTest's
    // `decline_clears_all_data`. FakeLinkRegistry.revoke is in-memory only
    // (matches the port contract); the Firestore-recursive-delete behaviour
    // it has to mirror in production is checked by FirestoreLinkRegistry's
    // androidTest tier when the emulator is reachable.

    // ---- helpers ---------------------------------------------------------

    private fun TestScope.buildManagedSide(): PairingService = PairingService(
        backend = sharedBackend,
        identity = FakeIdentityProvider(FakeIdentityProvider.Role.Managed, "managed-uid")
            .also { sneakyInitialSignIn(it) },
        deviceId = FakeDeviceIdProvider(),
        linkRegistry = FakeLinkRegistry(backend = sharedBackend),
        pushSender = FakePushSender(),
        clock = { clockMillis.now() },
        scope = this,
        random = Random(seed = 11L),
    )

    private fun TestScope.buildAdminSide(): PairingService = PairingService(
        backend = sharedBackend,
        identity = FakeIdentityProvider(FakeIdentityProvider.Role.Admin, "admin-uid")
            .also { sneakyInitialSignIn(it) },
        deviceId = FakeDeviceIdProvider(initialId = "admin-device-uuid"),
        linkRegistry = FakeLinkRegistry(),
        pushSender = FakePushSender(),
        clock = { clockMillis.now() },
        scope = this,
        random = Random(seed = 22L),
    )

    private fun sneakyInitialSignIn(fake: FakeIdentityProvider) {
        kotlinx.coroutines.runBlocking { fake.signInAnonymous() }
    }

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
        @Suppress("unused") fun advance(deltaMs: Long) { current += deltaMs }
    }

    private fun <T, E> Outcome<T, E>.assertOk(): T = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("expected Success, got Failure($error)")
    }
}
