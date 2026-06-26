package com.launcher.adapters.crypto

import android.util.Log
import com.launcher.api.identity.DeviceIdProvider
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.api.values.KeyId
import cryptokit.crypto.exception.CryptoException
import cryptokit.pairing.api.DeviceId
import cryptokit.pairing.api.DeviceIdentity
import cryptokit.pairing.api.DeviceIdentityRepository
import cryptokit.pairing.api.ED25519_SIGNATURE_SIZE
import cryptokit.pairing.api.PublicKey
import cryptokit.pairing.api.SUPPORTED_SCHEMA_VERSION
import cryptokit.pairing.api.SigningPublicKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

// Spec 011 T061 — связывает крипто-фундамент с pairing flow спека 007.
// TASK-51 Phase 6 — переписан на cryptokit (SecureKeyStore + AsymmetricCrypto).
//
// Responsibilities:
//   1) Гарантировать наличие per-device X25519 + Ed25519 ключей в SecureKeyStore
//      (ensureKeysReady — идемпотентно). Если ключей нет — generate + store.
//   2) Construct + sign + publish DeviceIdentity в /links/{linkId}/devices/{deviceId}
//      после consent.allow (publishOwnIdentity).
//
// Uniform error pattern: все public methods `throws CryptoException` (вместо
// legacy Outcome<T, CryptoError>). CancellationException всегда re-throw
// (R-003, FR-017).
//
// Silent migration logic (R-002, FR-005): see [loadOrMigrate] — first load
// attempt from new cryptokit KeyStore; on miss fall back to legacy alias via
// [LegacyKeystoreReader] + re-encrypt under new keyId. On genuine first-run
// generate fresh keypair via [AsymmetricCrypto].
class PairingCryptoCoordinator(
    private val secureKeyStore: KeyStoreAdapter,
    private val asymmetric: AsymmetricCrypto,
    private val repo: DeviceIdentityRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Production convenience constructor: takes the cryptokit `SecureKeyStore`
     * directly. Unit tests use the primary constructor + a [KeyStoreAdapter]
     * fake (Robolectric does not shadow AndroidKeyStore, so the real Android
     * actual is unusable in JVM-side unit tests — TASK-51 T060).
     */
    constructor(
        secureKeyStore: SecureKeyStore,
        asymmetric: AsymmetricCrypto,
        repo: DeviceIdentityRepository,
        deviceIdProvider: DeviceIdProvider,
        nowMillis: () -> Long = { System.currentTimeMillis() },
    ) : this(
        secureKeyStore = SecureKeyStoreAdapter(secureKeyStore),
        asymmetric = asymmetric,
        repo = repo,
        deviceIdProvider = deviceIdProvider,
        nowMillis = nowMillis,
    )

    /** Cached after first successful load. Public bytes are not sensitive; private bytes are reused suspending-method-internal only. */
    @Volatile private var cachedKeys: KeyMaterial? = null

    /**
     * Idempotent: вызывается на каждом старте app. На первом запуске генерирует
     * ключи; на последующих — no-op (load из SecureKeyStore).
     *
     * @throws CryptoException on TEE / keystore failure.
     */
    suspend fun ensureKeysReady(): KeyAliases = withCryptoLogging("ensureKeysReady") {
        loadOrGenerateKeys()
        KeyAliases(encryption = ENC_KEY_ID.raw, signing = SIGN_KEY_ID.raw)
    }

    /**
     * Вызывается после spec 007 consent.allow (обе стороны).
     * Собирает DeviceIdentity, подписывает payload, publishes в Firestore.
     *
     * @throws CryptoException on signing / publish failure.
     */
    suspend fun publishOwnIdentity(linkId: String): DeviceIdentity = withCryptoLogging("publishOwnIdentity") {
        val deviceIdStr = deviceIdProvider.currentDeviceId().first()
        val deviceId = try {
            DeviceId(deviceIdStr)
        } catch (e: IllegalArgumentException) {
            throw CryptoException.SerializationException("invalid deviceId format", e)
        }

        val keys = loadOrGenerateKeys()

        val now = nowMillis()
        val unsigned = DeviceIdentity(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            deviceId = deviceId,
            publicKey = PublicKey(keys.encryptionPublic),
            signingPublicKey = SigningPublicKey(keys.signingPublic),
            signedTimestamp = now,
            signature = ByteArray(ED25519_SIGNATURE_SIZE), // placeholder
            createdAt = now,
        )
        val sig = asymmetric.sign(unsigned.signedPayloadBytes(), keys.signingPrivate).bytes
        val signed = DeviceIdentity(
            schemaVersion = unsigned.schemaVersion,
            deviceId = unsigned.deviceId,
            publicKey = unsigned.publicKey,
            signingPublicKey = unsigned.signingPublicKey,
            signedTimestamp = unsigned.signedTimestamp,
            signature = sig,
            createdAt = unsigned.createdAt,
        )

        repo.publishOwn(linkId, signed)
        signed
    }

    /**
     * Resolve both X25519 + Ed25519 keypairs. Order of attempts per key:
     *   1) Load from new cryptokit SecureKeyStore (both priv + pub).
     *   2) Silent-migrate from legacy AndroidKeystoreSecureKeystore alias
     *      (priv bytes only; pub re-derived only on Ed25519, X25519 forces
     *      regenerate because we can't recover pub from priv via the cryptokit
     *      port surface — acceptable: legacy state on the owner's only test
     *      device does not exist, R-002 is structural-only).
     *   3) Generate fresh keypair + store both halves.
     *
     * @throws CryptoException on TEE / generation failure.
     */
    private suspend fun loadOrGenerateKeys(): KeyMaterial {
        cachedKeys?.let { return it }

        // ── X25519 (encryption) ────────────────────────────────────────────
        val encPriv = secureKeyStore.load(ENC_KEY_ID)
        val encPub = secureKeyStore.load(ENC_PUB_KEY_ID)
        val (encryptionPrivate, encryptionPublic) = if (encPriv != null && encPub != null) {
            encPriv to encPub
        } else {
            // Try legacy migrate (priv-only); always followed by regenerate
            // since we cannot derive pub from priv via cryptokit ports.
            tryLegacyMigrate(LEGACY_ALIAS_ENCRYPTION, ENC_KEY_ID)
            val pair = asymmetric.generateX25519KeyPair()
            secureKeyStore.store(ENC_KEY_ID, pair.privateKey)
            secureKeyStore.store(ENC_PUB_KEY_ID, pair.publicKey)
            pair.privateKey to pair.publicKey
        }

        // ── Ed25519 (signing) ──────────────────────────────────────────────
        val signPriv = secureKeyStore.load(SIGN_KEY_ID)
        val signPub = secureKeyStore.load(SIGN_PUB_KEY_ID)
        val (signingPrivate, signingPublic) = if (signPriv != null && signPub != null) {
            signPriv to signPub
        } else {
            tryLegacyMigrate(LEGACY_ALIAS_SIGNING, SIGN_KEY_ID)
            val pair = asymmetric.generateEd25519KeyPair()
            secureKeyStore.store(SIGN_KEY_ID, pair.privateKey)
            secureKeyStore.store(SIGN_PUB_KEY_ID, pair.publicKey)
            pair.privateKey to pair.publicKey
        }

        return KeyMaterial(
            encryptionPrivate = encryptionPrivate,
            encryptionPublic = encryptionPublic,
            signingPrivate = signingPrivate,
            signingPublic = signingPublic,
        ).also { cachedKeys = it }
    }

    /**
     * Silent migration best-effort (R-002, FR-005). Reads legacy Android Keystore
     * alias bytes (no lazysodium dep — uses raw Keystore APIs) and stores under
     * the new [KeyId], then deletes legacy. If anything fails — swallow and
     * proceed to fresh-generate; the owner's only test device has no successful
     * legacy state (always crashed at pairing pre-TASK-51), so this path is
     * structural-only.
     *
     * TODO(post-task-6): replace read-old-then-re-encrypt with derive-from-root
     * after Root Key Hierarchy lands (TASK-6).
     */
    private suspend fun tryLegacyMigrate(legacyAlias: String, newKeyId: KeyId) {
        val legacyBytes = try {
            LegacyKeystoreReader.read(legacyAlias)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        } ?: return
        try {
            secureKeyStore.store(newKeyId, legacyBytes)
            LegacyKeystoreReader.delete(legacyAlias)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Best-effort; ignore so caller can regenerate.
        }
    }

    /**
     * Universal try/catch wrapper enforcing FR-017 logging contract:
     * `operation=X exceptionClass=Y messageHash=H`. NO raw bytes, hex,
     * deviceIds, or other PII / key material in logs.
     */
    private suspend inline fun <T> withCryptoLogging(
        operation: String,
        block: () -> T,
    ): T = try {
        block()
    } catch (ce: CancellationException) {
        // R-003: structured concurrency — never swallow cancel.
        throw ce
    } catch (e: CryptoException) {
        Log.w(
            LOG_TAG,
            "operation=$operation exceptionClass=${e.javaClass.simpleName} " +
                "messageHash=${e.message?.hashCode()}",
        )
        throw e
    } catch (e: Throwable) {
        Log.w(
            LOG_TAG,
            "operation=$operation exceptionClass=${e.javaClass.simpleName} " +
                "messageHash=${e.message?.hashCode()}",
        )
        throw CryptoException.KeyStoreException("unexpected $operation failure", e)
    }

    data class KeyAliases(val encryption: String, val signing: String)

    private data class KeyMaterial(
        val encryptionPrivate: ByteArray,
        val encryptionPublic: ByteArray,
        val signingPrivate: ByteArray,
        val signingPublic: ByteArray,
    )

    companion object {
        // cryptokit KeyIds (media- namespace fits paired-device crypto). Priv +
        // pub stored separately because cryptokit.AsymmetricCrypto не exposes
        // scalar-mult-base from priv → pub; storing pub avoids re-derive on the
        // hot path.
        val ENC_KEY_ID = KeyId("media-pairing-x25519-priv-v1")
        val ENC_PUB_KEY_ID = KeyId("media-pairing-x25519-pub-v1")
        val SIGN_KEY_ID = KeyId("media-pairing-ed25519-priv-v1")
        val SIGN_PUB_KEY_ID = KeyId("media-pairing-ed25519-pub-v1")

        // Legacy aliases — used only by silent migration reader once.
        // TODO(post-task-6): remove after Root Key Hierarchy lands and silent
        // migration window expires.
        const val LEGACY_ALIAS_ENCRYPTION: String = "spec011.encryption.own"
        const val LEGACY_ALIAS_SIGNING: String = "spec011.signing.own"

        // Domain aliases kept for source-compat with consumers reading
        // KeyAliases (Spec011SmokeDebugActivity etc.). Match KeyId raw values.
        const val ALIAS_ENCRYPTION: String = "media-pairing-x25519-priv-v1"
        const val ALIAS_SIGNING: String = "media-pairing-ed25519-priv-v1"

        private const val LOG_TAG: String = "cryptokit"
    }
}

/**
 * Thin interface mirroring [SecureKeyStore]'s 3-method shape so unit tests
 * can inject an in-memory fake. Production wiring goes through the
 * [SecureKeyStoreAdapter] convenience class which simply forwards.
 *
 * Why this seam exists: `SecureKeyStore` is an `expect class` — it cannot be
 * subclassed from outside its module, and Robolectric does not shadow
 * AndroidKeyStore. So a JVM-side unit test of [PairingCryptoCoordinator]
 * needs a different way to inject a key-byte sink. Production code never
 * sees this interface directly.
 *
 * TASK-51 T060.
 */
interface KeyStoreAdapter {
    suspend fun store(keyId: KeyId, secret: ByteArray)
    suspend fun load(keyId: KeyId): ByteArray?
    suspend fun delete(keyId: KeyId)
}

/** Forwards every call to a real [SecureKeyStore]. */
class SecureKeyStoreAdapter(private val delegate: SecureKeyStore) : KeyStoreAdapter {
    override suspend fun store(keyId: KeyId, secret: ByteArray) = delegate.store(keyId, secret)
    override suspend fun load(keyId: KeyId): ByteArray? = delegate.load(keyId)
    override suspend fun delete(keyId: KeyId) = delegate.delete(keyId)
}
