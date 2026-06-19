// :core:keys — F-5 key hierarchy + ConfigCipher + Recovery (spec 018).
//
// Package convention: family.keys.* (consistent c family.crypto.*, готово к extract
// в family-crypto-kmp вместе с :core:crypto).
//
// Module rules (per spec 018 plan.md):
//  • commonMain — pure-Kotlin domain (ports, wire-format, sealed errors, fakes).
//  • Зависит ТОЛЬКО на :core:crypto (F-CRYPTO primitives) — никаких Firebase /
//    Compose / launcher domain imports. Verified через verifyKeysIsolation ниже.
//  • Adapters (Firestore, Google Sign-In, Android Autofill) живут в :app, не здесь.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
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
    // iOS targets declared inactive per F-CRYPTO decisions 2026-06-17 — добавим
    // когда iOS app появится в roadmap. Domain код уже multiplatform-ready.

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:crypto"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.property)
            implementation(project(":core:crypto"))
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}

android {
    namespace = "family.keys"
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

// Fitness function: :core:keys MUST NOT depend on launcher app or :firebase modules.
// Allowed deps: :core:crypto only. Per CLAUDE.md rule 1 (domain isolation).
tasks.register("verifyKeysIsolation") {
    doLast {
        val allowed = setOf(":core:keys", ":core:crypto")
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { it.dependencyProject.path }
            .filter { it !in allowed }
        check(forbidden.isEmpty()) {
            ":core:keys MUST depend only on :core:crypto (CLAUDE.md rule 1). " +
                "Found project deps: $forbidden"
        }
    }
}
tasks.named("check") { dependsOn("verifyKeysIsolation") }
