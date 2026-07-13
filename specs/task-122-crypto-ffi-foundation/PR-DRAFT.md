# TASK-122: F-CRYPTO Rust FFI Foundation

## Summary

Foundation для всех будущих Rust-integration крипто-tasks (TASK-124 openmls, TASK-67 snow, TASK-125 SQLCipher). Устанавливает toolchain, Gradle плагин, UniFFI binding pattern без реального крипто-кода. Deliverable: Kotlin вызывает Rust `hello("world") -> "Hello, world"` через UniFFI-generated binding на arm64 Android.

## What ships

- Rust workspace `crypto-ffi/` (Cargo.toml, rust-toolchain.toml pinned 1.97.0).
- Two exported functions: `hello(name)` (smoke test) + `panics(msg)` (panic contract fitness).
- Gradle module `:crypto-ffi` with direct cargo-ndk Exec tasks (no third-party plugin — willir/mozilla plugins lag AGP 8.7 as of 2026-07). arm64-v8a only.
- UniFFI 0.28+ proc-macro pattern (no `.udl` file).
- `libcrypto_ffi.so` (343 KB) -> `jniLibs/arm64-v8a/`.
- Kotlin binding auto-generated -> `build/generated/kotlin/uniffi/crypto_ffi.kt`.
- androidTest suite: `HelloFfiTest` (hello + non-ASCII round-trip), `PanicFfiTest` (panic-across-FFI contract).
- Fitness function `verifyUniffiVersions` (wired to `:check`).
- Documentation: `crypto-ffi/README.md`, `docs/dev/rust-setup.md`, exit-ramps in `docs/architecture/crypto.md`.
- Skill `.claude/skills/crypto-ffi-panic-check/SKILL.md` (panic contract fitness).
- Setup script `scripts/setup-rust-android.ps1` (Windows), skill `rust-android-setup`.

## What does NOT ship

- No actual crypto (that's TASK-124 openmls / TASK-67 snow / TASK-125 SQLCipher).
- No CryptoPort / GroupPort domain ports (TASK-123).
- No armv7 / x86 / x86_64 ABI (Q5 Clarification: arm64-only for MVP; armv7 sub-task in ~2 weeks).
- No GitHub Actions CI (Q2 Clarification: local testing on owner desktop only).
- No iOS build (TASK-26 V-1).

## Verification status

- B1-B6 code written, `./gradlew :crypto-ffi:build && :crypto-ffi:assembleAndroidTest && :crypto-ffi:check` all green.
- B7 arm64 emulator — [deferred-local-emulator]. Infrastructure blocker: Windows x86_64 host can't run arm64 QEMU (Google limitation). See `verification/b7-deferred-note.md`.
- B7 Xiaomi 11T USB — [deferred-physical-device]. Primary verification path. Owner will run when at desk.

**Transition plan**: PR merge -> task-122 -> `Verification` status -> owner runs `./gradlew :crypto-ffi:connectedAndroidTest` on Xiaomi 11T USB -> 3/3 green -> `Done`.

## Test plan

- [ ] `./gradlew :crypto-ffi:build` — arm64 .so + Kotlin binding produced.
- [ ] `./gradlew :crypto-ffi:assembleAndroidTest` — test APK compiles.
- [ ] `./gradlew :crypto-ffi:verifyUniffiVersions` — passes (no drift).
- [ ] `./gradlew :crypto-ffi:connectedAndroidTest` on Xiaomi 11T USB — 3/3 tests green (T015).
  - HelloFfiTest.hello_returnsGreeting
  - HelloFfiTest.hello_worksWithNonAsciiName
  - PanicFfiTest.panic_isConvertedToKotlinException

## Related

- Backlog: TASK-122 (see `backlog/tasks/task-122 - F-CRYPTO-Rust-FFI-Foundation.md`)
- Spec: `specs/task-122-crypto-ffi-foundation/`
- Downstream unblocked: TASK-123 (domain ports), TASK-124 (openmls), TASK-67 (snow pairing), TASK-125 (SQLCipher).

Backlog: task-122 -> In Progress (pending Xiaomi USB smoke; will move to Verification after merge, Done after 3/3 tests green on device)

Generated with [Claude Code](https://claude.com/claude-code)
