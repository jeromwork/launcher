package family.keys.impl

import family.keys.api.BackupError
import family.keys.api.KdfParams
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.StableId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * JSON encode/decode для [RecoveryKeyBackupBlob] с явной schemaVersion-проверкой (FR-006,
 * contracts/recovery-key-backup-v1.md §5 Versioning policy).
 *
 * **Безопасность**:
 *  - `schemaVersion` читается ДО полной десериализации. Если версия > [MAX_SUPPORTED_SCHEMA_VERSION] —
 *    немедленно возвращается [BackupError.UnsupportedSchema], без частичного парсинга.
 *  - Неизвестный `schemaVersion` (не Int, отсутствует) → [BackupError.Malformed].
 *  - Этот инвариант закрывает contracts §5 «No partial parse of unknown schemaVersion».
 *
 * **Почему не @Serializable напрямую**:
 *  - `@Serializable` на data class не позволяет отклонить blob ДО парсинга всех полей.
 *  - RecoveryBlobCodec даёт явный контроль над порядком чтения полей.
 */
object RecoveryBlobCodec {

    private val json = Json {
        ignoreUnknownKeys = true  // additive fields допустимы без schemaVersion bump
        encodeDefaults = true
    }

    private const val MAX_SUPPORTED_SCHEMA_VERSION: Int = RecoveryKeyBackupBlob.SCHEMA_VERSION_V1

    /**
     * Сериализует [blob] в JSON-строку.
     * Гарантирует наличие поля `schemaVersion` в выводе.
     *
     * @return JSON строка.
     */
    fun encode(blob: RecoveryKeyBackupBlob): String = json.encodeToString(RecoveryKeyBackupBlob.serializer(), blob)

    /**
     * Десериализует [jsonString] в [RecoveryKeyBackupBlob].
     *
     * **Forward-compat policy** (contracts/recovery-key-backup-v1.md §5):
     *  - schemaVersion == 1 → success
     *  - schemaVersion > 1 → [BackupError.UnsupportedSchema]
     *  - отсутствует / не Int → [BackupError.Malformed]
     *  - парсинг провален → [BackupError.Malformed]
     *
     * @param jsonString JSON строка из [RecoveryKeyBackup.fetchBlob].
     * @return [Outcome.Success] с blob или [Outcome.Failure] с [BackupError].
     */
    fun decode(jsonString: String): Outcome<RecoveryKeyBackupBlob, BackupError> {
        return try {
            // Step 1: parse as generic JSON object to read schemaVersion FIRST.
            val jsonElement = json.parseToJsonElement(jsonString)
            if (jsonElement !is JsonObject) {
                return Outcome.Failure(BackupError.Malformed)
            }
            val versionElement = jsonElement["schemaVersion"]
                ?: return Outcome.Failure(BackupError.Malformed)

            val version = try {
                versionElement.jsonPrimitive.int
            } catch (e: Exception) {
                return Outcome.Failure(BackupError.Malformed)
            }

            // Step 2: check version before full parse.
            if (version > MAX_SUPPORTED_SCHEMA_VERSION) {
                return Outcome.Failure(BackupError.UnsupportedSchema(version))
            }
            if (version < 1) {
                return Outcome.Failure(BackupError.Malformed)
            }

            // Step 3: full parse — only if version is supported.
            val blob = json.decodeFromString(RecoveryKeyBackupBlob.serializer(), jsonString)
            Outcome.Success(blob)
        } catch (e: Exception) {
            Outcome.Failure(BackupError.Malformed)
        }
    }
}
