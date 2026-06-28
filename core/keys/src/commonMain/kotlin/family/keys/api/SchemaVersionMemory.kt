package family.keys.api

/**
 * Trust-On-Last-Use (TOLU) memory для schemaVersion (H-2 mitigation, FR-028b).
 *
 * Запоминает максимальную увиденную schemaVersion per (uid, blobKind). При чтении
 * нового blob'а сравниваем `fetched.schemaVersion >= lastSeenVersion`. Если меньше —
 * `SchemaDowngradeDetected` ([BackupError.SchemaDowngradeDetected] или
 * [CipherError.SchemaDowngradeDetected]).
 *
 * **Why TOLU**: client'ы пишут только monotonically increasing version. Attacker
 * (или corruption) может подсунуть старый blob (rollback attack). TOLU detect'ит
 * это сравнивая с предыдущим максимумом.
 *
 * **Storage**: per-identity persistent KV (Android DataStore в production).
 * Cleared при Sign-Out / Clear App Data — это accepted (re-trust on first read).
 *
 * **Keys**: `tolu_${uid}_${blobKind}` (Int).
 *
 * **blobKind values**: `"recoveryVault"`, `"configBlob"`.
 *
 * Реальный adapter живёт в app/, см. DataStoreSchemaVersionMemory.
 */
interface SchemaVersionMemory {
    /** Зафиксировать seen version. Always `max(stored, version)`. */
    suspend fun recordSeenVersion(uid: String, blobKind: String, version: Int)

    /** Последняя seen version или null если ничего ещё не записано. */
    suspend fun lastSeenVersion(uid: String, blobKind: String): Int?

    companion object {
        const val KIND_RECOVERY_VAULT: String = "recoveryVault"
        const val KIND_CONFIG_BLOB: String = "configBlob"
    }
}
