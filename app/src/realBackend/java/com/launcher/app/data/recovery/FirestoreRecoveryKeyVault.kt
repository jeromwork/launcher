package com.launcher.app.data.recovery

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import family.keys.api.Outcome
import family.keys.api.RecoveryKeyVault
import family.keys.api.RecoveryVaultBlob
import family.keys.api.VaultError
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Firestore adapter для [RecoveryKeyVault] (T045, FR-008, FR-009).
 *
 * **Firebase Firestore SDK импортируется ТОЛЬКО здесь** — per CLAUDE.md rule 2 (ACL).
 * Domain (`:core:keys`) и UI не знают о Firebase.
 *
 * **Path**: `users/{uid}/recovery-key/main`. Document'ы хранят сериализованный
 * [RecoveryVaultBlob] в виде flat map (Firestore не любит nested ByteArray;
 * Base64 string fields per JSON serializer).
 *
 * **Security**: Firestore Rules enforce `request.auth.uid == uid` (T123). Этот
 * adapter не ре-проверяет на клиенте — server-side rules — source of truth.
 *
 * TODO(server-roadmap SRV-RECOVERY-001): когда переедем на свой сервер,
 * заменим этим OwnServerRecoveryKeyVault. F-5 domain не трогается, только
 * этот файл и DI binding.
 */
class FirestoreRecoveryKeyVault(
    private val firestore: FirebaseFirestore,
    private val json: Json = Json
) : RecoveryKeyVault {

    private fun docRef(uid: String) =
        firestore.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_RECOVERY).document(DOCUMENT_MAIN)

    override suspend fun fetchVault(uid: String): Outcome<RecoveryVaultBlob, VaultError> {
        if (uid.isEmpty()) return Outcome.Failure(VaultError.Unauthorized)
        return try {
            val snap = docRef(uid).get().await()
            if (!snap.exists()) return Outcome.Failure(VaultError.NotFound)
            val payload = snap.getString(FIELD_PAYLOAD)
                ?: return Outcome.Failure(VaultError.Malformed)
            val blob = json.decodeFromString<RecoveryVaultBlob>(payload)
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

    override suspend fun storeVault(uid: String, blob: RecoveryVaultBlob): Outcome<Unit, VaultError> {
        if (uid.isEmpty()) return Outcome.Failure(VaultError.Unauthorized)
        return try {
            val payload = json.encodeToString(RecoveryVaultBlob.serializer(), blob)
            val data = mapOf(
                FIELD_PAYLOAD to payload,
                FIELD_SCHEMA_VERSION to blob.schemaVersion,
                FIELD_UPDATED_AT to com.google.firebase.Timestamp.now()
            )
            docRef(uid).set(data).await()
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

    override suspend fun deleteVault(uid: String): Outcome<Unit, VaultError> {
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
        const val FIELD_PAYLOAD: String = "payload"
        const val FIELD_SCHEMA_VERSION: String = "schemaVersion"
        const val FIELD_UPDATED_AT: String = "updatedAt"
    }
}
