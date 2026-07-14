// :core:keys — F-5 key hierarchy + ConfigCipher + Recovery (spec 018).
//
// Package convention: cryptokit.keys.* (consistent c cryptokit.crypto.*, готово к extract
// в cryptokit-kmp вместе с :core:crypto). Renamed from family.keys.* in TASK-56.
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
            // libsodium для real impl JVM tests (через ConfigCipherRoundtripTest).
            implementation(libs.libsodium.bindings)
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
        // Android-specific adapters: AndroidDeviceIdentity (DataStore + SecureKeyStore)
        // + WorkManagerAsyncConfigPushQueue (WorkManager + Koin GlobalContext).
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.koin.core)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.1")
            }
        }
        // Android Keystore + Argon2id real-device tests (T122b, OEM matrix).
        val androidInstrumentedTest by getting {
            dependencies {
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test.ext:junit:1.2.1")
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.libsodium.bindings)
            }
        }
    }
}

android {
    namespace = "cryptokit.keys"
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

// TASK-112 T013: source-level fitness rule — commonMain sources MUST NOT import forbidden
// packages (vendor SDKs, platform system types). Grep-based; cheap alternative to a detekt
// custom rule until detekt is wired into the project.
tasks.register("verifyKeysNoVendorImports") {
    val commonMain = file("src/commonMain/kotlin")
    inputs.dir(commonMain)
    doLast {
        if (!commonMain.exists()) return@doLast
        val forbiddenPrefixes = listOf(
            "com.google.",
            "android.",
            "androidx.",
            "com.launcher.core.cloud.",
            "com.launcher.core.push.",
        )
        val violations = mutableListOf<String>()
        commonMain.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("import ")) {
                    val importPath = trimmed.removePrefix("import ").removeSuffix(";").trim()
                    if (forbiddenPrefixes.any { importPath.startsWith(it) }) {
                        violations += "${file.relativeTo(rootDir)}:${idx + 1}  $trimmed"
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            "Forbidden vendor imports in :core:keys commonMain (CLAUDE.md rule 1, TASK-112 FR-007):\n" +
                violations.joinToString("\n") { "  $it" }
        }
    }
}
tasks.named("check") { dependsOn("verifyKeysNoVendorImports") }
