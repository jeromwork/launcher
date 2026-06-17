package com.launcher.app.di

import family.crypto.api.AeadCipher
import family.crypto.api.AsymmetricCrypto
import family.crypto.api.KeyDerivation
import family.crypto.api.KeyEscrow
import family.crypto.api.KeyRotation
import family.crypto.api.KeyStoreContext
import family.crypto.api.RandomSource
import family.crypto.api.SecureKeyStore
import family.crypto.libsodium.LibsodiumAeadCipher
import family.crypto.libsodium.LibsodiumAsymmetricCrypto
import family.crypto.libsodium.LibsodiumKeyDerivation
import family.crypto.libsodium.LibsodiumRandomSource
import family.crypto.stubs.StubKeyEscrow
import family.crypto.stubs.StubKeyRotation
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Spec 016 (F-CRYPTO) Koin wiring per FR-030 + plan.md §"Lifecycle/Initialization order".
 *
 * Binds the 7 F-CRYPTO ports to real Libsodium adapters + interface-only stubs for
 * KeyRotation / KeyEscrow (deferred to spec 017 per FR-011/FR-012).
 *
 * Fake* adapters from `family.crypto.fake` are TEST-ONLY and MUST NEVER appear in
 * this module. The [assertNoFakeCryptoInRelease] helper invoked by [LauncherApplication.onCreate]
 * detects accidental wiring at runtime (SC-011); Detekt rule `FakeCryptoInReleaseRule` catches
 * imports at compile time; R8 strips them from the release APK as defense-in-depth.
 */
val f016CryptoModule = module {
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
 * Walks the resolved ports and confirms none come from `family.crypto.fake.*`. Crashes
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
        check(!pkg.startsWith("family.crypto.fake")) {
            "FATAL: ${impl.javaClass.simpleName} from package $pkg is a Fake crypto adapter " +
                "wired in a release build. Fake adapters MUST NOT appear in production DI. " +
                "Check F016CryptoModule.kt and build variant."
        }
    }
}
