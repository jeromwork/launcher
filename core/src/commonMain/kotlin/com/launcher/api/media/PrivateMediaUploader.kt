package com.launcher.api.media

import com.launcher.api.crypto.CryptoError
import com.launcher.api.result.Outcome

/**
 * Spec 012 facade — **entry point** для media operations из UI / business logic.
 *
 * Encapsulates pipeline:
 *  1. Encrypt bytes через `AeadCipher` (spec 011).
 *  2. Seal CEK для recipients через `AsymmetricCrypto` (spec 011).
 *  3. Upload envelope в `EncryptedMediaStorage` (spec 011).
 *  4. Increment `BlobReferenceLedger.refCount` (spec 011).
 *  5. Return iconId с namespace `private:<uuid>` (per spec 006 icon-id-namespace).
 *
 * ⚠️ **DO NOT call AeadCipher / AsymmetricCrypto / EncryptedMediaStorage / BlobReferenceLedger
 * directly** из UI / business logic. Используй этот facade.
 * Article XI §8 (Reuse before invention). См. docs/dev/private-media-architecture.md.
 *
 * Privacy invariant (FR-006, SC-008): sensitive label (е.g. "Паспорт") **MUST** идти
 * как параметр [labelInsideCiphertext] — фасад зашифрует его внутри ciphertext.
 * label НИКОГДА не должен попадать в envelope.metadata (она plaintext).
 *
 * Task: T1213 (Phase 2). FR-001, FR-006.
 */
interface PrivateMediaUploader {
    /**
     * Upload one media blob.
     *
     * @param bytes raw plaintext (после admin-side JPEG compression до ≤ 500 KB).
     * @param kind payload type (Image | Document); записывается в envelope.metadata.kind
     *             (plaintext, non-sensitive).
     * @param linkId target pair (admin ↔ Managed link).
     * @param labelInsideCiphertext optional sensitive label (e.g. "Паспорт"). Если non-null,
     *                              facade шифрует bytes+label вместе в один ciphertext.
     *                              Caller извлекает label обратно при decrypt в `PrivateMediaResolver`.
     * @param refSource identifier для `BlobReferenceLedger` — что ссылается на этот blob.
     *                  Format: "config:contact:<contactId>" или "config:slot:<slotId>".
     * @return Outcome.Success("private:<uuid>") если upload completed, иначе Failure(CryptoError).
     */
    suspend fun upload(
        bytes: ByteArray,
        kind: PrivateMediaKind,
        linkId: String,
        labelInsideCiphertext: String? = null,
        refSource: String,
    ): Outcome<String, CryptoError>
}
