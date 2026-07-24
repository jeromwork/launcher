# Quickstart: building & testing the openmls adapter

Extends the TASK-122 build loop; no new tooling. Prereq: Rust 1.97 + cargo-ndk + Android NDK (skill `rust-android-setup`).

## Build chain (already wired in `crypto-ffi/build.gradle.kts`, TASK-122)

1. `buildRustHostLibrary` — host-native cdylib (for bindgen introspection on Windows).
2. `generateUniffiBindings` — `cargo run --bin uniffi-bindgen -- generate --library <hostLib> --language kotlin` → `uniffi.crypto_ffi` Kotlin.
3. `buildRustLibraries` — `cargo ndk -t arm64-v8a -- build --release` → `libcrypto_ffi.so` → jniLibs.

After adding `mls.rs`/`storage.rs` + Cargo deps, these tasks pick them up automatically (proc-macro export surface).

## Test commands

```bash
# contract + roundtrip + property (JVM, on host-native lib)
./gradlew :core:crypto:testDebugUnitTest

# fitness (import isolation + uniffi version lockstep)
./gradlew :crypto-ffi:check

# emulator smoke (SC-005) — skill android-emulator, pixel_5_api_34
./gradlew :core:crypto:connectedDebugAndroidTest   # or the targeted smoke test
```

## Gotchas

- **First openmls build is slow** (~20-30 min per TASK-58 research) — heavy Rust crate graph. Subsequent incremental builds are fast.
- Property test (100 sequences) runs real MLS crypto — keep the sequence bound at 100 unless CI time allows more.
- If `verifyUniffiVersions` fails after adding deps: a transitive crate pulled a different uniffi minor — pin it; the lockstep number is **0.28.3**.
- Host-lib bindgen (step 1) must succeed before Kotlin compiles — it's wired as a `compile*Kotlin` dependency, but a Rust compile error surfaces there first.

---

## TL;DR для новичка

Как собрать и проверить: команды `./gradlew` уже настроены прошлой задачей — добавляем крипту в Rust, и сборка сама её подхватит. Тесты гоняются одной командой на компьютере (без телефона), плюс отдельный прогон на эмуляторе для «дымового» теста. Главная засада — первая сборка Rust-крипты долгая (20-30 минут), дальше быстро.
