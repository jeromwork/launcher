# Tasks: ECS Tags Foundation + HomeScreen Query Rewire

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Backlog task**: [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md)

---

## Phases Overview

| Phase | Description | Tasks |
|-------|-----------|-------|
| **0. Foundation** | Gradle/DI setup, no behaviour | T127-001 — T127-002 |
| **1. Domain types** | Pure-Kotlin enums, models, ports | T127-003 — T127-008 |
| **2. Wire format** | Serializers, contracts, tests | T127-009 — T127-010 (T127-011..T127-013 removed) |
| **3. Adapters** | ProfileBackedFlowRepository + tests | T127-014 — T127-016 |
| **4. DI wiring** | Rebind FlowRepository in both flavors | T127-017 — T127-018 |
| **5. Localization** | strings_wizard.xml keys | T127-019 |
| **6. Integration** | Extend HomeComponentLoadingStateTest | T127-020 |
| **7. Fitness** | Reflection walk, benchmarks | T127-021 — T127-023 |
| **8. Cleanup & docs** | doc-comments, server-roadmap, smoke | T127-024 — T127-027 |

**Total tasks**: 23 active (4 removed per Clarification Q6: no migration writer) | **Parallel-safe tasks**: 14 marked `[P]`

---

## Phase 0 — Foundation

### T127-001 — Verify ProfileStore contract for null emission

**Trace**: Plan §Data flow, FR-006, Assumption: "`ProfileStore.observe()` returns `Flow<Profile?>` with nulls on cold start".

**Acceptance**:
- Read TASK-126 code: confirm `ProfileStore.observe()` emits `null` on cold start.
- If contract differs from assumption, document deviation.
- Output: brief note in PR description if change needed, else green.

**Dependencies**: none | **[P]**

---

### T127-002 — Create core/preset/query/ directory + ProfileQuery.kt skeleton

**Trace**: Plan §Module map, FR-005.

**Acceptance**:
- Directory exists: `core/src/commonMain/kotlin/com/launcher/preset/query/`.
- File `ProfileQuery.kt` exists, empty or with stubs.
- Compiles.

**Dependencies**: none | **[P]**

---

## Phase 1 — Domain types

### T127-003 — Add Tag enum to Enums.kt

**Trace**: Plan §Data model, FR-001.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt` modified.
- `Tag` enum with **10 values**: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar`.
- Compiles without Android imports.
- `checklist-domain-isolation` passes on this enum (verified by running checklist in tasks-phase).

**Dependencies**: none | **[P]**

---

### T127-004 — Add tags field to Component sealed hierarchy

**Trace**: Plan §Data model, FR-002.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt` modified.
- Abstract `val tags: Set<Tag>` added to `Component` sealed class.
- All existing subtypes (AppTile, Sos, Toolbar, FontSize, LauncherRole, Theme) have default values per data-model.md.
- No syntax errors.
- `kotlinx.serialization @Serializable` decorator updated to include `tags` field (if it was present).

**Dependencies**: T127-003 | **[P]**

---

### T127-005 — Add pool.json optional tags override field (ComponentDeclaration)

**Trace**: Plan §Data model, FR-003.

**Acceptance**:
- `ComponentDeclaration` data class (location TBD, likely in preset-model or pool-related file) adds optional field `tags: List<String>? = null`.
- Pool v2 fixtures (if any exist in bundled assets) deserialize without breaking on this field (backward-compat).
- Doc-comment explains pool.json override mechanism vs Component default.

**Dependencies**: T127-004 | **[P]**

---

### T127-006 — Add Profile.query extensions in ProfileQuery.kt

**Trace**: Plan §Data model, FR-005.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` contains all **7 extension functions** (identical to data-model.md § Query API):
  - `query(predicate)`
  - `byTag(tag)`
  - `byAllTags(tags)`
  - `byAnyTag(tags)`
  - `byNotTag(tag)` — canonical ECS `Without<T>` equivalent
  - `homeScreenTiles()`
  - `toolbar()` — implemented as `byTag(Tag.Toolbar).firstOrNull()`, **no `is Toolbar` type check**
- No Android imports.
- Compiles.
- No tests yet (unit tests in separate Phase-2 task).

**Dependencies**: T127-003, T127-004 | **[P]**

---

### T127-007 — [REMOVED] ProfileMigrationV2toV3

**Removed 2026-07-16 per Clarification Q6**: MVP не релизнут, нет релизнутых Profile файлов, миграция не пишется. Constructor-defaults на `Component` subtypes покрывают отсутствие `tags` в JSON (единственный источник истины). Первый migration writer появится post-release при первом breaking change.

---

### T127-008 — Update Preset.kt + Component.kt with doc-comments linking preset-model.md

**Trace**: Plan §Data model, FR-009.

**Acceptance**:
- `Preset.kt` top-level KDoc includes: "See `docs/architecture/preset-model.md` for two-dimensions model overview (lifecycle vs semantic tagging)."
- `Component.kt` top-level KDoc includes same reference.
- No link-checking needed at this stage.

**Dependencies**: T127-003, T127-004 | **[P]**

---

## Phase 2 — Wire format + Contracts

### T127-009 — Write ProfileWireFormatV1ContractTest (roundtrip)

**Trace**: Plan §Wire formats, FR-004, contracts/profile-v1.md.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/serialization/ProfileWireFormatV1ContractTest.kt`.
- Test reads `core/src/commonTest/resources/fixtures/profile-v1-sample.json`.
- Fixture contains v1 Profile with `schemaVersion=1`, multiple Components with `tags` fields.
- Deserialize → serialize → deserialize → assert byte-equal (roundtrip guarantee).
- **Bonus test case**: deserialize JSON where Component omits `"tags"` field → assert result equals Component с constructor-default tags (verifies kotlinx.serialization defaults kick in).
- Test passes (green).

**Dependencies**: T127-004, T127-006 | **[P]**

---

### T127-010 — Write profile-v1-sample.json fixture

**Trace**: Plan §Wire formats, contracts/profile-v1.md.

**Acceptance**:
- File: `core/src/commonTest/resources/fixtures/profile-v1-sample.json`.
- Contains valid v1 Profile JSON with `schemaVersion=1`, at least 3 Components (AppTile, Sos, Toolbar), each with `"tags"` array.
- Matches example in contracts/profile-v1.md.
- No parse errors when read by ProfileSerializer.

**Dependencies**: T127-004 | **[P]**

---

### T127-011 — [REMOVED] ProfileMigrationV2toV3RoundtripTest

**Removed 2026-07-16 per Clarification Q6**: нет migration writer, нечего тестировать. Constructor-defaults покрыты через bonus test case в T127-009 (пропущенный `tags` field → constructor-default подставлен).

---

### T127-012 — [REMOVED] ProfileMigrationV2toV3BackwardCompatTest

**Removed 2026-07-16 per Clarification Q6**: нет migration writer, нет backward-compat test. Единственный источник истины — constructor-defaults, проверяются через `ComponentTagsFitnessTest` (T127-021).

---

### T127-013 — [REMOVED] Implement ProfileMigrationV2toV3 hardcoded mapping

**Removed 2026-07-16 per Clarification Q6**: нет migration writer вообще. Constructor-defaults на Component subtypes (T127-004) — единственный источник истины для tags-defaults.

---

## Phase 3 — Adapters

### T127-014 — Implement ProfileBackedFlowRepository

**Trace**: Plan §Adapters, FR-006.

**Acceptance**:
- File: `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt`.
- Class implements `FlowRepository` port.
- `observeFlows()`: reads `profileStore.observe().filterNotNull()`, applies `profile.homeScreenTiles()`, maps to `FlowDescriptor` list.
- `observeToolbar()`: reads `profileStore.observe().filterNotNull()`, applies `profile.toolbar()`, maps to `ToolbarDescriptor?`.
- TODO placeholders for `toFlowDescriptor` and `toToolbarDescriptor` mappers OK (scope of tasks.md, not implementation details).
- No Android imports (pure Kotlin + port interfaces).
- Compiles.

**Dependencies**: T127-006

---

### T127-015 — Write ProfileBackedFlowRepositoryTest (JVM, FakeProfileStore)

**Trace**: Plan §Test strategy, FR-006, NFR-002.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepositoryTest.kt`.
- Uses `FakeProfileStore` (existing fake adapter, or create if missing).
- Test 1: FakeProfileStore emits non-null Profile with one AppTile → repository emits 1-item FlowDescriptor list.
- Test 2: FakeProfileStore emits null → repository does NOT emit (filterNotNull working).
- Test 3: FakeProfileStore emits Profile v1, then Profile v2 → repository emits two lists in sequence.
- All tests pass (green).

**Dependencies**: T127-006, T127-014

---

### T127-016 — Add TODO(config-deprecation) comment to ConfigBackedFlowRepository

**Trace**: Plan §Adapters, FR-007, FR-010.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/adapters/flow/ConfigBackedFlowRepository.kt`: add inline comment.
- Comment: `// TODO(config-deprecation, SRV-CONFIG-DEPRECATION): ConfigDocument stays for admin push (spec 009); remove entirely when unified Profile-sync ships.`
- Class NOT deleted, existing tests NOT removed.
- Compiles.

**Dependencies**: none | **[P]**

---

## Phase 4 — DI Wiring

### T127-017 — Rebind FlowRepository to ProfileBackedFlowRepository in MockBackendModule

**Trace**: Plan §DI wiring, FR-007.

**Acceptance**:
- File: `app/src/main/java/com/launcher/app/di/MockBackendModule.kt`.
- Change single binding: `single<FlowRepository> { ProfileBackedFlowRepository(profileStore = get()) }`.
- Old binding to `ConfigBackedFlowRepository` removed or commented with note.
- App compiles (mockBackend flavor).
- No DI conflicts.

**Dependencies**: T127-014

---

### T127-018 — Rebind FlowRepository to ProfileBackedFlowRepository in RealBackendModule

**Trace**: Plan §DI wiring, FR-007.

**Acceptance**:
- File: `app/src/main/java/com/launcher/app/di/RealBackendModule.kt`.
- Change single binding: `single<FlowRepository> { ProfileBackedFlowRepository(profileStore = get()) }`.
- Old binding to `ConfigBackedFlowRepository` removed or commented with note.
- App compiles (realBackend flavor).
- No DI conflicts.

**Dependencies**: T127-014

---

## Phase 5 — Localization

### T127-019 — Create core/composeResources/values/strings_wizard.xml with all wizard keys

**Trace**: Plan §Localization, FR-008, US-3.

**Acceptance**:
- File: `core/src/commonMain/composeResources/values/strings_wizard.xml` (or correct path per Compose Resources convention).
- Contains all wizard string keys used by TASK-126 wizard code (grep identifies them: `wizard_step_of`, `wizard_component_font_size`, `wizard_component_sos`, `wizard_confirm`, + any others found).
- `wizard_step_of` declared as `<plurals>` with forms for Russian: `one`, `few`, `many`, `other`.
- Other keys as `<string>`.
- All values are readable Russian text (not raw keys).
- Compose resource compiles without warnings.

**Dependencies**: none | **[P]**

---

## Phase 6 — Integration

### T127-020 — Extend HomeComponentLoadingStateTest with postManifestWizardReconcile scenario

**Trace**: Plan §Integration tests, FR-006, FR-007, US-1 Scenario 1.

**Acceptance**:
- File: `core/src/androidTest/kotlin/com/launcher/home/HomeComponentLoadingStateTest.kt` (or equivalent).
- Add new test method: `postManifestWizardReconcile_profileSeeded_homeReady()`.
- Setup: wire `ProfileBackedFlowRepository`, seed `FakeProfileStore` with one `AppTile(Settings, tags = setOf(Tag.Presentation, Tag.Tile))`.
- Action: initialize `HomeComponent`, observe state emissions.
- Assert: `HomeLoadingState.Ready` emitted with 1 FlowDescriptor.
- Existing config-based test scenarios remain green (ConfigBackedFlowRepository tests still pass).
- Test passes (green).

**Dependencies**: T127-014, T127-015, T127-017, T127-018

---

## Phase 7 — Fitness Functions

### T127-021 — Write ComponentTagsFitnessTest (reflection walk)

**Trace**: Plan §Fitness functions, FR-002, NFR-001.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/model/ComponentTagsFitnessTest.kt`.
- Uses reflection to walk all `Component` sealed subclasses.
- For each subclass: instantiate with constructor default values, assert `tags` is non-empty.
- Prevent accidental `emptySet()` regressions.
- Test passes (green).

**Dependencies**: T127-004 | **[P]**

---

### T127-022 — Write ProfileQueryBenchmarkTest (JVM micro-benchmark)

**Trace**: Plan §Fitness functions, NFR-003, SC-008.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/query/ProfileQueryBenchmarkTest.kt`.
- Create Profile fixture with ~20 Components of varying tags.
- Benchmark `profile.homeScreenTiles()`, `profile.toolbar()`, `profile.byTag(Tag.System)`.
- Measure p95 latency per call.
- Assert p95 < 1 ms (NFR-003).
- Report (can be inline, no external benchmark tool needed).

**Dependencies**: T127-004, T127-006 | **[P]**

---

### T127-023 — Write ProfileQueryTest (unit tests for all Query API methods)

**Trace**: Plan §Unit tests, FR-005, NFR-002, US-2 Scenario 1-2.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/query/ProfileQueryTest.kt`.
- Test cases:
  - `byTag(Tag.Presentation)` returns only Presentation-tagged components.
  - `byAllTags(setOf(Tag.Presentation, Tag.Tile))` returns only components with both tags (homeScreenTiles equivalent).
  - `byAnyTag(setOf(Tag.Safety, Tag.Emergency))` returns components with at least one of these tags.
  - `byNotTag(Tag.Toolbar)` returns all components except Toolbar-tagged ones.
  - `homeScreenTiles()` returns tiles but not Toolbar.
  - `toolbar()` returns Toolbar-tagged component or null; verified NO `is Toolbar` type check in implementation.
  - Empty result on unmatched tag.
  - Empty Profile returns empty results.
  - Multi-tag membership: Sos component found by all of {Tag.Presentation, Tag.Tile, Tag.Safety, Tag.Emergency}.
- All tests pass (green).

**Dependencies**: T127-004, T127-006 | **[P]**

---

## Phase 8 — Cleanup & Documentation

### T127-024 — Add SRV-CONFIG-DEPRECATION entry to docs/dev/server-roadmap.md

**Trace**: Plan §Required context review, FR-010.

**Acceptance**:
- File: `docs/dev/server-roadmap.md`.
- New entry under a **Deferred migrations** or **Future server transitions** section.
- Entry: `SRV-CONFIG-DEPRECATION: Remove ConfigBackedFlowRepository (HomeScreen path) when unified Profile-based sync replaces admin push ConfigDocument. Trigger: when spec-009 admin push migrates to Profile-based delivery. Cost: remove ConfigBackedFlowRepository + ConfigDocument + related tests + DI binding changes (no behavior change). Inline TODO marker present in code.`
- Link-back: mention TASK-127, FR-010.

**Dependencies**: T127-016 | **[P]**

---

### T127-025 — Create/verify docs/architecture/preset-model.md with AI-TLDR block

**Trace**: Plan §Required context review, FR-009.

**Acceptance**:
- File exists: `docs/architecture/preset-model.md`.
- Contains `<!-- AI-TLDR:BEGIN ... AI-TLDR:END -->` block (~60-80 lines).
- Block documents two-dimensions model: **lifecycle** (Preset `wizardFlow` / `settingsMap` / `activeComponents`) vs **semantic** (`Component.tags`).
- Both dimensions shown as orthogonal.
- Doc-comments in `Preset.kt` and `Component.kt` reference this file.
- File is readable by non-developer owner (plain Russian).

**Dependencies**: T127-008 | **[P]**

---

### T127-026 — Emulator smoke test: fresh install → wizard → HomeScreen

**Trace**: Plan §Manual verification, US-1 Scenario 1, SC-002.

**Acceptance**:
- Command: `./gradlew :app:installMockBackendDebug` on pixel_5_api_34 (or available AVD ≤ API 34).
- Flow: app starts → manifest wizard appears → user taps through steps → HomeActivity shows tile grid (not Error UI).
- Wizard strings display as readable Russian text (no raw `wizard_*` keys visible in UI).
- **[deferred-local-emulator]** — AI session cannot verify visually; manual step for owner or must document deviation if not runnable.

**Dependencies**: T127-017, T127-019, T127-020

---

### T127-027 — Physical device smoke test: Xiaomi Redmi Note 11 (adb id 17f33878)

**Trace**: Plan §Manual verification, US-1 Scenario 1-2, SC-001, SC-002.

**Acceptance**:
- Device: Xiaomi Redmi Note 11, adb id `17f33878` (per spec.md Local Test Path).
- Flow: fresh install (or factory reset) → manifest wizard → HomeActivity shows tiles without Error UI.
- Wizard strings localized (readable Russian, not raw keys).
- Closes SC-001 and SC-002 acceptance criteria.
- **[deferred-physical-device]** — AI session cannot access device; manual verification required (owner with Xiaomi).

**Dependencies**: T127-017, T127-019, T127-020

---

## Instrumented Integration Tests

> **[deferred-local-emulator]** T126-020 cannot run reliably in AI session on Xiaomi 11T + compose-ui-test 1.7.x (API ≤34 mismatch, see memory `reference_compose_ui_test_api_mismatch.md`). Owned by test environment, not code.

---

## Cross-Artifact Trace Summary

### Spec → Tasks coverage

- **FR-001**: Tag enum — T127-003 ✓
- **FR-002**: Component.tags — T127-004 ✓
- **FR-003**: ComponentDeclaration pool override — T127-005 ✓
- **FR-004**: schemaVersion: 1 + constructor-defaults (no migration writer) — T127-004 (constructor defaults), T127-009 (roundtrip + bonus case for missing tags field), T127-021 (fitness verifies non-empty defaults) ✓
- **FR-005**: Query API — T127-006, T127-023 ✓
- **FR-006**: ProfileBackedFlowRepository — T127-014, T127-015 ✓
- **FR-007**: DI wiring — T127-017, T127-018 ✓
- **FR-008**: strings_wizard.xml — T127-019 ✓
- **FR-009**: preset-model.md — T127-008, T127-025 ✓
- **FR-010**: server-roadmap SRV-CONFIG-DEPRECATION — T127-024 ✓

### User Stories → Test evidence

- **US-1** (Fresh install → HomeScreen): T127-020 (integration), T127-026 (emulator smoke), T127-027 (physical smoke) ✓
- **US-2** (Developer adds Component): T127-023 (query unit tests), T127-021 (fitness reflection) ✓
- **US-3** (Wizard localization): T127-019 (strings resource), T127-026 (emulator visual check), T127-027 (physical visual check) ✓

### Contracts → Tests

- **profile-v1.md contract**:
  - Roundtrip test: T127-009 ✓
  - Missing-tags-field test (constructor defaults): T127-009 bonus case ✓
  - Fixture: T127-010 ✓
- **[REMOVED]** profile-v2 → v3 migration tests — не пишется migration writer.

### Non-Functional Requirements

- **NFR-001** (domain isolation): T127-021 (fitness function via checklist-domain-isolation) ✓
- **NFR-002** (ProfileBackedFlowRepository emissions): T127-015 (unit test FakeProfileStore) ✓
- **NFR-003** (Query performance < 1 ms): T127-022 (micro-benchmark) ✓
- **NFR-004** [REMOVED]: no migration writer, no idempotency test needed. Constructor-defaults verified through T127-021 fitness.

---

## Task Ordering + Dependencies

All forward dependencies valid. Task IDs in `requires:` point to earlier or same-phase tasks. No forward-refs.

---

## Manual Checkpoint Summary

| Checkpoint | Dependent tasks | Status |
|---|---|---|
| **Build gates** | T127-001..T127-023 | All unit/contract tests pass |
| **Fitness gates** | T127-021, T127-022, T127-023 | Pass; checklist-domain-isolation green |
| **Integration gate** | T127-020 | Robolectric green |
| **Emulator smoke** | T127-026 | [deferred-local-emulator] |
| **Physical smoke (Xiaomi)** | T127-027 | [deferred-physical-device] |

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 23 задачи разложены в 8 фаз для реализации tagged-component model (ECS-inspired) на `Profile`: добавить `Tag` enum (10 значений включая `Tag.Toolbar`), поле `tags: Set<Tag>` на Components, query API (7 функций включая `byNotTag` = canonical ECS `Without<T>`), новый `ProfileBackedFlowRepository` адаптер, локализованные строки wizard'а, тесты (contract, unit, integration, fitness). **Никакой migration writer**: `schemaVersion: 1` стартовая, MVP не релизнут (T127-007/011/012/013 удалены). Terminology + latent one-way door задокументированы в [ADR-012](../../docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md).

**Конкретика, которую стоит запомнить:**
- **23 активные задачи** в 8 фазах: Foundation (2) → Domain types (6) → Wire format (2, T127-011/012/013 removed) → Adapters (3) → DI wiring (2) → Localization (1) → Integration (1) → Fitness (3) → Cleanup/docs (4). 4 задачи `[REMOVED]` per Clarification Q6.
- **16 задач параллельные** (`[P]`), безопасно независимые, могут идти одновременно.
- **`Tag` enum**: 10 значений (`Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar`), hardcoded в коде.
- **`Component.tags` дефолты**: `AppTile → {Presentation, Tile}`, `Sos → {Presentation, Tile, Safety, Emergency}`, `Toolbar → {Presentation, Toolbar}`, `FontSize → {Appearance, Accessibility}`, `LauncherRole → {System}`, `Theme → {Appearance}`.
- **Query API** 7 функций: `query(predicate)`, `byTag`, `byAllTags`, `byAnyTag`, `byNotTag` (canonical ECS `Without<T>` эквивалент), `homeScreenTiles`, `toolbar` — все extension-функции на `Profile`. `toolbar()` реализован через `byTag(Tag.Toolbar).firstOrNull()`, без `is Toolbar`.
- **Никакой migration writer** (per Clarification Q6): `schemaVersion: 1` стартовая, MVP не релизнут. Отсутствие `tags` в JSON = kotlinx.serialization подставляет constructor-default (единственный источник истины). Первая migration появится post-release.
- **ProfileBackedFlowRepository** читает `ProfileStore.observe().filterNotNull()`, проецирует query результат, заменяет `ConfigBackedFlowRepository` в DI обоих flavor'ов.
- **Тесты**: 3 контракта (roundtrip v3, migration roundtrip, backward-compat v2), 3 юнит (Query API, ProfileBackedFlowRepository, ComponentTagsFitnessTest), 1 интеграция (HomeComponentLoadingStateTest), 1 микробенчмарк (< 1 мс на query).

**На что смотреть с осторожностью:**
- T127-026, T127-027 помечены `[deferred-physical-device]` — требуют Xiaomi Redmi Note 11 физически (adb id `17f33878`); AI не может закрыть, только владелец.
- Constructor-defaults на Component subtypes (T127-004) — единственный источник истины для tags. `ComponentTagsFitnessTest` (T127-021) через reflection гарантирует non-empty defaults. Если добавляется новый subtype без `tags` default — тест упадёт на build.
- `schemaVersion: 1` стартовая. Пока pre-release — каждое breaking dev-change = сброс dev `ProfileStore` (`adb uninstall`), не migration writer. Post-release первый breaking change = первый migration writer + bump `1 → 2`.
- `strings_wizard.xml` ключи должны быть полными — grep по TASK-126 коду перед T127-019 (не угадаем все ключи заранее).
- Null-handling в `ProfileBackedFlowRepository` (T127-014): `filterNotNull()` критичен для SEQ-4 контракта (Loading, не Error).
