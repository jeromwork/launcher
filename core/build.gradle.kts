plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
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
}
