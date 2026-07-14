package com.launcher.app.di

import cryptokit.crypto.api.KeyStoreContext
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.keys.api.vault.KeyVault
import cryptokit.keys.impl.vault.AndroidKeyVault
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * TASK-112 KeyVault DI wiring.
 *
 * The Application-scoped [KeyVault] singleton is provided by [AndroidKeyVault], which persists
 * `root_key` via [SecureKeyStore] (Android Keystore wrap) and executes every crypto op through
 * the libsodium software layer.
 *
 * Additive to [f018KeysModule]: legacy TASK-6 recovery flow (`RootKeyManagerImpl` +
 * `RecoveryFlow`) keeps working for existing UI (`FirstLaunchActivity`, `RecoveryViewModel`).
 * New downstream features (messenger, album, config sync v2) MUST depend on [KeyVault] instead
 * of `KeyRegistry` — see TASK-112 spec.md § Requirements FR-001.
 *
 * `RecoveryStrategy` (e.g. `PassphraseRecovery(...)`) is constructed per-unlock event by the
 * caller — NOT a singleton — because it holds a `CharArray` passphrase for a short lifetime.
 */
val keyVaultModule = module {
    single<KeyVault> {
        AndroidKeyVault(
            context = androidContext(),
            secureKeyStore = get<SecureKeyStore>(),
            // aead / kdf / asym / validationStore fall back to their libsodium + Android
            // defaults inside AndroidKeyVault's constructor — override only for tests.
        )
    }
}
