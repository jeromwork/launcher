package com.launcher.app.di

import com.google.firebase.firestore.FirebaseFirestore
import com.launcher.app.data.envelope.FirestoreEnvelopeStorage
import com.launcher.app.data.envelope.FirestorePublicKeyDirectory
import com.launcher.app.data.envelope.PublicKeyDirectoryRecipientResolver
import com.launcher.app.data.recovery.FirestoreRecoveryKeyVault
import family.crypto.api.AeadCipher
import family.crypto.api.AsymmetricCrypto
import family.crypto.api.RandomSource
import family.crypto.api.SecureKeyStore
import family.keys.android.AndroidDeviceIdentity
import family.keys.api.RecoveryKeyVault
import family.keys.api.RemoteStorage
import family.keys.api.internal.DeviceIdentity
import family.keys.api.internal.EnvelopeStorage
import family.keys.api.internal.PublicKeyDirectory
import family.keys.api.internal.RecipientResolver
import family.keys.impl.EnvelopeConfigCipherImpl
import family.keys.impl.EnvelopeRemoteStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Spec 018 (F-5 + F-5b) backend wiring — **realBackend flavor**.
 *
 * F-5 bindings:
 *  - [RecoveryKeyVault] → [FirestoreRecoveryKeyVault]
 *
 * F-5b envelope-storage bindings (Batch 4):
 *  - [DeviceIdentity] → [AndroidDeviceIdentity]
 *  - [EnvelopeStorage] → [FirestoreEnvelopeStorage]
 *  - [PublicKeyDirectory] → [FirestorePublicKeyDirectory]
 *  - [RecipientResolver] → [PublicKeyDirectoryRecipientResolver]
 *  - [RemoteStorage] → [EnvelopeRemoteStorage] (facade wiring all above)
 *
 * Per CLAUDE.md rule 2 (ACL): Firebase SDK is imported **only** in
 * Firestore-prefixed adapters and this DI module. No Firestore type leaks
 * into domain or UI layers.
 */
val f018KeysBackendModule = module {
    single<RecoveryKeyVault> {
        FirestoreRecoveryKeyVault(firestore = FirebaseFirestore.getInstance())
    }

    single<DeviceIdentity> {
        AndroidDeviceIdentity(
            context = androidContext(),
            secureKeyStore = get<SecureKeyStore>(),
            asymmetric = get<AsymmetricCrypto>()
        )
    }

    single<EnvelopeStorage> {
        FirestoreEnvelopeStorage(firestore = FirebaseFirestore.getInstance())
    }

    single<PublicKeyDirectory> {
        FirestorePublicKeyDirectory(firestore = FirebaseFirestore.getInstance())
    }

    single<RecipientResolver> {
        PublicKeyDirectoryRecipientResolver(directory = get<PublicKeyDirectory>())
    }

    single<RemoteStorage> {
        EnvelopeRemoteStorage(
            cipher = EnvelopeConfigCipherImpl(
                aead = get<AeadCipher>(),
                asymmetric = get<AsymmetricCrypto>(),
                random = get<RandomSource>()
            ),
            resolver = get<RecipientResolver>(),
            storage = get<EnvelopeStorage>(),
            deviceIdentity = get<DeviceIdentity>()
        )
    }
}
