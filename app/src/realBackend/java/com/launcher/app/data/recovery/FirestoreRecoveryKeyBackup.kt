package com.launcher.app.data.recovery

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import family.keys.api.Outcome
import family.keys.api.PassphraseKdfParams
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RecoveryKeyBackupBlob
import family.keys.api.VaultError
import kotlinx.coroutines.tasks.await

/**
 * Firestore adapter для [RecoveryKeyBackup] (T045, FR-008, FR-009).
 *
 * **Firebase Firestore SDK импортируется ТОЛЬКО здесь** — per CLAUDE.md rule 2 (ACL).
 * Domain (`:core:keys`) и UI не знают о Firebase.
 *
 * **Path**: `users/{uid}/recovery-key/main`. Document'ы хранят [RecoveryKeyBackupBlob]
 * как **native Firestore typed fields** (bytes / int / map) — НЕ как JSON blob
 * в одном string поле. Это позволяет Firestore Rules валидировать shape (sizes,
 * types) per contracts/firestore-security-rules.md.
 *
 * **Field mapping**:
 *  • `schemaVersion: int` (FR-028a — H-2 monotonic increase invariant).
 *  • `algorithm: string` (≤ 64 chars).
 *  • `kdfSalt: bytes` (== 16 байт).
 *  • `kdfParams: map { memoryKiB: int, iterations: int, parallelism: int }`.
 *  • `wrappedRootKey: bytes` (≤ 1024 байт).
 *  • `nonce: bytes` (== 24 байт).
 *  • `createdAt: int` (ms epoch).
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

    override suspend fun fetchBlob(uid: String): Outcome<RecoveryKeyBackupBlob, VaultError> {
        if (uid.isEmpty()) return Outcome.Failure(VaultError.Unauthorized)
        return try {
            val snap = docRef(uid).get().await()
            if (!snap.exists()) return Outcome.Failure(VaultError.NotFound)
            val blob = decodeBlob(snap.data ?: emptyMap())
                ?: return Outcome.Failure(VaultError.Malformed)
            Outcome.Success(blob)
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    Outcome.Failure(VaultError.Unauthorized)
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    Outcome.Failure(VaultError.Network(e))
                else -> Outcome.Failure(VaultError.Network(e))
            }
        } catch (t: Throwable) {
            Outcome.Failure(VaultError.Malformed)
        }
    }

    override suspend fun uploadBlob(uid: String, blob: RecoveryKeyBackupBlob): Outcome<Unit, VaultError> {
        if (uid.isEmpty()) return Outcome.Failure(VaultError.Unauthorized)
        return try {
            docRef(uid).set(encodeBlob(blob)).await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Outcome.Failure(VaultError.Unauthorized)
            } else {
                Outcome.Failure(VaultError.Network(e))
            }
        } catch (t: Throwable) {
            Outcome.Failure(VaultError.Network(t))
        }
    }

    private fun encodeBlob(blob: RecoveryKeyBackupBlob): Map<String, Any> = mapOf(
        FIELD_SCHEMA_VERSION to blob.schemaVersion,
        FIELD_ALGORITHM to blob.algorithm,
        FIELD_KDF_SALT to Blob.fromBytes(blob.kdfSalt),
        FIELD_KDF_PARAMS to mapOf(
            FIELD_KDF_MEMORY to blob.kdfParams.memoryKib,
            FIELD_KDF_ITERATIONS to blob.kdfParams.iterations,
            FIELD_KDF_PARALLELISM to blob.kdfParams.parallelism
        ),
        FIELD_WRAPPED_ROOT_KEY to Blob.fromBytes(blob.wrappedRootKey),
        FIELD_NONCE to Blob.fromBytes(blob.nonce),
        FIELD_CREATED_AT to blob.createdAt
    )

    @Suppress("UNCHECKED_CAST")
    private fun decodeBlob(data: Map<String, Any>): RecoveryKeyBackupBlob? = try {
        val schemaVersion = (data[FIELD_SCHEMA_VERSION] as? Number)?.toInt() ?: return null
        val algorithm = data[FIELD_ALGORITHM] as? String ?: return null
        val kdfSalt = (data[FIELD_KDF_SALT] as? Blob)?.toBytes() ?: return null
        val kdfParamsMap = data[FIELD_KDF_PARAMS] as? Map<String, Any> ?: return null
        val kdfParams = PassphraseKdfParams(
            memoryKib = (kdfParamsMap[FIELD_KDF_MEMORY] as? Number)?.toInt() ?: return null,
            iterations = (kdfParamsMap[FIELD_KDF_ITERATIONS] as? Number)?.toInt() ?: return null,
            parallelism = (kdfParamsMap[FIELD_KDF_PARALLELISM] as? Number)?.toInt() ?: return null
        )
        val wrappedRootKey = (data[FIELD_WRAPPED_ROOT_KEY] as? Blob)?.toBytes() ?: return null
        val nonce = (data[FIELD_NONCE] as? Blob)?.toBytes() ?: return null
        val createdAt = (data[FIELD_CREATED_AT] as? Number)?.toLong() ?: return null
        RecoveryKeyBackupBlob(
            schemaVersion = schemaVersion,
            algorithm = algorithm,
            kdfSalt = kdfSalt,
            kdfParams = kdfParams,
            wrappedRootKey = wrappedRootKey,
            nonce = nonce,
            createdAt = createdAt
        )
    } catch (t: Throwable) {
        null
    }

    override suspend fun deleteBlob(uid: String): Outcome<Unit, VaultError> {
        if (uid.isEmpty()) return Outcome.Failure(VaultError.Unauthorized)
        return try {
            docRef(uid).delete().await()
            Outcome.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Outcome.Failure(VaultError.Unauthorized)
            } else {
                Outcome.Failure(VaultError.Network(e))
            }
        } catch (t: Throwable) {
            Outcome.Failure(VaultError.Network(t))
        }
    }

    companion object {
        const val COLLECTION_USERS: String = "users"
        const val COLLECTION_RECOVERY: String = "recovery-key"
        const val DOCUMENT_MAIN: String = "main"

        // Document field names — должны совпадать с firestore.rules
        // validate-функцией isValidRecoveryVaultBlob.
        const val FIELD_SCHEMA_VERSION: String = "schemaVersion"
        const val FIELD_ALGORITHM: String = "algorithm"
        const val FIELD_KDF_SALT: String = "kdfSalt"
        const val FIELD_KDF_PARAMS: String = "kdfParams"
        const val FIELD_KDF_MEMORY: String = "memoryKiB"
        const val FIELD_KDF_ITERATIONS: String = "iterations"
        const val FIELD_KDF_PARALLELISM: String = "parallelism"
        const val FIELD_WRAPPED_ROOT_KEY: String = "wrappedRootKey"
        const val FIELD_NONCE: String = "nonce"
        const val FIELD_CREATED_AT: String = "createdAt"
    }
}
