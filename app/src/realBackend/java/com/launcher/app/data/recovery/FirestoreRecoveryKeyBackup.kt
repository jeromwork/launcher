package com.launcher.app.data.recovery

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import family.keys.api.Outcome
import family.keys.api.KdfParams
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.BackupError
import family.wire.WireVersion
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant

/**
 * Firestore adapter для [RecoveryKeyBackup] (T045, FR-008, FR-009).
 *
 * **Firebase Firestore SDK импортируется ТОЛЬКО здесь** — per CLAUDE.md rule 2 (ACL).
 * Domain (`:core:keys`) и UI не знают о Firebase.
 *
 * **Path**: `users/{uid}/recovery-key/main`. Document'ы хранят [RecoveryKeyBackupBlob]
 * как **native Firestore typed fields** (bytes / int / map / string) — НЕ как JSON blob
 * в одном string поле.
 *
 * **Field mapping**:
 *  • `schemaVersion: int` (FR-028a — H-2 monotonic increase invariant).
 *  • `stableId: string`.
 *  • `salt: bytes` (== 32 байта).
 *  • `kdfParams: map { algorithm: string, memoryKb: int, iterations: int, parallelism: int }`.
 *  • `ciphertext: bytes` (≥ 48 байт).
 *  • `nonce: bytes` (== 24 байта).
 *  • `createdAt: string` (ISO-8601).
 *
 * **Security**: Firestore Rules enforce `request.auth.uid == uid` + shape
 * validation + schema-version monotonic increase (T123). Этот adapter не
 * ре-проверяет на клиенте — server-side rules — source of truth.
 *
 * TODO(server-roadmap SRV-RECOVERY-001): когда переедем на свой сервер,
 * заменим этим OwnServerRecoveryKeyBackup. F-5 domain не трогается, только
 * этот файл и DI binding.
 */
class FirestoreRecoveryKeyBackup(
    private val firestore: FirebaseFirestore
) : RecoveryKeyBackup {

    private fun docRef(uid: String) =
        firestore.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_RECOVERY).document(DOCUMENT_MAIN)

    override suspend fun fetchBlob(uid: String): Outcome<RecoveryKeyBackupBlob, BackupError> {
        if (uid.isEmpty()) return Outcome.Failure(BackupError.AuthExpired)
        return try {
            val snap = docRef(uid).get().await()
            if (!snap.exists()) return Outcome.Failure(BackupError.NotFound)
            val blob = decodeBlob(snap.data ?: emptyMap())
                ?: return Outcome.Failure(BackupError.Malformed)
            Outcome.Success(blob)
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    Outcome.Failure(BackupError.AuthExpired)
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    Outcome.Failure(BackupError.NetworkUnavailable(e))
                else -> Outcome.Failure(BackupError.NetworkUnavailable(e))
            }
        } catch (t: Throwable) {
            Outcome.Failure(BackupError.Malformed)
        }
    }

    override suspend fun uploadBlob(uid: String, blob: RecoveryKeyBackupBlob): Outcome<Unit, BackupError> {
        if (uid.isEmpty()) return Outcome.Failure(BackupError.AuthExpired)
        return try {
            docRef(uid).set(encodeBlob(blob)).await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Outcome.Failure(BackupError.AuthExpired)
            } else {
                Outcome.Failure(BackupError.NetworkUnavailable(e))
            }
        } catch (t: Throwable) {
            Outcome.Failure(BackupError.NetworkUnavailable(t))
        }
    }

    private fun encodeBlob(blob: RecoveryKeyBackupBlob): Map<String, Any> = mapOf(
        FIELD_SCHEMA_VERSION to RecoveryBlobJsonCodec.WIRE_SCHEMA_VERSION.toString(),
        FIELD_MIN_READER_VERSION to RecoveryBlobJsonCodec.WIRE_MIN_READER_VERSION.toString(),
        FIELD_MIN_WRITER_VERSION to RecoveryBlobJsonCodec.WIRE_MIN_WRITER_VERSION.toString(),
        FIELD_STABLE_ID to blob.stableId,
        FIELD_SALT to Blob.fromBytes(blob.salt),
        FIELD_KDF_PARAMS to mapOf(
            FIELD_KDF_ALGORITHM to blob.kdfParams.algorithm,
            FIELD_KDF_MEMORY to blob.kdfParams.memoryKb,
            FIELD_KDF_ITERATIONS to blob.kdfParams.iterations,
            FIELD_KDF_PARALLELISM to blob.kdfParams.parallelism
        ),
        FIELD_CIPHERTEXT to Blob.fromBytes(blob.ciphertext),
        FIELD_NONCE to Blob.fromBytes(blob.nonce),
        FIELD_CREATED_AT to blob.createdAt.toString()
    )

    @Suppress("UNCHECKED_CAST")
    private fun decodeBlob(data: Map<String, Any>): RecoveryKeyBackupBlob? {
        return try {
            // Version header (§3): schemaVersion is diagnostics; the reader gate is minReaderVersion.
            // Reader gate lives here (moved out of the crypto type per TASK-141). A pre-conversion
            // integer parses to null → the document is refused rather than read on a guess.
            (data[FIELD_SCHEMA_VERSION] as? String)?.let { WireVersion.parseOrNull(it) } ?: return null
            val minReader = (data[FIELD_MIN_READER_VERSION] as? String)?.let { WireVersion.parseOrNull(it) }
                ?: return null
            if (minReader > RecoveryBlobJsonCodec.WIRE_SCHEMA_VERSION) return null
            val stableId = data[FIELD_STABLE_ID] as? String ?: return null
            val salt = (data[FIELD_SALT] as? Blob)?.toBytes() ?: return null
            val kdfParamsMap = data[FIELD_KDF_PARAMS] as? Map<String, Any> ?: return null
            val kdfParams = KdfParams(
                algorithm = kdfParamsMap[FIELD_KDF_ALGORITHM] as? String ?: return null,
                memoryKb = (kdfParamsMap[FIELD_KDF_MEMORY] as? Number)?.toInt() ?: return null,
                iterations = (kdfParamsMap[FIELD_KDF_ITERATIONS] as? Number)?.toInt() ?: return null,
                parallelism = (kdfParamsMap[FIELD_KDF_PARALLELISM] as? Number)?.toInt() ?: return null
            )
            val ciphertext = (data[FIELD_CIPHERTEXT] as? Blob)?.toBytes() ?: return null
            val nonce = (data[FIELD_NONCE] as? Blob)?.toBytes() ?: return null
            val createdAtStr = data[FIELD_CREATED_AT] as? String ?: return null
            val createdAt = Instant.parse(createdAtStr)
            RecoveryKeyBackupBlob(
                stableId = stableId,
                salt = salt,
                kdfParams = kdfParams,
                ciphertext = ciphertext,
                nonce = nonce,
                createdAt = createdAt
            )
        } catch (t: Throwable) {
            null
        }
    }

    override suspend fun deleteBlob(uid: String): Outcome<Unit, BackupError> {
        if (uid.isEmpty()) return Outcome.Failure(BackupError.AuthExpired)
        return try {
            docRef(uid).delete().await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Outcome.Failure(BackupError.AuthExpired)
            } else {
                Outcome.Failure(BackupError.NetworkUnavailable(e))
            }
        } catch (t: Throwable) {
            Outcome.Failure(BackupError.NetworkUnavailable(t))
        }
    }

    companion object {
        const val COLLECTION_USERS: String = "users"
        const val COLLECTION_RECOVERY: String = "recovery-key"
        const val DOCUMENT_MAIN: String = "main"

        // Document field names — note for Phase 4: firestore.rules rule isValidRecoveryVaultBlob
        // will need updating to match these new field names per a1-resolution.md.
        const val FIELD_SCHEMA_VERSION: String = "schemaVersion"
        const val FIELD_MIN_READER_VERSION: String = "minReaderVersion"
        const val FIELD_MIN_WRITER_VERSION: String = "minWriterVersion"
        const val FIELD_STABLE_ID: String = "stableId"
        const val FIELD_SALT: String = "salt"
        const val FIELD_KDF_PARAMS: String = "kdfParams"
        const val FIELD_KDF_ALGORITHM: String = "algorithm"
        const val FIELD_KDF_MEMORY: String = "memoryKb"
        const val FIELD_KDF_ITERATIONS: String = "iterations"
        const val FIELD_KDF_PARALLELISM: String = "parallelism"
        const val FIELD_CIPHERTEXT: String = "ciphertext"
        const val FIELD_NONCE: String = "nonce"
        const val FIELD_CREATED_AT: String = "createdAt"
    }
}
