package com.launcher.app.di

import com.launcher.api.auth.AuthProvider
import com.launcher.app.data.identity.GoogleSignInIdentityProof
import family.crypto.api.AeadCipher
import family.crypto.api.PasswordHash
import family.crypto.api.RandomSource
import family.crypto.api.SecureKeyStore
import family.crypto.libsodium.LibsodiumArgon2idPasswordHash
import family.keys.api.IdentityProof
import family.keys.api.PassphraseAttemptCounter
import family.keys.impl.Argon2idPassphraseKdf
import family.keys.impl.RootKeyManagerImpl
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
 *  • [PassphraseAttemptCounter] → DataStorePassphraseAttemptCounter (T122f, Lane B).
 *  • [family.keys.api.PassphrasePrompter] → AndroidPassphrasePrompter (T074, Lane B).
 *
 * **Не bindings RecoveryFlow / KeyHierarchy** — они per-identity instance'ы,
 * создаются on-demand в ViewModel'ах после Sign-In.
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
}
