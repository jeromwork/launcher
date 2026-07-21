package family.keys

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumAsymmetricCrypto
import family.crypto.libsodium.LibsodiumRandomSource
import family.keys.api.DeviceId
import family.keys.api.Envelope
import family.keys.api.Outcome
import family.keys.impl.EnvelopeConfigCipherImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Backward-compatibility test for the v1 [Envelope] ciphertext (spec 018 FR-012/013,
 * contracts/envelope-v1.md).
 *
 * The guarantee is a crypto one: a ciphertext produced by v1 code MUST still decrypt.
 * Since TASK-141 removed @Serializable from [Envelope] (it is a pure crypto type now),
 * the frozen document is parsed as generic JSON and its base64 byte fields decoded to
 * rebuild the [Envelope] — the wire container is incidental, the frozen ciphertext is
 * the thing under test. The fixture string itself is untouched (it is the evidence).
 */
class EnvelopeBackwardCompatTest {

    companion object {
        // Frozen v1 serialized JSON fixture produced by EnvelopeConfigCipherImpl.
        private const val FROZEN_V1_FIXTURE = """{"schemaVersion":1,"algorithm":"envelope-xchacha20poly1305-x25519-v1","ciphertext":"BA5ZFyzImJ+dfSZdngCyCS0nV0KXCKuz3YD8XTFTsKtgkXlPEssCkzMVZ5YxTZc=","nonce":"Hqdw0kucr5ONGU0W9BuDFf8GyS7p8fSk","aad":"YmFja3dhcmQtY29tcGF0LWFhZC12MQ==","recipientKeys":{"fixture-device-v1":"LDb2hqRVo0z5FvdhkrR91wQCjclknOrsWjbd3LXmlF5gfapxb1XmdwBuyKHwIF0orhv6hWB+iyzODrqkiPrVHi14QaQ69nu9gqyFYVFXJy8="}}"""
    }

    @Test
    fun frozenV1FixtureDeserializesAndDecryptsSuccessfully() = runTest {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initialize()

        val privKeyBytes = ByteArray(32) { (it + 11).toByte() }
        val deviceId = DeviceId("fixture-device-v1")
        val aad = "backward-compat-aad-v1".encodeToByteArray()

        val obj = Json.parseToJsonElement(FROZEN_V1_FIXTURE).jsonObject
        fun decode(key: String): ByteArray = Base64.getDecoder().decode(obj[key]!!.jsonPrimitive.content)
        val recipientKeys = obj["recipientKeys"]!!.jsonObject
            .mapValues { Base64.getDecoder().decode(it.value.jsonPrimitive.content) }

        // The wire document still declares schemaVersion 1 — the version now lives in
        // the adapter/wire, not inside the crypto type.
        assertEquals(1, obj["schemaVersion"]!!.jsonPrimitive.int)

        val envelope = Envelope(
            algorithm = obj["algorithm"]!!.jsonPrimitive.content,
            ciphertext = decode("ciphertext"),
            nonce = decode("nonce"),
            aad = decode("aad"),
            recipientKeys = recipientKeys,
        )
        assertEquals("envelope-xchacha20poly1305-x25519-v1", envelope.algorithm)
        assertContentEquals("backward-compat-aad-v1".encodeToByteArray(), envelope.aad)

        val cipher = EnvelopeConfigCipherImpl(
            aead = LibsodiumAeadCipher(),
            asymmetric = LibsodiumAsymmetricCrypto(),
            random = LibsodiumRandomSource()
        )

        val openResult = cipher.open(envelope, privKeyBytes, deviceId, aad)
        val success = openResult as Outcome.Success<ByteArray>
        assertEquals("frozen v1 config secret payload", success.value.decodeToString())
    }
}
