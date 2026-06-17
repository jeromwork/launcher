package family.crypto.api

import family.crypto.api.values.KeyId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class InMemorySecureKeyStoreTest {

    private val store = SecureKeyStore(KeyStoreContext())

    @Test
    fun storeAndLoadRoundtrip() = runTest {
        val id = KeyId("config-admin-identity-v1")
        val secret = ByteArray(32) { it.toByte() }
        store.store(id, secret)
        val loaded = store.load(id)
        assertContentEquals(secret, loaded)
    }

    @Test
    fun loadMissingReturnsNull() = runTest {
        val id = KeyId("media-photo-album-v1")
        assertNull(store.load(id))
    }

    @Test
    fun deleteIsIdempotent() = runTest {
        val id = KeyId("__internal-wrap-key-v1")
        store.delete(id) // no throw
        store.store(id, byteArrayOf(1, 2, 3))
        store.delete(id)
        assertNull(store.load(id))
    }
}
