package family.crypto.kat

import family.crypto.api.values.Signature
import family.crypto.libsodium.LibsodiumAsymmetricCrypto
import family.crypto.util.hexToByteArray
import family.crypto.util.toHex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC 8032 §7.1 — Ed25519 known-answer vectors. Per FR-019 + SC-002.
 *
 * ionspin `Signature.detached` expects a 64-byte secret key (RFC seed || pub).
 * We construct that by deriving the pubkey from the seed via `seedKeypair`.
 */
class Ed25519KatTest {

    private val crypto = LibsodiumAsymmetricCrypto()

    // RFC 8032 §7.1 TEST 1: empty message.
    @Test
    fun rfc8032_test1_emptyMessage() = runTest {
        val seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60".hexToByteArray()
        val pub = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".hexToByteArray()
        val sk64 = seed + pub
        val message = ByteArray(0)
        val expectedSig = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"

        val sig = crypto.sign(message, sk64)
        assertEquals(expectedSig, sig.bytes.toHex())
        assertTrue(crypto.verify(sig, message, pub))
    }

    // RFC 8032 §7.1 TEST 2: 1-byte message.
    @Test
    fun rfc8032_test2_singleByte() = runTest {
        val seed = "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb".hexToByteArray()
        val pub = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c".hexToByteArray()
        val sk64 = seed + pub
        val message = "72".hexToByteArray()
        val expectedSig = "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"

        val sig = crypto.sign(message, sk64)
        assertEquals(expectedSig, sig.bytes.toHex())
        assertTrue(crypto.verify(sig, message, pub))
    }

    // RFC 8032 §7.1 TEST 3: 2-byte message.
    @Test
    fun rfc8032_test3_twoBytes() = runTest {
        val seed = "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7".hexToByteArray()
        val pub = "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025".hexToByteArray()
        val sk64 = seed + pub
        val message = "af82".hexToByteArray()
        val expectedSig = "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"

        val sig = crypto.sign(message, sk64)
        assertEquals(expectedSig, sig.bytes.toHex())
        assertTrue(crypto.verify(sig, message, pub))
    }

    @Test
    fun tamperedSignatureFailsVerify() = runTest {
        val seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60".hexToByteArray()
        val pub = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".hexToByteArray()
        val sk64 = seed + pub
        val sig = crypto.sign(byteArrayOf(1, 2, 3), sk64)
        val tampered = sig.bytes.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x01).toByte()
        assertEquals(false, crypto.verify(Signature(tampered), byteArrayOf(1, 2, 3), pub))
    }
}
