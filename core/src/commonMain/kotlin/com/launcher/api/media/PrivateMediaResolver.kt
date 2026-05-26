package com.launcher.api.media

import com.launcher.api.crypto.CryptoError
import com.launcher.api.result.Outcome

/**
 * Spec 012 facade — **entry point** для resolving `"private:<uuid>"` iconRef strings
 * to расшифрованные media bytes.
 *
 * Encapsulates pipeline:
 *  1. [LocalMediaStore.read] (instant если файл уже на disk).
 *  2. Если miss → [EncryptedMediaStorage.download] (Firebase Storage / Backblaze B2).
 *  3. Decrypt envelope через `AeadCipher` / `AsymmetricCrypto` (spec 011 ports).
 *  4. [LocalMediaStore.write] (cache decrypted bytes persistent).
 *  5. Return bytes for Bitmap.decode by caller.
 *
 * Каждый последующий [resolve] для того же uuid — мгновенный (step 1 hit).
 * Decrypt происходит **ровно один раз** на blob per device.
 *
 * **NOT extending IconStorage** (per plan-phase deviation): `IconStorage.resolve()` —
 * synchronous, fast (≤ 100 мс), runs during Composable render. `PrivateMediaResolver`
 * требует disk read + потенциально network download — это suspend, async. UI код
 * dispatches на оба: sync IconStorage для bundled/custom, async PrivateMediaResolver
 * для private: namespace.
 *
 * ⚠️ **DO NOT call AeadCipher / EncryptedMediaStorage / LocalMediaStore directly** из UI.
 * Использовать этот facade. Article XI §8 (Reuse before invention).
 *
 * Honest error reporting (FR-021):
 *  - На любую CryptoError → returns [PrivateMediaResolution.Failed] + caller эмитит
 *    `/state.partialApplyReasons += MediaDecryptFailed` + structured log event.
 *
 * Task: T1214 (Phase 2). FR-002, FR-021.
 */
interface PrivateMediaResolver {
    /**
     * @param privateIconRef полная строка `"private:<uuid>"` (iconId namespace spec 006).
     * @param linkId pair link для download'а из Storage (нужен для path /links/{linkId}/private-media/{uuid}).
     * @return Resolved bytes если success, либо Failed с категорией error для diagnostics.
     */
    suspend fun resolve(
        privateIconRef: String,
        linkId: String,
    ): PrivateMediaResolution
}

/**
 * Spec 012 — outcome of [PrivateMediaResolver.resolve].
 *
 * Separate sealed type от [com.launcher.api.capability.IconResolution] потому что:
 *  - IconResolution.Drawable(androidResourceId) не подходит — у нас bytes, не Android resource.
 *  - Failed variant нужен с категоризацией error'а для admin indicator (FR-022).
 *
 * Caller дальше:
 *  - [Bytes] → decode в Bitmap для Compose Image().
 *  - [Failed] → render placeholder с инициалом + эмит partialApplyReasons.
 *
 * Task: T1214. FR-002, FR-021, FR-022.
 */
sealed class PrivateMediaResolution {
    /** Successfully decrypted и lazy-readable bytes. */
    data class Bytes(val bytes: ByteArray, val kind: PrivateMediaKind?) : PrivateMediaResolution() {
        // ByteArray equals/hashCode требует ручной реализации.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (kind != other.kind) return false
            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (kind?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Resolution failed — caller MUST показать placeholder и эмитить
     * `PartialReason.MediaDecryptFailed` через `/state/current.partialApplyReasons` (FR-021).
     */
    data class Failed(val reason: FailureReason, val cause: CryptoError? = null) : PrivateMediaResolution()

    /** Categorical failure subcategory — for admin indicator hint and rate metrics (FR-022). */
    enum class FailureReason {
        /** Blob отсутствует в Storage (deleted? never uploaded?). Hint: re-add. */
        BlobMissing,

        /** Envelope MAC verification failed (corrupted bytes). Hint: re-add. */
        MacFailed,

        /** Recipient (own device) не в envelope.recipients list. Hint: re-pair. */
        RecipientNotFound,

        /** SecureKeystore key для расшифровки не найден (reset app? device migrate?). Hint: re-pair. */
        KeyNotFound,

        /** Network failure при download'е (transient). UI может re-try later. */
        NetworkError,

        /** Wrong iconRef format (not "private:<valid-uuid>"). UI bug — log loudly. */
        InvalidRef,
    }
}
