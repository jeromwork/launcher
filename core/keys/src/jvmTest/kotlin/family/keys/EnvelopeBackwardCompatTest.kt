package family.keys

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumAsymmetricCrypto
import cryptokit.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.DeviceId
import family.keys.api.Envelope
import family.keys.api.Outcome
import family.keys.impl.EnvelopeConfigCipherImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Backward-compatibility test fixture for v1 [Envelope] wire format (Task 4 AC #4).
 *
 * Verifies long-term wire format stability against a frozen v1 serialized fixture
 * per FR-012 / FR-013 and contracts/envelope-v1.md.
 */
class EnvelopeBackwardCompatTest {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        // Frozen v1 serialized JSON fixture produced by EnvelopeConfigCipherImpl
        private const val FROZEN_V1_FIXTURE = """{"schemaVersion":1,"algorithm":"envelope-xchacha20poly1305-x25519-v1","ciphertext":"BA5ZFyzImJ+dfSZdngCyCS0nV0KXCKuz3YD8XTFTsKtgkXlPEssCkzMVZ5YxTZc=","nonce":"Hqdw0kucr5ONGU0W9BuDFf8GyS7p8fSk","aad":"YmFja3dhcmQtY29tcGF0LWFhZC12MQ==","recipientKeys":{"fixture-device-v1":"LDb2hqRVo0z5FvdhkrR91wQCjclknOrsWjbd3LXmlF5gfapxb1XmdwBuyKHwIF0orhv6hWB+iyzODrqkiPrVHi14QaQ69nu9gqyFYVFXJy8="}}"""
    }

    @Test
    fun frozenV1FixtureDeserializesAndDecryptsSuccessfully() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()

        val privKeyBytes = ByteArray(32) { (it + 11).toByte() }
        val deviceId = DeviceId("fixture-device-v1")
        val aad = "backward-compat-aad-v1".encodeToByteArray()

        val cipher = EnvelopeConfigCipherImpl(
            aead = LibsodiumAeadCipher(),
            asymmetric = LibsodiumAsymmetricCrypto(),
            random = LibsodiumRandomSource()
        )

        // Verify deserialization from frozen string
        val envelope = json.decodeFromString<Envelope>(FROZEN_V1_FIXTURE)
        assertEquals(1, envelope.schemaVersion)
        assertEquals("envelope-xchacha20poly1305-x25519-v1", envelope.algorithm)
        assertContentEquals("backward-compat-aad-v1".encodeToByteArray(), envelope.aad)

        // Verify decryption using open
        val openResult = cipher.open(envelope, privKeyBytes, deviceId, aad)
        val success = openResult as Outcome.Success<ByteArray>
        assertEquals("frozen v1 config secret payload", success.value.decodeToString())
    }
}
