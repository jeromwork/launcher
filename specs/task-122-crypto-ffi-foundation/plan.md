# Implementation Plan: F-CRYPTO Rust FFI Foundation (TASK-122)

**Branch**: `task-122-crypto-ffi-foundation` | **Date**: 2026-07-13 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/task-122-crypto-ffi-foundation/spec.md`

## Summary

**По-русски (для владельца)**: TASK-122 — фундаментальная build-инфраструктура для будущих Rust-крипто-библиотек (openmls, snow, SQLCipher). Ничего криптографического не пишем — только доказательство что Rust-код можно вызывать из Kotlin через FFI. Собираем `libcrypto_ffi.so` под arm64-v8a (Xiaomi 11T + arm64-эмулятор), UniFFI автоматически генерирует Kotlin binding, androidTest вызывает `hello("world")` → получает `"Hello, world"`. Плюс smoke-test на `panic!()` через FFI (документируем контракт, который UniFFI официально не гарантирует). Fitness function ловит расхождение версий UniFFI.

**In English (AI-facing)**: Build-infra spec producing a `:crypto-ffi` Gradle module wrapping a Rust workspace via UniFFI 0.28+ proc-macros. Two exported functions (`hello`, `panics`) + two androidTests + one Gradle fitness task (UniFFI version lockstep). Rust 1.97.0 pinned via `rust-toolchain.toml`. Single ABI (arm64-v8a) for first release; armv7 deferred (see Q5). No CI — local runs on owner's desktop per Q2. No domain ports here (TASK-123 territory).

## Technical Context

**Language/Version**: Rust 1.97.0 (pinned via `rust-toolchain.toml`), Kotlin 2.0.x (existing project), JVM 17.
**Primary Dependencies**:
- `uniffi = "0.28"` (Rust crate with `build`, `bindgen` features) — FFI interface generator.
- `uniffi_bindgen` — invoked via `cargo run --bin uniffi-bindgen` (per UniFFI 0.28+ convention, no standalone CLI).
- `cargo-ndk` 4.1.2 (host-side cargo subcommand) — Android cross-compile driver.
- Android NDK 30.0.15729638+ (installed via Android Studio SDK Manager, not by this task).
- `net.java.dev.jna:jna:5.14.0@aar` (Kotlin runtime dependency for UniFFI-generated bindings).

**Storage**: N/A — no persistence. FFI is intra-process only.

**Testing**: `androidTest` (instrumented, Kotlin) on arm64-v8a emulator or Xiaomi 11T via USB. No Robolectric — FFI needs a real `.so` loaded via JNI, which Robolectric cannot provide.

**Target Platform**: Android arm64-v8a only on first release (per Clarifications Q5). Structure allows one-line `abiFilters` addition for armv7 / x86_64 later without Rust/Kotlin code changes.

**Project Type**: Build-infrastructure Gradle module + Rust workspace at repo root.

**Performance Goals**:
- Full build from clean ≤ 10 min (SC-006, after one-off Rust setup).
- Incremental build ≤ 60 s (SC-007).

**Constraints**:
- No CI (Clarifications Q2) — local desktop verification only. Verification-status transition via backlog Kanban (In Progress → Verification → Done).
- No `.udl` file — UniFFI interface via proc-macro attributes (Clarifications Q1).
- Rust panic contract must survive across FFI (converted to Kotlin exception, not process abort) — Q4.

**Scale/Scope**: 1 Rust workspace, 1 Gradle module, 2 exported Rust functions, 2 androidTests, 1 fitness Gradle task, 1 README, 1 dev-setup doc.

## Constitution Check

Generated 2026-07-13 via `procedure-constitution-check` logic against Article XVI gates. **OVERALL: 4 PASS, 4 N/A, 0 FAIL — plan PASSES.**

| Gate | Verdict | Justification |
|---|---|---|
| **1. Architecture** | ✅ PASS | New Gradle module `:crypto-ffi` is justified — it isolates cross-compile toolchain (cargo-ndk plugin) from `:app` and `:core:crypto`. No new abstractions in Kotlin/Rust — just a `hello()` smoke function. Domain ports live in TASK-123, not here. Boundary is explicit: `:crypto-ffi` exposes UniFFI-generated Kotlin only. |
| **2. Core/System Integration** | N/A | No system events, broadcasts, lifecycle callbacks, or intents. FFI runs in-process on demand. |
| **3. Configuration** | N/A | No presets, no schema, no user-facing config. Rust version pinned in `rust-toolchain.toml` — dev-toolchain config, not product config. |
| **4. Required Context Review** | ✅ PASS | Plan §"Required Context Review" links CLAUDE.md (rules 1, 3, 4, 7), constitution.md (Articles V, XI, XIII, XVI), `docs/architecture/crypto.md` (frontmatter exit-ramp record), TASK-123 backlog card (downstream contract), skill `rust-android-setup` (Windows setup automation), skill `crypto-ffi-panic-check` (panic contract enforcement). No `docs/product/*` referenced — no user-facing behavior to trace. Omission justified. |
| **5. Accessibility** | N/A | No UI. |
| **6. Battery/Performance** | ✅ PASS | `libcrypto_ffi.so` is loaded once via `System.loadLibrary()` at first use (lazy, per Android/UniFFI convention). No background work, no polling, no broadcasts. Runtime cost per FFI call = single JNI transition (~microseconds, orders of magnitude below anything user-perceptible). Startup impact = zero if FFI not invoked at cold-start (verified: `hello()` is not called from `Application.onCreate`). Perf targets SC-006/SC-007 are dev-build times, not runtime. |
| **7. Testing** | ✅ PASS | Test strategy: 2 androidTests (hello + panic), 1 Gradle fitness task (UniFFI version lockstep, FR-008), manual verification on Xiaomi 11T. No unit tests possible without real `.so` (Rust unit tests via `cargo test` in workspace are separate and allowed but not required by AC). Fake adapters N/A — no domain port to fake here (TASK-123 territory). |
| **8. Simplicity** | ✅ PASS | Two abstractions justified in Complexity Tracking (UniFFI + cargo-ndk). No speculative generics, no premature port shapes. `hello()` returns `String` — smallest possible smoke. Panic function is the minimum needed to prove the contract per Clarifications Q4. Per CLAUDE.md rule 4 MVA: without these two tools the alternative is manual JNI hand-coding, which would be a *rewrite* for every future crypto task, not just an addition. |

**Notable strengths**:
- Both introduced abstractions (UniFFI, cargo-ndk) have documented exit ramps per CLAUDE.md rule 3 — see Complexity Tracking below.
- No new domain code, no new wire format, no new server contract — pure build-tooling scope discipline.
- Panic contract explicitly tested (Q4) even though UniFFI docs don't guarantee it — matches Element X / Matrix Rust SDK industry practice.

**No remediation required**. Plan ready for `/speckit.tasks`.

## Project Structure

### Documentation (this feature)

```text
specs/task-122-crypto-ffi-foundation/
├── spec.md              # Clarified spec (5 Q&As locked)
├── plan.md              # This file
├── tasks.md             # Phase 2 output — NOT created by this plan
└── (no research.md, data-model.md, contracts/, quickstart.md — see Sizing below)
```

**Sizing rationale**: TASK-122 is a **tiny spec** per skill classification.
- `research.md` — SKIP. Industry survey (Element X, Signal, Matrix, Bitwarden, Firefox, Mozilla) already consumed and locked into Clarifications Q1-Q5. No further one-way-door alternatives to compare.
- `data-model.md` — SKIP. No persistent data, no wire format.
- `contracts/` — SKIP. No external API, no cross-device wire format. UniFFI-generated Kotlin binding is a build artefact, not a stable contract (regenerated per build).
- `quickstart.md` — SKIP (content merged into `crypto-ffi/README.md` per FR-009). Dev setup lives in `docs/dev/rust-setup.md` per FR-010.

### Source Code (repository root)

```text
crypto-ffi/                         (new — repo root, sibling of app/, core/)
├── Cargo.toml                      workspace root; deps: uniffi = "0.28", crate-type = ["cdylib", "staticlib"]
├── Cargo.lock                      committed (NFR-002)
├── rust-toolchain.toml             channel = "1.97.0", targets = ["aarch64-linux-android"]
├── build.rs                        uniffi_build::generate_scaffolding("./src/lib.rs") — if using build-script variant
├── src/
│   ├── lib.rs                      #[uniffi::setup_scaffolding!] + #[uniffi::export] fn hello() + fn panics()
│   └── bin/
│       └── uniffi-bindgen.rs       standard UniFFI 0.28+ bindgen entry (invokes uniffi_bindgen::bindgen_main)
├── build.gradle.kts                cargo-ndk plugin (id "com.github.willir.rust.cargo-ndk-android") + UniFFI Kotlin gen task + verifyUniffiVersions task
├── src/androidTest/kotlin/family/launcher/cryptoffi/
│   ├── HelloFfiTest.kt             hello_returnsGreeting()
│   └── PanicFfiTest.kt             panic_isConvertedToKotlinException()
├── src/main/AndroidManifest.xml    empty <manifest package="family.launcher.cryptoffi"/>
└── README.md                       FR-009: add-function guide, rebuild guide, UniFFI bump procedure

docs/dev/
└── rust-setup.md                   FR-010: onboarding (link to rust-android-setup skill + macOS/Linux notes)

settings.gradle.kts                 add ":crypto-ffi" to include(...)

.gitignore                          append: /crypto-ffi/target/, /crypto-ffi/build/, *.so under build/generated/
```

**Structure Decision**: single new Gradle module at repo root. Rust workspace lives inside the same directory (`crypto-ffi/Cargo.toml`) — cargo-ndk plugin picks it up via `path = "."` config. No separate `rust/` top-level directory — keeps everything for one FFI module co-located, matches Element X / Bitwarden convention.

## Phases

### Phase 0: Research

**Status**: DONE (folded into Clarifications, no separate `research.md`).

Industry patterns already surveyed pre-Clarifications:
- **UniFFI proc-macro adoption**: Matrix Element X (largest UniFFI consumer, contributed proc-macro upstream), Bitwarden SDK, Mozilla application-services — all on proc-macro, no `.udl` files in new code.
- **Panic-across-FFI smoke tests**: Element X ships `panics()` in `matrix-rust-sdk-ffi`, Matrix client SDKs include panic conversion tests. Documented rationale: UniFFI panic contract is source-inferred (issue #485), not officially guaranteed.
- **Rust version pinning**: Signal (`rust-toolchain.toml`), Firefox, Matrix, Mullvad, Bitwarden — all pinned for application projects. MSRV in `Cargo.toml` is library-consumer concept, N/A here.
- **cargo-ndk vs manual `cargo build --target aarch64-linux-android`**: cargo-ndk handles NDK sysroot / linker / API level auto-selection. Manual = ~1-2 days of Gradle plumbing per project.

No unresolved research questions. All Q1-Q5 answers locked in Clarifications.

### Phase 1: Contracts

**N/A for this task.** No wire formats, no external API, no cross-device data flow. UniFFI-generated Kotlin binding is a build artefact regenerated per commit — not a stable contract subject to CLAUDE.md rule 5 wire-format versioning.

### Phase 2: Implementation approach

Ordering (mapped to future `tasks.md` phases — not decomposed here):

1. **Rust workspace scaffold**:
   - `crypto-ffi/Cargo.toml` (workspace-less single crate, `[lib] crate-type = ["cdylib", "staticlib"]`).
   - `crypto-ffi/rust-toolchain.toml` pinned to `1.97.0` + target `aarch64-linux-android` (per NFR-001, Q5).
   - `crypto-ffi/src/lib.rs`: `uniffi::setup_scaffolding!()` + `#[uniffi::export] pub fn hello(name: String) -> String` + `#[uniffi::export] pub fn panics(msg: String) -> String` (per FR-002, FR-011).
   - `crypto-ffi/src/bin/uniffi-bindgen.rs` — standard 0.28+ shape (calls `uniffi::uniffi_bindgen_main()`).

2. **Gradle module `:crypto-ffi`**:
   - Apply cargo-ndk-android plugin.
   - Configure `abiFilters = ["arm64-v8a"]` (per FR-003, Q5).
   - Add Gradle task to run `cargo run --bin uniffi-bindgen generate --library ... --language kotlin --out-dir build/generated/kotlin` (per FR-005).
   - Wire generated Kotlin dir into `sourceSets["androidTest"]`.
   - Add JNA runtime dependency for UniFFI.
   - `settings.gradle.kts` include `":crypto-ffi"`.

3. **androidTests**:
   - `HelloFfiTest.kt`: call `hello("world")`, assert `== "Hello, world"` (FR-006).
   - `PanicFfiTest.kt`: call `panics("test")` inside `assertFailsWith<Exception>`, verify process didn't die (FR-011).

4. **Fitness function**:
   - Gradle task `verifyUniffiVersions`: parse `uniffi` version from `crypto-ffi/Cargo.toml`, parse UniFFI runtime version from resolved dependencies, fail if mismatch (FR-008). Wire into `:crypto-ffi:check`.

5. **Documentation**:
   - `crypto-ffi/README.md`: add-function walk-through (proc-macro annotation → rebuild → new binding available), rebuild command, UniFFI bump procedure (FR-009).
   - `docs/dev/rust-setup.md`: link to `rust-android-setup` skill (Windows), notes for macOS/Linux (FR-010).

6. **Manual Verification workflow** (Clarifications Q2):
   - Backlog: `task-122` moves to `Verification` on PR merge with note `pending: local emulator smoke + Xiaomi 11T USB smoke`.
   - Owner runs `./gradlew :crypto-ffi:connectedAndroidTest` on desktop machine → green.
   - Backlog transitions to `Done`.

### Phase 3 (out-of-scope acknowledgement)

Not part of this task, but noted so tasks.md doesn't drift:
- Domain ports (`CryptoPort`, `GroupPort`, `KeyPackagePort`) — TASK-123.
- openmls integration — TASK-124.
- snow (Noise pairing) — TASK-67.
- SQLCipher — TASK-125.

## Complexity Tracking

Two abstractions introduced. Both justified per CLAUDE.md rule 4 (MVA) — without them the alternative is a *rewrite* per future crypto task, not just an *addition*.

| Introduction | Why Needed | Simpler Alternative Rejected Because | Exit Ramp (CLAUDE.md rule 3) |
|---|---|---|---|
| **UniFFI 0.28 (proc-macro)** | Auto-generates Kotlin binding from Rust `#[uniffi::export]` — zero hand-written JNI. Every future crypto function (openmls MLS ops, snow handshake, SQLCipher wrap) gets its Kotlin binding for free. | Manual JNI + cbindgen: adds ~2-3 weeks per crypto task (TASK-124, TASK-67, TASK-125 each) and requires hand-writing thread-safety wrappers, error conversion, panic handling. Element X abandoned manual JNI after 3 months for exactly this reason. | Manual JNI via cbindgen. Estimated cost: 2-3 weeks per exported Rust module. Recorded in `docs/architecture/crypto.md` frontmatter. Two-way door — can revisit per-crate. |
| **cargo-ndk 4.1.2 (Gradle plugin)** | Drives cargo cross-compile under Gradle: auto-sets `CC_aarch64_linux_android` / linker / sysroot from `$ANDROID_NDK_HOME`, wires `.so` outputs into `jniLibs/`. Zero manual NDK env var wrangling. | Manual `cargo build --target aarch64-linux-android` in a shell script + custom Gradle Copy task: ~1-2 days of Gradle plumbing per project, brittle to NDK version bumps. | Manual cross-compile shell script. Estimated cost: 1-2 days initial + brittle to NDK NDK version bumps. Two-way door. |

**Rejected non-abstractions**:
- **Multi-ABI first release** (armv7 + x86_64 + arm64): rejected per Q5 — nothing to test them on today. `abiFilters` structure allows one-line addition later. Deferred sub-task ~2 weeks.
- **CI on GitHub Actions**: rejected per Q2 — owner does not want to spend CI-minutes on Rust cross-compile until value proven. Local runs on desktop suffice. Deferred sub-task (self-hosted runner) if local runs prove unreliable.
- **`.udl` file for UniFFI interface**: rejected per Q1 — industry has moved to proc-macro. Two-way door if needed (~days per crate to convert).

## Test Strategy

Per CLAUDE.md rule §6 (mock-first) and §7 (fitness functions):

### androidTest (instrumented, arm64 device or emulator)
- `HelloFfiTest.hello_returnsGreeting()` — verifies `hello("world") == "Hello, world"` (SC-002, FR-006).
- `PanicFfiTest.panic_isConvertedToKotlinException()` — verifies `panics("test")` throws Kotlin exception; process still alive (SC-003, FR-011).

### Fitness function (Gradle task)
- `verifyUniffiVersions` — parses `uniffi` Cargo dep + Kotlin runtime `net.java.dev.jna` + UniFFI runtime version, fails on mismatch with concrete message (SC-005, FR-008).

### Not applicable
- Unit tests (Kotlin): FFI requires real `.so` load, Robolectric can't emulate. Rust-side `cargo test` for pure Rust logic — allowed but not part of AC (nothing to test at this level; `hello` is trivial).
- Fake adapters: no domain port here (TASK-123 territory).
- Contract roundtrip / backward-compat tests: no wire format.

### Manual verification (owner, on desktop, Clarifications Q2)
- Run `./gradlew :crypto-ffi:build` on Windows dev-machine → green + `.so` present under arm64-v8a in build outputs (SC-001).
- Run `./gradlew :crypto-ffi:connectedAndroidTest` on Xiaomi 11T via USB OR arm64 emulator → 2 tests green (SC-002, SC-003).
- Verify SC-004 (README walk-through readable, add-function ≤ 30 min) by owner review.
- Verify SC-008 (backlog Verification → Done transition) via `pre-pr-backlog-sync` skill on PR.

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **UniFFI version lockstep drift** at build time | MEDIUM | HIGH (runtime crash on first FFI call) | Fitness function `verifyUniffiVersions` (FR-008) — fails build before deployment. |
| **Panic-across-FFI contract silently breaks** in UniFFI 0.29+ | LOW | HIGH (process abort in prod crypto ops) | `PanicFfiTest` (FR-011) + `crypto-ffi-panic-check` skill runs on any `crypto-ffi/**` PR. |
| **cargo-ndk incompatibility with future Android NDK version** | LOW | MEDIUM | Exit ramp: manual JNI via cbindgen (documented in `docs/architecture/crypto.md`). Two-way door. |
| **arm64-only means owner can't test in x86_64 emulator on Intel dev-machine** | HIGH (already accepted) | LOW | Trade-off accepted per Q5 — owner has Xiaomi 11T (arm64) + slow arm64 emulator. Documented. |
| **NDK not installed on fresh dev-machine** | MEDIUM | LOW (build error) | `rust-android-setup` skill checks presence; `docs/dev/rust-setup.md` links to Android Studio SDK Manager install path. |
| **Windows path length / long-path issues with Rust `target/` dir** | LOW | LOW (build error) | Documented in README + `docs/dev/rust-setup.md` — enable Windows LongPaths registry key. Owner already has this enabled. |

## Required Context Review

Read before implementation:

- [`CLAUDE.md`](../../CLAUDE.md) — Engineering rules 1 (domain isolation — domain ports live in TASK-123, not here), 2 (ACL — UniFFI-generated Kotlin is the ACL over Rust `.so`), 3 (one-way doors — exit ramps for UniFFI + cargo-ndk documented), 4 (MVA — two justified abstractions, no premature generics), 7 (fitness functions — `verifyUniffiVersions`).
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Articles V (Modularization — new `:crypto-ffi` module justified), XI (Simplicity — no speculative shapes), XIII (Dependency Governance — 4 new build-time deps documented), XVI (Constitution Check — this section).
- [`docs/architecture/crypto.md`](../../docs/architecture/crypto.md) — F-CRYPTO architecture doc; TASK-122 is entry point of the Rust wave. Exit-ramp record for UniFFI belongs in frontmatter.
- [`backlog/tasks/task-122 - F-CRYPTO-Rust-FFI-Foundation.md`](../../backlog/tasks/) — task card with description + AC.
- [`backlog/tasks/task-123 - ...`](../../backlog/tasks/) — downstream: domain ports (`CryptoPort`, `GroupPort`, `KeyPackagePort`) — informs what NOT to build here.
- [`.claude/skills/rust-android-setup/SKILL.md`](../../.claude/skills/rust-android-setup/SKILL.md) — Windows setup automation for onboarding.
- [`.claude/skills/crypto-ffi-panic-check/SKILL.md`](../../.claude/skills/crypto-ffi-panic-check/SKILL.md) — panic-contract enforcement invoked on `crypto-ffi/**` PRs.
- Memory:
  - [`project_f_crypto_decisions_2026_06_17`](../../../C:/Users/user/.claude/projects/c--work-launcher/memory/project_f_crypto_decisions.md) — F-CRYPTO strategic decisions (validation approach, wrap pattern, ionspin choice, library repo strategy).

Not read (justified omissions):
- `docs/product/*` — no user-facing behavior, no persona, no use-case.
- `docs/compliance/*` — no permissions, no PII, no runtime surface.
- `docs/adr/*` — no ADR touched (this task is scoped narrowly enough not to require one; Q5 arm64-only trade-off is Clarification-level, not ADR-level).

## Rollout / Verification

### Phased rollout (commits on branch)

| Phase | Scope | Commit shape |
|---|---|---|
| Phase 1 (DONE) | Skill `rust-android-setup` + PowerShell bootstrap script | `d79e709` already on branch |
| Phase 2 (DONE) | Spec + Clarifications + Sequences skip note | 3 commits already on branch |
| Phase 3 (this file) | Plan.md — Constitution Check PASS | 1 commit |
| Phase 4 | Rust workspace scaffold + `lib.rs` (hello + panics) | 1 commit |
| Phase 5 | Gradle `:crypto-ffi` module + cargo-ndk + UniFFI gen task | 1 commit |
| Phase 6 | androidTests (hello + panic) | 1 commit |
| Phase 7 | Fitness function `verifyUniffiVersions` | 1 commit |
| Phase 8 | README + `docs/dev/rust-setup.md` | 1 commit |
| Phase 9 | Manual smoke on Xiaomi 11T (owner-executed) → `pre-pr-backlog-sync` → PR open | 1 sync commit |

### Verification gates

After each phase:
- `./gradlew :crypto-ffi:build` on Windows dev-machine — green.
- `cargo check` in `crypto-ffi/` — green.

Final gates (before PR merge):
- SC-001 ✅ full build green on Windows.
- SC-002 ✅ `hello_returnsGreeting` green on Xiaomi 11T OR arm64 emulator.
- SC-003 ✅ `panic_isConvertedToKotlinException` green (same device).
- SC-005 ✅ `verifyUniffiVersions` proven by artificial mismatch trial.
- SC-006 / SC-007 ✅ measured on owner's desktop.

Final gates (after PR merge, backlog `Verification` → `Done`):
- SC-002 / SC-003 re-run on Xiaomi 11T (owner physical device gate).
- SC-004 owner reads README, confirms add-function walk-through ≤ 30 min.
- SC-008 backlog transition verified via `pre-pr-backlog-sync`.

## Deferred items (out of scope for TASK-122)

- **armv7 ABI addition** — separate task, ~2 weeks (needs old arm32 phone purchase).
- **x86_64 ABI** — not planned; owner not seeing value.
- **Self-hosted GitHub Actions runner** — only if local runs prove unreliable (unlikely).
- **Rust version bump procedure task** — will be created when first bump is needed.
- **Domain ports** (`CryptoPort` etc.) — TASK-123.
- **iOS cross-compile** — TASK-26.

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** План build-инфраструктуры под будущие Rust-крипто-библиотеки (openmls, snow, SQLCipher). Ничего криптографического не пишем. Собираем один Gradle-модуль `:crypto-ffi` + Rust-workspace внутри него, доказываем что Kotlin вызывает Rust-функцию `hello("world")` через FFI и получает строку обратно. Плюс smoke-test на panic (важно потому что официальный UniFFI контракт этого не гарантирует).

**Конкретика, которую стоит запомнить**:
- **1 новый Gradle модуль**: `:crypto-ffi/` в корне репо, рядом с `app/` и `core/`.
- **Rust 1.97.0 pinned** через `rust-toolchain.toml` (не в Cargo.toml — это MSRV для библиотек, здесь N/A).
- **UniFFI 0.28+ proc-macro** — inline annotations в `lib.rs`, никаких `.udl` файлов (индустриальный default 2026).
- **Только arm64-v8a** на первом релизе (единственное тестовое устройство — Xiaomi 11T). armv7 добавится через ~2 недели (нужен старый телефон).
- **Никакого CI на GitHub Actions** — прогон вручную на desktop-машине владельца (Windows). Backlog Kanban отслеживает Verification-статус до owner-run.
- **2 функции экспортируется**: `hello(name)` — smoke-test, `panics(msg)` — доказывает что Rust panic конвертируется в Kotlin exception, а не крашит процесс.
- **2 abstractions вводятся**: UniFFI (auto-gen Kotlin binding) + cargo-ndk (drives cross-compile под Android). Обе — two-way door с задокументированным exit ramp'ом (manual JNI + shell script соответственно).
- **1 fitness function**: `verifyUniffiVersions` Gradle task — ловит расхождение версий UniFFI до runtime crash.
- **Constitution Check**: 4 PASS, 4 N/A (Core/System, Configuration, Accessibility — не применимы к build-инфре).

**На что смотреть с осторожностью**:
- **Panic contract** — UniFFI docs его не документируют. Если сломается в 0.29+ — наш `PanicFfiTest` поймает при bump'е. Skill `crypto-ffi-panic-check` дублирует enforcement.
- **NDK path на fresh машине** — `rust-android-setup` skill проверяет; `docs/dev/rust-setup.md` линкует на Android Studio SDK Manager.
- **Windows long-path** — включён на owner-машине, но на другой Windows-машине может быть issue. Документировано в README.
- **Domain ports НЕ здесь** — TASK-123 territory. Если появляется соблазн ввести `CryptoPort` во время работы над этой таской — стоп, это scope creep.
