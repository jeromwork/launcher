package family.keys.api

/**
 * Стабильный provider-агностичный идентификатор пользователя (FR-001).
 *
 * **Инварианты**:
 *  - UUID v4 lowercase canonical form (`8-4-4-4-12` hex без фигурных скобок).
 *  - Никакого `googleSub` / `firebaseUid` / email / телефона внутри значения.
 *  - Создаётся однажды при первом Sign-In через `workers/identity/ POST /init-claim`
 *    → `firebase-admin.auth().setCustomUserClaims(uid, { stableId })`.
 *  - Живёт в Firebase Custom Claims и в Firestore `/identity-links/{uid}`.
 *    Worker и его логи видят только `stableId` — не Google UID и не email.
 *
 * **Почему type alias, а не value class**:
 *  - `StableId` — String в wire-формате (JSON); Kotlin value class over String добавляет
 *    overhead через boxing на JVM в generic-контекстах (Map<StableId, ...>).
 *  - Validation (UUID v4 format) выполняется на слое создания (workers/identity),
 *    не в каждом call-site. Domain code доверяет значению из Custom Claims.
 *
 * @see KeyRegistry
 * @see RecoveryKeyBackup
 */
typealias StableId = String
