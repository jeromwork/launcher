// :core:crypto — F-CRYPTO KMP crypto foundation (spec 016).
//
// Package root `family` = the FAMILY OF PRODUCTS (launcher, messenger, gallery), NOT the target
// audience. The product refuses a family-only domain model — it must serve a clinic, a managed
// fleet and a self-configuring user equally (CLAUDE.md, personas vs domain roles). Read
// `family.crypto` as "our product family", never as "for families". Renamed from
// cryptokit.crypto.* in TASK-141 (superseding TASK-56).
//
// Module rules (per spec.md FR-001..FR-005, plan.md §"Project Structure"):
//  • commonMain — pure-Kotlin crypto domain (ports, value types, fakes).
//  • libsodium adapters — реальные через ionspin/kotlin-multiplatform-libsodium 0.9.5
//    (см. specs/016-f-crypto-core-module/research.md §R1).
//  • Не зависит ни от каких launcher-модулей (см. verifyCryptoIsolation ниже).
//  • iOS targets объявлены day 1 (FR-002). SecureKeyStore iosMain = stub-screamer.
//
// TODO(extract-when-2nd-consumer): когда вторая senior-launcher app начнёт зависеть
// от этого модуля — extract в отдельный repo `family-crypto-kmp` через `git filter-repo`
// + Apache 2.0 license at extract (см. plan.md §"Library extraction strategy").
//
// TODO(pre-release-audit): library extract sequence — выполнить ДО написания
// spec.md для messenger (планируется ~2026-11, см. owner-mentor 2026-06-18).
// Логика: 5 месяцев procrastination = 5-10x работы потом, потому что три
// потребителя одновременно растягивают API в разные стороны. См.
// docs/dev/crypto-review.md §A5.
//
// TODO(pre-release-audit): Wycheproof subset SHA pin — выбрать commit SHA из
// github.com/google/wycheproof, скачать x25519_test.json + eddsa_test.json +
// chacha20_poly1305_test.json в core/crypto/src/commonTest/resources/wycheproof-subset/,
// добавить тесты WycheproofX25519Test + WycheproofEd25519Test + WycheproofAeadTest.
// Срок: до Play Store submission. См. docs/dev/crypto-review.md §A4.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    // TASK-146: kotlin.serialization plugin removed — family.pairing.* (the only
    // @Serializable types here) moved to :core:pairing, and ByteArrayBase64Serializer
    // moved to :core:wire. Crypto primitives now carry ZERO serialization, restoring the
    // crypto-SDK "no serialization" invariant (docs/architecture/extraction-policy.md).
}

kotlin {
    // expect/actual classes (used for SecureKeyStore) are still marked Beta in Kotlin 2.0.
    // Per spec 016 Clarifications Q1 we intentionally use this shape — suppress warning.
    targets.configureEach {
        compilations.configureEach {
            kotlinOptions {
                freeCompilerArgs += "-Xexpect-actual-classes"
            }
        }
    }

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.libsodium.bindings)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.property)
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
        // TASK-124 — the real openmls adapters (family.crypto.mls) live in androidMain and call
        // the UniFFI-generated `uniffi.crypto_ffi` API from the :crypto-ffi Rust module.
        // commonMain stays vendor-free (fitness-gated by PortsNoVendorImportTest).
        val androidMain by getting {
            dependencies {
                api(project(":crypto-ffi"))
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.property)
                implementation(libs.kotest.assertions.core)
                // :crypto-ffi ships JNA as `@aar` (Android-only). Host JVM unit tests need the
                // plain jar, which carries the desktop native JNA library.
                implementation("net.java.dev.jna:jna:5.14.0")
            }
        }
        // Spec 016 Phase B — Android Keystore instrumentation tests run on a device/emulator.
        val androidInstrumentedTest by getting {
            dependencies {
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test.ext:junit:1.2.1")
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

android {
    namespace = "family.crypto"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// JVM unit tests (androidUnitTest) exercise the real openmls adapters, which call into the
// UniFFI binding via JNA. On the host that means the *host-native* cdylib built by
// :crypto-ffi:buildRustHostLibrary (crypto_ffi.dll / libcrypto_ffi.so / .dylib) — the Android
// `.so` is not loadable here. Point JNA at it and make sure it exists before the tests run.
tasks.withType<Test>().configureEach {
    dependsOn(":crypto-ffi:buildRustHostLibrary")
    systemProperty(
        "jna.library.path",
        rootProject.file("crypto-ffi/target/release").absolutePath
    )
}

// Fitness function: :core:crypto MUST NOT depend on launcher modules.
// Per FR-005 + SC-006. Тест: ./gradlew :core:crypto:verifyCryptoIsolation.
//
// TASK-124: `:crypto-ffi` is allowed — it is the Rust/UniFFI crypto engine module, part of the
// extractable crypto SDK (docs/architecture/extraction-policy.md), NOT a launcher module.
tasks.register("verifyCryptoIsolation") {
    doLast {
        val allowed = setOf(":core:crypto", ":crypto-ffi")
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { it.dependencyProject.path }
            .filter { it !in allowed }
        check(forbidden.isEmpty()) {
            ":core:crypto MUST NOT depend on launcher modules (FR-005). " +
                "Found project deps: $forbidden"
        }
    }
}
tasks.named("check") { dependsOn("verifyCryptoIsolation") }
