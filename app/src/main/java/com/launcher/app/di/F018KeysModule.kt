package com.launcher.app.di

import com.launcher.api.auth.AuthProvider
import com.launcher.app.data.identity.GoogleSignInIdentityProof
import com.launcher.app.data.recovery.DataStorePassphraseAttemptCounter
import com.launcher.app.data.recovery.DataStoreSchemaVersionMemory
import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.PasswordHash
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.libsodium.LibsodiumArgon2idPasswordHash
import family.keys.api.IdentityProof
import family.keys.api.PassphraseAttemptCounter
import family.keys.api.SchemaVersionMemory
import family.keys.impl.Argon2idPassphraseKdf
import family.keys.impl.RootKeyManagerImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Spec 018 (F-5) Koin wiring (T033, T076).
 *
 * Binds:
 *  • [IdentityProof] → [GoogleSignInIdentityProof] (bridges F-4 AuthProvider).
 *  • [PasswordHash] → [LibsodiumArgon2idPasswordHash] (F-CRYPTO Argon2id real).
 *  • [Argon2idPassphraseKdf] singleton.
 *  • [RootKeyManagerImpl] singleton (потребляет F-CRYPTO ports).
 *
 * **Flavor-specific bindings** (см. f018KeysBackendModule):
 *  • [family.keys.api.RecoveryKeyVault] → FirestoreRecoveryKeyVault (realBackend)
 *    или NoOpRecoveryKeyVault (mockBackend / non-GMS fallback).
 *  • [family.keys.api.RemoteStorage] / [family.keys.api.ConfigSaver] /
 *    [family.keys.api.EnvelopeBootstrap] (F-5b envelope surface).
 *  • [PassphraseAttemptCounter] → DataStorePassphraseAttemptCounter.
 *  • [family.keys.api.PassphrasePrompter] → AndroidPassphrasePrompter.
 *
 * RecoveryFlow создаётся on-demand в ViewModel'ах после Sign-In; root key
 * восстанавливается через passphrase, а envelope-decryption ключи (per-device
 * X25519 keypair) хранятся в Keystore — recovery'у не подлежат.
 */
val f018KeysModule = module {
    single<IdentityProof> { GoogleSignInIdentityProof(authProvider = get<AuthProvider>()) }

    single<PasswordHash> { LibsodiumArgon2idPasswordHash() }
    single { Argon2idPassphraseKdf(passwordHash = get()) }

    single {
        RootKeyManagerImpl(
            secureKeyStore = get<SecureKeyStore>(),
            random = get<RandomSource>(),
            aead = get<AeadCipher>()
        )
    }

    // H-1 / H-2 mitigations (T122f, T122m).
    single<PassphraseAttemptCounter> { DataStorePassphraseAttemptCounter(context = androidContext()) }
    single<SchemaVersionMemory> { DataStoreSchemaVersionMemory(context = androidContext()) }

    // F-5b envelope caller surface (ConfigSaver, EnvelopeBootstrap) лежит в
    // f018KeysBackendModule — потому что эти binding'и используют internal
    // family.keys.api.internal ports (DeviceIdentity, PublicKeyDirectory),
    // которые fitness rule запрещает импортировать из app/src/main.
}
