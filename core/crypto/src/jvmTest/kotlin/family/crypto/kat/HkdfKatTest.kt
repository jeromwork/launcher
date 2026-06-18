package family.crypto.kat

import family.crypto.libsodium.LibsodiumKeyDerivation
import family.crypto.util.hexToByteArray
import family.crypto.util.toHex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 5869 App. A — HKDF-SHA256 known-answer vectors. Per FR-019 + SC-002.
 *
 * NOTE: vectors use raw byte `info` per RFC. Our [LibsodiumKeyDerivation.derive] takes
 * `info: String` — we convert via `String(bytes)` for binary-clean ASCII vectors below.
 */
class HkdfKatTest {

    private val kdf = LibsodiumKeyDerivation()

    // RFC 5869 §A.1: Test Case 1 (Basic test case with SHA-256).
    @Test
    fun rfc5869_testCase1() = runTest {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToByteArray()
        val salt = "000102030405060708090a0b0c".hexToByteArray()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray()
        val length = 42
        val expectedOkm = (
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865"
            ).hexToByteArray()

        val okm = kdf.derive(ikm, salt, info, length)
        assertEquals(expectedOkm.toHex(), okm.toHex())
    }

    // RFC 5869 §A.3: Test Case 3 (Test with SHA-256 and zero-length salt/info).
    @Test
    fun rfc5869_testCase3_emptySaltAndInfo() = runTest {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToByteArray()
        val salt = ByteArray(0)
        val length = 42
        val expectedOkm = (
            "8da4e775a563c18f715f802a063c5a31" +
                "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a96c8"
            ).hexToByteArray()

        val okm = kdf.derive(ikm, salt, ByteArray(0), length)
        assertEquals(expectedOkm.toHex(), okm.toHex())
    }
}
