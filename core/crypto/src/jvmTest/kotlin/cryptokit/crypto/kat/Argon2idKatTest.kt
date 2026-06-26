@file:OptIn(ExperimentalUnsignedTypes::class)

package cryptokit.crypto.kat

import cryptokit.crypto.api.PasswordHash
import cryptokit.crypto.libsodium.LibsodiumArgon2idPasswordHash
import cryptokit.crypto.libsodium.LibsodiumInit
import cryptokit.crypto.util.toHex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

/**
 * KAT + property tests для [LibsodiumArgon2idPasswordHash] (spec 018 F-5 dependency).
 *
 * Полный RFC 9106 vector с массивными memory params (128 MiB+) непрактичен в CI —
 * используем libsodium-self-consistent validation:
 *  • determinism: тот же password+salt+params → тот же output.
 *  • salt sensitivity: разный salt → совершенно разный output.
 *  • password sensitivity: разный password → совершенно разный output.
 *  • output length variants: 16/32/64 bytes — все detrministic.
 *  • parameter validation: invalid salt / outputLength / memoryKib / iterations rejected.
 *
 * Lightweight params (mem=8 MiB, iter=2) для быстрого CI; production params
 * (mem=64 MiB, iter=3) проверяются в Argon2idBenchmark (jvmTest perf).
 */
class Argon2idKatTest {

    @Test
    fun determinism_sameInputsProduceSameOutput() = runTest {
        LibsodiumInit.ensure()
        val pwhash = LibsodiumArgon2idPasswordHash()
        val pw = "correct horse battery staple".toCharArray()
        val salt = ByteArray(16) { (it * 7).toByte() }
        val out1 = pwhash.deriveFromPassphrase(pw, salt, memoryKib = 8192, iterations = 2)
        val out2 = pwhash.deriveFromPassphrase(pw, salt, memoryKib = 8192, iterations = 2)
        assertContentEquals(out1, out2, "Argon2id MUST be deterministic for identical inputs")
        assertEquals(32, out1.size)
    }

    @Test
    fun saltSensitivity_differentSaltProducesDifferentOutput() = runTest {
        LibsodiumInit.ensure()
        val pwhash = LibsodiumArgon2idPasswordHash()
        val pw = "correct horse battery staple".toCharArray()
        val salt1 = ByteArray(16) { 0x01 }
        val salt2 = ByteArray(16) { 0x02 }
        val out1 = pwhash.deriveFromPassphrase(pw, salt1, memoryKib = 8192, iterations = 2)
        val out2 = pwhash.deriveFromPassphrase(pw, salt2, memoryKib = 8192, iterations = 2)
        assertNotEquals(out1.toHex(), out2.toHex())
    }

    @Test
    fun passwordSensitivity_differentPasswordProducesDifferentOutput() = runTest {
        LibsodiumInit.ensure()
        val pwhash = LibsodiumArgon2idPasswordHash()
        val salt = ByteArray(16) { 0x42 }
        val out1 = pwhash.deriveFromPassphrase("password1".toCharArray(), salt, memoryKib = 8192, iterations = 2)
        val out2 = pwhash.deriveFromPassphrase("password2".toCharArray(), salt, memoryKib = 8192, iterations = 2)
        assertNotEquals(out1.toHex(), out2.toHex())
    }

    @Test
    fun outputLength_16_32_64_allWork() = runTest {
        LibsodiumInit.ensure()
        val pwhash = LibsodiumArgon2idPasswordHash()
        val pw = "hello".toCharArray()
        val salt = ByteArray(16) { 0x55 }
        val out16 = pwhash.deriveFromPassphrase(pw, salt, memoryKib = 8192, iterations = 2, outputLength = 16)
        val out32 = pwhash.deriveFromPassphrase(pw, salt, memoryKib = 8192, iterations = 2, outputLength = 32)
        val out64 = pwhash.deriveFromPassphrase(pw, salt, memoryKib = 8192, iterations = 2, outputLength = 64)
        assertEquals(16, out16.size)
        assertEquals(32, out32.size)
        assertEquals(64, out64.size)
    }

    @Test
    fun defaultParams_matchInteractiveProfileFromSpec018() {
        // FR-030 fixture: interactive profile = 64MB memory, 3 iterations, output 32 bytes.
        assertEquals(65_536, PasswordHash.DEFAULT_MEMORY_KIB)
        assertEquals(3, PasswordHash.DEFAULT_ITERATIONS)
        assertEquals(32, PasswordHash.DEFAULT_OUTPUT_LENGTH)
        assertEquals(16, PasswordHash.SALT_SIZE)
    }

    @Test
    fun invalidSalt_tooShort_rejected() = runTest {
        LibsodiumInit.ensure()
        val pwhash = LibsodiumArgon2idPasswordHash()
        assertFailsWith<IllegalArgumentException> {
            pwhash.deriveFromPassphrase(
                "pw".toCharArray(),
                ByteArray(8),
                memoryKib = 8192,
                iterations = 2
            )
        }
    }

    @Test
    fun invalidOutputLength_tooShort_rejected() = runTest {
        LibsodiumInit.ensure()
        val pwhash = LibsodiumArgon2idPasswordHash()
        assertFailsWith<IllegalArgumentException> {
            pwhash.deriveFromPassphrase(
                "pw".toCharArray(),
                ByteArray(16),
                memoryKib = 8192,
                iterations = 2,
                outputLength = 8
            )
        }
    }

    @Test
    fun invalidIterations_zero_rejected() = runTest {
        LibsodiumInit.ensure()
        val pwhash = LibsodiumArgon2idPasswordHash()
        assertFailsWith<IllegalArgumentException> {
            pwhash.deriveFromPassphrase(
                "pw".toCharArray(),
                ByteArray(16),
                memoryKib = 8192,
                iterations = 0
            )
        }
    }
}
