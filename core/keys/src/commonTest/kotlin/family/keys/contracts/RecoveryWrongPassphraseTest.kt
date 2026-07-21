package family.keys.contracts

import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.RootKeyError
import family.keys.fakes.FakeRootKeyManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * **Fake-adapter contract test.** Verifies that [FakeRootKeyManager] correctly
 * signals [RootKeyError.WrongPassphrase] / Success based on its string-compared
 * seeded passphrase. The *cryptographic* WrongPassphrase invariant (Poly1305 auth
 * fail under real libsodium AEAD) is covered by
 * [family.keys.RecoveryFlowTest.recoveryWrongPassphraseReturnsWrongPassphrase].
 *
 * (T627, FR-019, A3)
 */
class RecoveryWrongPassphraseTest {

    @Test
    fun recoverWithWrongPassphraseReturnsWrongPassphraseError() = runTest {
        val rootMgr = FakeRootKeyManager()
        val identity = AuthIdentity("user-stable-id-123", null, null)

        // Seed a key with a required passphrase
        rootMgr.seedKey(identity.stableId, ByteArray(32) { 0x42 }, passphrase = "correct-secret-passphrase")

        // Attempt recovery with wrong passphrase
        val result = rootMgr.recover(identity, "wrong-passphrase".toCharArray())

        assertIs<Outcome.Failure<RootKeyError>>(result)
        assertEquals(RootKeyError.WrongPassphrase, result.error)
    }

    @Test
    fun recoverWithCorrectPassphraseSucceeds() = runTest {
        val rootMgr = FakeRootKeyManager()
        val identity = AuthIdentity("user-stable-id-123", null, null)

        rootMgr.seedKey(identity.stableId, ByteArray(32) { 0x42 }, passphrase = "correct-secret-passphrase")

        val result = rootMgr.recover(identity, "correct-secret-passphrase".toCharArray())
        assertIs<Outcome.Success<*>>(result)
    }
}
