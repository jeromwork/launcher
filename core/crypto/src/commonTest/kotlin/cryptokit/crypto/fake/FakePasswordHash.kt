package cryptokit.crypto.fake

import cryptokit.crypto.api.PasswordHash

/**
 * Deterministic Fake [PasswordHash] для тестов — НЕ Argon2id, простой SHA-256-like
 * stretching via repeated XOR mix. Используется в `:core:keys` тестах где
 * Argon2id-perf не важен, а нужна detrministic derive(password, salt) -> bytes.
 *
 * **НЕ использовать в production.** Не memory-hard, легко подбирается.
 */
class FakePasswordHash : PasswordHash {

    override suspend fun deriveFromPassphrase(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        outputLength: Int
    ): ByteArray {
        require(salt.size >= PasswordHash.SALT_SIZE)
        require(outputLength >= 16)
        val pwBytes = String(password).encodeToByteArray()
        val seed = ByteArray(outputLength)
        for (i in 0 until outputLength) {
            val pwB = pwBytes[i % pwBytes.size].toInt()
            val saltB = salt[i % salt.size].toInt()
            seed[i] = ((pwB xor saltB xor i xor iterations) and 0xFF).toByte()
        }
        // Деterministic «stretching» — несколько проходов mix'а.
        for (round in 0 until 4) {
            for (i in 0 until outputLength) {
                seed[i] = (seed[i].toInt() xor seed[(i + 1) % outputLength].toInt() xor round).toByte()
            }
        }
        return seed
    }
}
