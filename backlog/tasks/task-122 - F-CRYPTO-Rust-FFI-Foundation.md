---
id: TASK-122
title: 'F-CRYPTO Rust FFI Foundation (cargo-ndk + UniFFI toolchain)'
status: Draft
assignee: []
created_date: '2026-07-10 16:40'
updated_date: '2026-07-10 16:40'
labels:
  - phase-2
  - F-feature
  - crypto
  - rust-ffi
  - foundation
  - toolchain
milestone: m-1
dependencies: []
priority: high
ordinal: 122000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **Контекст создания (2026-07-10).** TASK-58 closure note назначила «MLS library integration → TASK-2», но TASK-2 закрыт со scope'ом только на libsodium primitives. Реальная openmls integration никогда не выполнялась — грепом в `app/`, `core/`: нет ни одного `Cargo.toml`, нет `libopenmls_ffi.so`, нет `CryptoPort` / `GroupPort` / `KeyPackagePort` в Kotlin. Эта таска (вместе с TASK-123/124/125) заполняет гэп, разбитый на 4 отдельных concerns per «one task = one PR».

## Что это простыми словами

**Первый шаг** к использованию Rust-крипто-библиотек (openmls, snow) на Android/iOS. Устанавливаем **toolchain** — программы, которые превращают Rust-код в `.so`/`.dylib` файлы, которые Kotlin умеет вызывать. **Никакой крипты в этой задаче нет**, только «hello from Rust» — доказательство что build system работает.

**Что происходит по шагам:**
1. Создаётся корневой Rust workspace `crypto-ffi/` в репо.
2. Настраивается `cargo-ndk` — плагин cargo, который умеет собирать Rust-код под 4 Android ABI (`armv7`, `aarch64`, `x86`, `x86_64`).
3. Настраивается UniFFI — Mozilla-инструмент, который из Rust-функций автоматически генерирует Kotlin-обёртку (безопасный JNI без ручного писания).
4. Пишется одна trivial Rust-функция `hello(name: String) -> String` — для доказательства работы toolchain'а.
5. Gradle module `crypto-ffi:` собирает `.so`, кладёт в `jniLibs/`, генерирует Kotlin binding.
6. Kotlin-тест вызывает `hello("world")` → возвращает `"Hello, world"` → тест зелёный на эмуляторе.
7. CI прогоняет весь этот pipeline на каждом PR.

**Что НЕ входит в scope:**
- openmls, snow, любая крипто-библиотека — это TASK-124.
- Доменные порты (CryptoPort и т.п.) — это TASK-123.
- SQLCipher — это TASK-125.
- iOS build — deferred в V-1 (TASK-26), пока только Android.

## Зачем

Одноразовые прибитые расходы: настроить cargo-ndk, UniFFI, cross-compile под Android ABI, CI. Если запихать всё это внутрь TASK-124 (openmls integration) — PR будет монстр который сложно review, и любая проблема с toolchain'ом смешивается с проблемами MLS-логики. Отдельная foundation-task = чистый reviewable PR + разблокировка любой будущей Rust-integration (не только openmls: snow для pairing handshake, будущий post-quantum crypto).

**Alternatives rejected**:
- Manual JNI: TASK-58 research (Session log 2026-07-07) confirmed UniFFI industry standard (Wire, Element X, Firefox mobile — все на UniFFI). Manual JNI = +2-3 недели скрытого технического долга.
- Kotlin/Native Rust binding (KMP): pre-alpha, не production. Отклонено в TASK-58.

## Что входит технически (для AI-агента)

- **Rust workspace** `crypto-ffi/`:
  - `Cargo.toml` — workspace root, pinned Rust MSRV.
  - `crypto-ffi/src/lib.rs` — trivial `hello()` функция + UniFFI scaffold.
  - `crypto-ffi/crypto_ffi.udl` — UniFFI Interface Definition файл.
  - `crypto-ffi/build.rs` — uniffi-bindgen build script.
- **Gradle module** `crypto-ffi:`:
  - `build.gradle.kts` — конфигурация cargo-ndk plugin.
  - Task `cargoBuild` — cross-compile под `armv7-linux-androideabi`, `aarch64-linux-android`, `i686-linux-android`, `x86_64-linux-android`.
  - Task `uniffiKotlinGen` — генерация Kotlin binding.
  - `libcrypto_ffi.so` копируется в `jniLibs/<abi>/`.
- **Kotlin bindings** попадают в `crypto-ffi/build/generated/kotlin/`.
- **Kotlin test** в `crypto-ffi/src/commonTest` или `androidTest`: `assertEquals("Hello, world", hello("world"))`.
- **CI**:
  - Rust setup step (`actions-rust-lang/setup-rust-toolchain` + cargo-ndk install).
  - Build step запускает `./gradlew :crypto-ffi:build`.
  - Test step запускает Kotlin test на эмуляторе (skill `android-emulator`).
- **Documentation**:
  - README в `crypto-ffi/` — как локально пересобрать, как добавить новую Rust-функцию, как обновить UniFFI binding.
  - Inline TODO(server-roadmap) если релевантно: N/A — pure client-side foundation.
- **Exit ramp** (rule 3): если UniFFI сломается / OSS-проект умрёт → migration path на manual JNI (2-3 недели, известная процедура). Fixed via architecture decision в TASK-104.
- **Fitness function** (rule 7): CI job проверяет что `uniffi-rs` version + `uniffi-bindgen` CLI + generated Kotlin runtime — same version (lockstep pin per crypto.md frontmatter follow-up-flag).

## Состояние

**Draft.** Готова к `/speckit.specify`. Deps пустые — стартует немедленно. Параллельна TASK-123.

---

## Готовый промт для `/speckit.specify`

```
Реализуй F-CRYPTO Rust FFI Foundation.

ЧТО СТРОИМ:
Rust workspace `crypto-ffi/` в корне репо + Gradle module `crypto-ffi:` + cargo-ndk cross-compile под 4 Android ABI + UniFFI-generated Kotlin binding + CI pipeline. Deliverable: Kotlin-тест вызывает Rust-функцию `hello(name)` → зелёный на эмуляторе.

ЗАЧЕМ:
Foundation для всех будущих Rust-integration (openmls в TASK-124, snow crate для pairing handshake в TASK-67, future crypto). Отделяет build-system сложность от крипто-логики (rule 3 two-way door: build tooling инкапсулировано, migration на manual JNI = 2-3 недели если UniFFI OSS-проект умрёт).

SCOPE ВКЛЮЧАЕТ:
- Rust workspace `crypto-ffi/Cargo.toml` + trivial `hello()` функция.
- `crypto_ffi.udl` UniFFI interface + `build.rs`.
- Gradle module `crypto-ffi:` с cargo-ndk plugin, cross-compile под armv7 / aarch64 / x86 / x86_64.
- UniFFI Kotlin bindings generation.
- `libcrypto_ffi.so` в `jniLibs/<abi>/`.
- Kotlin androidTest: `assertEquals("Hello, world", hello("world"))`.
- CI: Rust toolchain setup + build + emulator smoke test.
- README в `crypto-ffi/` с инструкцией "как добавить новую функцию".
- Fitness function: version lockstep check (uniffi-rs + uniffi-bindgen + runtime).

SCOPE НЕ ВКЛЮЧАЕТ:
- openmls, snow, любая крипто-логика — TASK-124.
- CryptoPort / GroupPort / KeyPackagePort доменные порты — TASK-123.
- SQLCipher persistence — TASK-125.
- iOS cross-compile — deferred в TASK-26 (V-1 iOS Admin Preset).

DEPENDENCIES: none.

ACCEPTANCE CRITERIA:
- Локально: `./gradlew :crypto-ffi:build` собирает `.so` под 4 Android ABI.
- Локально: Kotlin androidTest вызывает `hello("world")` на эмуляторе pixel_5_api_34 → зелёный.
- CI: PR запускает full pipeline (Rust setup + cross-compile + emulator test) → зелёный.
- README описывает: (a) как добавить новую Rust-функцию, (b) как пересобрать локально, (c) как обновить UniFFI version.
- Version lockstep fitness function падает если uniffi-rs / uniffi-bindgen / runtime versions расходятся.

LOCAL TEST PATH:
- `./gradlew :crypto-ffi:build`.
- Emulator: skill `android-emulator` → pixel_5_api_34 → `./gradlew :crypto-ffi:connectedAndroidTest`.
- CI: full pipeline на GitHub Actions.

CONSTITUTION GATES:
- Rule 1 (domain isolation): в этой таске domain-side ports НЕТ — они в TASK-123. Здесь только infrastructure.
- Rule 2 (ACL): UniFFI-generated Kotlin binding — это generated code, не хранится в git; git видит только `crypto_ffi.udl` (наш source of truth).
- Rule 3 (one-way door): использование UniFFI — two-way door (exit ramp: manual JNI, 2-3 недели). Documented в docs/architecture/crypto.md frontmatter.
- Rule 7 (fitness functions): version lockstep CI check.
- Rule 8 (server migration): N/A — pure client foundation.

EFFORT: ~1 week (32-40 часов).
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] `./gradlew :crypto-ffi:build` собирает `.so` под 4 Android ABI (armv7, aarch64, x86, x86_64)
- [ ] #2 [hand] Kotlin androidTest вызывает Rust `hello("world")` → возвращает `"Hello, world"` → зелёный на pixel_5_api_34
- [ ] #3 [hand] CI pipeline (Rust setup + cross-compile + emulator smoke) зелёный на PR
- [ ] #4 [hand] README в `crypto-ffi/` содержит инструкции: добавить функцию / пересобрать / обновить UniFFI
- [ ] #5 [hand] Fitness function падает при расхождении uniffi-rs / uniffi-bindgen / runtime versions
<!-- AC:END -->
