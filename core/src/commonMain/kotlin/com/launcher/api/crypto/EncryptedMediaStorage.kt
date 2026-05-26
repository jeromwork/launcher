package com.launcher.api.crypto

import com.launcher.api.result.Outcome
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Encrypted blob storage port. Adapter (Phase 5) — Firebase Storage по path
// /links/{linkId}/private-media/{uuid}. Размерный лимит 500 KB enforce'ится
// Storage Rules (контракт, не клиентом).
@OptIn(ExperimentalUuidApi::class)
interface EncryptedMediaStorage {
    suspend fun upload(linkId: String, uuid: Uuid, envelope: EncryptedEnvelope): Outcome<Unit, CryptoError>

    suspend fun download(linkId: String, uuid: Uuid): Outcome<EncryptedEnvelope, CryptoError>

    suspend fun delete(linkId: String, uuid: Uuid): Outcome<Unit, CryptoError>

    suspend fun exists(linkId: String, uuid: Uuid): Boolean

    suspend fun list(linkId: String): List<Uuid>
}
