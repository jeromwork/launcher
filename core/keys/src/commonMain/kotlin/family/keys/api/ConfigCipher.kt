package family.keys.api

/**
 * AEAD encryption для ConfigDocument (spec 008) перед отправкой в Firestore
 * (FR-015, FR-016, FR-020).
 *
 * **Не модифицирует spec 008 domain** — оперирует только serialized bytes:
 * ConfigDocument → bytes (spec 008 responsibility) → seal → SealedConfig → Firestore.
 *
 * **AAD binding** (FR-020 identity defense, replay protection):
 *  • `aad = uid || schemaVersion` — попытка open под другим UID → AeadAuthFailed.
 *  • Это защищает от attacker'а, который перехватил SealedConfig и пытается
 *    decrypt'ить под чужим аккаунтом (даже если у него есть тот же DEK material).
 *
 * **Size limit** (FR-029): plaintext > 256 KB → `CipherError.ConfigTooLarge`.
 * Это protects backend bandwidth + предотвращает DoS через over-large blobs.
 *
 * **TODO(capability-registry)**: `ConfigCipher.open` exposes ConfigDocument
 * к potential AI affordance layer — AI MUST run client-side only, никогда
 * не shipить plaintext config в external LLM.
 *
 * Per contracts/sealed-config-v1.md.
 */
interface ConfigCipher {
    /**
     * Шифрует config bytes под текущим uid. DEK выбирается через
     * [KeyRegistry.getDek] с именем `"config-cipher-aead-v1"`.
     *
     * @param configBytes serialized ConfigDocument (spec 008 формат).
     * @param uid Auth identity stableId, embedded в AAD для identity-binding.
     */
    suspend fun seal(configBytes: ByteArray, uid: String): Outcome<SealedConfig, CipherError>

    /**
     * Расшифровывает blob под текущим uid. Mismatch UID → `CipherError.AeadAuthFailed`.
     */
    suspend fun open(sealed: SealedConfig, uid: String): Outcome<ByteArray, CipherError>
}
