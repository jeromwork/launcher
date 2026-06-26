package com.launcher.app.di

import com.launcher.adapters.config.AndroidSqlDriverProvider
import com.launcher.adapters.crypto.BackgroundReconciler
import com.launcher.adapters.crypto.ClearDataDetector
import com.launcher.adapters.crypto.PairRecipientResolver
import com.launcher.adapters.crypto.PairingCryptoCoordinator
import com.launcher.adapters.crypto.SqlDelightBlobReferenceLedger
import com.launcher.adapters.crypto.db.CryptoStore
import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.AsymmetricCrypto
import cryptokit.crypto.api.KeyDerivation
import cryptokit.crypto.api.KeyEscrow
import cryptokit.crypto.api.KeyRotation
import cryptokit.crypto.api.KeyStoreContext
import cryptokit.crypto.api.RandomSource
import cryptokit.crypto.api.SecureKeyStore
import cryptokit.crypto.libsodium.LibsodiumAeadCipher
import cryptokit.crypto.libsodium.LibsodiumAsymmetricCrypto
import cryptokit.crypto.libsodium.LibsodiumKeyDerivation
import cryptokit.crypto.libsodium.LibsodiumRandomSource
import cryptokit.crypto.stubs.StubKeyEscrow
import cryptokit.crypto.stubs.StubKeyRotation
import cryptokit.pairing.api.DeviceIdentityRepository
import cryptokit.pairing.api.EncryptedMediaStorage
import cryptokit.pairing.api.RecipientResolver
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Unified cryptokit Koin module (TASK-51 Phase 6 T038).
 *
 * Replaces both the old `cryptoModule` (spec 011 pairing-side wiring) and the
 * spec 016 `cryptokitModule`. Single source of truth for crypto-related
 * bindings used by the app:
 *
 *   ── F-CRYPTO ports (spec 016) ──
 *     - RandomSource, AeadCipher, AsymmetricCrypto, KeyDerivation, SecureKeyStore
 *     - Interface-only stubs for KeyRotation / KeyEscrow (deferred per ADR-008)
 *
 *   ── pairing-side adapters (spec 011) ──
 *     - SqlDelightBlobReferenceLedger + ClearDataDetector (KMP-pure storage)
 *     - BackgroundReconciler (orphan blob reconciliation, FR-042)
 *     - PairRecipientResolver (one-on-one pair; spec 014 will introduce group resolvers)
 *     - PairingCryptoCoordinator (key lifecycle + DeviceIdentity publication)
 *
 * Flavor-specific port bindings (Firestore-backed DeviceIdentityRepository,
 * Worker-backed EncryptedMediaStorage; or their Fake equivalents) live in
 * `:core/androidRealBackend` and `:core/androidMockBackend` `backendModule` —
 * this module composes the orchestrator + ledger + reconciler on top.
 *
 * Fake* adapters from `cryptokit.crypto.fake` are TEST-ONLY and MUST NEVER appear in
 * this module. The [assertNoFakeCryptoInRelease] helper invoked by
 * `LauncherApplication.onCreate` detects accidental wiring at runtime (SC-011);
 * Detekt rule `FakeCryptoInReleaseRule` catches imports at compile time; R8
 * strips them from the release APK as defense-in-depth.
 *
 * TODO(pre-release-audit): multi-app cohabitation — chain-of-trust strategy для
 * launcher + messenger + photo (P-10 in Phase 3; см.
 * docs/product/future/multi-app-cohabitation.md и docs/dev/crypto-review.md §A2).
 *
 * TODO(pre-release-audit): server-side entitlement JWT validation для billing —
 * клиент проверяет TEE attestation (см. SecureKeyStore.android.kt TODO), но
 * server проверяет JWT с claims о том, что ключ в TEE. Спека: см. server-roadmap.
 */
val cryptokitModule = module {

    // ── F-CRYPTO ports ───────────────────────────────────────────────────
    single<RandomSource> { LibsodiumRandomSource() }
    single<AeadCipher> { LibsodiumAeadCipher(random = get()) }
    single<AsymmetricCrypto> { LibsodiumAsymmetricCrypto() }
    single<KeyDerivation> { LibsodiumKeyDerivation() }
    single { KeyStoreContext(androidContext = androidContext()) }
    single { SecureKeyStore(context = get()) }
    single<KeyRotation> { StubKeyRotation() }
    single<KeyEscrow> { StubKeyEscrow() }

    // ── SQLDelight CryptoStore + cleanup machinery (KMP-pure) ────────────
    single<CryptoStore> { AndroidSqlDriverProvider.createCryptoStore(androidContext()) }
    single { SqlDelightBlobReferenceLedger(db = get()) }
    single { ClearDataDetector(db = get()) }

    // ── BackgroundReconciler — wraps Storage + ledger + clear-data sentinel
    single {
        BackgroundReconciler(
            storage = get<EncryptedMediaStorage>(),
            ledger = get(),
            clearData = get(),
        )
    }

    // ── PairRecipientResolver — depends on flavor-bound DeviceIdentityRepository
    single<RecipientResolver> {
        PairRecipientResolver(
            repo = get<DeviceIdentityRepository>(),
            ownDeviceId = { error("ownDeviceId not wired — supply через PairingCryptoCoordinator") },
        )
    }

    // ── PairingCryptoCoordinator — key lifecycle + DeviceIdentity publish ─
    single {
        PairingCryptoCoordinator(
            secureKeyStore = get<SecureKeyStore>(),
            asymmetric = get<AsymmetricCrypto>(),
            repo = get<DeviceIdentityRepository>(),
            deviceIdProvider = get(),
        )
    }
}

/**
 * Fail-fast guard for release builds (FR-018, SC-011).
 *
 * Walks the resolved ports and confirms none come from `cryptokit.crypto.fake.*`. Crashes
 * the app with a loud message if any do — that's by design, fake crypto in production
 * is worse than a crash.
 *
 * Call from `Application.onCreate()` AFTER Koin start. The check is skipped on debug builds.
 */
fun assertNoFakeCryptoInRelease(get: (Class<*>) -> Any) {
    val ports = listOf(
        AeadCipher::class.java,
        AsymmetricCrypto::class.java,
        KeyDerivation::class.java,
        RandomSource::class.java,
    )
    for (port in ports) {
        val impl = get(port)
        val pkg = impl.javaClass.`package`?.name.orEmpty()
        check(!pkg.startsWith("cryptokit.crypto.fake")) {
            "FATAL: ${impl.javaClass.simpleName} from package $pkg is a Fake crypto adapter " +
                "wired in a release build. Fake adapters MUST NOT appear in production DI. " +
                "Check CryptokitModule.kt and build variant."
        }
    }
}
