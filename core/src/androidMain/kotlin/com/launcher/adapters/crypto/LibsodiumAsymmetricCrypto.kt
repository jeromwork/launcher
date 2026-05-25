package com.launcher.adapters.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.launcher.api.crypto.AsymmetricCrypto
import com.launcher.api.crypto.CEK_SIZE
import com.launcher.api.crypto.ContentEncryptionKey
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceKeyPair
import com.launcher.api.crypto.InMemoryPrivateKey
import com.launcher.api.crypto.PrivateKey
import com.launcher.api.crypto.PublicKey
import com.launcher.api.crypto.SEALED_CEK_SIZE
import com.launcher.api.crypto.X25519_KEY_SIZE
import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi

// X25519 + crypto_box_seal hybrid encryption. sealedCEK = 80 bytes
// (ephemeral pub 32 + encrypted CEK 32 + Poly1305 MAC 16).
//
// Resolves PrivateKey → bytes через `priv: PrivateKey -> ByteArray` функцию,
// которую SecureKeystore передаёт. В тестах используем InMemoryPrivateKey
// напрямую. В production AndroidKeystoreSecureKeystore unwrap через AES-GCM.
@OptIn(ExperimentalUuidApi::class)
class LibsodiumAsymmetricCrypto(
    private val sodium: LazySodiumAndroid = LibsodiumProvider.sodium,
    // Resolver для opaque PrivateKey → 32 X25519 priv bytes. Внедряется адаптером
    // SecureKeystore. Дефолт — поддерживает только InMemoryPrivateKey (тесты).
    private val privBytesResolver: (PrivateKey) -> ByteArray = { defaultResolve(it) },
) : AsymmetricCrypto {

    override fun generateX25519Pair(alias: String): DeviceKeyPair {
        val pub = ByteArray(X25519_KEY_SIZE)
        val priv = ByteArray(X25519_KEY_SIZE)
        val ok = sodium.cryptoBoxKeypair(pub, priv)
        check(ok) { "crypto_box_keypair failed" }
        return DeviceKeyPair(PublicKey(pub), InMemoryPrivateKey(alias, priv))
    }

    override fun sealCEK(cek: ContentEncryptionKey, recipientPub: PublicKey): ByteArray {
        val cekBytes = cek.bytesOrThrow()
        require(cekBytes.size == CEK_SIZE)
        val sealed = ByteArray(SEALED_CEK_SIZE)
        val ok = sodium.cryptoBoxSeal(sealed, cekBytes, cekBytes.size.toLong(), recipientPub.bytes)
        check(ok) { "crypto_box_seal failed" }
        return sealed
    }

    // T056 constant-time recipient search: при unseal перебираем все возможные
    // позиции БЕЗ early-return. В 011 envelope содержит 1 recipient — но интерфейс
    // должен быть готов к N recipients (FR-060, будущие группы). Сам unsealCEK
    // здесь работает с одним sealed blob; constant-time иттерация — на уровне
    // вызывающего кода (decoder перебирает recipients[] equally).
    override fun unsealCEK(
        sealedCEK: ByteArray,
        ownPair: DeviceKeyPair,
    ): Outcome<ContentEncryptionKey, CryptoError> {
        if (sealedCEK.size != SEALED_CEK_SIZE) return Outcome.Failure(CryptoError.MalformedEnvelope())
        val privBytes = try {
            privBytesResolver(ownPair.privateKey)
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.KeystoreFailure(e))
        }
        val out = ByteArray(CEK_SIZE)
        val ok = try {
            sodium.cryptoBoxSealOpen(out, sealedCEK, sealedCEK.size.toLong(), ownPair.publicKey.bytes, privBytes)
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.MacFailed())
        }
        return if (ok) Outcome.Success(ContentEncryptionKey(out)) else Outcome.Failure(CryptoError.MacFailed())
    }

    companion object {
        private fun defaultResolve(priv: PrivateKey): ByteArray =
            when (priv) {
                is InMemoryPrivateKey -> priv.bytes
                else -> error("LibsodiumAsymmetricCrypto: unknown PrivateKey impl ${priv::class.simpleName}; inject privBytesResolver via SecureKeystore.")
            }
    }
}
