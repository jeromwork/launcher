// :core:cloud — TASK-49 cloud availability boolean + SOS local alternative.
//
// Module rules (per TASK-49 plan.md):
//  • commonMain — pure-Kotlin ports + data types + fakes.
//  • Зависит на :core (для AuthProvider port) + kotlinx-coroutines.
//  • androidMain — DataStore-backed CloudAvailabilityImpl, TelephonyManager-backed
//    EmergencyNumberResolverImpl, Intent-based SOSDialerAlternative.
//  • Никаких Firebase / Google Play Services / Android UI deps anywhere.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    // No jvm() target: :core has only androidJvm variants (no pure JVM
    // publication), so adding jvm() здесь не разрешается без дублирования
    // AuthProvider port. Когда :core получит jvm() target — добавим.

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.datastore.preferences)
            }
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.mockk)
            implementation(libs.androidx.test.core)
        }
    }
}

android {
    namespace = "com.launcher.cloud"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        // :core has product flavors (realBackend / mockBackend). :core:cloud
        // doesn't need to vary by backend (AuthProvider port is flavor-agnostic),
        // so we pick mockBackend as default for compile-time resolution and let
        // :app's flavor matchingFallbacks pick the right :core variant transitively.
        missingDimensionStrategy("backend", "mockBackend", "realBackend")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Fitness function (CLAUDE.md rule 7, mirror :core:push verifyPushIsolation):
// :core:cloud разрешено зависеть ТОЛЬКО на :core (AuthProvider port).
// Никаких vendor / Firebase / Google Play Services project deps.
tasks.register("verifyCloudIsolation") {
    doLast {
        val allowed = setOf(":core", project.path)
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { it.dependencyProject.path }
            .filter { it !in allowed }
            .distinct()
        check(forbidden.isEmpty()) {
            ":core:cloud allowed project deps: $allowed. Found forbidden: $forbidden " +
                "(per CLAUDE.md rule 1 + TASK-49 plan §Test Strategy)."
        }
    }
}
tasks.named("check") { dependsOn("verifyCloudIsolation") }
