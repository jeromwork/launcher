package com.launcher.api.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class DeviceIdInitTest {
    @Test fun random_generates_valid_uuid() {
        val d = DeviceId.random()
        assertEquals(36, d.value.length)
        assertEquals('-', d.value[8])
    }

    @Test fun random_generates_unique() {
        assertNotEquals(DeviceId.random(), DeviceId.random())
    }

    @Test fun accepts_uuid_format() {
        DeviceId("f1111111-1111-4111-8111-111111111111")
    }

    @Test fun rejects_short() {
        assertFailsWith<IllegalArgumentException> { DeviceId("short") }
    }

    @Test fun rejects_non_hex() {
        assertFailsWith<IllegalArgumentException> { DeviceId("g1111111-1111-4111-8111-111111111111") }
    }
}

class PublicKeyInitTest {
    @Test fun accepts_32_bytes() {
        PublicKey(ByteArray(X25519_KEY_SIZE))
    }

    @Test fun rejects_wrong_size() {
        assertFailsWith<IllegalArgumentException> { PublicKey(ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { PublicKey(ByteArray(64)) }
    }

    @Test fun equality_by_bytes() {
        val a = PublicKey(ByteArray(32) { 1 })
        val b = PublicKey(ByteArray(32) { 1 })
        val c = PublicKey(ByteArray(32) { 2 })
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}

class SigningPublicKeyInitTest {
    @Test fun accepts_32_bytes() {
        SigningPublicKey(ByteArray(ED25519_KEY_SIZE))
    }

    @Test fun rejects_wrong_size() {
        assertFailsWith<IllegalArgumentException> { SigningPublicKey(ByteArray(31)) }
    }

    @Test fun equality_by_bytes() {
        val a = SigningPublicKey(ByteArray(32) { 7 })
        val b = SigningPublicKey(ByteArray(32) { 7 })
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}

@OptIn(ExperimentalUuidApi::class)
class DeviceIdentityInitTest {
    @Test fun signedPayloadBytes_is_deterministic() {
        val id = DeviceId("f1111111-1111-4111-8111-111111111111")
        val pk = PublicKey(ByteArray(32) { 1 })
        val spk = SigningPublicKey(ByteArray(32) { 2 })
        val a = DeviceIdentity(
            deviceId = id,
            publicKey = pk,
            signingPublicKey = spk,
            signedTimestamp = 1_700_000_000_000L,
            signature = ByteArray(64),
            createdAt = 1_700_000_000_000L,
        )
        val b = DeviceIdentity(
            deviceId = id,
            publicKey = pk,
            signingPublicKey = spk,
            signedTimestamp = 1_700_000_000_000L,
            signature = ByteArray(64) { 9 },
            createdAt = 999L,
        )
        // signedPayloadBytes depends только на deviceId + publicKey + signingPublicKey + signedTimestamp
        // — не на signature, не на createdAt.
        assertContentEquals(a.signedPayloadBytes(), b.signedPayloadBytes())
    }

    @Test fun signedPayloadBytes_changes_with_timestamp() {
        val id = DeviceId("f1111111-1111-4111-8111-111111111111")
        val pk = PublicKey(ByteArray(32) { 1 })
        val spk = SigningPublicKey(ByteArray(32) { 2 })
        val sig = ByteArray(64)
        val a = DeviceIdentity(deviceId = id, publicKey = pk, signingPublicKey = spk,
            signedTimestamp = 1L, signature = sig, createdAt = 0L)
        val b = DeviceIdentity(deviceId = id, publicKey = pk, signingPublicKey = spk,
            signedTimestamp = 2L, signature = sig, createdAt = 0L)
        assertNotEquals(a.signedPayloadBytes().toList(), b.signedPayloadBytes().toList())
    }

    @Test fun rejects_wrong_signature_size() {
        assertFailsWith<IllegalArgumentException> {
            DeviceIdentity(
                deviceId = DeviceId.random(),
                publicKey = PublicKey(ByteArray(32)),
                signingPublicKey = SigningPublicKey(ByteArray(32)),
                signedTimestamp = 0L,
                signature = ByteArray(32),
                createdAt = 0L,
            )
        }
    }
}

class CEKZeroizationTest {
    @Test fun close_zeros_bytes() {
        val cek = ContentEncryptionKey(ByteArray(32) { 0xAB.toByte() })
        cek.close()
        assertTrue(cek.bytes.all { it == 0.toByte() })
    }

    @Test fun use_block_zeros_on_normal_path() {
        val cek = ContentEncryptionKey(ByteArray(32) { 0xCD.toByte() })
        cek.use { /* no-op */ }
        assertTrue(cek.bytes.all { it == 0.toByte() })
    }

    @Test fun use_block_zeros_on_exception_path() {
        val cek = ContentEncryptionKey(ByteArray(32) { 0xEF.toByte() })
        runCatching {
            cek.use { error("boom") }
        }
        assertTrue(cek.bytes.all { it == 0.toByte() })
    }

    @Test fun bytesOrThrow_after_close_throws() {
        val cek = ContentEncryptionKey(ByteArray(32))
        cek.close()
        assertFailsWith<IllegalStateException> { cek.bytesOrThrow() }
    }

    @Test fun rejects_wrong_size() {
        assertFailsWith<IllegalArgumentException> { ContentEncryptionKey(ByteArray(16)) }
    }
}

class RecipientInitTest {
    @Test fun accepts_80_bytes() {
        Recipient(DeviceId.random(), ByteArray(SEALED_CEK_SIZE))
    }

    @Test fun rejects_wrong_sealed_size() {
        assertFailsWith<IllegalArgumentException> {
            Recipient(DeviceId.random(), ByteArray(64))
        }
    }
}

class EnvelopeInitTest {
    private fun validRecipient() = Recipient(DeviceId.random(), ByteArray(SEALED_CEK_SIZE))

    @Test fun accepts_minimal_valid() {
        EncryptedEnvelope(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            cipherSuiteId = CIPHER_SUITE_ID_V1,
            nonce = ByteArray(XCHACHA20_NONCE_SIZE),
            recipients = listOf(validRecipient()),
            ciphertext = ByteArray(0),
            mac = ByteArray(POLY1305_MAC_SIZE),
        )
    }

    @Test fun rejects_empty_recipients() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedEnvelope(
                schemaVersion = 1,
                cipherSuiteId = CIPHER_SUITE_ID_V1,
                nonce = ByteArray(XCHACHA20_NONCE_SIZE),
                recipients = emptyList(),
                ciphertext = ByteArray(0),
                mac = ByteArray(POLY1305_MAC_SIZE),
            )
        }
    }

    @Test fun rejects_nonce_mismatch() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedEnvelope(
                schemaVersion = 1,
                cipherSuiteId = CIPHER_SUITE_ID_V1,
                nonce = ByteArray(12),
                recipients = listOf(validRecipient()),
                ciphertext = ByteArray(0),
                mac = ByteArray(POLY1305_MAC_SIZE),
            )
        }
    }

    @Test fun rejects_mac_mismatch() {
        assertFailsWith<IllegalArgumentException> {
            EncryptedEnvelope(
                schemaVersion = 1,
                cipherSuiteId = CIPHER_SUITE_ID_V1,
                nonce = ByteArray(XCHACHA20_NONCE_SIZE),
                recipients = listOf(validRecipient()),
                ciphertext = ByteArray(0),
                mac = ByteArray(8),
            )
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
class CryptoErrorTest {
    @Test fun all_subcases_constructable() {
        val errors = listOf<CryptoError>(
            CryptoError.KeyNotFound(alias = "x25519/own"),
            CryptoError.MacFailed(),
            CryptoError.BlobMissing(uuid = Uuid.random()),
            CryptoError.CipherSuiteUnsupported("unknown_v9"),
            CryptoError.RecipientNotFound(DeviceId.random()),
            CryptoError.SignatureVerifyFailed(),
            CryptoError.MalformedEnvelope(),
            CryptoError.StorageFailure(cause = RuntimeException("network")),
            CryptoError.KeystoreFailure(cause = RuntimeException("oem")),
        )
        assertEquals(9, errors.size)
    }

    @Test fun key_not_found_carries_alias_only_no_bytes() {
        val e = CryptoError.KeyNotFound(alias = "x25519/own", cause = null)
        assertEquals("x25519/own", e.alias)
        assertNull(e.cause)
    }
}

class SupportedSchemaVersionTest {
    @Test fun version_is_one_at_initial_release() {
        assertEquals(1, SUPPORTED_SCHEMA_VERSION)
    }

    @Test fun cipher_suite_id_pinned() {
        assertEquals("xchacha20poly1305_x25519_sealed_v1", CIPHER_SUITE_ID_V1)
    }
}
