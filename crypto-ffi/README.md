# crypto-ffi

Rust ↔ Kotlin FFI foundation for the F-CRYPTO wave. Established by TASK-122.

## Purpose

This module is the **toolchain foundation** for all future crypto work in the
launcher project. It sets up cargo-ndk, UniFFI, and the Gradle wiring so that
any subsequent task (TASK-124 openmls integration, TASK-125 SQLCipher
persistence, future primitives) can add Rust code and call it from Kotlin
without re-inventing the plumbing.

Today the module ships two smoke functions:

- `hello(name: String) -> String` — verifies the round-trip works end-to-end.
- `panics(msg: String) -> String` — always panics; verifies UniFFI converts
  Rust panics into Kotlin exceptions instead of aborting the process
  (FR-011 fitness function, see [Panic contract](#panic-contract) below).

**No actual crypto lives here yet.** openmls, libsodium bindings, SQLCipher
storage — all arrive in downstream tasks. This module is pure infrastructure.

## Prerequisites

- **Rust 1.97.0** — pinned via `rust-toolchain.toml`, rustup auto-installs on
  first `cargo` invocation. No manual `rustup default` needed.
- **Android NDK 26+** — install via Android Studio → SDK Manager → SDK Tools →
  "NDK (Side by side)". Set `ANDROID_NDK_HOME` to the installed path
  (typically `~/Android/Sdk/ndk/<version>` or `%LOCALAPPDATA%\Android\Sdk\ndk\<version>`).
- **cargo-ndk 4.1.2**:
  ```
  cargo install cargo-ndk --version 4.1.2 --locked
  ```
- **Android Studio** with either:
  - a physical arm64 device connected over USB (recommended — Xiaomi 11T is
    the project's reference device), or
  - an **arm64** AVD (Android Studio → Device Manager → new device → System
    Image → filter by ABI = `arm64-v8a`).

For a full one-shot setup on a fresh Windows machine, see
[`docs/dev/rust-setup.md`](../docs/dev/rust-setup.md) and the automated
PowerShell bootstrap `.\scripts\setup-rust-android.ps1`.

## Architecture (30-second version)

```
crypto-ffi/src/lib.rs          # Rust source with #[uniffi::export] functions
       ↓ (cargo-ndk build --target aarch64-linux-android)
crypto-ffi/target/.../libcrypto_ffi.so
       ↓ (Gradle Exec task copies)
app/src/main/jniLibs/arm64-v8a/libcrypto_ffi.so
       ↓ (uniffi-bindgen generates)
crypto-ffi/build/generated/uniffi/kotlin/uniffi/crypto_ffi/crypto_ffi.kt
       ↓ (normal Kotlin import)
import uniffi.crypto_ffi.hello   // callable from any Android module
```

The whole pipeline is driven by `./gradlew :crypto-ffi:build`. Cargo, cargo-ndk,
and uniffi-bindgen are invoked as Gradle `Exec` tasks — no third-party
cargo-ndk Gradle plugin (the willir / mozilla plugins lag AGP 8.7 as of
2026-07). This is a deliberate exit-ramp-friendly choice; see
[`docs/architecture/crypto.md § FFI Toolchain — Exit Ramps`](../docs/architecture/crypto.md).

## How to add a new Rust function

Goal: expose `add(a: i32, b: i32) -> i32` to Kotlin.

1. **Edit `src/lib.rs`.** Add:
   ```rust
   #[uniffi::export]
   pub fn add(a: i32, b: i32) -> i32 {
       a + b
   }
   ```
   Any argument/return type that UniFFI supports (primitives, `String`,
   `Vec<u8>`, `Option<T>`, custom structs annotated with `#[derive(uniffi::Record)]`)
   works. See the [UniFFI types reference](https://mozilla.github.io/uniffi-rs/latest/types/builtin_types.html).

2. **Rebuild.** From repo root:
   ```
   ./gradlew :crypto-ffi:build
   ```
   cargo-ndk recompiles `.so`; uniffi-bindgen regenerates the Kotlin binding.
   Clean build: ~30–90s. Incremental: usually <10s.

3. **Call from Kotlin.** In any Android module that depends on `:crypto-ffi`:
   ```kotlin
   import uniffi.crypto_ffi.add

   val result = add(2, 3)   // 5
   ```
   The generated binding takes care of JNI, memory, and error mapping.

4. **Write an androidTest.** Instrumented tests run on an Android device (the
   only place `.so` is actually loaded). Add to
   `src/androidTest/kotlin/family/launcher/cryptoffi/AddFfiTest.kt`:
   ```kotlin
   package family.launcher.cryptoffi

   import androidx.test.ext.junit.runners.AndroidJUnit4
   import org.junit.Test
   import org.junit.runner.RunWith
   import uniffi.crypto_ffi.add
   import kotlin.test.assertEquals

   @RunWith(AndroidJUnit4::class)
   class AddFfiTest {
       @Test
       fun add_returnsSum() {
           assertEquals(5, add(2, 3))
       }
   }
   ```

## How to rebuild locally

- Full build: `./gradlew :crypto-ffi:build` (~30–90s clean).
- Just Rust: `cd crypto-ffi && cargo ndk -t arm64-v8a build --release`.
- Just Kotlin binding regeneration:
  `./gradlew :crypto-ffi:generateUniffiBindings`.

Incremental builds are fast — Cargo caches per-crate, Gradle caches Kotlin
compile.

## How to run tests

Three paths depending on what you have:

### Physical device (recommended)

Reference device: **Xiaomi 11T (arm64)**. Any arm64 Android 8.0+ device works.

1. Enable Developer Options + USB debugging on the device.
2. Connect via USB, accept the debugging prompt.
3. Verify: `adb devices` shows the device as `device` (not `unauthorized`).
4. Run:
   ```
   ./gradlew :crypto-ffi:connectedAndroidTest
   ```

Fast and honest — same ABI as production.

### arm64 emulator (Android Studio AVD)

Use when no physical device is available.

1. Android Studio → Device Manager → **Create Device**.
2. Pick any phone hardware profile.
3. On the "System Image" page, **filter by ABI = arm64-v8a**. Do NOT pick
   `x86_64` — our `.so` is arm64-only, and x86_64 emulator will fail to load
   the library at runtime.
4. Launch the AVD.
5. Run `./gradlew :crypto-ffi:connectedAndroidTest`.

On Intel/AMD host CPUs the arm64 emulator uses QEMU CPU emulation and is
noticeably slow — expect 30–60s just for the test app install. It's fine for
occasional verification, unacceptable as a daily loop.

### Compile-only smoke (no device)

Verifies the binding compiles + androidTest sources link, without needing any
device or emulator:

```
./gradlew :crypto-ffi:assembleAndroidTest
```

Useful as a CI-gate and for quick sanity checks after a Rust API change.

## How to safely update UniFFI version

UniFFI has three moving pieces that MUST stay in lockstep, or you get
undefined behaviour at runtime:

1. The `uniffi` crate in `[dependencies]`.
2. The `uniffi` crate in `[build-dependencies]`.
3. The Kotlin runtime shipped alongside the generated binding.

The Gradle task `verifyUniffiVersions` enforces this at build time.

### Procedure

1. **Edit `crypto-ffi/Cargo.toml`.** Bump the version in ALL places
   `uniffi = "0.28..."` appears (currently `[dependencies]` and
   `[build-dependencies]`):
   ```toml
   [dependencies]
   uniffi = { version = "0.29", features = ["build"] }

   [build-dependencies]
   uniffi = { version = "0.29", features = ["build"] }
   ```

2. **Update `Cargo.lock`:**
   ```
   cd crypto-ffi && cargo update -p uniffi
   ```

3. **Run the fitness function:**
   ```
   ./gradlew :crypto-ffi:verifyUniffiVersions
   ```
   Should PASS. If it fails, the Cargo.toml versions are out of sync with
   each other or with the Kotlin runtime — re-read the error, fix, re-run.

4. **Full rebuild + compile-test:**
   ```
   ./gradlew :crypto-ffi:build
   ./gradlew :crypto-ffi:assembleAndroidTest
   ```

5. **Handle API breakage on major bumps** (0.28 → 0.29 etc.). UniFFI's proc
   macros occasionally rename attributes or tighten type rules. Read the
   [UniFFI CHANGELOG](https://github.com/mozilla/uniffi-rs/blob/main/CHANGELOG.md)
   and adjust `#[uniffi::export]` annotations accordingly.

6. **Run the full androidTest suite** on a real device — smoke tests
   (`HelloFfiTest`, `PanicFfiTest`) MUST pass. `PanicFfiTest` in particular
   guards the panic-across-FFI contract that UniFFI itself does NOT document
   (see [Panic contract](#panic-contract) below).

### Windows long-path caveat

If Gradle emits "path too long" during Rust compile: enable Windows long-path
support:

```powershell
# One-time, as administrator
git config --system core.longpaths true
```

And ensure `%CARGO_TARGET_DIR%` (or the default `crypto-ffi/target/`) is not
buried under deep nested directories.

## Fitness function: `verifyUniffiVersions`

Custom Gradle task in `crypto-ffi/build.gradle.kts`. On every build it parses
`Cargo.toml` and asserts that `uniffi` versions in `[dependencies]` and
`[build-dependencies]` match. Failure means the version was bumped in one
place but not the other — a silent-corruption class of bug. See TASK-122 B5
for the implementation history.

## Panic contract

UniFFI 0.28+ automatically wraps every `#[uniffi::export]`-ed function in
`std::panic::catch_unwind`. A Rust panic becomes an `InternalException` on
the Kotlin side — NOT a process abort.

**This is undocumented in official UniFFI docs.** We infer it from source
review and the upstream fix history (issue #485 and follow-ups). Because it's
undocumented, we ship an explicit smoke-test
(`PanicFfiTest.panic_isConvertedToKotlinException`) and a skill
[`crypto-ffi-panic-check`](../.claude/skills/crypto-ffi-panic-check/SKILL.md)
to catch regression on future UniFFI upgrades.

See also FR-011 in
[`specs/task-122-crypto-ffi-foundation/spec.md`](../specs/task-122-crypto-ffi-foundation/spec.md)
and the exit-ramp analysis in
[`docs/architecture/crypto.md § FFI Toolchain — Exit Ramps`](../docs/architecture/crypto.md).

## Related

- Skill [`rust-android-setup`](../.claude/skills/rust-android-setup/SKILL.md)
  — automated Windows bootstrap.
- Skill [`crypto-ffi-panic-check`](../.claude/skills/crypto-ffi-panic-check/SKILL.md)
  — panic-contract regression detector.
- [`docs/dev/rust-setup.md`](../docs/dev/rust-setup.md) — cross-platform
  onboarding guide (Russian, owner-facing).
- [`specs/task-122-crypto-ffi-foundation/spec.md`](../specs/task-122-crypto-ffi-foundation/spec.md)
  — feature spec (FR-001..FR-012, SC-001..SC-006).
- [`specs/task-122-crypto-ffi-foundation/plan.md`](../specs/task-122-crypto-ffi-foundation/plan.md)
  — implementation plan and constitution gates.
- [`specs/task-122-crypto-ffi-foundation/tasks.md`](../specs/task-122-crypto-ffi-foundation/tasks.md)
  — task breakdown (T001–T021).
- [`docs/architecture/crypto.md`](../docs/architecture/crypto.md) — crypto
  domain architecture with FFI-toolchain exit ramps.
