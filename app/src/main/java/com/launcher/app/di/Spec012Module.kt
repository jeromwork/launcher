package com.launcher.app.di

import com.launcher.adapters.crypto.SqlDelightBlobReferenceLedger
import com.launcher.adapters.media.BlobReferenceRemover
import com.launcher.adapters.media.BlobReferenceWriter
import com.launcher.adapters.media.FileLocalMediaStore
import com.launcher.adapters.media.JpegCompressor
import com.launcher.adapters.media.MediaDecryptFailedReporter
import com.launcher.adapters.media.NoOpMediaDecryptFailedReporter
import com.launcher.adapters.media.PrivateMediaResolverImpl
import com.launcher.adapters.media.PrivateMediaUploaderImpl
import com.launcher.adapters.media.SqlDelightBlobReferenceRemover
import com.launcher.adapters.media.SqlDelightBlobReferenceWriter
import com.launcher.adapters.media.SystemPhotoPickerAdapter
import com.launcher.api.crypto.AeadCipher
import com.launcher.api.crypto.AsymmetricCrypto
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.crypto.RecipientResolver
import com.launcher.api.media.LocalMediaStore
import com.launcher.api.media.MediaPicker
import com.launcher.api.media.PrivateMediaResolver
import com.launcher.api.media.PrivateMediaUploader
import kotlin.uuid.ExperimentalUuidApi
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Spec 012 — Koin DI module для private-media stack.
 *
 *  Layer 1 facades (PrivateMediaUploader / PrivateMediaResolver) wired как singletons.
 *  Layer 1 LocalMediaStore — FileLocalMediaStore (real Android adapter); fake variant
 *    лежит в test code (FakeLocalMediaStore в commonTest).
 *  Layer 2 MediaPicker — SystemPhotoPickerAdapter (real); fake — FakeMediaPicker.
 *  BlobReferenceWriter — thin adapter over existing 011 SqlDelightBlobReferenceLedger.
 *
 * Task: T1233 (Phase 4).
 */
@OptIn(ExperimentalUuidApi::class)
val spec012Module = module {

    // ── Layer 1 ─────────────────────────────────────────────────────────────

    single<LocalMediaStore> { FileLocalMediaStore(appContext = androidContext()) }

    single<BlobReferenceWriter> {
        SqlDelightBlobReferenceWriter(ledger = get<SqlDelightBlobReferenceLedger>())
    }

    single<PrivateMediaUploader> {
        PrivateMediaUploaderImpl(
            aeadCipher = get<AeadCipher>(),
            asymmetricCrypto = get<AsymmetricCrypto>(),
            storage = get<EncryptedMediaStorage>(),
            recipientResolver = get<RecipientResolver>(),
            ledger = get<BlobReferenceWriter>(),
        )
    }

    single<PrivateMediaResolver> {
        PrivateMediaResolverImpl(
            aeadCipher = get<AeadCipher>(),
            asymmetricCrypto = get<AsymmetricCrypto>(),
            storage = get<EncryptedMediaStorage>(),
            localStore = get<LocalMediaStore>(),
            ownDeviceId = {
                // PairingCryptoCoordinator owns identity lifecycle; we read deviceId from it.
                // TODO(spec-012-wiring): wire actual deviceId provider here when integrating
                //   first real consumer (Phase 5 admin upload).
                error("ownDeviceId not wired — supply через PairingCryptoCoordinator's identity")
            },
            ownKeyPair = {
                error("ownKeyPair not wired — load via SecureKeystore.loadEncryption(alias)")
            },
        )
    }

    // ── Layer 2 ─────────────────────────────────────────────────────────────

    single<MediaPicker> { SystemPhotoPickerAdapter(context = androidContext()) }

    // ── Phase 7 — cleanup + reporting + compression ────────────────────────

    /** RefCount decrement on Contact / Document slot delete (FR-013, FR-023). */
    single<BlobReferenceRemover> {
        SqlDelightBlobReferenceRemover(ledger = get<SqlDelightBlobReferenceLedger>())
    }

    /**
     * Admin-side JPEG compression до 500 KB cap. Used by AddDocumentScreen
     * VCard receiver flow перед PrivateMediaUploader.upload().
     */
    single { JpegCompressor() }

    /**
     * MediaDecryptFailed emission в /state.partialApplyReasons (FR-021, SC-010).
     * TODO(spec-012-wiring): replace NoOpMediaDecryptFailedReporter с real impl
     *   которое hooks в StateApplied writer (spec 008 contracts/state-applied.md).
     */
    single<MediaDecryptFailedReporter> { NoOpMediaDecryptFailedReporter() }
}
