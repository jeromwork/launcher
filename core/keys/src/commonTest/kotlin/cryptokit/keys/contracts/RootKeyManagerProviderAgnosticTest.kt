package cryptokit.keys.contracts

import cryptokit.keys.api.AuthAvailabilityStatus
import cryptokit.keys.api.AuthIdentity
import cryptokit.keys.api.AvailabilityReason
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RootKey
import cryptokit.keys.fakes.FakeAuthAvailability
import cryptokit.keys.fakes.FakeRootKeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Contract test: RootKeyManager works with provider-agnostic fakes (T626, SC-009, US-6).
 *
 * Proves US-1 + US-2 scenarios through FakeAuthAdapter (provider-agnostic) —
 * никакого Google/Firebase в цепочке.
 */
class RootKeyManagerProviderAgnosticTest {

    private val aliceId = AuthIdentity("alice-stable-id-a1b2c3", null, null)
    private val bobId = AuthIdentity("bob-stable-id-x9y8z7", null, null)

    @Test
    fun us1CreateRootKeyWithFakeAvailability() = runTest {
        val rootMgr = FakeRootKeyManager()
        val availability = FakeAuthAvailability()

        // Check availability — must be Available by default.
        val status = availability.check()
        assertIs<AuthAvailabilityStatus.Available>(status)

        // Create root key (US-1 path).
        val result = rootMgr.create(aliceId)
        assertIs<Outcome.Success<RootKey>>(result, "create() MUST succeed when provider is available")
    }

    /**
     * Verifies that the provider-agnostic `RootKeyManager.recover()` surface returns
     * a Success Outcome through a fake adapter — i.e. the **API shape** is reachable
     * without any Google/Firebase dependency.
     *
     * **NOT a cryptographic recovery test.** Real US-2 byte-equal cross-device
     * recovery (Argon2id derive → AEAD unwrap → seedFromRecovery → byte-equal root)
     * lives in [cryptokit.keys.RecoveryFlowTest.recoveryRoundtripBytewise].
     * This test would still pass if `recover()` were a constant `Success` — its only
     * job is to prove the port can be hit through the fake adapter.
     */
    @Test
    fun recoverIsReachableThroughProviderAgnosticFakeApi() = runTest {
        val rootMgr = FakeRootKeyManager()

        val createResult = rootMgr.create(aliceId)
        assertIs<Outcome.Success<RootKey>>(createResult)

        val recoverResult = rootMgr.recover(aliceId, charArrayOf('p', 'a', 's', 's'))
        assertIs<Outcome.Success<RootKey>>(recoverResult, "recover() MUST be callable through fake adapter")
    }

    @Test
    fun unavailableProviderDocumentedInStatus() = runTest {
        val availability = FakeAuthAvailability()
        availability.setUnavailable(AvailabilityReason.NoSupportedProvider)

        val status = availability.check()
        assertIs<AuthAvailabilityStatus.Unavailable>(status)
        // No Google/Firebase/OAuth mentions in domain enum.
        assertIs<AvailabilityReason>(status.reason)
    }

    @Test
    fun currentFlowEmitsNullAfterForget() = runTest {
        val rootMgr = FakeRootKeyManager()
        rootMgr.create(aliceId)
        rootMgr.forget(aliceId)
        // current emits null after forget (StateFlow always gives latest value).
        val currentValue = rootMgr.current.first()
        assertNull(currentValue, "After forget, current MUST emit null")
    }

    @Test
    fun twoIdentitiesGetDifferentKeys() = runTest {
        val rootMgr = FakeRootKeyManager()
        val aliceResult = rootMgr.create(aliceId)
        val bobResult = rootMgr.create(bobId)

        assertIs<Outcome.Success<RootKey>>(aliceResult)
        assertIs<Outcome.Success<RootKey>>(bobResult)

        val aliceBytes = aliceResult.value.bytes
        val bobBytes = bobResult.value.bytes

        kotlin.test.assertFalse(
            aliceBytes.contentEquals(bobBytes),
            "Different identities MUST get different root keys (FR-031)"
        )
    }
}
