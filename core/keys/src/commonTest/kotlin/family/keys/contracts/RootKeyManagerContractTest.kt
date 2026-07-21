package family.keys.contracts

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.AuthIdentity
import family.keys.api.Outcome
import family.keys.api.RootKey
import family.keys.impl.RootKeyManagerImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract test для [family.keys.api.RootKeyManager] (T029, FR-003, FR-031).
 */
class RootKeyManagerContractTest {

    private suspend fun make(): RootKeyManagerImpl {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
        return RootKeyManagerImpl(
            secureKeyStore = SecureKeyStore(KeyStoreContext()),
            random = LibsodiumRandomSource(),
            aead = LibsodiumAeadCipher()
        )
    }

    @Test
    fun getOrCreateTwiceReturnsSameKey() = runTest {
        val manager = make()
        val identity = AuthIdentity("uid-1", null, null)
        val first = (manager.getOrCreate(identity) as Outcome.Success<RootKey>).value
        val second = (manager.getOrCreate(identity) as Outcome.Success<RootKey>).value
        assertContentEquals(first.bytes, second.bytes)
        assertEquals(RootKey.SIZE, first.bytes.size)
    }

    @Test
    fun differentIdentitiesGetDifferentKeys() = runTest {
        val manager = make()
        val id1 = AuthIdentity("uid-alice", null, null)
        val id2 = AuthIdentity("uid-bob", null, null)
        val k1 = (manager.getOrCreate(id1) as Outcome.Success<RootKey>).value
        val k2 = (manager.getOrCreate(id2) as Outcome.Success<RootKey>).value
        assertTrue(!k1.bytes.contentEquals(k2.bytes), "Different identities MUST get different root keys (FR-031)")
    }

    @Test
    fun wipeRemovesKey() = runTest {
        val manager = make()
        val identity = AuthIdentity("uid-1", null, null)
        val first = (manager.getOrCreate(identity) as Outcome.Success<RootKey>).value
        val w = manager.wipe(identity)
        assertIs<Outcome.Success<Unit>>(w)
        val second = (manager.getOrCreate(identity) as Outcome.Success<RootKey>).value
        // После wipe регенерируется — новый ключ ≠ старому.
        assertTrue(!first.bytes.contentEquals(second.bytes), "After wipe getOrCreate generates fresh key")
    }

    @Test
    fun emptyStableIdRejected() = runTest {
        val manager = make()
        val identity = AuthIdentity("", null, null)
        val r = try {
            manager.getOrCreate(identity)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            true
        }
        assertTrue(r)
    }
}
