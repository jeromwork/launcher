package com.launcher.api.crypto

import com.launcher.api.result.Outcome
import com.launcher.fake.crypto.FakeDigitalSignature
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

@OptIn(ExperimentalSerializationApi::class)
class DeviceIdentityWireFormatTest {

    private val cbor: Cbor = Cbor { ignoreUnknownKeys = true }

    private fun fakeIdentity(
        sign: FakeDigitalSignature = FakeDigitalSignature(),
        alias: String = "test-signing",
        timestamp: Long = 1_700_000_000_000L,
    ): Pair<DeviceIdentity, FakeDigitalSignature> {
        val signKp = sign.generateEd25519Pair(alias)
        val payload = DeviceIdentity(
            deviceId = DeviceId("f1111111-1111-4111-8111-111111111111"),
            publicKey = PublicKey(ByteArray(X25519_KEY_SIZE) { it.toByte() }),
            signingPublicKey = signKp.publicKey,
            signedTimestamp = timestamp,
            signature = ByteArray(ED25519_SIGNATURE_SIZE),  // placeholder; replaced below
            createdAt = timestamp,
        )
        val sig = sign.sign(payload.signedPayloadBytes(), signKp)
        return Pair(payload.copy(signature = sig), sign)
    }

    private fun DeviceIdentity.copy(
        schemaVersion: Int = this.schemaVersion,
        deviceId: DeviceId = this.deviceId,
        publicKey: PublicKey = this.publicKey,
        signingPublicKey: SigningPublicKey = this.signingPublicKey,
        signedTimestamp: Long = this.signedTimestamp,
        signature: ByteArray = this.signature,
        createdAt: Long = this.createdAt,
    ) = DeviceIdentity(schemaVersion, deviceId, publicKey, signingPublicKey, signedTimestamp, signature, createdAt)

    @Test
    fun roundtrip() {
        val (identity, _) = fakeIdentity()
        val bytes = cbor.encodeToByteArray(identity)
        val restored = cbor.decodeFromByteArray<DeviceIdentity>(bytes)
        assertEquals(identity, restored)
    }

    @Test
    fun signAndVerify_happyPath() {
        val (identity, signer) = fakeIdentity()
        val verify = signer.verify(identity.signedPayloadBytes(), identity.signature, identity.signingPublicKey)
        assertTrue(verify is Outcome.Success)
    }

    @Test
    fun signatureRejected_whenPayloadTampered() {
        val (identity, signer) = fakeIdentity()
        // Tamper: модифицируем timestamp (часть signed payload).
        val tampered = identity.copy(signedTimestamp = identity.signedTimestamp + 1000L)
        val verify = signer.verify(tampered.signedPayloadBytes(), tampered.signature, tampered.signingPublicKey)
        assertTrue(verify is Outcome.Failure)
        assertTrue(verify.error is CryptoError.SignatureVerifyFailed)
    }

    @Test
    fun signatureRejected_whenSignatureTampered() {
        val (identity, signer) = fakeIdentity()
        val flippedSig = identity.signature.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        val verify = signer.verify(identity.signedPayloadBytes(), flippedSig, identity.signingPublicKey)
        assertTrue(verify is Outcome.Failure)
    }

    @Test
    fun signatureRejected_whenWrongSize() {
        val signer = FakeDigitalSignature()
        val kp = signer.generateEd25519Pair("x")
        val result = signer.verify(byteArrayOf(1, 2, 3), ByteArray(32), kp.publicKey)
        assertTrue(result is Outcome.Failure)
        assertTrue(result.error is CryptoError.SignatureVerifyFailed)
    }

    @Test
    fun signedPayloadBytes_unchanged_by_createdAt_or_signature() {
        val (a, _) = fakeIdentity()
        val b = a.copy(createdAt = a.createdAt + 999L, signature = ByteArray(ED25519_SIGNATURE_SIZE) { 0xFF.toByte() })
        assertContentEquals(a.signedPayloadBytes(), b.signedPayloadBytes())
    }

    @Test
    fun signedPayloadBytes_changes_with_timestamp() {
        val (a, _) = fakeIdentity(timestamp = 1L)
        val (b, _) = fakeIdentity(timestamp = 2L)
        assertNotEquals(a.signedPayloadBytes().toList(), b.signedPayloadBytes().toList())
    }

    @Test
    fun staleTimestamp_detectableByConsumer() {
        // Domain-level freshness gate (Security Rule, 7-day window) тестируется в Phase 4.
        // Здесь — структурная проверка: signedTimestamp присутствует и сериализуем.
        val (identity, _) = fakeIdentity(timestamp = 1L)
        val bytes = cbor.encodeToByteArray(identity)
        val restored = cbor.decodeFromByteArray<DeviceIdentity>(bytes)
        assertEquals(1L, restored.signedTimestamp)
    }
}
