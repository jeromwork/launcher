package family.keys.api

import kotlinx.coroutines.flow.Flow

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
 *
 * **One-way door rule** (FR-007, TASK-41 exit ramp):
 * // TODO(FR-007, TASK-41): порт через этот interface изолирует потребительей от Keystore-специфики.
 * //   При переходе на Go microservice порт адаптируется без изменения domain interface.
 */
interface RootKeyManager {

    // --- Legacy API (spec 018) — KEEP for backward compatibility (D3) ---

    /**
     * Возвращает root key текущей identity. Создаёт новый если не существует.
     * Spec 018 consumers используют этот метод; F-5 код предпочитает [current] + [create]/[recover].
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

    // --- F-5 additions (T613, D3) ---

    /**
     * Flow текущего root key (или `null` если нет активного ключа). Наблюдаемый
     * составляющими (ViewModel, ConfigSaver) отреагируют на изменения.
     *
     * `null` в flow означает:
     *  - Пользователь не залогинен, или
     *  - [forget] был вызван, или
     *  - Keystore инвалидирован и recovery ещё не выполнен.
     */
    val current: Flow<RootKey?>

    /**
     * Генерирует новый root key для данной identity (путь US-1: first Sign-In).
     * Обновляет [current] flow.
     *
     * @param identity Identity для которой генерируется ключ.
     */
    suspend fun create(identity: AuthIdentity): Outcome<RootKey, RootKeyError>

    /**
     * Восстанавливает root key через passphrase + blob из [RecoveryKeyBackup] (путь US-2: new device).
     * Обновляет [current] flow.
     *
     * @param identity Identity для которой выполняется recovery.
     * @param passphrase Passphrase с которого выводится wrapKey (читается один раз; обнуляется после).
     */
    suspend fun recover(identity: AuthIdentity, passphrase: CharArray): Outcome<RootKey, RootKeyError>

    /**
     * Удаляет root key и все производные ключи из памяти и локального Keystore (FR-019, SC-012).
     * Единица каскадного wipe: caller также должен вызвать [RecoveryKeyBackup.deleteBlob].
     *
     * После [current] flow эмитит `null`.
     *
     * @param identity Identity для которой выполняется forget.
     */
    suspend fun forget(identity: AuthIdentity): Outcome<Unit, RootKeyError>
}
