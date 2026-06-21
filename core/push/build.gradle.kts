// :core:push — F-5c generic push-trigger foundation (spec 019).
//
// Package convention: family.push.* (consistent with :core:keys / :core:crypto,
// готово к extract в standalone family-push-kmp module per TODO-ARCH-017).
//
// Module rules (per spec 019 plan.md):
//  • commonMain — pure-Kotlin domain (ports, wire-format DTOs, sealed errors, fakes).
//  • Зависит ТОЛЬКО на Ktor (HTTP client) + kotlinx primitives. Никаких Firebase /
//    Compose / launcher domain imports. Verified через verifyPushIsolation ниже.
//  • Adapters (Firebase Messaging, WorkManager) живут в androidMain — vendor types
//    confined here per CLAUDE.md rule 1.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    jvm()
    // iOS targets — добавим когда iOS app появится в roadmap. Foundation
    // multiplatform-ready (Ktor + kotlinx commonMain only).

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Ktor HTTP client — KMP-compatible, used by DefaultPushTrigger
            // (plan §Dependency impact). Future iOS port reuses тот же impl.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // MockEngine для unit tests Ktor-based DefaultPushTrigger.
            implementation(libs.ktor.client.mock)
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                // CIO engine — Android-side default. Other engines (Android engine,
                // OkHttp engine) пробовать только при profile-driven evidence.
                implementation(libs.ktor.client.cio)
                // WorkManager — primary BackgroundDispatcher backend (plan §G6).
                implementation(libs.androidx.work.runtime.ktx)
                // Koin — для GlobalContext lookup в WorkManagerBackgroundDispatcher
                // (consistent с :core:keys WorkManagerAsyncConfigPushQueue pattern).
                implementation(libs.koin.core)
            }
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.junit)
            implementation(libs.robolectric)
            // Konsist — fitness functions (T400-T402).
            implementation(libs.konsist)
        }
    }
}

android {
    namespace = "family.push"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Fitness function: :core:push MUST NOT depend on launcher app or other feature
// modules. Allowed deps: ZERO project deps (foundation is standalone) — same
// invariant as :core:keys verifyKeysIsolation. Per CLAUDE.md rule 1 (domain
// isolation) + SC-009 (extraction-readiness).
//
// TODO(TODO-ARCH-017 push extraction): когда modulesince extraction trigger
// fires (per server-roadmap), этот fitness function проверит что :core:push
// готов к extract — ZERO project deps required.
tasks.register("verifyPushIsolation") {
    doLast {
        val allowed = emptySet<String>()
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { it.dependencyProject.path }
            .filter { it !in allowed }
        check(forbidden.isEmpty()) {
            ":core:push MUST be standalone (zero project deps) per CLAUDE.md rule 1 " +
                "+ SC-009. Found project deps: $forbidden"
        }
    }
}
tasks.named("check") { dependsOn("verifyPushIsolation") }
