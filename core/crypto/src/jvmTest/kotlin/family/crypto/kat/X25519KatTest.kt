package family.crypto.kat

import family.crypto.libsodium.LibsodiumAsymmetricCrypto
import family.crypto.util.hexToByteArray
import family.crypto.util.toHex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 7748 §5.2 + §6.1 — X25519 known-answer vectors. Per FR-019 + SC-002.
 *
 * Vectors validate raw `crypto_scalarmult` output (NOT `crypto_box_beforenm`).
 */
class X25519KatTest {

    private val crypto = LibsodiumAsymmetricCrypto()

    // RFC 7748 §5.2 vector 1 — single scalar*basepoint operation.
    @Test
    fun rfc7748_section5_2_vector1() = runTest {
        val scalar = "a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4".hexToByteArray()
        val uCoord = "e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c".hexToByteArray()
        val expected = "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"

        val result = crypto.deriveSharedSecret(scalar, uCoord).bytes
        assertEquals(expected, result.toHex())
    }

    @Test
    fun rfc7748_section5_2_vector2() = runTest {
        val scalar = "4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d".hexToByteArray()
        val uCoord = "e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493".hexToByteArray()
        val expected = "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957"

        val result = crypto.deriveSharedSecret(scalar, uCoord).bytes
        assertEquals(expected, result.toHex())
    }

    /**
     * RFC 7748 §6.1 — Alice + Bob ECDH key exchange. Verifies symmetry over the same byte
     * pattern that the RFC text mandates.
     */
    @Test
    fun rfc7748_section6_1_aliceBobEcdh() = runTest {
        val alicePriv = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hexToByteArray()
        val alicePub = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a".hexToByteArray()
        val bobPriv = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb".hexToByteArray()
        val bobPub = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f".hexToByteArray()
        val expectedShared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"

        val s1 = crypto.deriveSharedSecret(alicePriv, bobPub).bytes
        val s2 = crypto.deriveSharedSecret(bobPriv, alicePub).bytes
        assertEquals(expectedShared, s1.toHex())
        assertEquals(expectedShared, s2.toHex())
    }
}
