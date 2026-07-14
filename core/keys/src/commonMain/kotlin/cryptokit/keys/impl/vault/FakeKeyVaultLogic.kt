package cryptokit.keys.impl.vault

import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.KeyDerivation
import cryptokit.crypto.api.values.Ciphertext as CryptoCiphertext
import cryptokit.crypto.api.values.Signature as CryptoSignature
import cryptokit.crypto.exception.CryptoException
import cryptokit.keys.api.vault.Aad
import cryptokit.keys.api.vault.Ciphertext
import cryptokit.keys.api.vault.KeyVault
import cryptokit.keys.api.vault.MacTag
import cryptokit.keys.api.vault.PublicIdentity
import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.RecoveryStrategy
import cryptokit.keys.api.vault.Signature
import cryptokit.keys.api.vault.VaultException

/**
 * Shared in-memory [KeyVault] logic used by:
 *  * `cryptokit.keys.impl.vault.FakeKeyVault` in `commonTest` — the mock-first fake per Rule 6.
 *  * `cryptokit.keys.impl.vault.AndroidKeyVault` in `androidMain` — wraps this class and adds
 *    Android Keystore for `root_key` at-rest storage (FR-010).
 *
 * All crypto ops go through `:core:crypto` ports — no direct libsodium imports here. Ensures
 * the same deterministic wire format across every platform (SC-004).
 *
 * ### MAC construction
 * There is no dedicated keyed-MAC port in `:core:crypto` yet. `mac()` uses HKDF-Extract
 * (`HMAC-SHA256(macKey, message)` = HKDF-Extract per RFC 5869 §2.2 — implemented internally by
 * [KeyDerivation.derive] when `salt=macKey, ikm=message`). Deterministic and secure; upgrade
 * to real BLAKE2b keyed hash tracked in server-roadmap SRV-CRYPTO-MAC-UPGRADE.
 */
internal open class KeyVaultCore(
    private val aead: AeadCipher,
    private val kdf: KeyDerivation,
    private val asym: AsymmetricCrypto,
    private val validationStore: ValidationBlobStore,
) : KeyVault {

    private enum class State { INITIAL, UNLOCKED, WIPED }

    private var state: State = State.INITIAL
    private var rootKey: RootKey? = null
    private var identityPub: ByteArray? = null
    private var identityPriv: ByteArray? = null

    override suspend fun unlock(strategy: RecoveryStrategy) {
        if (state == State.UNLOCKED) return
        val rootBytes = strategy.deriveRoot()
        try {
            strategy.verifyUnlock(rootBytes)
        } catch (e: VaultException.RecoveryFailed) {
            rootBytes.fill(0)
            throw e
        }
        rootKey = RootKey(rootBytes)
        val identitySeed = kdf.derive(
            ikm = rootBytes,
            salt = ByteArray(0),
            info = "ed25519-identity-v1",
            length = 32,
        )
        val kp = asym.ed25519KeyPairFromSeed(identitySeed)
        identitySeed.fill(0)
        identityPub = kp.publicKey
        identityPriv = kp.privateKey
        state = State.UNLOCKED
    }

    override suspend fun wipe() {
        rootKey?.wipe()
        rootKey = null
        identityPriv?.fill(0)
        identityPriv = null
        identityPub = null
        state = State.WIPED
    }

    override suspend fun aeadSeal(purpose: Purpose, plaintext: ByteArray, aad: Aad): Ciphertext {
        val key = derivePurposeKey(purpose)
        try {
            val fullAad = composeAeadAad(aad.bytes, purpose.stableId, keyEpoch = 0)
            val aeadCt = aead.encrypt(plaintext, key, fullAad)
            val bytes = aeadCt.bytes
            val nonce = bytes.copyOfRange(0, BlobHeader.NONCE_SIZE)
            val payload = bytes.copyOfRange(BlobHeader.NONCE_SIZE, bytes.size)
            return Ciphertext(BlobHeader.pack(purpose.stableId, 0, nonce, payload))
        } finally {
            key.fill(0)
        }
    }

    override suspend fun aeadOpen(purpose: Purpose, ciphertext: Ciphertext, aad: Aad): ByteArray {
        val parsed = BlobHeader.parse(ciphertext.bytes)
        if (parsed.purposeStableId != purpose.stableId) {
            throw VaultException.WrongPurpose(purpose, parsed.purposeStableId)
        }
        val key = derivePurposeKey(purpose)
        try {
            val fullAad = composeAeadAad(aad.bytes, purpose.stableId, parsed.keyEpoch)
            val reconstructed = ByteArray(parsed.nonce.size + parsed.aeadPayload.size)
            parsed.nonce.copyInto(reconstructed, 0)
            parsed.aeadPayload.copyInto(reconstructed, parsed.nonce.size)
            return try {
                aead.decrypt(CryptoCiphertext(reconstructed), key, fullAad)
            } catch (e: CryptoException.DecryptionFailed) {
                throw VaultException.TamperDetected("AEAD MAC failed on open")
            } catch (e: CryptoException.MalformedCiphertext) {
                throw VaultException.TamperDetected("Malformed ciphertext: ${e.message}")
            }
        } finally {
            key.fill(0)
        }
    }

    override suspend fun mac(purpose: Purpose, message: ByteArray): MacTag {
        val macKey = derivePurposeMacKey(purpose)
        try {
            val tag = kdf.derive(ikm = message, salt = macKey, info = "mac-v1".encodeToByteArray(), length = 32)
            return MacTag(tag)
        } finally {
            macKey.fill(0)
        }
    }

    override suspend fun verifyMac(purpose: Purpose, message: ByteArray, tag: MacTag): Boolean {
        val expected = mac(purpose, message).bytes
        return constantTimeEquals(expected, tag.bytes)
    }

    override suspend fun sign(message: ByteArray): Signature {
        val priv = identityPriv ?: throw currentStateException()
        val sig = asym.sign(message, priv)
        return Signature(sig.bytes)
    }

    override suspend fun verify(
        publicIdentity: PublicIdentity,
        message: ByteArray,
        signature: Signature,
    ): Boolean = asym.verify(CryptoSignature(signature.bytes), message, publicIdentity.bytes)

    override suspend fun publicIdentity(): PublicIdentity {
        val pub = identityPub ?: throw currentStateException()
        return PublicIdentity(pub.copyOf())
    }

    private suspend fun derivePurposeKey(purpose: Purpose): ByteArray {
        val root = requireRoot()
        return kdf.derive(
            ikm = root.bytes,
            salt = purposeSalt(purpose),
            info = "purpose-key-v1".encodeToByteArray(),
            length = 32,
        )
    }

    private suspend fun derivePurposeMacKey(purpose: Purpose): ByteArray {
        val root = requireRoot()
        return kdf.derive(
            ikm = root.bytes,
            salt = purposeSalt(purpose),
            info = "purpose-mac-key-v1".encodeToByteArray(),
            length = 32,
        )
    }

    private fun requireRoot(): RootKey = rootKey ?: throw currentStateException()

    private fun currentStateException(): VaultException = when (state) {
        State.INITIAL -> VaultException.VaultLocked()
        State.WIPED -> VaultException.NoRootKey()
        State.UNLOCKED -> error("Invariant broken: state=UNLOCKED but rootKey=null")
    }

    private fun purposeSalt(purpose: Purpose): ByteArray = byteArrayOf(
        ((purpose.stableId ushr 8) and 0xFF).toByte(),
        (purpose.stableId and 0xFF).toByte(),
    )

    private fun composeAeadAad(userAad: ByteArray, purposeStableId: Int, keyEpoch: Int): ByteArray {
        // userAad || purposeId(2 BE) || keyEpoch(2 BE) || magic(2) || fmt(1). Binds the header
        // fields into the AEAD MAC so any header tamper is caught on open.
        val out = ByteArray(userAad.size + 2 + 2 + 2 + 1)
        var p = 0
        userAad.copyInto(out, p); p += userAad.size
        out[p++] = ((purposeStableId ushr 8) and 0xFF).toByte()
        out[p++] = (purposeStableId and 0xFF).toByte()
        out[p++] = ((keyEpoch ushr 8) and 0xFF).toByte()
        out[p++] = (keyEpoch and 0xFF).toByte()
        out[p++] = BlobHeader.MAGIC_0
        out[p++] = BlobHeader.MAGIC_1
        out[p] = BlobHeader.FORMAT_VERSION
        return out
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
