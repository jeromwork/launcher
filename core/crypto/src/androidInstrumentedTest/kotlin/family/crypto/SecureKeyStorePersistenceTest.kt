package family.crypto

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import family.crypto.api.InMemoryKeyBlobStore
import family.crypto.api.KeyStoreContext
import family.crypto.api.SecureKeyStore
import family.crypto.api.values.KeyId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 SC-008 + Сценарий 3 (первый запуск + identity ключ): store→load roundtrip
 * против реального Android Keystore TEE wrap pattern. Запускается на эмуляторе или
 * физическом устройстве.
 */
@RunWith(AndroidJUnit4::class)
class SecureKeyStorePersistenceTest {

    private val ctx = KeyStoreContext(ApplicationProvider.getApplicationContext(), InMemoryKeyBlobStore())
    private val store = SecureKeyStore(ctx)
    private val testKeyId = KeyId("__internal-test-key-v1")

    @After
    fun cleanup() = runTest {
        store.delete(testKeyId)
    }

    @Test
    fun storeAndLoadRoundtrip() = runTest {
        val secret = ByteArray(32) { it.toByte() }
        store.store(testKeyId, secret)
        val loaded = store.load(testKeyId)
        assertArrayEquals(secret, loaded)
    }

    @Test
    fun loadMissingKeyReturnsNull() = runTest {
        val missing = KeyId("__internal-never-stored-v1")
        val result = store.load(missing)
        assert(result == null) { "expected null for missing keyId, got $result" }
    }

    @Test
    fun deleteIsIdempotent() = runTest {
        store.delete(testKeyId)
        store.store(testKeyId, byteArrayOf(1, 2, 3))
        store.delete(testKeyId)
        store.delete(testKeyId) // second delete must not throw
        assert(store.load(testKeyId) == null)
    }
}
