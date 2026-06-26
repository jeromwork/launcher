package com.launcher.app.di

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
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Spec 016 (F-CRYPTO) Koin wiring per FR-030 + plan.md §"Lifecycle/Initialization order".
 *
 * Binds the 7 F-CRYPTO ports to real Libsodium adapters + interface-only stubs for
 * KeyRotation / KeyEscrow (deferred to a future spec, TBD — see ADR-008, per FR-011/FR-012).
 *
 * Fake* adapters from `cryptokit.crypto.fake` are TEST-ONLY and MUST NEVER appear in
 * this module. The [assertNoFakeCryptoInRelease] helper invoked by [LauncherApplication.onCreate]
 * detects accidental wiring at runtime (SC-011); Detekt rule `FakeCryptoInReleaseRule` catches
 * imports at compile time; R8 strips them from the release APK as defense-in-depth.
 *
 * TODO(pre-release-audit): multi-app cohabitation — chain-of-trust strategy для
 * launcher + messenger + photo (P-10 in Phase 3; см.
 * docs/product/future/multi-app-cohabitation.md и docs/dev/crypto-review.md §A2).
 * Сейчас Variant A (Independent) — каждое app имеет свои ключи. Перед messenger MVP —
 * реализовать Variant B (ContentProvider + custom permission) или гибрид B+C.
 *
 * TODO(pre-release-audit): server-side entitlement JWT validation для billing —
 * клиент проверяет TEE attestation (см. SecureKeyStore.android.kt TODO), но
 * server проверяет JWT с claims о том, что ключ в TEE. Спека: см. server-roadmap
 * (отдельная инициатива, не F-CRYPTO scope).
 */
val cryptokitModule = module {
    single<RandomSource> { LibsodiumRandomSource() }
    single<AeadCipher> { LibsodiumAeadCipher(random = get()) }
    single<AsymmetricCrypto> { LibsodiumAsymmetricCrypto() }
    single<KeyDerivation> { LibsodiumKeyDerivation() }
    single { KeyStoreContext(androidContext = androidContext()) }
    single { SecureKeyStore(context = get()) }
    single<KeyRotation> { StubKeyRotation() }
    single<KeyEscrow> { StubKeyEscrow() }
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
        RandomSource::class.java
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
