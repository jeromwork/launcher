---
id: TASK-122
title: F-CRYPTO Rust FFI Foundation (cargo-ndk + UniFFI toolchain)
status: In Progress
assignee: []
created_date: '2026-07-10 16:40'
updated_date: '2026-07-13 06:50'
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
references:
  - specs/task-122-crypto-ffi-foundation/
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
- [x] #1 [hand] Windows dev setup автоматизирован: skill `rust-android-setup` + companion script `scripts/setup-rust-android.ps1`. Idempotent, ставит только недостающее, refuse'ит на не-Windows машине. Владелец запускает раз на новой машине, ~15 мин.
- [ ] #2 [hand] `./gradlew :crypto-ffi:build` собирает `libcrypto_ffi.so` под **arm64-v8a**; structure supports adding armv7/x86_64 через one-line изменение `abiFilters` без rewrite'ов Kotlin/Rust/tests
- [ ] #3 [hand] Kotlin androidTest вызывает Rust `hello("world")` → возвращает `"Hello, world"` → зелёный на **Xiaomi 11T (arm64) через USB** ИЛИ на arm64-эмуляторе Android Studio
- [ ] #4 [hand] Panic smoke-test (`panic_isConvertedToKotlinException`) — зелёный на том же устройстве; Rust `panic!()` конвертируется в Kotlin exception, а не в process abort
- [ ] #5 [hand] README в `crypto-ffi/` содержит инструкции: добавить функцию / пересобрать / обновить UniFFI
- [ ] #6 [hand] Fitness function падает при расхождении uniffi-rs / uniffi-bindgen / runtime versions
- [x] #7 [hand] Skill `crypto-ffi-panic-check` создан в `.claude/skills/` — статически проверяет наличие `panics()` функции и panic-теста, опционально прогоняет тест
<!-- AC:END -->

## Implementation Notes
<!-- SECTION:NOTES:BEGIN -->

**Session 2026-07-13 (branch `task-122-crypto-ffi-foundation`)**: слой setup закрыт — Windows dev-машина настраивается через одну команду. Skill + script готовы. Владелец подтвердил: работаем только на Windows.

**Что ещё НЕ сделано в этом PR** (scope для следующих sessions):
- Rust workspace `crypto-ffi/` с `hello()` функцией.
- Gradle module `crypto-ffi:` с cargo-ndk plugin.
- UniFFI `.udl` + build.rs.
- Kotlin androidTest.
- CI workflow (`.github/workflows/crypto-ffi.yml`).
- Fitness function version lockstep.
- README + `docs/dev/rust-setup.md`.

**Почему setup skill в отдельном PR**: (a) skill сам по себе — reusable инфраструктура, не требует spec-kit; (b) владелец может запустить его на своей машине **до** того, как реализация начнётся, чтобы окружение было готово; (c) уменьшает scope основного TASK-122 PR — implementation отделена от dev-env.

**Next session pickup**: следующая сессия начинает с `Skill mentor` или `speckit-specify TASK-122`, ветка та же (`task-122-crypto-ffi-foundation`), setup уже сделан.

### Session 2026-07-13 (clarify)

Владелец прошёл mentor-clarify по 5 grey zones. Финальные решения (полная rationale в [`specs/task-122-crypto-ffi-foundation/spec.md#clarifications`](../../specs/task-122-crypto-ffi-foundation/spec.md#clarifications)):

- **Q1 UniFFI interface**: proc-macro (`#[uniffi::export]` inline в Rust), НЕ `.udl` файл. Индустриальный default (Matrix Element X, Bitwarden, Mozilla). Two-way door — migration тривиальная.
- **Q2 Testing environment**: локальные прогоны на desktop-ПК владельца (эмулятор Android Studio или Xiaomi 11T через USB), НЕ GitHub Actions CI. Владелец не хочет тратить CI-минуты; ноутбук слабый; реальный device есть. Verification workflow — через backlog status transitions.
- **Q3 Rust version**: `rust-toolchain.toml` pinned на `1.97.0`. Bump = отдельная future task.
- **Q4 Panic across FFI**: явный smoke-test `panic_isConvertedToKotlinException` + функция `panics()` в Rust + skill `crypto-ffi-panic-check`. UniFFI docs НЕ гарантируют panic contract — inferred from source. Element X / Matrix Rust SDK делают то же.
- **Q5 Android ABI matrix**: только **arm64-v8a**. Xiaomi 11T = единственное тестовое устройство. armv7 — отдельная future task (~2 недели, покупка arm32 телефона). x86 / x86_64 не собираем совсем. Structure тестов пишется универсально — добавление ABI позже = one-liner `abiFilters`.

### Status transition post-implementation

PR merge → task-122 status → **`Verification`** (не `Done`) per CLAUDE.md status workflow. Ждёт local emulator smoke + Xiaomi 11T device smoke (AC #2, #3, #4). Владелец приходит за desktop-ПК → агент напоминает («что висит в Verification?») → прогон `./gradlew :crypto-ffi:connectedAndroidTest` → зелёный → transitions to `Done` через повторный `pre-pr-backlog-sync`. Никакого «merged = Done» автомата.

### Sub-tasks created (placeholders, будущая работа)

Пока НЕ создаются как отдельные backlog task-файлы — записаны здесь как памятка для будущих sessions:

1. **Rust version bump procedure + first bump task** — документировать процедуру (test matrix, breaking change check, Cargo.lock regeneration) + создать первый future bump task когда 1.98 stable выйдет и подтвердится industry adoption.
2. **armv7-linux-androideabi ABI addition** (~2 недели) — после покупки владельцем старого arm32 телефона. One-liner в `abiFilters` + прогон тестов на устройстве.
3. **Self-hosted GitHub runner** — только если ручной прогон окажется unreliable (регулярно забывается / затягивается). Сейчас не нужен.

<!-- SECTION:NOTES:END -->
