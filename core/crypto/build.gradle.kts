// :core:crypto — F-CRYPTO KMP crypto foundation (spec 016).
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

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
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
            implementation(libs.kotlinx.serialization.json)
            // libsodium-bindings будет включён в Phase 5 (T650+).
            // implementation(libs.libsodium.bindings)
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
    }
}

android {
    namespace = "family.crypto"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Fitness function: :core:crypto MUST NOT depend on launcher modules.
// Per FR-005 + SC-006. Тест: ./gradlew :core:crypto:verifyCryptoIsolation.
tasks.register("verifyCryptoIsolation") {
    doLast {
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { it.dependencyProject.path }
            .filter { it != ":core:crypto" }
        check(forbidden.isEmpty()) {
            ":core:crypto MUST NOT depend on launcher modules (FR-005). " +
                "Found project deps: $forbidden"
        }
    }
}
tasks.named("check") { dependsOn("verifyCryptoIsolation") }
