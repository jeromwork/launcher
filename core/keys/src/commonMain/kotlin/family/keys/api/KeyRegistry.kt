package family.keys.api

/**
 * Реестр DEK'ов (Data Encryption Keys), partitioned by current identity
 * (FR-004, FR-005, FR-023, FR-031).
 *
 * **Naming convention**: stable name per use case (`"config-cipher-aead-v1"`,
 * `"pair-x25519-v1"`, `"photo-aead-v1"`). Версия в имени — explicit migration
 * trigger (новая версия = новый name, обе coexist).
 *
 * **Identity isolation** (FR-031, FR-031a): один KeyRegistry instance видит ТОЛЬКО
 * DEK'и текущей identity. Switch identity → новый namespace. Никаких cross-UID
 * операций в API (verified compile-time через `NoCrossUidApiInKeyRegistryTest`).
 *
 * **Forward-compat** (FR-005): чтение storage с unknown DEK (новый spec) НЕ должно
 * crashить — `getDek` для unknown name возвращает `KeyRegistryError.NotFound`.
 *
 * Per contracts/key-registry-v1.md.
 */
interface KeyRegistry {
    /**
     * Регистрирует DEK под [name]. Идемпотентно: повторный вызов с тем же name —
     * overwrite (используется при rotation).
     *
     * @param dekMaterial 32-байтовый key material, plaintext. Implementation
     *   обязана сразу обнулить input после wrapping.
     */
    suspend fun registerDek(name: String, dekMaterial: ByteArray): Outcome<Unit, KeyRegistryError>

    /**
     * Возвращает unwrapped DEK material. Caller обязан обнулить буфер после использования.
     */
    suspend fun getDek(name: String): Outcome<ByteArray, KeyRegistryError>

    /** Быстрая проверка наличия DEK без unwrap'а. */
    suspend fun hasDek(name: String): Boolean
}
