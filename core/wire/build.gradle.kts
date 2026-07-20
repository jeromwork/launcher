// :core:wire — the version identifier shared by every wire format in the ecosystem.
//
// Package root `family` means the FAMILY OF PRODUCTS (launcher, messenger, gallery), not the
// target audience. The product deliberately refuses a family-only domain model — it must serve
// a clinic, a managed device fleet and a self-configuring user equally (CLAUDE.md, personas vs
// domain roles). Read `family.wire` as "our product family", never as "for families".
//
// Rules live in docs/architecture/wire-format.md (single source of truth). This module
// implements §2 (the identifier + comparison), §3 (the three-outcome reader gate) and
// §4 (fail closed with a typed error). It must stay a LEAF: every other module depends
// on it, so a dependency here would create a cycle. Enforced by verifyWireIsolation.
//
// Why a module rather than a helper inside :core — :core:crypto and :core:push are
// leaves that carry wire formats of their own and cannot see :core. Without a shared
// leaf each module would re-implement version comparison, which is precisely the
// silent-misroute failure invariant I3 exists to prevent.

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
            // Core, not JSON: this module describes the shape of a version, it does not decide
            // how one is written. A consumer serializing to CBOR must not inherit the JSON artifact.
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            // JSON is a TEST-only dependency on purpose: proving a version survives a roundtrip
            // needs some concrete format, but the module itself must not commit to one.
            implementation(libs.kotlinx.serialization.json)
            implementation(kotlin("test"))
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
    namespace = "family.wire"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Fitness function: :core:wire MUST stay a leaf — see the module header.
tasks.register("verifyWireIsolation") {
    doLast {
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { it.dependencyProject.path }
            .filter { it != ":core:wire" }
        check(forbidden.isEmpty()) {
            ":core:wire MUST stay a leaf module — every wire format depends on it. " +
                "Found project deps: $forbidden"
        }
    }
}
tasks.named("check") { dependsOn("verifyWireIsolation") }
