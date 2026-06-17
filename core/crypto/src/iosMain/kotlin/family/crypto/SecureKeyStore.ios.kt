package family.crypto.api

import family.crypto.api.values.KeyId
import family.crypto.exception.CryptoException

/**
 * iOS actual — stub-screamer per Clarifications Q1 (spec 016).
 *
 * The contract is fixed day 1 so consumer code can dependency-inject `SecureKeyStore`
 * on iOS without conditional compilation. Real Keychain-backed implementation lands
 * with V-1 (iOS Admin Preset). Every call here throws clearly.
 *
 * TODO(physical-mac): iOS build verification on macOS host; replace stub when V-1 ships.
 */
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {

    actual suspend fun store(keyId: KeyId, secret: ByteArray): Unit =
        throw CryptoException.NotImplementedOnIos(
            "SecureKeyStore iOS adapter — see V-1 (iOS Admin Preset) spec"
        )

    actual suspend fun load(keyId: KeyId): ByteArray? =
        throw CryptoException.NotImplementedOnIos(
            "SecureKeyStore iOS adapter — see V-1 (iOS Admin Preset) spec"
        )

    actual suspend fun delete(keyId: KeyId): Unit =
        throw CryptoException.NotImplementedOnIos(
            "SecureKeyStore iOS adapter — see V-1 (iOS Admin Preset) spec"
        )
}

actual class KeyStoreContext
