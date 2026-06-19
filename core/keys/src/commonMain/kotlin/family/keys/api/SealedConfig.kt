package family.keys.api

import family.keys.api.internal.ByteArrayBase64Serializer
import kotlinx.serialization.Serializable

/**
 * Wire-format для зашифрованного ConfigDocument (spec 018 FR-017).
 *
 * **Path в Firestore**: `users/{uid}/config` (spec 008-level path; F-5 заменяет
 * payload bytes на этот тип через JSON serialization).
 *
 * **Layout** (per CLAUDE.md rule 5 wire-format versioning):
 *  • `schemaVersion` — для backward-compat reads.
 *  • `algorithm` — имя/версия AEAD алгоритма; allows future migration без
 *    breaking schemaVersion.
 *  • `ciphertext` — зашифрованные plaintext bytes (без nonce; nonce отдельно).
 *  • `nonce` — 24-byte XChaCha20 nonce.
 *  • `aad` — AAD bytes (= `uid || schemaVersion`, FR-020 identity binding).
 *  • `recipientMasterSignature` — RESERVED для S-2 multi-recipient encryption,
 *    null в F-5 (FR-024 TODO).
 *
 * Сериализация — kotlinx-serialization JSON; ByteArray поля кодируются Base64
 * через [ByteArrayBase64Serializer] (consistent с :core:crypto wire formats).
 */
@Serializable
data class SealedConfig(
    val schemaVersion: Int = SCHEMA_VERSION,
    val algorithm: String = ALGORITHM_V1,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val ciphertext: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val nonce: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val aad: ByteArray,
    @Serializable(with = ByteArrayBase64Serializer::class)
    val recipientMasterSignature: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealedConfig) return false
        return schemaVersion == other.schemaVersion &&
            algorithm == other.algorithm &&
            ciphertext.contentEquals(other.ciphertext) &&
            nonce.contentEquals(other.nonce) &&
            aad.contentEquals(other.aad) &&
            (recipientMasterSignature?.contentEquals(other.recipientMasterSignature) ?: (other.recipientMasterSignature == null))
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + aad.contentHashCode()
        result = 31 * result + (recipientMasterSignature?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val ALGORITHM_V1: String = "xchacha20poly1305-v1"
    }
}
