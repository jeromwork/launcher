package family.pairing.api

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Encrypted blob storage port. Adapter proxies через Cloudflare Worker к
// Backblaze B2 по path /links/{linkId}/private-media/{uuid}.
//
// Scope note (TASK-141): the upload/download/exists surface was never wired into
// a production path — only a debug smoke screen constructed EncryptedEnvelope and
// called upload/download. Those methods (and the EncryptedEnvelope wire format)
// were removed. The live consumer is FirestoreLinkRegistry.revoke() (spec 011
// FR-043 blob cleanup on unlink), which needs only list + delete.
//
// Signatures use uniform `throws CryptoException` pattern (TASK-51 FR-009).
@OptIn(ExperimentalUuidApi::class)
interface EncryptedMediaStorage {
    /** @throws family.crypto.exception.CryptoException on delete failure. */
    suspend fun delete(linkId: String, uuid: Uuid)

    suspend fun list(linkId: String): List<Uuid>
}
