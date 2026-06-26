package com.launcher.adapters.crypto

import com.launcher.api.identity.DeviceIdProvider
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.values.KeyId
import cryptokit.crypto.api.values.KeyPair
import cryptokit.crypto.api.values.SealedBlob
import cryptokit.crypto.api.values.SharedSecret
import cryptokit.crypto.api.values.Signature
import cryptokit.crypto.exception.CryptoException
import cryptokit.pairing.api.DeviceId
import cryptokit.pairing.api.DeviceIdentity
import cryptokit.pairing.api.DeviceIdentityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TASK-51 T060 — rewrite of legacy PairingCryptoCoordinatorTest after the
 * libsodium/cryptokit consolidation (Phase 7 deleted the prior version which
 * exercised Outcome/CryptoError + legacy AndroidKeystoreSecureKeystore).
 *
 * Drives the coordinator through its [KeyStoreAdapter] seam — Robolectric does
 * not shadow AndroidKeyStore, so the real `SecureKeyStore` actual cannot run
 * in a JVM unit test. The in-memory `InMemoryKeyStoreAdapter` defined below
 * captures the byte-flow contract without invoking AndroidKeyStore.
 * AsymmetricCrypto + DeviceIdentityRepository are similarly inline-faked —
 * the `Fake*` counterparts live in `:core:crypto`'s `commonTest` source set,
 * which is not visible to `:core`'s androidUnitTest.
 *
 * ≥ 6 cases per acceptance:
 *  1. ensureKeysReady is idempotent (second call returns same alias pair).
 *  2. ensureKeysReady persists both priv + pub bytes for both X25519 + Ed25519.
 *  3. publishOwnIdentity happy path — identity flows to repo with non-zero signature.
 *  4. Repo publish failure surfaces as CryptoException (SerializationException).
 *  5. Invalid deviceId format → SerializationException.
 *  6. Silent migration "empty legacy" → fresh-generate path completes.
 *  7. CancellationException re-thrown (R-003), not wrapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PairingCryptoCoordinatorTest {

    private lateinit var keyStore: InMemoryKeyStoreAdapter
    private lateinit var asymmetric: InMemoryAsymmetricCrypto
    private lateinit var repo: InMemoryDeviceIdentityRepository
    private lateinit var deviceIdProvider: DeviceIdProvider
    private val nowMs: () -> Long = { 1_700_000_000_000L }

    @Before
    fun setUp() {
        keyStore = InMemoryKeyStoreAdapter()
        asymmetric = InMemoryAsymmetricCrypto()
        repo = InMemoryDeviceIdentityRepository()
        deviceIdProvider = FixedDeviceIdProvider("12345678-1234-1234-1234-1234567890ab")
    }

    @Test
    fun ensureKeysReady_isIdempotent() = runTest {
        val coordinator = newCoordinator()
        val first = coordinator.ensureKeysReady()
        val second = coordinator.ensureKeysReady()
        assertEquals(first.encryption, second.encryption)
        assertEquals(first.signing, second.signing)
        assertEquals(PairingCryptoCoordinator.ALIAS_ENCRYPTION, first.encryption)
        assertEquals(PairingCryptoCoordinator.ALIAS_SIGNING, first.signing)
    }

    @Test
    fun ensureKeysReady_persistsBothPrivAndPubBytes() = runTest {
        val coordinator = newCoordinator()
        coordinator.ensureKeysReady()
        assertNotNull(
            "X25519 priv must be persisted",
            keyStore.load(PairingCryptoCoordinator.ENC_KEY_ID),
        )
        assertNotNull(
            "X25519 pub must be persisted",
            keyStore.load(PairingCryptoCoordinator.ENC_PUB_KEY_ID),
        )
        assertNotNull(
            "Ed25519 priv must be persisted",
            keyStore.load(PairingCryptoCoordinator.SIGN_KEY_ID),
        )
        assertNotNull(
            "Ed25519 pub must be persisted",
            keyStore.load(PairingCryptoCoordinator.SIGN_PUB_KEY_ID),
        )
    }

    @Test
    fun publishOwnIdentity_happyPath_signsAndPublishes() = runTest {
        val coordinator = newCoordinator()
        val identity = coordinator.publishOwnIdentity(linkId = "link-1")
        assertEquals(1, repo.publishCount)
        assertEquals("12345678-1234-1234-1234-1234567890ab", identity.deviceId.value)
        assertEquals(nowMs(), identity.signedTimestamp)
        assertTrue(
            "Signature must be populated by sign()",
            identity.signature.any { it != 0.toByte() },
        )
    }

    @Test
    fun publishOwnIdentity_repoFailure_throwsCryptoException() = runTest {
        repo.publishError = CryptoException.SerializationException("network down")
        val coordinator = newCoordinator()
        try {
            coordinator.publishOwnIdentity(linkId = "link-1")
            fail("expected CryptoException")
        } catch (e: CryptoException) {
            assertTrue(
                "expected SerializationException, got ${e::class.simpleName}",
                e is CryptoException.SerializationException,
            )
        }
    }

    @Test
    fun publishOwnIdentity_invalidDeviceIdFormat_throwsSerializationException() = runTest {
        val badDeviceIdProvider = FixedDeviceIdProvider("not-a-uuid")
        val coordinator = PairingCryptoCoordinator(
            secureKeyStore = keyStore,
            asymmetric = asymmetric,
            repo = repo,
            deviceIdProvider = badDeviceIdProvider,
            nowMillis = nowMs,
        )
        try {
            coordinator.publishOwnIdentity(linkId = "link-1")
            fail("expected SerializationException for malformed deviceId")
        } catch (e: CryptoException.SerializationException) {
            assertTrue(
                "message must mention deviceId",
                e.message?.contains("deviceId") == true,
            )
        }
    }

    @Test
    fun ensureKeysReady_emptyLegacy_freshGenerate() = runTest {
        // LegacyKeystoreReader is a no-op stub returning null (Phase 7 source).
        // That path MUST drive a fresh generate via AsymmetricCrypto.
        val coordinator = newCoordinator()
        val aliases = coordinator.ensureKeysReady()
        assertEquals(PairingCryptoCoordinator.ALIAS_ENCRYPTION, aliases.encryption)

        val stored = keyStore.load(PairingCryptoCoordinator.ENC_KEY_ID)
        assertNotNull("priv bytes generated by AsymmetricCrypto must be stored", stored)
        assertEquals(32, stored!!.size)
    }

    @Test
    fun cancellation_isReThrown_notWrapped() = runTest {
        val ce = CancellationException("test cancel")
        val cancellingRepo = object : DeviceIdentityRepository {
            override suspend fun publishOwn(linkId: String, identity: DeviceIdentity) { throw ce }
            override suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): DeviceIdentity =
                throw ce
            override suspend fun listAll(linkId: String): List<DeviceIdentity> = emptyList()
        }
        val c2 = PairingCryptoCoordinator(
            secureKeyStore = keyStore,
            asymmetric = asymmetric,
            repo = cancellingRepo,
            deviceIdProvider = deviceIdProvider,
            nowMillis = nowMs,
        )
        try {
            c2.publishOwnIdentity(linkId = "link-1")
            fail("expected CancellationException to propagate")
        } catch (thrown: CancellationException) {
            assertSame("CE must be the SAME instance — not wrapped/copied", ce, thrown)
        }
    }

    // ─── helpers (private to this test) ──────────────────────────────────

    private fun newCoordinator(): PairingCryptoCoordinator = PairingCryptoCoordinator(
        secureKeyStore = keyStore,
        asymmetric = asymmetric,
        repo = repo,
        deviceIdProvider = deviceIdProvider,
        nowMillis = nowMs,
    )

    private class FixedDeviceIdProvider(private val id: String) : DeviceIdProvider {
        override fun currentDeviceId(): Flow<String> = flowOf(id)
        override suspend fun regenerate() {}
    }

    private class InMemoryKeyStoreAdapter : KeyStoreAdapter {
        private val store: MutableMap<String, ByteArray> = mutableMapOf()
        override suspend fun store(keyId: KeyId, secret: ByteArray) {
            store[keyId.raw] = secret.copyOf()
        }
        override suspend fun load(keyId: KeyId): ByteArray? = store[keyId.raw]?.copyOf()
        override suspend fun delete(keyId: KeyId) {
            store.remove(keyId.raw)
        }
    }

    /** Deterministic 32-byte priv/pub pairs; counter-seeded. */
    private class InMemoryAsymmetricCrypto : AsymmetricCrypto {
        private var counter: Int = 0
        override suspend fun generateX25519KeyPair(): KeyPair = makePair("X25519")
        override suspend fun generateEd25519KeyPair(): KeyPair = makePair("Ed25519")
        override suspend fun deriveSharedSecret(
            myPrivate: ByteArray,
            theirPublic: ByteArray,
        ): SharedSecret = SharedSecret(ByteArray(32))
        override suspend fun sign(message: ByteArray, privateKey: ByteArray): Signature {
            val out = ByteArray(64)
            for (i in 0 until 64) {
                out[i] = (privateKey[i % privateKey.size].toInt() xor (message.size + i)).toByte()
            }
            return Signature(out)
        }
        override suspend fun verify(
            signature: Signature,
            message: ByteArray,
            publicKey: ByteArray,
        ): Boolean = true
        override suspend fun sealForRecipient(
            payload: ByteArray,
            recipientPublicKey: ByteArray,
        ): SealedBlob = SealedBlob(payload.copyOf())
        override suspend fun openSealed(
            blob: SealedBlob,
            recipientPrivateKey: ByteArray,
        ): ByteArray = blob.bytes.copyOf()

        private fun makePair(algorithm: String): KeyPair {
            val n = counter++
            val priv = ByteArray(32) { ((it + n + 1) and 0xff).toByte() }
            val pub = ByteArray(32) { ((it + n + 1) xor 0xA5).toByte() }
            return KeyPair(privateKey = priv, publicKey = pub, algorithm = algorithm)
        }
    }

    private class InMemoryDeviceIdentityRepository : DeviceIdentityRepository {
        var publishError: CryptoException? = null
        var publishCount: Int = 0
            private set

        private val store = mutableMapOf<Pair<String, DeviceId>, DeviceIdentity>()

        override suspend fun publishOwn(linkId: String, identity: DeviceIdentity) {
            publishCount++
            publishError?.let { ex -> publishError = null; throw ex }
            store[linkId to identity.deviceId] = identity
        }
        override suspend fun fetchPeer(linkId: String, peerDeviceId: DeviceId): DeviceIdentity =
            store[linkId to peerDeviceId]
                ?: throw CryptoException.SerializationException("not found: $peerDeviceId")
        override suspend fun listAll(linkId: String): List<DeviceIdentity> =
            store.entries.filter { it.key.first == linkId }.map { it.value }
    }
}
