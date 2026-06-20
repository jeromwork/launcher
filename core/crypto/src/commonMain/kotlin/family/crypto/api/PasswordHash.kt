package family.crypto.api

/**
 * Memory-hard password hashing — Argon2id only.
 *
 * **Не путать с [KeyDerivation]** (HKDF-SHA256): KDF растягивает уже-хороший ключ
 * (результат ECDH, master key), PasswordHash превращает **слабый человеческий пароль**
 * в дорогой для перебора 32-байтовый ключ.
 *
 * Добавлено 2026-06-19 для spec 018 (F-5 Recovery flow). Без spec extension —
 * расширение F-CRYPTO как evolution library, второй потребитель = `:core:keys`.
 *
 * **Параметры по умолчанию = interactive** (spec 018 FR-030):
 *  • memory = 64 MiB (crypto_pwhash_MEMLIMIT_INTERACTIVE = 67108864)
 *  • iterations (opsLimit) = 3 (consistent с RecoveryVaultBlob spec — explicit value, не libsodium constant)
 *  • parallelism = 1 (libsodium API не выставляет parallel — внутренний sodium default)
 *
 * **Output length** = 32 bytes (для XChaCha20-Poly1305 ключа в recovery wrap).
 *
 * **Security**:
 *  • Salt — caller обязан передать min 16 байт от [RandomSource].
 *  • Output ByteArray — caller отвечает за обнуление после wrap/unwrap.
 *  • Password CharArray — caller отвечает за обнуление сразу после вызова.
 */
interface PasswordHash {

    /**
     * Argon2id derivation.
     *
     * @param password passphrase as [CharArray] (mutable так как caller обнуляет после).
     *   Internally encoded as UTF-8 для derivation.
     * @param salt cryptographic salt, ≥ 16 байт. Уникальный per [RecoveryVaultBlob].
     * @param memoryKib memory cost в KiB. По умолчанию 65536 (64 MiB) = interactive.
     * @param iterations opsLimit (passes). По умолчанию 3 = interactive baseline.
     * @param outputLength размер derived key в байтах. По умолчанию 32 (для AEAD ключа).
     * @return Derived key bytes длины [outputLength]. Caller обязан обнулить после использования.
     *
     * @throws family.crypto.exception.CryptoException.RandomSourceUnavailable если sodium init упал.
     * @throws IllegalArgumentException если [salt].size < 16 или [outputLength] < 16.
     */
    suspend fun deriveFromPassphrase(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int = DEFAULT_MEMORY_KIB,
        iterations: Int = DEFAULT_ITERATIONS,
        outputLength: Int = DEFAULT_OUTPUT_LENGTH
    ): ByteArray

    companion object {
        /** 64 MiB — interactive (spec 018 FR-030). */
        const val DEFAULT_MEMORY_KIB: Int = 65_536

        /** 3 passes — interactive. */
        const val DEFAULT_ITERATIONS: Int = 3

        /** 32 bytes = AEAD key size. */
        const val DEFAULT_OUTPUT_LENGTH: Int = 32

        /** libsodium fixed salt size. */
        const val SALT_SIZE: Int = 16
    }
}
