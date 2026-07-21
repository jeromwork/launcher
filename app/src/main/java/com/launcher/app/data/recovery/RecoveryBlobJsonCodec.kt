package com.launcher.app.data.recovery

import family.crypto.api.values.ByteArrayBase64Serializer
import family.keys.api.BackupError
import family.keys.api.KdfParams
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyBackupBlob
import family.wire.WireVersion
import family.wire.WireVersionHeader
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * JSON wire codec for the recovery-vault blob (contracts/recovery-key-backup-v1.md §3/§5).
 *
 * TASK-141 — the crypto type [RecoveryKeyBackupBlob] (:core:keys) carries neither a
 * version nor serialization annotations (rule 1 crypto exception). This adapter-side
 * DTO owns both: the version header and the reader gate. It is the standardized DTO-twin
 * form the owner chose for the one recovery format that is genuinely serialized via
 * kotlinx in production (WorkerRecoveryKeyBackup), versus the two other formats whose
 * adapters map by hand.
 *
 * Part D converts the version from the bare integer to the dotted three-field header
 * (`docs/architecture/wire-format.md` §3, invariant I1): `schemaVersion` / `minReaderVersion`
 * / `minWriterVersion`, each a [WireVersion] serialized as a string (`"1.0"`). The Worker
 * (`workers/backup/src/index.ts`) mirrors this gate and must agree on the string form.
 *
 * Reader gate: `minReaderVersion` is read from a generic JSON object BEFORE the full parse.
 * A document demanding a reader newer than this build → [BackupError.UnsupportedSchema] with
 * no partial parse (contracts §5). Missing / non-string / unparseable header → [BackupError.Malformed].
 */
object RecoveryBlobJsonCodec {

    /** Reader level of this build (`wire-format.md` §11 — one named constant per format). */
    val WIRE_SCHEMA_VERSION: WireVersion = WireVersion(1, 0)

    /** Minimum reader the documents we write demand. */
    val WIRE_MIN_READER_VERSION: WireVersion = WireVersion(1, 0)

    /** Minimum writer required to rewrite without losing meaning. */
    val WIRE_MIN_WRITER_VERSION: WireVersion = WireVersion(1, 0)

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
            val minReaderRaw = (root["minReaderVersion"] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content
                ?: return Outcome.Failure(BackupError.Malformed)
            val minReader = WireVersion.parseOrNull(minReaderRaw)
                ?: return Outcome.Failure(BackupError.Malformed)
            // Reader gate (§3): refuse a document that needs a newer reader than we implement,
            // rather than parse it partially. Report the demanded major for the UI's "update" hint.
            if (minReader > WIRE_SCHEMA_VERSION) {
                return Outcome.Failure(BackupError.UnsupportedSchema(minReader.major))
            }

            val dto = json.decodeFromString(RecoveryKeyBackupBlobDto.serializer(), jsonString)
            Outcome.Success(dto.toDomain())
        } catch (e: Exception) {
            Outcome.Failure(BackupError.Malformed)
        }
    }

    private fun RecoveryKeyBackupBlob.toDto() = RecoveryKeyBackupBlobDto(
        schemaVersion = WIRE_SCHEMA_VERSION,
        minReaderVersion = WIRE_MIN_READER_VERSION,
        minWriterVersion = WIRE_MIN_WRITER_VERSION,
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

/** Wire DTO — @Serializable + the version header live here, in the adapter, not on the crypto type (rule 1). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("RecoveryKeyBackupBlob")
internal data class RecoveryKeyBackupBlobDto(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val schemaVersion: WireVersion = RecoveryBlobJsonCodec.WIRE_SCHEMA_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minReaderVersion: WireVersion = RecoveryBlobJsonCodec.WIRE_MIN_READER_VERSION,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    override val minWriterVersion: WireVersion = RecoveryBlobJsonCodec.WIRE_MIN_WRITER_VERSION,
    val stableId: String,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val salt: ByteArray,
    val kdfParams: KdfParamsDto,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,
    val createdAt: Instant,
) : WireVersionHeader

@Serializable
@SerialName("KdfParams")
internal data class KdfParamsDto(
    val algorithm: String,
    val iterations: Int,
    val memoryKb: Int,
    val parallelism: Int,
)
