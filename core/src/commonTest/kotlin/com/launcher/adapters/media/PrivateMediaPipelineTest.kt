package com.launcher.adapters.media

import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.DeviceKeyPair
import com.launcher.api.crypto.ED25519_SIGNATURE_SIZE
import com.launcher.api.crypto.PublicKey
import com.launcher.api.crypto.SUPPORTED_SCHEMA_VERSION
import com.launcher.api.crypto.SigningPublicKey
import com.launcher.api.crypto.X25519_KEY_SIZE
import com.launcher.api.media.PrivateMediaKind
import com.launcher.api.media.PrivateMediaResolution
import com.launcher.api.result.Outcome
import com.launcher.fake.crypto.FakeAeadCipher
import com.launcher.fake.crypto.FakeAsymmetricCrypto
import com.launcher.fake.crypto.FakeEncryptedMediaStorage
import com.launcher.fake.crypto.FakeRecipientResolver
import com.launcher.fake.media.FakeLocalMediaStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Spec 012 — integration tests для PrivateMediaUploader/Resolver pipeline
 * с fake adapters.
 *
 * Tasks: T1217, T1218, T1219.
 *
 * 🚨 **T1219 — privacy gate test (SC-008)**: проверяет что sensitive label
 * ("Паспорт") НЕ появляется в plaintext envelope.metadata. Этот тест —
 * **MUST be green** перед merge'ем PR (FR-006 invariant).
 */
@OptIn(ExperimentalUuidApi::class)
class PrivateMediaPipelineTest {

    // ─── T1217: encrypt + upload roundtrip ────────────────────────────────

    @Test
    fun roundtrip_image_encrypt_upload_download_decrypt() = runTest {
        val rig = Rig.create("link-roundtrip-image")
        val plaintext = "imagine-this-is-a-jpeg".encodeToByteArray()

        val uploadResult = rig.uploader.upload(
            bytes = plaintext,
            kind = PrivateMediaKind.Image,
            linkId = rig.linkId,
            labelInsideCiphertext = null,
            refSource = "config:contact:c1",
        )
        assertTrue(uploadResult is Outcome.Success, "upload should succeed; got $uploadResult")

        val iconRef = uploadResult.value
        assertTrue(iconRef.startsWith("private:"))

        val resolveResult = rig.resolver.resolve(iconRef, rig.linkId)
        assertTrue(resolveResult is PrivateMediaResolution.Bytes, "expected Bytes, got $resolveResult")
        assertTrue(resolveResult.bytes.contentEquals(plaintext), "decrypted bytes must match original")
    }

    @Test
    fun roundtrip_document_with_label_inside_ciphertext() = runTest {
        val rig = Rig.create("link-roundtrip-doc")
        val plaintext = "passport-jpeg-bytes".encodeToByteArray()
        val sensitiveLabel = "Паспорт"

        val uploadResult = rig.uploader.upload(
            bytes = plaintext,
            kind = PrivateMediaKind.Document,
            linkId = rig.linkId,
            labelInsideCiphertext = sensitiveLabel,
            refSource = "config:slot:s1",
        )
        assertTrue(uploadResult is Outcome.Success)
        val iconRef = uploadResult.value

        // На первый show LocalMediaStore пустой, идёт download+decrypt.
        // Resolver кеширует *image bytes* в LocalMediaStore (не DocumentPayload JSON).
        val resolveResult = rig.resolver.resolve(iconRef, rig.linkId)
        assertTrue(resolveResult is PrivateMediaResolution.Bytes)

        // Image bytes decoded из DocumentPayload должны совпадать с original.
        assertTrue(resolveResult.bytes.contentEquals(plaintext), "decoded image bytes must match")
        assertEquals(PrivateMediaKind.Document, resolveResult.kind)
    }

    // ─── T1218: resolver behavior ──────────────────────────────────────────

    @Test
    fun resolver_cache_hit_on_repeat_call() = runTest {
        val rig = Rig.create("link-cache-hit")
        val plaintext = "cached-bytes".encodeToByteArray()
        val upload = rig.uploader.upload(plaintext, PrivateMediaKind.Image, rig.linkId, null, "config:contact:c1")
        val iconRef = (upload as Outcome.Success).value

        // First resolve — populates LocalMediaStore.
        val first = rig.resolver.resolve(iconRef, rig.linkId)
        assertTrue(first is PrivateMediaResolution.Bytes)

        // Drop network access — second resolve must succeed from local cache.
        // (FakeEncryptedMediaStorage.delete simulates blob gone from server.)
        val uuid = Uuid.parse(iconRef.removePrefix("private:"))
        rig.storage.delete(rig.linkId, uuid)

        val second = rig.resolver.resolve(iconRef, rig.linkId)
        assertTrue(second is PrivateMediaResolution.Bytes, "second resolve should hit LocalMediaStore")
        assertTrue(second.bytes.contentEquals(plaintext))
    }

    @Test
    fun resolver_blob_missing_returns_failed() = runTest {
        val rig = Rig.create("link-missing")
        // Construct an iconRef pointing at non-existent blob.
        val ghostRef = "private:00000000-0000-4000-8000-000000000000"

        val result = rig.resolver.resolve(ghostRef, rig.linkId)
        assertTrue(result is PrivateMediaResolution.Failed)
        assertEquals(PrivateMediaResolution.FailureReason.BlobMissing, result.reason)
    }

    @Test
    fun resolver_invalid_ref_format() = runTest {
        val rig = Rig.create("link-invalid")

        val notNamespaced = rig.resolver.resolve("bundled:whatsapp", rig.linkId)
        assertTrue(notNamespaced is PrivateMediaResolution.Failed)
        assertEquals(PrivateMediaResolution.FailureReason.InvalidRef, notNamespaced.reason)

        val malformed = rig.resolver.resolve("private:not-a-uuid", rig.linkId)
        assertTrue(malformed is PrivateMediaResolution.Failed)
        assertEquals(PrivateMediaResolution.FailureReason.InvalidRef, malformed.reason)
    }

    // ─── 🚨 T1219 — privacy gate (SC-008) ─────────────────────────────────

    @Test
    fun no_label_leak_in_plaintext_metadata() = runTest {
        val rig = Rig.create("link-privacy-gate")
        val sensitiveLabel = "TestLabelLeak_СНИЛС_123"
        val plaintext = "passport-jpeg".encodeToByteArray()

        val upload = rig.uploader.upload(
            bytes = plaintext,
            kind = PrivateMediaKind.Document,
            linkId = rig.linkId,
            labelInsideCiphertext = sensitiveLabel,
            refSource = "config:slot:s-privacy",
        )
        assertTrue(upload is Outcome.Success)
        val uuid = Uuid.parse(upload.value.removePrefix("private:"))

        // Download envelope ИЗ Storage — это plaintext snapshot, что server видит.
        val envelope = (rig.storage.download(rig.linkId, uuid) as Outcome.Success).value

        // PRIVACY INVARIANT (FR-006 / SC-008):
        // Sensitive label "TestLabelLeak_СНИЛС_123" MUST NOT appear в любом
        // plaintext metadata entry (envelope.metadata = "kind" + nothing else).
        envelope.metadata.forEach { (key, valueBytes) ->
            val valueAsString = valueBytes.decodeToString()
            assertFalse(
                valueAsString.contains(sensitiveLabel),
                "🚨 FR-006 VIOLATION: sensitive label leaked в plaintext metadata key='$key' " +
                    "value='$valueAsString'. Label MUST be encrypted внутри ciphertext, " +
                    "не в metadata (которая plaintext + AAD-bound)."
            )
            assertFalse(
                key.contains(sensitiveLabel),
                "🚨 FR-006 VIOLATION: sensitive label leaked в plaintext metadata key name='$key'."
            )
        }

        // Metadata должна содержать ТОЛЬКО non-sensitive "kind" entry.
        assertEquals(setOf("kind"), envelope.metadata.keys)
        assertEquals("document", envelope.metadata["kind"]?.decodeToString())

        // Label MUST be recoverable через decrypt'a (т.е. он есть в ciphertext).
        val resolved = rig.resolver.resolve(upload.value, rig.linkId)
        assertTrue(resolved is PrivateMediaResolution.Bytes)
        // Bytes decoded успешно — payload decode'ился, значит label был внутри.
        assertTrue(resolved.bytes.contentEquals(plaintext))
    }

    @Test
    fun no_label_leak_when_label_contains_emoji_or_unicode() = runTest {
        val rig = Rig.create("link-privacy-unicode")
        val labels = listOf("Паспорт 📋", "СНИЛС-2024", "Медкарта №42", "License 🇷🇺")

        for (label in labels) {
            val plaintext = "$label-fake-bytes".encodeToByteArray()
            val upload = rig.uploader.upload(
                bytes = plaintext,
                kind = PrivateMediaKind.Document,
                linkId = rig.linkId,
                labelInsideCiphertext = label,
                refSource = "config:slot:s-${label.hashCode()}",
            )
            assertTrue(upload is Outcome.Success)
            val uuid = Uuid.parse(upload.value.removePrefix("private:"))
            val envelope = (rig.storage.download(rig.linkId, uuid) as Outcome.Success).value

            envelope.metadata.forEach { (_, valueBytes) ->
                val str = valueBytes.decodeToString()
                assertFalse(
                    str.contains(label),
                    "Label '$label' leaked into plaintext metadata: $str"
                )
            }
        }
    }

    // ─── Test rig ─────────────────────────────────────────────────────────

    private class Rig(
        val linkId: String,
        val uploader: PrivateMediaUploaderImpl,
        val resolver: PrivateMediaResolverImpl,
        val storage: FakeEncryptedMediaStorage,
        val localStore: FakeLocalMediaStore,
    ) {
        companion object {
            fun create(linkId: String): Rig {
                val aead = FakeAeadCipher()
                val asymmetric = FakeAsymmetricCrypto()
                val storage = FakeEncryptedMediaStorage()
                val localStore = FakeLocalMediaStore()

                // One recipient — own device. Uploader and Resolver share same identity для simplicity.
                val deviceId = DeviceId("a1b2c3d4-5678-4abc-9def-000000000001")
                val keyPair = asymmetric.generateX25519Pair("alpha-x25519")
                val signingPub = SigningPublicKey(ByteArray(32) { 0x77 })
                val identity = DeviceIdentity(
                    schemaVersion = SUPPORTED_SCHEMA_VERSION,
                    deviceId = deviceId,
                    publicKey = keyPair.publicKey,
                    signingPublicKey = signingPub,
                    signedTimestamp = 0L,
                    signature = ByteArray(ED25519_SIGNATURE_SIZE) { 0 },
                    createdAt = 0L,
                )

                val recipientResolver = FakeRecipientResolver().apply {
                    setRecipients(linkId, listOf(identity))
                }

                val ledgerStub = BlobReferenceWriter { _, _, _, _ -> /* no-op for facade tests */ }

                val uploader = PrivateMediaUploaderImpl(
                    aeadCipher = aead,
                    asymmetricCrypto = asymmetric,
                    storage = storage,
                    recipientResolver = recipientResolver,
                    ledger = ledgerStub,
                )

                val resolver = PrivateMediaResolverImpl(
                    aeadCipher = aead,
                    asymmetricCrypto = asymmetric,
                    storage = storage,
                    localStore = localStore,
                    ownDeviceId = { deviceId },
                    ownKeyPair = { Outcome.Success(keyPair) },
                )

                return Rig(linkId, uploader, resolver, storage, localStore)
            }
        }
    }
}
