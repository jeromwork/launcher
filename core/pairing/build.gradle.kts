// :core:pairing — device pairing / Authentication Service zone (extracted from :core:crypto, TASK-146).
//
// Package root `family` = the FAMILY OF PRODUCTS (launcher, messenger, gallery), NOT the target
// audience. Read `family.pairing` as "our product family", never as "for families"
// (CLAUDE.md, personas vs domain roles).
//
// Why a separate module (per docs/architecture/crypto-pairing.md): pairing is our
// Authentication Service — a distinct zone above the crypto primitives, NOT a primitive itself
// (RFC 9750 / Signal precedent). Serialization is LEGAL here: `PublicKey`, `SigningPublicKey`,
// `DeviceId` are `@Serializable` wire types, which would violate the crypto-SDK "no serialization"
// invariant if left inside :core:crypto. The shared Base64 codec lives in :core:wire (the
// extractability barrier), not here — pairing depends on it.
//
// Depends on :core:crypto (crypto value types) and :core:wire (ByteArrayBase64Serializer).
// Both are zero-dependency leaves, so no cycle is possible.

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
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:crypto"))
            api(project(":core:wire"))
            implementation(libs.kotlinx.serialization.json)
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
    namespace = "family.pairing"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
