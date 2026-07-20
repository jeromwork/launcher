package family.keys

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumAsymmetricCrypto
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.CipherError
import family.keys.api.DeviceId
import family.keys.api.Envelope
import family.keys.api.Outcome
import family.keys.api.RecipientPubKey
import family.keys.impl.EnvelopeConfigCipherImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 4b roundtrip + multi-recipient tests for hybrid envelope cipher (F-5b
 * scope expansion 2026-06-20). Replaces the legacy symmetric
 * [family.keys.ConfigCipherRoundtripTest] semantics with the envelope pattern
 * from spec 011 §C-3.
 */
class EnvelopeConfigCipherRoundtripTest {

    private suspend fun init() {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()
    }

    private suspend fun cipher() = EnvelopeConfigCipherImpl(
        aead = LibsodiumAeadCipher(),
        asymmetric = LibsodiumAsymmetricCrypto(),
        random = LibsodiumRandomSource()
    )

    private suspend fun newRecipient(idStr: String): RecipientWithKeys {
        val asym = LibsodiumAsymmetricCrypto()
        val kp = asym.generateX25519KeyPair()
        return RecipientWithKeys(
            recipient = RecipientPubKey(DeviceId(idStr), kp.publicKey),
            privKey = kp.privateKey
        )
    }

    private data class RecipientWithKeys(
        val recipient: RecipientPubKey,
        val privKey: ByteArray
    )

    @Test
    fun singleRecipientRoundtrip() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice-phone")
        val aad = "test::owner-uid::config/default".encodeToByteArray()
        val plaintext = "secret config payload".encodeToByteArray()

        val envelope = (c.seal(plaintext, listOf(alice.recipient), aad) as Outcome.Success<Envelope>).value
        assertEquals(1, envelope.recipientKeys.size)
        assertTrue("alice-phone" in envelope.recipientKeys)

        val opened = (c.open(envelope, alice.privKey, alice.recipient.deviceId, aad) as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, opened)
    }

    @Test
    fun multiRecipientThreeDevicesEachCanOpen() = runTest {
        init()
        val c = cipher()
        val phone = newRecipient("admin-phone")
        val tablet = newRecipient("admin-tablet")
        val tv = newRecipient("admin-tv")
        val aad = "test::admin-uid::config/default".encodeToByteArray()
        val plaintext = "shared admin config".encodeToByteArray()

        val envelope = (c.seal(plaintext, listOf(phone.recipient, tablet.recipient, tv.recipient), aad)
                as Outcome.Success<Envelope>).value
        assertEquals(3, envelope.recipientKeys.size)

        // Each of the three devices can open with its own privKey.
        val fromPhone = (c.open(envelope, phone.privKey, phone.recipient.deviceId, aad) as Outcome.Success<ByteArray>).value
        val fromTablet = (c.open(envelope, tablet.privKey, tablet.recipient.deviceId, aad) as Outcome.Success<ByteArray>).value
        val fromTv = (c.open(envelope, tv.privKey, tv.recipient.deviceId, aad) as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, fromPhone)
        assertContentEquals(plaintext, fromTablet)
        assertContentEquals(plaintext, fromTv)
    }

    @Test
    fun crossUserDelegationOwnerAndHelperBothOpen() = runTest {
        init()
        val c = cipher()
        // Babushka writes config to her own namespace; admin holds an access-grant.
        // RecipientResolver in prod would assemble [babushka-phone, admin-phone].
        val babushka = newRecipient("babushka-phone")
        val admin = newRecipient("admin-phone")
        val aad = "test::babushka-uid::config/grannys-phone".encodeToByteArray()
        val plaintext = "config edited by admin via grant".encodeToByteArray()

        val envelope = (c.seal(plaintext, listOf(babushka.recipient, admin.recipient), aad)
                as Outcome.Success<Envelope>).value

        // Owner reads.
        val ownerOpen = (c.open(envelope, babushka.privKey, babushka.recipient.deviceId, aad)
                as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, ownerOpen)

        // Helper (admin) reads.
        val helperOpen = (c.open(envelope, admin.privKey, admin.recipient.deviceId, aad)
                as Outcome.Success<ByteArray>).value
        assertContentEquals(plaintext, helperOpen)
    }

    @Test
    fun nonRecipientCannotOpenReturnsNotARecipient() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val bob = newRecipient("bob")
        val aad = "test::owner::key".encodeToByteArray()
        val envelope = (c.seal("x".encodeToByteArray(), listOf(alice.recipient), aad)
                as Outcome.Success<Envelope>).value

        // Bob is not in recipientKeys.
        val attempt = c.open(envelope, bob.privKey, bob.recipient.deviceId, aad)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertEquals(CipherError.NotARecipient, attempt.error)
    }

    @Test
    fun aadMismatchReturnsAeadAuthFailed() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aadStored = "test::owner::config/default".encodeToByteArray()
        val envelope = (c.seal("x".encodeToByteArray(), listOf(alice.recipient), aadStored)
                as Outcome.Success<Envelope>).value

        val aadWrong = "test::owner::config/grannys-phone".encodeToByteArray()
        val attempt = c.open(envelope, alice.privKey, alice.recipient.deviceId, aadWrong)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertEquals(CipherError.AeadAuthFailed, attempt.error)
    }

    @Test
    fun tamperedCiphertextReturnsAeadAuthFailed() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "test::owner::key".encodeToByteArray()
        val envelope = (c.seal("secret".encodeToByteArray(), listOf(alice.recipient), aad)
                as Outcome.Success<Envelope>).value
        val tampered = envelope.copy(
            ciphertext = envelope.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        )
        val attempt = c.open(tampered, alice.privKey, alice.recipient.deviceId, aad)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertEquals(CipherError.AeadAuthFailed, attempt.error)
    }

    @Test
    fun tamperedSealedCekReturnsAeadAuthFailed() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "test::owner::key".encodeToByteArray()
        val envelope = (c.seal("secret".encodeToByteArray(), listOf(alice.recipient), aad)
                as Outcome.Success<Envelope>).value

        val brokenSealed = envelope.recipientKeys["alice"]!!.copyOf().also {
            it[0] = (it[0].toInt() xor 0xFF).toByte()
        }
        val tampered = envelope.copy(recipientKeys = mapOf("alice" to brokenSealed))
        val attempt = c.open(tampered, alice.privKey, alice.recipient.deviceId, aad)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        // sealForRecipient/openSealed surfaces DecryptionFailed which maps to AeadAuthFailed.
        assertEquals(CipherError.AeadAuthFailed, attempt.error)
    }

    @Test
    fun futureSchemaVersionRejected() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "test::owner::key".encodeToByteArray()
        val envelope = (c.seal("x".encodeToByteArray(), listOf(alice.recipient), aad)
                as Outcome.Success<Envelope>).value
        val future = envelope.copy(schemaVersion = Envelope.SCHEMA_VERSION + 99)
        val attempt = c.open(future, alice.privKey, alice.recipient.deviceId, aad)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertEquals(CipherError.AlgorithmUnsupported, attempt.error)
    }

    @Test
    fun unknownAlgorithmRejected() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "test::owner::key".encodeToByteArray()
        val envelope = (c.seal("x".encodeToByteArray(), listOf(alice.recipient), aad)
                as Outcome.Success<Envelope>).value
        val forged = envelope.copy(algorithm = "unknown-experimental")
        val attempt = c.open(forged, alice.privKey, alice.recipient.deviceId, aad)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertEquals(CipherError.AlgorithmUnsupported, attempt.error)
    }

    @Test
    fun emptyRecipientListRejected() = runTest {
        init()
        val c = cipher()
        val aad = "test::owner::key".encodeToByteArray()
        val attempt = c.seal("x".encodeToByteArray(), emptyList(), aad)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertIs<CipherError.InvalidInput>(attempt.error)
    }

    @Test
    fun duplicateRecipientIdsRejected() = runTest {
        init()
        val c = cipher()
        val asym = LibsodiumAsymmetricCrypto()
        val kp1 = asym.generateX25519KeyPair()
        val kp2 = asym.generateX25519KeyPair()
        val dup = listOf(
            RecipientPubKey(DeviceId("phone-A"), kp1.publicKey),
            RecipientPubKey(DeviceId("phone-A"), kp2.publicKey) // same id
        )
        val attempt = c.seal("x".encodeToByteArray(), dup, "aad".encodeToByteArray())
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertIs<CipherError.InvalidInput>(attempt.error)
    }

    @Test
    fun ciphertextDiffersOnEachSealEvenForSamePlaintextAndRecipients() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "test::owner::key".encodeToByteArray()
        val plaintext = "deterministic-ish".encodeToByteArray()
        val a = (c.seal(plaintext, listOf(alice.recipient), aad) as Outcome.Success<Envelope>).value
        val b = (c.seal(plaintext, listOf(alice.recipient), aad) as Outcome.Success<Envelope>).value
        assertNotEquals(a.nonce.toList(), b.nonce.toList())
        assertNotEquals(a.ciphertext.toList(), b.ciphertext.toList())
        // CEK is random per seal, so sealed CEK also differs.
        assertNotEquals(
            a.recipientKeys["alice"]!!.toList(),
            b.recipientKeys["alice"]!!.toList()
        )
    }

    @Test
    fun oversizedPlaintextRejected() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "aad".encodeToByteArray()
        val huge = ByteArray(EnvelopeConfigCipherImpl.MAX_PLAINTEXT_BYTES + 1)
        val attempt = c.seal(huge, listOf(alice.recipient), aad)
        assertIs<Outcome.Failure<CipherError>>(attempt)
        assertEquals(CipherError.ConfigTooLarge, attempt.error)
    }

    @Test
    fun atMaxPlaintextSizeAccepted() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "aad".encodeToByteArray()
        val maxPayload = ByteArray(EnvelopeConfigCipherImpl.MAX_PLAINTEXT_BYTES) { (it % 251).toByte() }
        val envelope = (c.seal(maxPayload, listOf(alice.recipient), aad) as Outcome.Success<Envelope>).value
        val opened = (c.open(envelope, alice.privKey, alice.recipient.deviceId, aad)
                as Outcome.Success<ByteArray>).value
        assertContentEquals(maxPayload, opened)
    }

    @Test
    fun emptyPlaintextRoundtrip() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val aad = "aad".encodeToByteArray()
        val envelope = (c.seal(ByteArray(0), listOf(alice.recipient), aad) as Outcome.Success<Envelope>).value
        val opened = (c.open(envelope, alice.privKey, alice.recipient.deviceId, aad)
                as Outcome.Success<ByteArray>).value
        assertEquals(0, opened.size)
    }

    @Test
    fun envelopeMapKeysSurviveJsonRoundtripImplicitlyByEqualityCheck() = runTest {
        init()
        val c = cipher()
        val alice = newRecipient("alice")
        val bob = newRecipient("bob")
        val aad = "aad".encodeToByteArray()
        val envelope = (c.seal("xx".encodeToByteArray(), listOf(alice.recipient, bob.recipient), aad)
                as Outcome.Success<Envelope>).value
        // Sanity that two recipients survive equals + hashCode.
        assertEquals(2, envelope.recipientKeys.size)
        val same = envelope.copy()
        assertEquals(envelope, same)
        assertEquals(envelope.hashCode(), same.hashCode())
        assertNotNull(envelope.recipientKeys["alice"])
        assertNotNull(envelope.recipientKeys["bob"])
    }
}
