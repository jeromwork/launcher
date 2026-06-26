package cryptokit.crypto.api

import cryptokit.crypto.api.values.KeyId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * JVM actual — **TEST-ONLY** in-memory implementation.
 *
 * NOT for production: bytes are stored in a plain HashMap. Used by JVM unit tests
 * that need a working `SecureKeyStore` without an Android device / iOS Keychain.
 * Production paths use `androidMain`/`iosMain` actuals.
 */
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {

    private val store = HashMap<String, ByteArray>()
    private val mutex = Mutex()

    actual suspend fun store(keyId: KeyId, secret: ByteArray) {
        mutex.withLock { store[keyId.raw] = secret.copyOf() }
    }

    actual suspend fun load(keyId: KeyId): ByteArray? =
        mutex.withLock { store[keyId.raw]?.copyOf() }

    actual suspend fun delete(keyId: KeyId) {
        mutex.withLock { store.remove(keyId.raw) }
    }
}

actual class KeyStoreContext
