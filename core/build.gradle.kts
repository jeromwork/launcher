plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    // Spec 008 — SQLDelight для local persistence (LocalConfigStore).
    // Schema lives в commonMain/sqldelight/, queries generated в commonMain.
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)

            // Compose Multiplatform — UI runtime + Material 3 (per ADR-005 §1, §6)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Koin — DI (per ADR-005 Amendment 2026-05-07a)
            implementation(libs.koin.core)

            // Decompose — navigation (per ADR-005 Amendment 2026-05-07a)
            implementation(libs.decompose)
            implementation(libs.decompose.extensions.compose)

            // kotlinx.serialization — used for Decompose Config persistence + future config files
            implementation(libs.kotlinx.serialization.json)

            // SQLDelight — KMP local persistence для spec 008 LocalConfigStore.
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)

            // Spec 011 — CBOR для EncryptedEnvelope wire format.
            // Binary-friendly, no base64 overhead, deterministic encoding
            // (важно для Ed25519 signature над canonical payload в DeviceIdentity).
            implementation(libs.kotlinx.serialization.cbor)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            // DataStore Preferences — spec 006 persistence для capability/health/settings snapshots.
            implementation(libs.androidx.datastore.preferences)
            // ProcessLifecycleOwner — spec 006 RESUMED hooks для capability/health collectors.
            implementation(libs.androidx.lifecycle.process)
            // SQLDelight Android driver — spec 008 LocalConfigStore.
            implementation(libs.sqldelight.android.driver)
            // WorkManager — spec 008 FR-022 T3 (fallback periodic config refresh).
            implementation(libs.androidx.work.runtime.ktx)
            // Google Play Services base — spec 010 GmsAvailabilityAdapter
            // (FR-042..FR-044 hard-block on missing GMS). Tiny module (~100 KB),
            // safe in both backend flavors.
            implementation(libs.google.play.services.base)

            // --- Spec 011 crypto stack (androidMain — vendor types confined here per CLAUDE.md rule 1)
            // Lazysodium-android = libsodium JNI binding (XChaCha20-Poly1305 + X25519 +
            // crypto_box_seal + Ed25519 + BLAKE2b). Per research.md §1, §2, §2b, §2c.
            //
            // NB: Сам implementation(libs.lazysodium.android) — wired через
            // "androidMainImplementation"(libs.lazysodium.android) { exclude(jna) }
            // в plain dependencies block ниже, потому что KMP source-set DSL не
            // поддерживает closure form для targeted exclude.
            // JNA aar — see flavor-agnostic dependencies block below
            // (KMP source-set DSL does not support artifact{} closure).
            // EncryptedSharedPreferences — для хранения AES-wrapped X25519 priv bytes
            // (Android Keystore не поддерживает X25519 нативно).
            implementation(libs.androidx.security.crypto)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            // Compose UI test (Robolectric-backed) per T411
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            // Konsist — fitness-function tests per spec 005 §8 (domain-isolation,
            // whatsapp-residue, legacy-bridge expiry). JVM-only; lives in androidUnitTest.
            implementation(libs.konsist)
            // SQLDelight JVM driver — in-memory SQLite для unit tests of
            // SqlDelightLocalConfigStore (spec 008 T052).
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

android {
    namespace = "com.launcher.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    // Spec 007 product flavors (FR-035). Must match :app's flavors verbatim so
    // AGP variant resolution picks the right :core artefact for each :app flavor.
    // KMP+Android auto-creates source sets `androidMainRealBackend` and
    // `androidMainMockBackend` (extending `androidMain`).
    flavorDimensions += "backend"
    productFlavors {
        create("realBackend") { dimension = "backend" }
        create("mockBackend") { dimension = "backend" }
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

    sourceSets {
        getByName("testRealBackend") {
            java.srcDirs("src/androidRealBackendUnitTest/kotlin")
        }
    }
}

// Spec 007 realBackend-only deps (FR-034): Firebase Auth, Firestore, FCM.
// Spec 011 (FR-030..033) adds Firebase Storage to realBackend.
// mockBackend stays Firebase-free so the APK builds without google-services.json.
// Wired via Android's flavor-specific configuration name (not KMP source set
// DSL, which can't address the bare flavor-only Kotlin source set in current
// AGP 8.7 + KMP 2.0 combo).
dependencies {
    "realBackendImplementation"(platform(libs.firebase.bom))
    "realBackendImplementation"(libs.firebase.firestore.ktx)
    "realBackendImplementation"(libs.firebase.auth.ktx)
    "realBackendImplementation"(libs.firebase.messaging.ktx)
    // Spec 011 — encrypted blobs storage переехал на Worker-proxied B2
    // (server-roadmap SRV-CRYPTO-001 — раньше планировали Firebase Storage,
    // но Spark plan требует Blaze для Storage). См. WorkerEncryptedMediaStorage.

    // Spec 011 — Lazysodium pulls JNA как transitive JAR; Android requires aar variant
    // (содержит правильно упакованные .so под ABI). JAR variant создаёт duplicate
    // class conflict с aar в Android dex'инге. Решение: explicit aar dependency.
    // Wired через 'androidMainImplementation' Android-flavor-agnostic configuration.
    "androidMainImplementation"(variantOf(libs.jna) { artifactType("aar") })

    // Spec 011 — Robolectric unit tests for libsodium adapters need pure JVM
    // JNA jar (aar variant carries .so файлы для device runtime, не работают
    // в host JVM). aar exclusion ниже исключает jna для Android compilation;
    // здесь возвращаем jar variant конкретно для unitTest classpath.
    "testImplementation"(libs.jna)

    // Spec 011 — exclude transitive JAR variant of JNA pulled in by lazysodium-android.
    // Без exclude AGP падает на "Duplicate class com.sun.jna.*" потому что aar
    // (содержит classes.jar + .so) И jar дублируют Java classes.
    // Exclude применяется конкретно к Android-runtime configurations
    // (NOT к testImplementation, где Robolectric нуждается в pure jar).
    "androidMainImplementation"(libs.lazysodium.android) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
}

// Spec 011 — JNA / Lazysodium dependency notes.
//
// Lazysodium-android тянет JNA через transitive jar. Android packaging
// требует aar variant (содержит .so + classes.jar). Когда оба variant'а
// идут в DEX merge — "Duplicate class com.sun.jna.*".
//
// Решение в "androidMainImplementation"(libs.lazysodium.android) { exclude }
// выше — переопределяет KMP-source-set decl. с targeted exclude. Это
// работает потому что в KMP "androidMainImplementation" — Android flavor
// configuration который supports closure form (не строковый KMP DSL).
//
// Sanity check: после изменения проверить
//   1) ./gradlew :app:assembleRealBackendDebug — green (нет Duplicate class)
//   2) ./gradlew :core:testMockBackendDebugUnitTest --tests "*LibsodiumAdaptersTest" — green
//   3) Real device APK install + start Spec011SmokeDebugActivity — no NoClassDefFoundError

// Spec 008 — SQLDelight schema setup.
// Schema lives в `core/src/commonMain/sqldelight/com/launcher/adapters/config/db/ConfigStore.sq`.
// Plugin generates type-safe Kotlin queries в commonMain (KMP-pure code).
sqldelight {
    databases {
        create("ConfigStore") {
            packageName.set("com.launcher.adapters.config.db")
            srcDirs.setFrom("src/commonMain/sqldelight-config")
        }
        // Spec 011 — BlobReferenceLedger + SystemMeta (cleanup machinery).
        create("CryptoStore") {
            packageName.set("com.launcher.adapters.crypto.db")
            srcDirs.setFrom("src/commonMain/sqldelight-crypto")
        }
    }
}
