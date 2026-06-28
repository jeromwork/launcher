package family.keys.api

/**
 * Управление root key per [AuthIdentity] (FR-003, FR-031).
 *
 * **Lifecycle**:
 *  1. First Sign-In: generate 256-bit random root key → wrap via SecureKeyStore →
 *     persist wrapped blob → prompt user to set passphrase → derive Argon2id wrapKey →
 *     AEAD-wrap root → upload to [RecoveryKeyBackup].
 *  2. Subsequent launches: read wrapped blob → unwrap via SecureKeyStore → return.
 *  3. New device (Sign-In но Keystore пуст): fetchBlob → если есть → prompt passphrase →
 *     derive Argon2id → unwrap → re-persist в local Keystore.
 *  4. Recovery missing: return `RecoveryRequired`. UI offers "set up as new device" path.
 *
 * **UID partitioning** (FR-031): namespace key = `AuthIdentity.stableId`. Switch
 * identity → новый namespace, существующий ключ другой identity не trogается.
 *
 * **Caller responsibility**: после получения [RootKey] вызвать `.wipe()` когда
 * больше не нужен (но обычно держится до process kill, потому что DEK derive
 * вызывается часто).
 */
interface RootKeyManager {
    /**
     * Возвращает root key текущей identity. Создаёт новый если не существует.
     */
    suspend fun getOrCreate(identity: AuthIdentity): Outcome<RootKey, RootKeyError>

    /**
     * Удаляет root key конкретной identity (Sign-Out + local cleanup). Также
     * удаляет связанные DEK'и из [KeyRegistry] для этой identity.
     *
     * NOT touches Firestore recovery-backup — это server-side responsibility
     * через `RecoveryKeyBackup.deleteBlob` отдельно.
     */
    suspend fun wipe(identity: AuthIdentity): Outcome<Unit, RootKeyError>
}
