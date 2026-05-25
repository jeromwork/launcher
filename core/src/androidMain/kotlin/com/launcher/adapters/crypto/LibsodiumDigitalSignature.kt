package com.launcher.adapters.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.interfaces.Sign
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceSigningKeyPair
import com.launcher.api.crypto.DigitalSignature
import com.launcher.api.crypto.ED25519_KEY_SIZE
import com.launcher.api.crypto.ED25519_SIGNATURE_SIZE
import com.launcher.api.crypto.InMemorySigningPrivateKey
import com.launcher.api.crypto.SigningPrivateKey
import com.launcher.api.crypto.SigningPublicKey
import com.launcher.api.result.Outcome

// Ed25519 signing через libsodium crypto_sign_detached / crypto_sign_verify_detached.
// 64-byte detached signature.
internal class LibsodiumDigitalSignature(
    private val sodium: LazySodiumAndroid = LibsodiumProvider.sodium,
    private val privBytesResolver: (SigningPrivateKey) -> ByteArray = { defaultResolve(it) },
) : DigitalSignature {

    override fun generateEd25519Pair(alias: String): DeviceSigningKeyPair {
        val pub = ByteArray(ED25519_KEY_SIZE)
        // libsodium Ed25519 secretkey = 64 bytes (seed 32 || pub 32). Используем
        // полный 64-byte вариант — он принимается crypto_sign_detached напрямую.
        val priv = ByteArray(Sign.SECRETKEYBYTES)
        val ok = sodium.cryptoSignKeypair(pub, priv)
        check(ok) { "crypto_sign_keypair failed" }
        return DeviceSigningKeyPair(SigningPublicKey(pub), InMemorySigningPrivateKey(alias, priv))
    }

    override fun sign(data: ByteArray, ownPair: DeviceSigningKeyPair): ByteArray {
        val privBytes = privBytesResolver(ownPair.privateKey)
        val sig = ByteArray(ED25519_SIGNATURE_SIZE)
        val ok = sodium.cryptoSignDetached(sig, data, data.size.toLong(), privBytes)
        check(ok) { "crypto_sign_detached failed" }
        return sig
    }

    override fun verify(
        data: ByteArray,
        signature: ByteArray,
        pubKey: SigningPublicKey,
    ): Outcome<Unit, CryptoError> {
        if (signature.size != ED25519_SIGNATURE_SIZE) {
            return Outcome.Failure(CryptoError.SignatureVerifyFailed(reason = "wrong size"))
        }
        val ok = try {
            sodium.cryptoSignVerifyDetached(signature, data, data.size, pubKey.bytes)
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.SignatureVerifyFailed(reason = "verify threw"))
        }
        return if (ok) Outcome.Success(Unit) else Outcome.Failure(CryptoError.SignatureVerifyFailed())
    }

    companion object {
        private fun defaultResolve(priv: SigningPrivateKey): ByteArray =
            when (priv) {
                is InMemorySigningPrivateKey -> priv.bytes
                else -> error("LibsodiumDigitalSignature: unknown SigningPrivateKey impl ${priv::class.simpleName}; inject privBytesResolver via SecureKeystore.")
            }
    }
}
