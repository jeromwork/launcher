package com.launcher.app.data.recovery

import family.crypto.api.values.ByteArrayBase64Serializer
import family.keys.api.BackupError
import family.keys.api.KdfParams
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackupBlob
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * JSON wire codec for the recovery-vault blob (contracts/recovery-key-backup-v1.md §3/§5).
 *
 * TASK-141 — the crypto type [RecoveryKeyBackupBlob] (:core:keys) carries neither a
 * version nor serialization annotations (rule 1 crypto exception). This adapter-side
 * DTO owns both: the `schemaVersion` field and the reader gate. It is the standardized
 * DTO-twin form the owner chose for the one recovery format that is genuinely
 * serialized via kotlinx in production (WorkerRecoveryKeyBackup), versus the two other
 * formats whose adapters map by hand.
 *
 * The JSON shape is byte-compatible with the pre-TASK-141 `@Serializable
 * RecoveryKeyBackupBlob` (same field names, same base64 encoding, same nested
 * kdfParams object), so existing vault documents still decode.
 *
 * Reader gate: `schemaVersion` is read from a generic JSON object BEFORE the full
 * parse. A version above what we know → [BackupError.UnsupportedSchema] with no
 * partial parse (contracts §5). Missing / non-int / < 1 → [BackupError.Malformed].
 */
object RecoveryBlobJsonCodec {

    /** Reader level of this build. Bump when a breaking schema change ships. */
    const val WIRE_SCHEMA_VERSION: Int = 1

    private val json = Json {
        ignoreUnknownKeys = true // additive fields допустимы без bump
        encodeDefaults = true
    }

    fun encode(blob: RecoveryKeyBackupBlob): String =
        json.encodeToString(RecoveryKeyBackupBlobDto.serializer(), blob.toDto())

    fun decode(jsonString: String): Outcome<RecoveryKeyBackupBlob, BackupError> {
        return try {
            val root = json.parseToJsonElement(jsonString)
            if (root !is JsonObject) return Outcome.Failure(BackupError.Malformed)
            val versionElement = root["schemaVersion"] ?: return Outcome.Failure(BackupError.Malformed)
            val version = try {
                versionElement.jsonPrimitive.int
            } catch (e: Exception) {
                return Outcome.Failure(BackupError.Malformed)
            }
            if (version > WIRE_SCHEMA_VERSION) return Outcome.Failure(BackupError.UnsupportedSchema(version))
            if (version < 1) return Outcome.Failure(BackupError.Malformed)

            val dto = json.decodeFromString(RecoveryKeyBackupBlobDto.serializer(), jsonString)
            Outcome.Success(dto.toDomain())
        } catch (e: Exception) {
            Outcome.Failure(BackupError.Malformed)
        }
    }

    private fun RecoveryKeyBackupBlob.toDto() = RecoveryKeyBackupBlobDto(
        schemaVersion = WIRE_SCHEMA_VERSION,
        stableId = stableId,
        salt = salt,
        kdfParams = KdfParamsDto(
            algorithm = kdfParams.algorithm,
            iterations = kdfParams.iterations,
            memoryKb = kdfParams.memoryKb,
            parallelism = kdfParams.parallelism,
        ),
        ciphertext = ciphertext,
        nonce = nonce,
        createdAt = createdAt,
    )

    private fun RecoveryKeyBackupBlobDto.toDomain() = RecoveryKeyBackupBlob(
        stableId = stableId,
        salt = salt,
        kdfParams = KdfParams(
            algorithm = kdfParams.algorithm,
            iterations = kdfParams.iterations,
            memoryKb = kdfParams.memoryKb,
            parallelism = kdfParams.parallelism,
        ),
        ciphertext = ciphertext,
        nonce = nonce,
        createdAt = createdAt,
    )
}

/** Wire DTO — @Serializable lives here, in the adapter, not on the crypto type (rule 1). */
@Serializable
@SerialName("RecoveryKeyBackupBlob")
internal data class RecoveryKeyBackupBlobDto(
    val schemaVersion: Int = RecoveryBlobJsonCodec.WIRE_SCHEMA_VERSION,
    val stableId: String,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val salt: ByteArray,
    val kdfParams: KdfParamsDto,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,
    val createdAt: Instant,
)

@Serializable
@SerialName("KdfParams")
internal data class KdfParamsDto(
    val algorithm: String,
    val iterations: Int,
    val memoryKb: Int,
    val parallelism: Int,
)
