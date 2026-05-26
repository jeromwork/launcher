package com.launcher.adapters.crypto

import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceKeyPair
import com.launcher.api.crypto.DeviceSigningKeyPair
import com.launcher.api.crypto.InMemoryPrivateKey
import com.launcher.api.crypto.InMemorySigningPrivateKey
import com.launcher.api.crypto.PublicKey
import com.launcher.api.crypto.SecureKeystore
import com.launcher.api.crypto.SigningPublicKey
import com.launcher.api.crypto.ED25519_KEY_SIZE
import com.launcher.api.crypto.X25519_KEY_SIZE
import com.launcher.api.identity.DeviceIdProvider
import com.launcher.api.result.Outcome
import com.launcher.fake.crypto.FakeDigitalSignature
import com.launcher.fake.crypto.InMemoryDeviceIdentityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PairingCryptoCoordinatorTest {

    private val testDeviceIdProvider = object : DeviceIdProvider {
        private val state = MutableStateFlow("f1111111-1111-4111-8111-111111111111")
        override fun currentDeviceId(): Flow<String> = state.asStateFlow()
        override suspend fun regenerate() {
            error("not used in test")
        }
    }

    @Test
    fun ensureKeysReady_generates_keys_on_first_call() {
        val keystore = SimpleSecureKeystore()
        val coordinator = PairingCryptoCoordinator(
            keystore = keystore,
            signature = FakeDigitalSignature(),
            repo = InMemoryDeviceIdentityRepository(),
            deviceIdProvider = testDeviceIdProvider,
        )
        val result = coordinator.ensureKeysReady()
        assertTrue(result is Outcome.Success)
        assertTrue(keystore.exists(PairingCryptoCoordinator.ALIAS_ENCRYPTION))
        assertTrue(keystore.exists(PairingCryptoCoordinator.ALIAS_SIGNING))
    }

    @Test
    fun ensureKeysReady_idempotent_on_second_call() {
        val keystore = SimpleSecureKeystore()
        val coordinator = PairingCryptoCoordinator(
            keystore = keystore,
            signature = FakeDigitalSignature(),
            repo = InMemoryDeviceIdentityRepository(),
            deviceIdProvider = testDeviceIdProvider,
        )
        coordinator.ensureKeysReady()
        val firstEncPub = (keystore.loadEncryption(PairingCryptoCoordinator.ALIAS_ENCRYPTION) as Outcome.Success).value.publicKey
        coordinator.ensureKeysReady()
        val secondEncPub = (keystore.loadEncryption(PairingCryptoCoordinator.ALIAS_ENCRYPTION) as Outcome.Success).value.publicKey
        assertEquals(firstEncPub, secondEncPub)
    }

    @Test
    fun publishOwnIdentity_creates_signed_document() = runTest {
        val keystore = SimpleSecureKeystore()
        val repo = InMemoryDeviceIdentityRepository()
        val signer = FakeDigitalSignature()
        val coordinator = PairingCryptoCoordinator(
            keystore = keystore,
            signature = signer,
            repo = repo,
            deviceIdProvider = testDeviceIdProvider,
            nowMillis = { 1_700_000_000_000L },
        )
        val result = coordinator.publishOwnIdentity("link-A")
        assertTrue(result is Outcome.Success)
        val identity = result.value
        assertEquals("f1111111-1111-4111-8111-111111111111", identity.deviceId.value)
        assertEquals(1_700_000_000_000L, identity.signedTimestamp)

        // Verify Pub published.
        val all = repo.listAll("link-A")
        assertEquals(1, all.size)

        // Verify signature verifiable.
        val verify = signer.verify(identity.signedPayloadBytes(), identity.signature, identity.signingPublicKey)
        assertTrue(verify is Outcome.Success)
    }

    @Test
    fun publishOwnIdentity_fails_gracefully_when_repo_fails() = runTest {
        val keystore = SimpleSecureKeystore()
        val failingRepo = object : com.launcher.api.crypto.DeviceIdentityRepository {
            override suspend fun publishOwn(
                linkId: String,
                identity: com.launcher.api.crypto.DeviceIdentity,
            ): Outcome<Unit, CryptoError> = Outcome.Failure(CryptoError.StorageFailure(RuntimeException("network")))

            override suspend fun fetchPeer(
                linkId: String,
                peerDeviceId: com.launcher.api.crypto.DeviceId,
            ): Outcome<com.launcher.api.crypto.DeviceIdentity, CryptoError> =
                Outcome.Failure(CryptoError.StorageFailure(RuntimeException("network")))

            override suspend fun listAll(linkId: String) = emptyList<com.launcher.api.crypto.DeviceIdentity>()
        }
        val coordinator = PairingCryptoCoordinator(
            keystore = keystore,
            signature = FakeDigitalSignature(),
            repo = failingRepo,
            deviceIdProvider = testDeviceIdProvider,
        )
        val result = coordinator.publishOwnIdentity("link-A")
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is CryptoError.StorageFailure)
    }

    @Test
    fun publishOwnIdentity_rejects_invalid_device_id_format() = runTest {
        val keystore = SimpleSecureKeystore()
        val badProvider = object : DeviceIdProvider {
            private val state = MutableStateFlow("not-a-uuid")
            override fun currentDeviceId(): Flow<String> = state.asStateFlow()
            override suspend fun regenerate() = error("n/a")
        }
        val coordinator = PairingCryptoCoordinator(
            keystore = keystore,
            signature = FakeDigitalSignature(),
            repo = InMemoryDeviceIdentityRepository(),
            deviceIdProvider = badProvider,
        )
        val result = coordinator.publishOwnIdentity("link-A")
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is CryptoError.KeystoreFailure)
    }
}

// Minimal SecureKeystore stub для test — deterministic keys without libsodium.
private class SimpleSecureKeystore : SecureKeystore {
    private val encryption = mutableMapOf<String, DeviceKeyPair>()
    private val signing = mutableMapOf<String, DeviceSigningKeyPair>()
    private var counter = 0

    override fun generateAndStoreEncryption(alias: String): DeviceKeyPair {
        val pub = ByteArray(X25519_KEY_SIZE) { ((counter + it) and 0xFF).toByte() }
        val priv = ByteArray(X25519_KEY_SIZE) { ((counter + 100 + it) and 0xFF).toByte() }
        counter++
        val kp = DeviceKeyPair(PublicKey(pub), InMemoryPrivateKey(alias, priv))
        encryption[alias] = kp
        return kp
    }

    override fun generateAndStoreSigning(alias: String): DeviceSigningKeyPair {
        val pub = ByteArray(ED25519_KEY_SIZE) { ((counter + 200 + it) and 0xFF).toByte() }
        val priv = ByteArray(ED25519_KEY_SIZE) { ((counter + 300 + it) and 0xFF).toByte() }
        counter++
        val kp = DeviceSigningKeyPair(SigningPublicKey(pub), InMemorySigningPrivateKey(alias, priv))
        signing[alias] = kp
        return kp
    }

    override fun loadEncryption(alias: String): Outcome<DeviceKeyPair, CryptoError> {
        val kp = encryption[alias] ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        return Outcome.Success(kp)
    }

    override fun loadSigning(alias: String): Outcome<DeviceSigningKeyPair, CryptoError> {
        val kp = signing[alias] ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        return Outcome.Success(kp)
    }

    override fun delete(alias: String) {
        encryption.remove(alias)
        signing.remove(alias)
    }

    override fun exists(alias: String): Boolean =
        encryption.containsKey(alias) || signing.containsKey(alias)
}
