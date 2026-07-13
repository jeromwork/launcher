package com.launcher.app.di

import com.launcher.app.data.envelope.InMemoryEnvelopeStorage
import com.launcher.app.data.envelope.InMemoryPublicKeyDirectory
import com.launcher.app.data.recovery.NoOpRecoveryKeyBackup
import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.SecureKeyStore
import com.launcher.app.data.envelope.InMemoryAsyncConfigPushQueueImpl
import cryptokit.keys.android.AndroidDeviceIdentity
import cryptokit.keys.api.AsyncConfigPushQueue
import cryptokit.keys.api.ConfigSaver
import cryptokit.keys.api.EnvelopeBootstrap
import cryptokit.keys.api.IdentityProof
import cryptokit.keys.api.RecoveryKeyBackup
import cryptokit.keys.api.RemoteStorage
import cryptokit.keys.api.internal.DeviceIdentity
import cryptokit.keys.api.internal.EnvelopeStorage
import cryptokit.keys.api.internal.PublicKeyDirectory
import cryptokit.keys.api.internal.RecipientResolver
import cryptokit.keys.impl.DefaultEnvelopeBootstrap
import cryptokit.keys.impl.EnvelopeConfigCipherImpl
import cryptokit.keys.impl.EnvelopeRemoteStorage
import cryptokit.keys.impl.LocalFirstConfigSaver
import cryptokit.keys.impl.PublicKeyDirectoryRecipientResolver
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
