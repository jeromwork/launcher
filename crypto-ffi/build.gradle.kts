// TASK-122 F-CRYPTO Rust FFI Foundation — Gradle module wrapping the crypto-ffi/
// Rust crate via cargo-ndk + UniFFI 0.28 proc-macros.
//
// See:
//   - specs/task-122-crypto-ffi-foundation/plan.md §Phase 2.3
//   - specs/task-122-crypto-ffi-foundation/tasks.md T007-T011
//   - Clarifications Q5: arm64-v8a only on first release (armv7/x86 deferred).
//
// Design choice — direct cargo-ndk invocation via Exec tasks (no Gradle plugin
// wrapper). Rationale: the willir cargo-ndk-android-gradle plugin (0.3.4) and
// mozilla/rust-android-gradle (0.9.6) both lag AGP 8.x compatibility as of
// 2026-07-13; using plain Exec tasks keeps us on stable AGP 8.7.3 with zero
// plugin risk. Exit ramp — add a plugin later if the Rust FFI surface grows.

import org.gradle.api.tasks.Exec
import java.io.File

// ---------------------------------------------------------------------------
// Cargo binary lookup
// ---------------------------------------------------------------------------
//
// On Windows, `rustup` installs cargo into %USERPROFILE%\.cargo\bin, which is
// added to the *user* PATH — not always propagated to child processes launched
// by Gradle (e.g. from an IDE run). Try common locations and fall back to
// bare "cargo" (relies on the process PATH).
fun findCargo(): String {
    val candidates = listOfNotNull(
        System.getenv("CARGO_HOME")?.let { "$it/bin/cargo" },
        System.getenv("USERPROFILE")?.let { "$it/.cargo/bin/cargo.exe" },
        System.getProperty("user.home")?.let { "$it/.cargo/bin/cargo.exe" },
        System.getProperty("user.home")?.let { "$it/.cargo/bin/cargo" }
    )
    return candidates.firstOrNull { File(it).exists() } ?: "cargo"
}

val cargoBin: String = findCargo()

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "family.launcher.cryptoffi"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        // Clarifications Q5: arm64-v8a only on first release.
        // Adding armv7 / x86_64 later = one-line addition here + one target in
        // the cargoNdkTargets list below.
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Register UniFFI-generated Kotlin bindings as a source set so both main
    // consumers and androidTest see the generated `uniffi.crypto_ffi.*` package.
    sourceSets {
        getByName("main").java.srcDir(
            layout.buildDirectory.dir("generated/kotlin/uniffi")
        )
        // The cargo-ndk Exec task copies libcrypto_ffi.so into src/main/jniLibs/<abi>/;
        // AGP picks it up automatically from the standard jniLibs.srcDirs default.
    }
}

// Kotlin/JVM target — match compileOptions (JDK 17). No jvmToolchain block:
// other modules in the project rely on the ambient JAVA_HOME (JDK 21 per
// developer setup); adding jvmToolchain(17) here would trigger auto-provision
// on machines without a local JDK 17.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

// ---------------------------------------------------------------------------
// Rust cross-compile — direct cargo-ndk invocation
// ---------------------------------------------------------------------------
//
// Per Clarifications Q5 we build arm64-v8a only. The pair (ABI, Rust triple)
// stays in sync via this list. To add armv7 later: append "armeabi-v7a" to
// abiFilters above and add ("armeabi-v7a" to "armv7-linux-androideabi") here.
val cargoNdkAbiToTarget = mapOf(
    "arm64-v8a" to "aarch64-linux-android"
)

val rustCrateDir: File = projectDir
val jniLibsDir: File = File(rustCrateDir, "src/main/jniLibs")

val buildRustLibraries by tasks.registering {
    group = "uniffi"
    description = "Cross-compile crypto-ffi Rust crate for all configured Android ABIs via cargo-ndk."

    inputs.file(File(rustCrateDir, "Cargo.toml"))
    inputs.file(File(rustCrateDir, "Cargo.lock"))
    inputs.file(File(rustCrateDir, "src/lib.rs"))
    inputs.file(File(rustCrateDir, "rust-toolchain.toml"))
    cargoNdkAbiToTarget.values.forEach { triple ->
        outputs.file(File(rustCrateDir, "target/$triple/release/libcrypto_ffi.so"))
    }

    doLast {
        cargoNdkAbiToTarget.forEach { (abi, _) ->
            // cargo-ndk 4.x uses Android ABI names directly (arm64-v8a,
            // armeabi-v7a, x86, x86_64) and maps to the Rust target triple.
            exec {
                workingDir = rustCrateDir
                commandLine(
                    cargoBin, "ndk",
                    "--platform", libs.versions.minSdk.get(),
                    "-t", abi,
                    "--", "build", "--release"
                )
            }
        }

        // Copy produced `.so` files into src/main/jniLibs/<abi>/ so AGP picks them up.
        cargoNdkAbiToTarget.forEach { (abi, triple) ->
            val src = File(rustCrateDir, "target/$triple/release/libcrypto_ffi.so")
            val dstDir = File(jniLibsDir, abi)
            dstDir.mkdirs()
            val dst = File(dstDir, "libcrypto_ffi.so")
            src.copyTo(dst, overwrite = true)
        }
    }
}

// ---------------------------------------------------------------------------
// generateUniffiBindings — invoke UniFFI's proc-macro-mode bindgen
// ---------------------------------------------------------------------------
//
// Runs after cargo-ndk has produced libcrypto_ffi.so. Invokes the crate's own
// `uniffi-bindgen` binary (built by cargo) via `--library <path-to-.so>`.
// This is the UniFFI 0.28+ convention (no global CLI, no .udl file — per
// Clarifications Q1). Output: build/generated/kotlin/uniffi/uniffi/crypto_ffi/*.kt
//
// The generated file declares e.g. `fun hello(name: String): String` in the
// package `uniffi.crypto_ffi`, callable from Kotlin as-is (T012, T013).
// UniFFI 0.28 bindgen needs to load a cdylib and introspect exported metadata
// symbols. On Windows the Android `.so` cannot be loaded / parsed by a
// Windows-native `uniffi-bindgen.exe`, so we build a *host-native* cdylib
// (`crypto_ffi.dll` on Windows, `libcrypto_ffi.dylib` on macOS, `libcrypto_ffi.so`
// on Linux) and point bindgen at it. Same crate, same UniFFI metadata — the
// binding is ABI-agnostic.
val buildRustHostLibrary by tasks.registering(Exec::class) {
    group = "uniffi"
    description = "Build host-native crypto_ffi cdylib for uniffi-bindgen introspection."

    inputs.file(File(rustCrateDir, "Cargo.toml"))
    inputs.file(File(rustCrateDir, "Cargo.lock"))
    inputs.file(File(rustCrateDir, "src/lib.rs"))
    inputs.file(File(rustCrateDir, "rust-toolchain.toml"))
    // We don't declare a specific output path (varies by OS — dll/so/dylib);
    // this task is fast (cargo caches) and the downstream bindgen task will
    // fail loudly if the artefact is missing.
    outputs.dir(File(rustCrateDir, "target/release"))

    workingDir = rustCrateDir
    commandLine(cargoBin, "build", "--release", "--lib")
}

val generateUniffiBindings by tasks.registering(Exec::class) {
    group = "uniffi"
    description = "Generate Kotlin bindings via UniFFI 0.28 proc-macro bindgen (against host cdylib)."

    dependsOn(buildRustHostLibrary)

    // Host-native library name — Windows: crypto_ffi.dll, Linux: libcrypto_ffi.so,
    // macOS: libcrypto_ffi.dylib. Bindgen accepts any of these.
    val hostReleaseDir = File(rustCrateDir, "target/release")
    val outDir = layout.buildDirectory.dir("generated/kotlin/uniffi").get().asFile

    inputs.file(File(rustCrateDir, "src/lib.rs"))
    inputs.file(File(rustCrateDir, "Cargo.toml"))
    outputs.dir(outDir)

    workingDir = rustCrateDir

    doFirst {
        outDir.mkdirs()
        val candidates = listOf(
            File(hostReleaseDir, "crypto_ffi.dll"),
            File(hostReleaseDir, "libcrypto_ffi.so"),
            File(hostReleaseDir, "libcrypto_ffi.dylib")
        )
        val hostLib = candidates.firstOrNull { it.exists() }
            ?: throw GradleException(
                "Host cdylib not found in ${hostReleaseDir.absolutePath}. " +
                    "Tried: ${candidates.map { it.name }}. Did buildRustHostLibrary run?"
            )
        commandLine(
            cargoBin, "run", "--bin", "uniffi-bindgen", "--",
            "generate",
            "--library", hostLib.absolutePath,
            "--language", "kotlin",
            "--out-dir", outDir.absolutePath
        )
    }
}

// Wire generation into the standard build chain so `./gradlew :crypto-ffi:build`
// produces both the `.so` and the Kotlin binding.
afterEvaluate {
    tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }
        .configureEach { dependsOn(generateUniffiBindings) }
    tasks.named("preBuild") { dependsOn(generateUniffiBindings) }
    // Also ensure `.so` files exist before packaging.
    tasks.matching { it.name.startsWith("merge") && it.name.contains("JniLibFolders") }
        .configureEach { dependsOn(buildRustLibraries) }
}

// ---------------------------------------------------------------------------
// verifyUniffiVersions — fitness function per FR-008 / CLAUDE.md rule 7
// ---------------------------------------------------------------------------
//
// Guards against UniFFI version drift between three sources of truth:
//   1. Cargo.toml [dependencies]      — the crate we compile against.
//   2. Cargo.toml [build-dependencies] — the codegen crate at build.rs time.
//   3. Cargo.lock                     — the authoritative resolved version.
//
// Kotlin runtime is JNA-only in UniFFI 0.28 (generated file is self-contained,
// no separate `uniffi-kotlin-runtime` artifact) — so there is no fourth source
// to align today. Future-proofed: if a Kotlin runtime dep appears later, add
// a fourth check here.
//
// Cargo.toml pins as major.minor ("0.28"); Cargo.lock resolves to full semver
// ("0.28.3"). We compare on major.minor to reflect the actual pin semantics
// (any 0.28.x is intended-compatible; a 0.29 in the lock file would mean the
// pin drifted).
val verifyUniffiVersions by tasks.registering {
    group = "verification"
    description = "Fitness function per FR-008: UniFFI version lockstep across " +
        "Cargo.toml [dependencies], [build-dependencies], and Cargo.lock."

    val cargoTomlFile = File(rustCrateDir, "Cargo.toml")
    val cargoLockFile = File(rustCrateDir, "Cargo.lock")
    inputs.file(cargoTomlFile)
    inputs.file(cargoLockFile)

    doLast {
        val cargoToml = cargoTomlFile.readText()

        // Match every `uniffi = { version = "X.Y" ... }` line (covers both
        // [dependencies] and [build-dependencies] sections).
        val tomlVersions = Regex("""^\s*uniffi\s*=\s*\{\s*version\s*=\s*"([^"]+)".*$""", RegexOption.MULTILINE)
            .findAll(cargoToml)
            .map { it.groupValues[1] }
            .toList()

        require(tomlVersions.isNotEmpty()) {
            "verifyUniffiVersions: no `uniffi = { version = ... }` entry found in Cargo.toml. " +
                "Expected both [dependencies] and [build-dependencies] to pin uniffi."
        }
        require(tomlVersions.size >= 2) {
            "verifyUniffiVersions: expected uniffi in BOTH [dependencies] and " +
                "[build-dependencies], only found ${tomlVersions.size} entry: $tomlVersions."
        }
        require(tomlVersions.toSet().size == 1) {
            "UniFFI version drift within Cargo.toml: $tomlVersions. " +
                "All uniffi entries in [dependencies] and [build-dependencies] MUST match. " +
                "See crypto-ffi/README.md § UniFFI bump."
        }
        val cargoTomlVersion = tomlVersions.first()

        // Cargo.lock: find the `[[package]]` entry for uniffi and extract its version.
        val cargoLock = cargoLockFile.readText()
        val lockVersion = Regex("""\[\[package\]\]\s*\r?\nname\s*=\s*"uniffi"\s*\r?\nversion\s*=\s*"([^"]+)"""")
            .find(cargoLock)
            ?.groupValues
            ?.get(1)
            ?: throw GradleException(
                "verifyUniffiVersions: no `[[package]] name = \"uniffi\"` entry in Cargo.lock. " +
                    "Run `cargo build` in crypto-ffi/ to regenerate."
            )

        fun majorMinor(v: String): String = v.split(".").take(2).joinToString(".")

        val tomlMM = majorMinor(cargoTomlVersion)
        val lockMM = majorMinor(lockVersion)
        require(tomlMM == lockMM) {
            "UniFFI version drift: Cargo.toml=$cargoTomlVersion (major.minor $tomlMM), " +
                "Cargo.lock=$lockVersion (major.minor $lockMM). All sources MUST match on major.minor. " +
                "See crypto-ffi/README.md § UniFFI bump."
        }

        logger.lifecycle(
            "verifyUniffiVersions: PASS — Cargo.toml=$cargoTomlVersion, Cargo.lock=$lockVersion " +
                "(major.minor $tomlMM aligned)."
        )
    }
}

// Wire into :crypto-ffi:check so drift is caught on every build.
tasks.named("check") {
    dependsOn(verifyUniffiVersions)
}

dependencies {
    // JNA — UniFFI-generated Kotlin binding uses JNA to call into libcrypto_ffi.so.
    // Version 5.14.0 per plan.md §Technical Context. `@aar` variant bundles the
    // native JNA `.so` for Android.
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // Coroutines — UniFFI 0.28 generated Kotlin uses kotlinx-coroutines for
    // suspending / async support. Even our two synchronous functions pull it
    // in transitively via the generated runtime helpers.
    implementation(libs.kotlinx.coroutines.core)

    // androidTest deps (T012-T014) — added here so `compileArm64V8aDebugAndroidTestKotlin`
    // compiles the future HelloFfiTest / PanicFfiTest classes.
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("net.java.dev.jna:jna:5.14.0@aar")
}
