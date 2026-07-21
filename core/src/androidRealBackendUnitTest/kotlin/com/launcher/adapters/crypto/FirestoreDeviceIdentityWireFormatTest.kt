package com.launcher.adapters.crypto

import android.util.Base64
import family.pairing.api.DeviceId
import family.pairing.api.DeviceIdentity
import family.pairing.api.ED25519_KEY_SIZE
import family.pairing.api.ED25519_SIGNATURE_SIZE
import family.pairing.api.PublicKey
import family.pairing.api.SigningPublicKey
import family.pairing.api.X25519_KEY_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TASK-141 — wire-format roundtrip + backward-compat + reader-gating for the
 * DeviceIdentity Firestore document. After the version + @Serializable were
 * removed from the crypto type (rule 1), the wire format IS this adapter's
 * `toMap`/`fromMap`; the version lives here as [WIRE_SCHEMA_VERSION]. This test
 * replaces the old `DeviceIdentitySerializationTest` (which exercised the now
 * removed @Serializable path in :core:crypto).
 *
 * Robolectric — `toMap`/`fromMap` use `android.util.Base64`.
 */
@RunWith(RobolectricTestRunner::class)
class FirestoreDeviceIdentityWireFormatTest {

    private fun sample(): DeviceIdentity = DeviceIdentity(
        deviceId = DeviceId("11111111-2222-3333-4444-555555555555"),
        publicKey = PublicKey(ByteArray(X25519_KEY_SIZE) { (it + 1).toByte() }),
        signingPublicKey = SigningPublicKey(ByteArray(ED25519_KEY_SIZE) { (it + 2).toByte() }),
        signedTimestamp = 1_700_000_000_000L,
        signature = ByteArray(ED25519_SIGNATURE_SIZE) { (it + 3).toByte() },
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun roundtrip_toMap_then_fromMap_returns_equal_value() {
        val original = sample()
        val map = FirestoreDeviceIdentityRepository.toMap(original).toMutableMap()
        // toMap writes createdAt as a serverTimestamp sentinel (resolved server-side).
        // Substitute the concrete value the document would carry once read back.
        map["createdAt"] = original.createdAt
        val decoded = FirestoreDeviceIdentityRepository.fromMap(map)
        assertEquals(original, decoded)
    }

    @Test
    fun toMap_stamps_the_dotted_version_header() {
        val map = FirestoreDeviceIdentityRepository.toMap(sample())
        assertEquals(FirestoreDeviceIdentityRepository.WIRE_SCHEMA_VERSION.toString(), map["schemaVersion"])
        assertEquals(FirestoreDeviceIdentityRepository.WIRE_MIN_READER_VERSION.toString(), map["minReaderVersion"])
        assertEquals(FirestoreDeviceIdentityRepository.WIRE_MIN_WRITER_VERSION.toString(), map["minWriterVersion"])
    }

    @Test
    fun fromMap_rejects_a_document_demanding_a_newer_reader() {
        val map = FirestoreDeviceIdentityRepository.toMap(sample()).toMutableMap()
        map["createdAt"] = 1_700_000_000_000L
        map["minReaderVersion"] = "999.0"
        assertNull(FirestoreDeviceIdentityRepository.fromMap(map))
    }

    @Test
    fun fromMap_rejects_the_pre_conversion_integer_version() {
        // The retired integer form is refused rather than read on a guess (§4, fail closed).
        val id = sample()
        val doc = mapOf<String, Any?>(
            "schemaVersion" to 1,
            "deviceId" to id.deviceId.value,
            "publicKey" to Base64.encodeToString(id.publicKey.bytes, Base64.NO_WRAP),
            "signingPublicKey" to Base64.encodeToString(id.signingPublicKey.bytes, Base64.NO_WRAP),
            "signedTimestamp" to id.signedTimestamp,
            "signature" to Base64.encodeToString(id.signature, Base64.NO_WRAP),
            "createdAt" to id.createdAt,
        )
        assertNull(FirestoreDeviceIdentityRepository.fromMap(doc))
    }

    @Test
    fun fromMap_reads_a_hand_built_document() {
        val id = sample()
        val doc = mapOf<String, Any?>(
            "schemaVersion" to "1.0",
            "minReaderVersion" to "1.0",
            "minWriterVersion" to "1.0",
            "deviceId" to id.deviceId.value,
            "publicKey" to Base64.encodeToString(id.publicKey.bytes, Base64.NO_WRAP),
            "signingPublicKey" to Base64.encodeToString(id.signingPublicKey.bytes, Base64.NO_WRAP),
            "signedTimestamp" to id.signedTimestamp,
            "signature" to Base64.encodeToString(id.signature, Base64.NO_WRAP),
            "createdAt" to id.createdAt,
        )
        assertEquals(id, FirestoreDeviceIdentityRepository.fromMap(doc))
    }

    @Test
    fun fromMap_rejects_malformed_missing_field() {
        val map = FirestoreDeviceIdentityRepository.toMap(sample()).toMutableMap()
        map["createdAt"] = 1_700_000_000_000L
        map.remove("deviceId")
        assertNull(FirestoreDeviceIdentityRepository.fromMap(map))
    }
}
