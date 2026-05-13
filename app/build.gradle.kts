plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
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

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
