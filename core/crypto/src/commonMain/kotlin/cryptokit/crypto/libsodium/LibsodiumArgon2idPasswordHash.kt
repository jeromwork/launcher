@file:OptIn(ExperimentalUnsignedTypes::class)

package cryptokit.crypto.libsodium

import com.ionspin.kotlin.crypto.pwhash.PasswordHash as LibsodiumPwhash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import cryptokit.crypto.api.PasswordHash

/**
 * Argon2id real adapter через ionspin/kotlin-multiplatform-libsodium 0.9.5
 * (`com.ionspin.kotlin.crypto.pwhash.PasswordHash.pwhash`).
 *
 * Добавлено 2026-06-19 для spec 018 (F-5 Recovery flow). Реализует
 * [cryptokit.crypto.api.PasswordHash].
 *
 * **Параметры libsodium**:
 *  • `memLimit` в **байтах** (libsodium API), наш контракт — в **KiB**, конверсия `× 1024`.
 *  • `opsLimit` в passes, наш контракт — `iterations`.
 *  • `algorithm = crypto_pwhash_ALG_DEFAULT` = Argon2id v1.3.
 *
 * **Memory management**:
 *  • CharArray → UTF-8 String — libsodium API требует String, нет CharArray overload.
 *    Это означает immutable copy в memory; caller всё равно должен обнулить CharArray
 *    после вызова. **Acceptable trade-off**: libsodium internally zerolises buffer.
 *  • Output UByteArray → ByteArray конверсия — caller обнуляет ByteArray.
 *
 * **TODO(future-spec algorithm-migration)**: при miграции на Argon2id v2 или другой algorithm,
 * добавить parameter `algorithm: Int` в expand-API.
 */
class LibsodiumArgon2idPasswordHash : PasswordHash {

    override suspend fun deriveFromPassphrase(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        outputLength: Int
    ): ByteArray {
        require(salt.size >= PasswordHash.SALT_SIZE) {
            "Argon2id salt must be ≥ ${PasswordHash.SALT_SIZE} bytes, got ${salt.size}"
        }
        require(outputLength >= 16) {
            "Argon2id output length must be ≥ 16 bytes (crypto_pwhash_BYTES_MIN), got $outputLength"
        }
        require(memoryKib >= 8) {
            "Argon2id memoryKib must be ≥ 8 (crypto_pwhash_MEMLIMIT_MIN = 8192 bytes), got $memoryKib"
        }
        require(iterations >= 1) {
            "Argon2id iterations must be ≥ 1, got $iterations"
        }

        LibsodiumInit.ensure()

        // libsodium expects exactly crypto_pwhash_SALTBYTES (16) bytes; truncate if caller passed more.
        val saltFixed = if (salt.size == crypto_pwhash_SALTBYTES) salt else salt.copyOf(crypto_pwhash_SALTBYTES)

        val derived: UByteArray = LibsodiumPwhash.pwhash(
            outputLength = outputLength,
            password = password.concatToString(),
            salt = saltFixed.toUByteArray(),
            opsLimit = iterations.toULong(),
            memLimit = memoryKib * 1024,
            algorithm = crypto_pwhash_ALG_DEFAULT
        )

        return derived.toByteArray()
    }
}
