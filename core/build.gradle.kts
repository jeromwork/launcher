plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    // Spec 008 — SQLDelight для local persistence (LocalConfigStore).
    // Schema lives в commonMain/sqldelight/, queries generated в commonMain.
    alias(libs.plugins.sqldelight)
    // Spec 015 — Roborazzi screenshot tests for Senior UI primitives (T094-T096).
    alias(libs.plugins.roborazzi)
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
            // TASK-51 Phase 6 — cryptokit API surfaces (DeviceIdentityRepository,
            // EncryptedMediaStorage, RecipientResolver, CryptoException, etc.)
            // are imported by pairing-side adapters (Background reconciler, fakes)
            // and by core's androidMain Firestore/Worker adapters.
            implementation(project(":core:crypto"))
            // family.pairing.* types (DeviceIdentityRepository, RecipientResolver,
            // EncryptedMediaStorage, PublicKey/SigningPublicKey/DeviceId) moved out of
            // :core:crypto into their own module (TASK-146). `api`: they appear in the
            // public shape of core's crypto/link adapters, so consumers must see them.
            api(project(":core:pairing"))
            // `api`, not `implementation`: WireVersion appears in the public shape of
            // every wire format here, so consumers must see the type.
            api(project(":core:wire"))
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
            // kotlinx-datetime — Instant in the KeyBlob wire format (moved here from
            // :core:crypto in TASK-141).
            implementation(libs.kotlinx.datetime)

            // SQLDelight — KMP local persistence для spec 008 LocalConfigStore.
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
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

            // EncryptedSharedPreferences — для хранения AES-wrapped X25519 priv bytes
            // (Android Keystore не поддерживает X25519 нативно).
            // Crypto-стопка целиком через :core:crypto (ionspin libsodium-kmp) —
            // см. модуль `core/crypto/`, добавлен через project dependency в :app.
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
            // Spec 015 (T094-T096) — Roborazzi screenshot tests.
            // Snapshots saved under core/src/androidUnitTest/snapshots/.
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
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

    // Spec 017 (F-4 AuthProvider) — Credential Manager + Google Identity provider.
    // Используются GoogleSignInAuthAdapter (androidRealBackend/adapters/auth/).
    // mockBackend не подтягивает — там FakeAuthAdapter.
    "realBackendImplementation"(libs.androidx.credentials)
    "realBackendImplementation"(libs.androidx.credentials.play.services.auth)
    "realBackendImplementation"(libs.google.id)
    // Spec 011 — encrypted blobs storage переехал на Worker-proxied B2
    // (server-roadmap SRV-CRYPTO-001 — раньше планировали Firebase Storage,
    // но Spark plan требует Blaze для Storage). См. WorkerEncryptedMediaStorage.

    // Spec 019 (F-5c) — receiver-side dispatch via PushHandlerRegistry.
    // Used by LauncherFirebaseMessagingService (androidRealBackend) для
    // forking new-shape payloads ("eventType" field) к family.push.api.PushHandlerRegistry.
    // Legacy "type"-shape payloads продолжают работать через existing FcmReceiverContract.
    "androidMainImplementation"(project(":core:push"))
}

// Spec 008 — SQLDelight schema setup.
// Schema lives в `core/src/commonMain/sqldelight/com/launcher/adapters/config/db/ConfigStore.sq`.
// Plugin generates type-safe Kotlin queries в commonMain (KMP-pure code).
sqldelight {
    databases {
        create("ConfigStore") {
            packageName.set("com.launcher.adapters.config.db")
            srcDirs.setFrom("src/commonMain/sqldelight-config")
        }
        // TASK-141 — the CryptoStore DB (BlobReferenceLedger + SystemMeta) backed
        // only the dead spec-011 orphan-blob reconciler; removed with it.
    }
}
