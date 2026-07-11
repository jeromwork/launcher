plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    // Spec 007 — Firebase google-services plugin. Per-variant mockBackend
    // disable добавляется отдельным commit'ом (e3066a9 в 007 branch).
    alias(libs.plugins.google.services)
}

// google-services plugin processes google-services.json for every variant
// by default and fails the mockBackend build because that flavor uses
// applicationIdSuffix ".mock" which has no matching client in the JSON.
// mockBackend doesn't link Firebase SDKs so the resources aren't needed —
// disable the per-variant task on every mockBackend variant.
androidComponents {
    onVariants(selector().withFlavor("backend" to "mockBackend")) { variant ->
        tasks.named("process${variant.name.replaceFirstChar { it.uppercase() }}GoogleServices") {
            enabled = false
        }
    }
}

android {
    namespace = "com.launcher.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.launcher.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // F-5b E2E: when `-PuseFirebaseEmulator=true` is passed to gradle,
        // LauncherApplication routes Firestore + Auth SDK calls to the local
        // Firebase Emulator instead of the real cloud.
        // Default host = `10.0.2.2` (AVD loopback). For a real device on USB,
        // set up `adb reverse tcp:8080 tcp:8080 && adb reverse tcp:9099 tcp:9099`
        // and pass `-PfirebaseEmulatorHost=127.0.0.1`.
        // Default is false (use real launcher-old-dev project).
        val useEmulator = (project.findProperty("useFirebaseEmulator") as? String)
            ?.toBooleanStrictOrNull() ?: false
        val emulatorHost = (project.findProperty("firebaseEmulatorHost") as? String)
            ?.takeIf { it.isNotBlank() } ?: "10.0.2.2"
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", useEmulator.toString())
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"")

        // F-5 (task-6) — Cloudflare Worker URL for recovery-key-backup R2 storage.
        // Debug: `10.0.2.2` (AVD loopback) on `:8787` (wrangler dev default).
        // For real device on USB: `adb reverse tcp:8787 tcp:8787` then build debug.
        // Release: read from gradle.properties / -PRECOVERY_BACKUP_WORKER_URL=...
        // (placeholder URL until deploy lands — Phase 4 T666).
        //
        // TODO(server-roadmap SRV-RECOVERY-001): replace `*.workers.dev` with our
        // own domain once we move off the free Cloudflare tier.
        val recoveryWorkerUrl = (project.findProperty("RECOVERY_BACKUP_WORKER_URL") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: "https://recovery-backup.placeholder.workers.dev"
        buildConfigField(
            "String",
            "RECOVERY_BACKUP_WORKER_URL",
            "\"$recoveryWorkerUrl\""
        )

        // F-5 task-6 Track C — identity-init Worker URL (separate Worker per
        // DZ-5 microservice boundary). One-time call after first Sign-In to
        // bind UUID v4 stableId to the Firebase uid.
        // TODO(server-roadmap SRV-IDENTITY-001): replace *.workers.dev with
        //   our own domain when off the free tier.
        val identityWorkerUrl = (project.findProperty("IDENTITY_INIT_CLAIM_WORKER_URL") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: "https://identity-init.placeholder.workers.dev"
        buildConfigField(
            "String",
            "IDENTITY_INIT_CLAIM_WORKER_URL",
            "\"$identityWorkerUrl\""
        )

        // F-5b E2E instrumented tests (CloudConfigEncryptionE2ETest).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // TASK-65 (T632) — switch PoolSource adapter at build time:
        //   ./gradlew :app:assembleDebug                  → HardcodedPoolSource (default)
        //   ./gradlew :app:assembleDebug -Ppools.json=true → JsonAssetPoolSource scaffold
        val poolsJson = (project.findProperty("pools.json") as? String)
            ?.toBooleanStrictOrNull() ?: false
        buildConfigField("boolean", "POOLS_JSON", poolsJson.toString())
    }

    // Spec 007 product flavors (FR-034). `realBackend` wires Firebase + Cloudflare Worker;
    // `mockBackend` wires in-memory Fakes so the app builds and runs without
    // google-services.json or network access. Flavor-specific Koin wiring lives
    // in :core/src/androidMain{RealBackend,MockBackend}/.
    flavorDimensions += "backend"
    productFlavors {
        create("realBackend") {
            dimension = "backend"
            // TODO(spec 007 Phase 4): once Firebase adapters are in place, this
            // is where applicationIdSuffix / firebase-config selectors land.
        }
        create("mockBackend") {
            dimension = "backend"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Spec 016 (F-CRYPTO) — release strips Fake* crypto adapters via R8.
    // Defense-in-depth alongside the Detekt rule (compile-time) and
    // assertNoFakeCryptoInRelease (runtime).
    buildTypes {
        getByName("debug") {
            // Local Worker dev: `cd workers/backup && wrangler dev` exposes :8787 on
            // host. Emulator reaches it via `10.0.2.2`; real device via `adb reverse
            // tcp:8787 tcp:8787`. Overridable via `-PRECOVERY_BACKUP_WORKER_URL=...`
            // on the gradle command line.
            val debugRecoveryUrl = (project.findProperty("RECOVERY_BACKUP_WORKER_URL") as? String)
                ?.takeIf { it.isNotBlank() }
                ?: "http://10.0.2.2:8787"
            buildConfigField(
                "String",
                "RECOVERY_BACKUP_WORKER_URL",
                "\"$debugRecoveryUrl\""
            )

            // Identity worker default port 8788 (Track B). adb reverse for
            // physical devices: `adb reverse tcp:8788 tcp:8788`.
            val debugIdentityUrl = (project.findProperty("IDENTITY_INIT_CLAIM_WORKER_URL") as? String)
                ?.takeIf { it.isNotBlank() }
                ?: "http://10.0.2.2:8788"
            buildConfigField(
                "String",
                "IDENTITY_INIT_CLAIM_WORKER_URL",
                "\"$debugIdentityUrl\""
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // ABI splits для release builds — ionspin libsodium-kmp (через :core:crypto)
    // поставляет libsodium.so под 4 ABI (arm64-v8a, armeabi-v7a, x86, x86_64).
    // Без splits release APK потяжелеет на ~1.0-1.2 MiB (все ABIs упакованы).
    // Со splits — каждый пользователь Play Store скачивает только свой ABI
    // (~300 KiB delta per device).
    // Debug builds: splits **отключены** (универсальный APK для удобства dev/CI).
    //
    // History: до 2026-06-26 (TASK-51) тут также стоял packaging.jniLibs.pickFirsts
    // костыль для разрешения конфликта между lazysodium-android и ionspin (обе
    // тащили свой libsodium.so). lazysodium удалён — pickFirsts больше не нужен.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(project(":core"))
    // Spec 016 (F-CRYPTO) — KMP crypto foundation module.
    implementation(project(":core:crypto"))
    // Spec 018 (F-5) — key hierarchy, ConfigCipher, recovery.
    implementation(project(":core:keys"))
    // Spec 019 (F-5c) — generic push-trigger foundation.
    implementation(project(":core:push"))
    // TASK-49 — CloudAvailability + LocalAlternative + EmergencyNumberResolver.
    implementation(project(":core:cloud"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    // TASK-120 — wire format decoding for pool.json / preset.json / profile blobs
    // in :app Android adapters (BundledPoolSource, BundledPresetSource,
    // DataStoreProfileStore).
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Decompose used by HomeActivity entry point (defaultComponentContext + ComponentContext).
    implementation(libs.decompose)
    implementation(libs.decompose.extensions.compose)

    // Compose Android — нужны для :app debug screens (CapabilitySnapshotDebugActivity и
    // HealthSnapshotDebugActivity) + spec 006 HomeBannerHost. CMP transitively через :core
    // не expose'ит эти symbols в :app source set, нужно явное подключение через BOM.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)

    // ZXing — QR encoder for the Managed-side pairing display (spec 007 FR-004).
    // Pure JVM, ~150 KB; lives in :app since the Managed UI is Android-only
    // (TODO when iOS support arrives: extract a :core/api/qr/QrEncoder port
    // with expect/actual impls per checklist domain-isolation note 1).
    implementation(libs.zxing.core)

    // Spec 008 FR-022 T3 — WorkManager Configuration.Provider в LauncherApplication
    // ссылается на androidx.work.Configuration. Transitively pulled from :core
    // but :app's compilation needs it as direct dep.
    implementation(libs.androidx.work.runtime.ktx)

    // Spec 019 F-5c T131 — FcmTokenBootstrapPublisher (realBackend flavor) дёргает
    // FirebaseMessaging.getInstance().token. Firebase BOM + messaging-ktx
    // подключены транзитивно через :core, но :app компилятору нужны direct deps.
    "realBackendImplementation"(platform(libs.firebase.bom))
    "realBackendImplementation"(libs.firebase.messaging.ktx)
    "realBackendImplementation"(libs.firebase.firestore.ktx)

    // CameraX + ML Kit barcode — admin-side QR scanner (spec 007 FR-005, T089).
    "realBackendImplementation"(libs.androidx.camera.core)
    "realBackendImplementation"(libs.androidx.camera.camera2)
    "realBackendImplementation"(libs.androidx.camera.lifecycle)
    "realBackendImplementation"(libs.androidx.camera.view)
    "realBackendImplementation"(libs.mlkit.barcode.scanning)
    // Guava ListenableFuture — needed at compile time for ProcessCameraProvider.getInstance().
    // The CameraX runtime brings it transitively, but Kotlin compiler needs the type.
    "realBackendImplementation"("com.google.guava:guava:33.4.0-android")

    // Firebase SDKs needed by realBackend adapters (FirestoreEnvelopeStorage,
    // FirestorePublicKeyDirectory, FirestoreRecoveryKeyBackup, FirebaseEmulatorWiring).
    "realBackendImplementation"(platform(libs.firebase.bom))
    "realBackendImplementation"(libs.firebase.firestore.ktx)
    "realBackendImplementation"(libs.firebase.auth.ktx)
    "realBackendImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")

    // Spec 017 (F-4 AuthProvider) — Credential Manager + Google Identity provider
    // подключены в :core/androidRealBackend (где живут GoogleSignInAuthAdapter
    // и EncryptedLocalSessionStore). Транзитивно доступны в :app через api(),
    // если когда-то понадобится прямой вызов из Activity.

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Spec 019 (F-5c) — runTest для ConfigUpdatedHandlerTest.
    testImplementation(libs.kotlinx.coroutines.test)
    // Spec 016 (F-CRYPTO) — Konsist fitness function: catches Fake crypto imports in :app/src/main.
    testImplementation(libs.konsist)
    // Spec 015 T119 — WizardEngineIntegrationTest constructs JsonPrimitive
    // payloads for the wizard step host. kotlinx-serialization-json is
    // implementation in :core (not api) so test classpath needs it explicit.
    testImplementation(libs.kotlinx.serialization.json)

    // F-5b E2E instrumented tests (app/src/androidTest/).
    // Firebase SDKs (firestore/auth) уже подключены к realBackend variant'у
    // через :core/androidRealBackend, andtest classpath их подхватывает
    // транзитивно. Coroutines tasks await помогает с .await() в тестах.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")

    // TASK-49 T025 — Compose UI instrumented tests.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
