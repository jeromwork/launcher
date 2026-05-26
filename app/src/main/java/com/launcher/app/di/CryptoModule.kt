package com.launcher.app.di

import com.launcher.adapters.config.AndroidSqlDriverProvider
import com.launcher.adapters.crypto.AndroidKeystoreSecureKeystore
import com.launcher.adapters.crypto.LibsodiumAeadCipher
import com.launcher.adapters.crypto.LibsodiumAsymmetricCrypto
import com.launcher.adapters.crypto.LibsodiumDigitalSignature
import com.launcher.adapters.crypto.LibsodiumHashFunction
import com.launcher.adapters.crypto.PairRecipientResolver
import com.launcher.adapters.crypto.SqlDelightBlobReferenceLedger
import com.launcher.adapters.crypto.ClearDataDetector
import com.launcher.adapters.crypto.BackgroundReconciler
import com.launcher.adapters.crypto.PairingCryptoCoordinator
import com.launcher.adapters.crypto.db.CryptoStore
import com.launcher.api.crypto.AeadCipher
import com.launcher.api.crypto.AsymmetricCrypto
import com.launcher.api.crypto.DeviceIdentityRepository
import com.launcher.api.crypto.DigitalSignature
import com.launcher.api.crypto.EncryptedMediaStorage
import com.launcher.api.crypto.HashFunction
import com.launcher.api.crypto.RecipientResolver
import com.launcher.api.crypto.SecureKeystore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

// Spec 011 crypto Koin wiring (T061 DI glue).
//
// Все port-bindings регистрируются process-wide singletons — порты stateless
// (libsodium provider singleton — see LibsodiumProvider).
//
// Flavor-specific bindings (Firestore DeviceIdentityRepository + Firebase
// Storage) живут в backendModule в :core/androidRealBackend / androidMockBackend
// — здесь только flavor-agnostic adapters + coordinator.
val cryptoModule = module {

    // ── Libsodium-backed port adapters (vendor types confined here) ────────

    single<AeadCipher> { LibsodiumAeadCipher() }
    single<AsymmetricCrypto> { LibsodiumAsymmetricCrypto() }
    single<DigitalSignature> { LibsodiumDigitalSignature() }
    single<HashFunction> { LibsodiumHashFunction() }

    // ── Android Keystore SecureKeystore ───────────────────────────────────

    single<SecureKeystore> { AndroidKeystoreSecureKeystore(androidContext()) }

    // ── SQLDelight CryptoStore + cleanup machinery ────────────────────────

    single<CryptoStore> { AndroidSqlDriverProvider.createCryptoStore(androidContext()) }

    single { SqlDelightBlobReferenceLedger(db = get()) }
    single { ClearDataDetector(db = get()) }

    // Reconciler wraps Storage + ledger + clear-data sentinel.
    single {
        BackgroundReconciler(
            storage = get<EncryptedMediaStorage>(),
            ledger = get(),
            clearData = get(),
        )
    }

    // ── PairRecipientResolver — depends on real DeviceIdentityRepository ──

    single<RecipientResolver> {
        PairRecipientResolver(
            repo = get<DeviceIdentityRepository>(),
            ownDeviceId = { error("ownDeviceId not wired — supply через PairingCryptoCoordinator") },
        )
    }

    // ── Coordinator — keygen on first launch + publishOwn after consent ──

    single {
        PairingCryptoCoordinator(
            keystore = get<SecureKeystore>(),
            signature = get<DigitalSignature>(),
            repo = get<DeviceIdentityRepository>(),
            deviceIdProvider = get(),
        )
    }
}
