package com.launcher.app.di

import com.launcher.app.data.envelope.InMemoryEnvelopeStorage
import com.launcher.app.data.envelope.InMemoryPublicKeyDirectory
import com.launcher.app.data.recovery.NoOpRecoveryKeyBackup
import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.SecureKeyStore
import com.launcher.app.data.envelope.InMemoryAsyncConfigPushQueueImpl
import family.keys.android.AndroidDeviceIdentity
import family.keys.api.AsyncConfigPushQueue
import family.keys.api.ConfigSaver
import family.keys.api.EnvelopeBootstrap
import family.keys.api.IdentityProof
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RemoteStorage
import family.keys.api.internal.DeviceIdentity
import family.keys.api.internal.EnvelopeStorage
import family.keys.api.internal.PublicKeyDirectory
import family.keys.api.internal.RecipientResolver
import family.keys.impl.DefaultEnvelopeBootstrap
import family.keys.impl.EnvelopeConfigCipherImpl
import family.keys.impl.EnvelopeRemoteStorage
import family.keys.impl.LocalFirstConfigSaver
import family.keys.impl.PublicKeyDirectoryRecipientResolver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Spec 018 (F-5 + F-5b) backend wiring — **mockBackend flavor**.
 *
 * F-5 bindings:
 *  - [RecoveryKeyBackup] → [NoOpRecoveryKeyBackup] (no cloud recovery in mockBackend).
 *
 * F-5b envelope-storage bindings (Batch 4):
 *  - [DeviceIdentity] → [AndroidDeviceIdentity] (same as realBackend; uses Keystore,
 *    no Firebase dependency).
 *  - [EnvelopeStorage] → [InMemoryEnvelopeStorage] (process-local; lost on app kill).
 *  - [PublicKeyDirectory] → [InMemoryPublicKeyDirectory] (process-local; supports
 *    `seedGrant` dev hook for smoke flows).
 *  - [RecipientResolver] → [PublicKeyDirectoryRecipientResolver] (same composite
 *    as realBackend — only the backend swap differs).
 *  - [RemoteStorage] → [EnvelopeRemoteStorage] (facade).
 */
val f018KeysBackendModule = module {
    single<RecoveryKeyBackup> { NoOpRecoveryKeyBackup() }

    single<DeviceIdentity> {
        AndroidDeviceIdentity(
            context = androidContext(),
            secureKeyStore = get<SecureKeyStore>(),
            asymmetric = get<AsymmetricCrypto>()
        )
    }

    single<EnvelopeStorage> { InMemoryEnvelopeStorage() }
    single<PublicKeyDirectory> { InMemoryPublicKeyDirectory() }
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

    single<AsyncConfigPushQueue> {
        InMemoryAsyncConfigPushQueueImpl(storage = get<RemoteStorage>())
    }

    single<ConfigSaver> {
        LocalFirstConfigSaver(
            storage = get<RemoteStorage>(),
            identity = get<IdentityProof>(),
            pushQueue = get<AsyncConfigPushQueue>()
        )
    }

    single<EnvelopeBootstrap> {
        DefaultEnvelopeBootstrap(
            identity = get<IdentityProof>(),
            deviceIdentity = get<DeviceIdentity>(),
            directory = get<PublicKeyDirectory>()
        )
    }
}
