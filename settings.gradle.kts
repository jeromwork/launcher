pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "launcher"

include(":app", ":core", ":core:crypto", ":core:keys", ":core:push", ":core:cloud", ":core:wire", ":core:pairing")

// TASK-122 F-CRYPTO Rust FFI Foundation — Gradle module wrapping the crypto-ffi/
// Rust crate via cargo-ndk + UniFFI 0.28 proc-macros.
include(":crypto-ffi")

// Future feature modules (pattern :feature-* per spec FR-019), e.g.:
// include(":feature-favorites")
