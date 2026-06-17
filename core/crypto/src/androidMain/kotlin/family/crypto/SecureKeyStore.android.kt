package family.crypto.api

import android.content.Context
import family.crypto.api.values.KeyId
import family.crypto.exception.CryptoException

/**
 * Android actual — **Phase 6 placeholder**. Real wrap-pattern implementation lands
 * with T660 (LibsodiumAeadCipher + Android Keystore AES wrap). Until then this
 * actual throws on every call so anyone wiring it in a release build fails fast.
 *
 * TODO(spec-016-phase-6): replace with real Android Keystore wrap implementation
 * per research.md §R3 (AES-256-GCM wrap key in TEE, KeyBlob persisted to
 * `/data/data/<pkg>/files/keys/${keyId.raw}.blob`).
 */
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {

    @Suppress("UNUSED_PARAMETER")
    private val androidContext: Context = context.androidContext

    actual suspend fun store(keyId: KeyId, secret: ByteArray): Unit =
        throw CryptoException.KeystoreUnavailable(
            "Android SecureKeyStore not yet implemented (spec 016 Phase 6, T660)."
        )

    actual suspend fun load(keyId: KeyId): ByteArray? =
        throw CryptoException.KeystoreUnavailable(
            "Android SecureKeyStore not yet implemented (spec 016 Phase 6, T660)."
        )

    actual suspend fun delete(keyId: KeyId): Unit =
        throw CryptoException.KeystoreUnavailable(
            "Android SecureKeyStore not yet implemented (spec 016 Phase 6, T660)."
        )
}

actual class KeyStoreContext(val androidContext: Context)
