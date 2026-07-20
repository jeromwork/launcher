package family.pairing.api

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Encrypted blob storage port. Adapter (Phase 5) — Firebase Storage по path
// /links/{linkId}/private-media/{uuid}. Размерный лимит 500 KB enforce'ится
// Storage Rules (контракт, не клиентом).
//
// Signatures use uniform `throws CryptoException` pattern (TASK-51 FR-009).
@OptIn(ExperimentalUuidApi::class)
interface EncryptedMediaStorage {
    /** @throws family.crypto.exception.CryptoException on upload / serialization failure. */
    suspend fun upload(linkId: String, uuid: Uuid, envelope: EncryptedEnvelope)

    /** @throws family.crypto.exception.CryptoException on download / deserialization failure. */
    suspend fun download(linkId: String, uuid: Uuid): EncryptedEnvelope

    /** @throws family.crypto.exception.CryptoException on delete failure. */
    suspend fun delete(linkId: String, uuid: Uuid)

    suspend fun exists(linkId: String, uuid: Uuid): Boolean

    suspend fun list(linkId: String): List<Uuid>
}
