# Feature Specification: F-CRYPTO Rust FFI Foundation

**Feature Branch**: `task-122-crypto-ffi-foundation`
**Created**: 2026-07-13
**Status**: Draft
**Input**: TASK-122 backlog card — «F-CRYPTO Rust FFI Foundation (cargo-ndk + UniFFI toolchain)»

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Kotlin вызывает Rust-функцию через FFI (Priority: P1)

Разработчик в Kotlin-коде вызывает Rust-функцию `hello("world")` и получает строку `"Hello, world"`. Ничего криптографического — только доказательство что весь toolchain (Rust компилятор → Android `.so` → UniFFI Kotlin binding → JNI загрузчик) работает end-to-end на Android-эмуляторе.

**Why this priority**: без этого end-to-end path невозможно вводить ни openmls (TASK-124), ни snow (TASK-67), ни любую другую Rust-крипто-библиотеку. Foundation блокирует все F-CRYPTO wave feature-tasks.

**Independent Test**: `./gradlew :crypto-ffi:connectedAndroidTest` на эмуляторе `pixel_5_api_34` — тест `HelloFfiTest.hello_returnsGreeting()` зелёный.

**Acceptance Scenarios**:

1. **Given** свежий clone репо на dev-машине с настроенным Rust toolchain, **When** разработчик запускает `./gradlew :crypto-ffi:build`, **Then** собираются `libcrypto_ffi.so` под 4 Android ABI (armv7, aarch64, x86, x86_64) и Kotlin binding-файл.
2. **Given** APK установлен на эмуляторе pixel_5_api_34, **When** запускается androidTest `hello("world")`, **Then** возвращается строка `"Hello, world"`, тест зелёный.
3. **Given** разработчик добавляет новую Rust-функцию `add(a: i32, b: i32) -> i32` следуя README, **When** пересобирает и вызывает из Kotlin `add(2, 3)`, **Then** получает `5` без ручного JNI-кода.

---

## Clarifications

### Session 2026-07-13

- **Q1 (UniFFI interface method)**: proc-macro (`#[uniffi::export]` inline в Rust), не `.udl` файл.
  - **Rationale**: индустриальный default для новых проектов (Matrix Element X — крупнейший UniFFI consumer, contributed proc-macro upstream; Bitwarden SDK; Mozilla application-services). Skip `build.rs`, single source of truth, easier refactoring. Migration .udl ↔ proc-macro — two-way door (~days per crate).
  - **Impact on Requirements**: FR-001 уточняется — UniFFI interface = proc-macro attributes, `.udl` файла нет.

- **Q2 (Testing environment)**: локальные прогоны на мощном desktop-ПК владельца через Android Studio эмулятор либо реальный Xiaomi 11T (arm64), НЕ GitHub Actions.
  - **Rationale**: (a) владелец не хочет тратить GitHub CI-минуты; (b) ноутбук слишком слабый для эмулятора; (c) real device уже есть. Механика: PR merge → task-122 статус `Verification` (per CLAUDE.md status workflow), не `Done`; backlog note «pending: local emulator smoke + Xiaomi 11T device smoke»; владелец приходит за desktop → агент напоминает → прогон → зелёный → `Done`.
  - **Impact on Requirements**: FR-007 переписывается — вместо «CI pipeline на GitHub Actions» — «local run на desktop-машине владельца через `./gradlew :crypto-ffi:connectedAndroidTest`». Verification workflow — через backlog status transitions.
  - **Deferred to sub-task**: возможная миграция на self-hosted GitHub runner если ручной прогон окажется unreliable (не сейчас).

- **Q3 (Rust version)**: `rust-toolchain.toml` pinned на `1.97.0` (текущая stable на dev-машине владельца).
  - **Rationale**: индустриальный default для application projects (Signal, Matrix, Element X, Mullvad, Firefox, Bitwarden — все pinned). Reproducible builds. Bump = отдельная backlog-задача с прогоном всех тестов, как у лидеров рынка.
  - **Impact on Requirements**: NFR-001 уточняется — версия MUST быть в `rust-toolchain.toml` файле (не в Cargo.toml `rust-version` — это MSRV для библиотек-consumer'ов, у нас пока N/A).
  - **Deferred to sub-task**: bump procedure documented + first future bump task.

- **Q4 (Panic across FFI)**: явный smoke-test что Rust `panic!()` конвертируется в Kotlin exception, а не в process abort. Плюс skill-страховка.
  - **Rationale**: официальные UniFFI docs НЕ документируют panic contract (behavior inferred from source и issue #485). Значит контракт может незаметно сломаться в UniFFI 0.29+ или при переходе на manual JNI (exit ramp). Element X и Matrix Rust SDK явно шипят panic smoke-tests именно поэтому. Real incidents: zcash#4652, flutter_rust_bridge через FFI.
  - **Impact on Requirements**: новый FR-011 добавляется — «`crypto-ffi/src/lib.rs` MUST export function `panics(msg: String) -> String` that always panics; androidTest MUST include `panic_isConvertedToKotlinException` verifying Kotlin sees exception, not process abort». Плюс skill `crypto-ffi-panic-check` создаётся.

- **Q5 (Android ABI matrix)**: только **arm64-v8a** (`aarch64-linux-android`) на данном этапе. armv7 добавится отдельной sub-task через ~2 недели (после покупки старого arm32-телефона). x86 / x86_64 не собираем вообще.
  - **Rationale**: единственное тестовое устройство сейчас — Xiaomi 11T (arm64). Rule 4 MVA — не собирать под ABI которые сейчас нельзя проверить. Структура тестов пишется универсально — добавление armv7 позже = one-liner в `abiFilters` Gradle, никакой переработки Kotlin/Rust кода не требуется.
  - **Impact on Requirements**: FR-003 переписывается — «Gradle module MUST build `.so` для arm64-v8a на первом релизе; structure MUST allow adding additional ABIs (armv7-linux-androideabi, x86_64-linux-android) via one-line `abiFilters` change without code changes». SC-001, SC-002 переписываются под arm64-only.
  - **Trade-off surfaced**: локальное тестирование в x86_64-эмуляторе Android Studio невозможно (нет x86_64 `.so`). Владелец принимает это — тестирование через Xiaomi 11T USB + опционально arm64-эмулятор (медленный на Intel-ПК).
  - **Deferred to sub-task**: armv7 addition (~2 недели, покупка arm32 телефона).

---

## Sequences

**Skipped intentionally** for this spec.

**Rationale**: TASK-122 is pure build infrastructure — no user-visible runtime behavior, no external services, no multi-step user flows. The only "sequence" is `developer runs ./gradlew build → cargo compiles Rust → UniFFI generates Kotlin binding → androidTest calls hello("world") → passes`. This is a build pipeline, not application behavior — sequence diagrams would document tooling, not domain flow. `speckit-scenarios` skill's own "When NOT to invoke" allows skip for trivial specs.

Sequences will be authored for downstream F-CRYPTO tasks that DO have user-visible runtime:
- TASK-124 (openmls integration) — MLS group creation / message send-receive flows.
- TASK-67 (snow pairing) — Noise handshake sequence between devices.
- TASK-125 (SQLCipher) — encrypted persistence read/write flows.

---

### User Story 2 — Manual local verification workflow (Priority: P2) — replaces original "CI automation"

**Original scope** (initial draft, pre-Q2): «GitHub Actions автоматически прогоняет pipeline на каждом PR». **Отменено** в Clarifications Q2 — владелец не хочет тратить GitHub CI-минуты, ноутбук слишком слабый для эмулятора, real device (Xiaomi 11T) уже есть.

**Actual scope** (post-Q2): manual verification workflow через backlog status transitions.

Разработчик открывает PR, задевающий `crypto-ffi/`. **Никакой GitHub Actions не запускается.** Вместо этого:
1. PR review + merge происходит по стандартной процедуре.
2. Backlog task-122 переходит в статус `Verification` (не `Done`) с note «pending: local emulator + Xiaomi 11T smoke».
3. Владелец за desktop-ПК запускает `./gradlew :crypto-ffi:connectedAndroidTest` — либо на arm64-эмуляторе Android Studio, либо на Xiaomi 11T через USB.
4. Оба теста (`hello` + `panic_isConvertedToKotlinException`) зелёные → task-122 переходит в `Done`.

**Why this priority (post-Q2)**: без manual verification foundation превращается в «работает на моей машине». Разница с Q2 original: verification делаем **вручную владельцем**, а не автоматически на GitHub.

**Independent Test**: изменить один символ в `crypto-ffi/src/lib.rs` (например, `"Hello, "` → `"Hi, "`) — при следующем manual verification прогоне тест должен упасть с assertion mismatch. Это demonstration что verification workflow работает.

**Acceptance Scenarios**:

1. **Given** PR merged, task-122 в статусе `Verification`, **When** владелец за desktop-ПК запускает `./gradlew :crypto-ffi:connectedAndroidTest` с Xiaomi 11T подключённым через USB, **Then** оба теста зелёные, task-122 переходит в `Done`.
2. **Given** PR ломает Rust-функцию (например, меняет сигнатуру `hello`), **When** владелец прогоняет verification, **Then** сборка или тест падает — task-122 остаётся в `Verification`, следующий PR исправляет.
3. **Given** device временно недоступен, **When** приоритет верификации подождать до появления device, **Then** task-122 остаётся в `Verification` (не `Done`) без давления — это soft gate, не автоматический CI.

---

### User Story 3 — Fitness function ловит расхождение версий UniFFI (Priority: P3)

Скрипт CI (или Gradle task) проверяет что версии `uniffi` crate в `Cargo.toml`, `uniffi_bindgen` crate (build-dep), и runtime Kotlin-библиотеки UniFFI (Maven-артефакт) — одинаковые. Если разошлись — build падает с понятным сообщением. Предотвращает hard-to-debug ABI-несовместимости между generated Kotlin binding и Rust `.so`.

**Why this priority**: UniFFI **требует** version lockstep — иначе runtime crash при первом FFI-вызове. Ловить эту ошибку через CI дёшево, ловить через runtime crash в prod — дорого.

**Independent Test**: вручную поменять версию `uniffi` в `Cargo.toml` (например с `0.28.0` на `0.27.0`), запустить fitness function локально — должна упасть с сообщением «uniffi version mismatch: Cargo.toml=0.27.0, Kotlin runtime=0.28.0».

**Acceptance Scenarios**:

1. **Given** все 3 версии UniFFI совпадают, **When** запускается fitness function (`./gradlew :crypto-ffi:verifyUniffiVersions` или CI-step), **Then** проходит зелёным.
2. **Given** версия одной из 3-х расходится, **When** запускается fitness function, **Then** падает с указанием какие именно версии не совпадают и где они объявлены.

---

### Edge Cases

- **Что если NDK не установлен на dev-машине?** — cargo-ndk выдаст ошибку `ANDROID_NDK_HOME not set`. README ссылается на skill `rust-android-setup` и `docs/dev/rust-setup.md` — там инструкция.
- **Что если разработчик на macOS/Linux (не Windows)?** — Rust cross-compile под Android работает одинаково на всех host OS; отличаются только setup-инструкции. `rust-android-setup` skill Windows-only; для других OS README указывает стандартные rustup-шаги.
- **Что если UniFFI major bump ломает API?** — версия pinned в `Cargo.toml`. Bump = отдельный PR с обновлением Kotlin runtime + regen bindings + прогоном всех тестов. Fitness function поймает mismatch на этапе PR.
- **Что если cargo-ndk ломается на новом Android API level?** — exit ramp (см. TASK-122 description): переход на manual JNI через `cbindgen`, 2-3 недели. Документируется в `docs/architecture/crypto.md` frontmatter.
- **Что если эмулятор pixel_5_api_34 недоступен в CI (например, kvm не работает)?** — CI использует Robolectric fallback для JVM-only проверки (compile check + UniFFI binding syntax check), полноценный androidTest откладывается до self-hosted runner. Inline TODO(ci-emulator) в workflow.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Repo MUST contain `crypto-ffi/` Rust workspace с `Cargo.toml`, `src/lib.rs`, и UniFFI interface через **proc-macro attributes** (`#[uniffi::export]` / `#[uniffi::setup_scaffolding!]`) inline в Rust source. `.udl` файл НЕ используется (per Clarifications Q1).
- **FR-002**: Rust code MUST экспортировать функцию `hello(name: String) -> String` возвращающую `format!("Hello, {}", name)`.
- **FR-003**: Gradle module `:crypto-ffi` MUST собирать `libcrypto_ffi.so` под **arm64-v8a** (`aarch64-linux-android`) на первом релизе (per Clarifications Q5). Structure MUST allow adding additional ABIs (`armv7-linux-androideabi`, `x86_64-linux-android`, `i686-linux-android`) через one-line изменение в Gradle `abiFilters` — никаких изменений Kotlin/Rust кода или структуры тестов не требуется.
- **FR-004**: Gradle build MUST помещать `.so` файлы в `jniLibs/<abi>/` в правильных подпапках per Android convention.
- **FR-005**: Gradle build MUST генерировать Kotlin binding (через UniFFI bindgen) в `crypto-ffi/build/generated/kotlin/` и делать его доступным для androidTest.
- **FR-006**: Kotlin androidTest MUST вызывать Rust `hello("world")` и утверждать что результат равен `"Hello, world"`.
- **FR-007**: Verification MUST выполняться локально на desktop-машине владельца через `./gradlew :crypto-ffi:connectedAndroidTest` (per Clarifications Q2) — либо на arm64-эмуляторе Android Studio, либо на подключённом по USB Xiaomi 11T (arm64). GitHub Actions CI для этой таски НЕ настраивается. Verification-workflow привязан к backlog status transition (PR merged → `Verification` → owner runs tests → `Done`).
- **FR-008**: Fitness function (Gradle task) MUST падать если версии UniFFI crate (Cargo.toml), uniffi_bindgen build-dep (Cargo.toml), и runtime Kotlin-библиотеки UniFFI (Maven artifact в `crypto-ffi/build.gradle.kts`) не совпадают.
- **FR-009**: `crypto-ffi/README.md` MUST содержать инструкции: (a) как добавить новую Rust-функцию и exposed через UniFFI proc-macro, (b) как локально пересобрать и запустить тест на устройстве/эмуляторе, (c) как безопасно обновить UniFFI major/minor version.
- **FR-010**: `docs/dev/rust-setup.md` MUST документировать onboarding нового разработчика: ссылка на skill `rust-android-setup` (Windows), эквивалентные шаги для macOS/Linux.
- **FR-011**: `crypto-ffi/src/lib.rs` MUST export функцию `panics(msg: String) -> String` которая всегда вызывает `panic!("{}", msg)` (per Clarifications Q4). Kotlin androidTest MUST содержать тест `panic_isConvertedToKotlinException` который вызывает `panics("test")` и утверждает что Kotlin получает exception, а не process abort. Skill `crypto-ffi-panic-check` MUST существовать в `.claude/skills/` и вызываться при работе над PR, затрагивающим `crypto-ffi/**`.

### Non-Functional Requirements

- **NFR-001**: Rust toolchain версия MUST быть pinned в `rust-toolchain.toml` (per Clarifications Q3). Первичный выбор — **1.97.0** (текущая stable на dev-машине владельца). Bump = отдельная backlog-задача. Cargo.toml `rust-version` (MSRV) НЕ используется (это concept для library crates-consumer'ов).
- **NFR-002**: `Cargo.lock` MUST быть committed в git (стандарт для binary crates / приложений).
- **NFR-003**: Generated Kotlin bindings MUST быть в `.gitignore` (генерируются at build time, source of truth — proc-macro annotations в `lib.rs`).
- **NFR-004**: `libcrypto_ffi.so` MUST быть в `.gitignore` (build artefact).

### Key Entities

- **`crypto-ffi/Cargo.toml`**: root workspace manifest, MSRV, deps (`uniffi`, `uniffi_bindgen`).
- **`crypto-ffi/src/lib.rs`**: Rust source с exported function `hello`.
- **UniFFI interface**: proc-macro attributes (`#[uniffi::export]`, `#[derive(uniffi::Object)]`) inline в `lib.rs` (per Clarifications Q1). `.udl` файл не используется.
- **`crypto-ffi/build.gradle.kts`**: Gradle module, cargo-ndk plugin config, UniFFI kotlin gen task.
- **`crypto-ffi/src/bin/uniffi-bindgen.rs`**: entry-point для UniFFI bindgen CLI, живёт внутри крейта per UniFFI 0.28+ convention.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: `./gradlew :crypto-ffi:build` на dev-машине владельца (Windows) собирает `libcrypto_ffi.so` под arm64-v8a без ручных шагов помимо one-off Rust setup.
- **SC-002 [backlog]**: Kotlin androidTest вызывает Rust `hello("world")` → `"Hello, world"` → зелёный на реальном Xiaomi 11T (arm64) через USB ИЛИ на arm64-эмуляторе Android Studio.
- **SC-003 [backlog]**: Panic smoke-test `panic_isConvertedToKotlinException` зелёный на том же устройстве — Rust `panic!()` конвертируется в Kotlin exception, процесс не крашится.
- **SC-004 [backlog]**: `crypto-ffi/README.md` позволяет разработчику, не знающему UniFFI, добавить новую функцию за ≤ 30 минут (walk-through инструкция).
- **SC-005**: Fitness function ловит искусственно введённое расхождение UniFFI версий с понятным сообщением.
- **SC-006**: Первая полная сборка на чистой машине занимает ≤ 10 минут (после one-off Rust setup, без учёта download времени).
- **SC-007**: Инкрементальная сборка (после изменения одной строки в `lib.rs`) занимает ≤ 60 секунд.
- **SC-008 [backlog]**: Verification workflow работает per CLAUDE.md: PR merge → task-122 переходит в `Verification` с note «pending: local emulator + Xiaomi 11T smoke»; после успешных прогонов на устройстве владельцем — переход в `Done`. Никакого «merged = Done» автомата.

## Assumptions

- Разработчики используют Windows dev-машину (owner setup automated через `rust-android-setup` skill). macOS/Linux — best-effort, инструкции есть, но CI и primary flow — на Windows.
- Android NDK 30.0.15729638 или новее (это то что стоит на dev-машине). Совместимость со старыми NDK не гарантирована.
- Rust stable channel, latest на момент создания (~1.97). MSRV bump — отдельное решение через PR.
- UniFFI 0.28+ используется (нет standalone CLI, bindgen inside consuming crate).
- CI runner имеет ≥ 4 GB RAM и способен либо запускать Android эмулятор через KVM, либо fallback на Robolectric.
- iOS cross-compile deferred до TASK-26 (V-1). Здесь только Android.

## Local Test Path *(mandatory)*

- **Emulator / device**: `pixel_5_api_34` через skill `android-emulator` (для androidTest). Для JVM-only smoke — Robolectric.
- **Fake adapters used**: N/A — эта таска build-infrastructure, доменных портов нет (они в TASK-123).
- **Fixtures / seed data**: N/A — тест hard-coded string `"world"`, expected `"Hello, world"`.
- **Verification command**:
  - Full: `./gradlew :crypto-ffi:build && ./gradlew :crypto-ffi:connectedAndroidTest`
  - Fitness: `./gradlew :crypto-ffi:verifyUniffiVersions`
  - CI-equivalent local run: `./gradlew :crypto-ffi:check`
- **Cannot-test-locally gaps**: N/A — весь pipeline тестируется на локальном эмуляторе. CI = replay того же на GitHub Actions runner.

## AI Affordance *(mandatory)*

**No AI affordance — internal build infrastructure only.**

Эта таска — foundation для будущих Rust-крипто-библиотек. Никаких domain-verbs, никакого user-facing behavior, никаких данных для AI-инспекции. Функция `hello()` — smoke test, не exposed capability. Future crypto features (TASK-124 openmls, TASK-67 pairing) введут доменные ports через TASK-123 CryptoPort — там будет AI Affordance rethink.

## OEM Matrix *(mandatory if feature touches device behavior)*

**Not applicable — build infrastructure only.**

Rust FFI code через JNI работает одинаково на всех Android OEMs (Google, Samsung, Xiaomi, Huawei, etc.) — это standard Android JNI mechanism, не задействует permission surfaces / background restrictions / launcher role / notifications / etc. OEM divergence может появиться у будущих feature-tasks (TASK-124 mls storage, TASK-67 pairing на bluetooth) — но не здесь.

## Constitution Gates *(reference — full check в plan.md)*

- **Rule 1 (domain isolation)**: N/A на этой стадии — domain ports в TASK-123. Здесь только infrastructure module `:crypto-ffi`.
- **Rule 2 (ACL для external SDK)**: UniFFI wraps Rust в Kotlin binding — это technically thin generated wrapper. Domain layer не увидит UniFFI types напрямую, потому что domain-ports (TASK-123) выступят ACL над generated binding.
- **Rule 3 (one-way door)**: UniFFI = **two-way door**. Exit ramp: manual JNI через cbindgen, 2-3 недели. Zafiksirovan в `docs/architecture/crypto.md` frontmatter.
- **Rule 4 (MVA)**: минимум абстракций — trivial `hello()` функция, никаких «future-proof» generic wrappers. Domain ports — separate task.
- **Rule 5 (wire format)**: N/A — FFI intra-process, не wire.
- **Rule 6 (mock-first)**: N/A — здесь нет domain-side ports для mocking.
- **Rule 7 (fitness functions)**: FR-008 — version lockstep check.
- **Rule 8 (server migration)**: N/A — pure client foundation.

## Dependencies

- **Blocks**: TASK-123 (CryptoPort/GroupPort/KeyPackagePort), TASK-124 (openmls integration), TASK-67 (snow crate для pairing).
- **Depends on**: none. Может стартовать немедленно.
- **Parallel with**: TASK-123 (domain ports можно писать без реального Rust binding — они используют in-memory fake adapter).

## Out of Scope

- openmls, snow, любая крипто-логика — TASK-124.
- CryptoPort / GroupPort / KeyPackagePort доменные порты — TASK-123.
- SQLCipher persistence — TASK-125.
- iOS cross-compile — TASK-26 (V-1 iOS Admin Preset).
- Rotation / escrow — deferred stubs в TASK-123 area.
- Post-quantum crypto — future.
