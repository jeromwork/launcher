package family.keys.fitness

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import cryptokit.crypto.api.KeyStoreContext
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.impl.RootKeyManagerImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Fitness function — H-4 finding (rejected empty UID at construction time).
 *
 * Empty `AuthIdentity.stableId` MUST be rejected, otherwise all
 * configs / vaults across UID-less users would collide in the same backend
 * namespace.
 *
 * Coverage in F-5b envelope architecture:
 *  - [RootKeyManagerImpl] still rejects empty UID (preserved here).
 *  - The envelope cipher / remote storage path enforces `namespace.isNotEmpty()`
 *    at port level (see [family.keys.api.RemoteStorage] / [family.keys.impl.EnvelopeRemoteStorage]) —
 *    those are covered by [family.keys.EnvelopeRemoteStorageTest].
 */
class EmptyUidRejectionTest {

    @Test
    fun rootKeyManagerRejectsEmptyStableId() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        val mgr = RootKeyManagerImpl(
            secureKeyStore = SecureKeyStore(KeyStoreContext()),
            random = LibsodiumRandomSource(),
            aead = LibsodiumAeadCipher()
        )
        assertFailsWith<IllegalArgumentException> {
            mgr.getOrCreate(AuthIdentity("", null, null))
        }
    }
}
