# Tasks: ECS Tags Foundation + HomeScreen Query Rewire

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Backlog task**: [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md)

---

## Phases Overview

| Phase | Description | Tasks |
|-------|-----------|-------|
| **0. Foundation** | Gradle/DI setup, no behaviour | T127-001 — T127-002 |
| **1. Domain types** | Pure-Kotlin enums, models, ports | T127-003 — T127-008 |
| **2. Wire format** | Serializers, contracts, tests | T127-009 — T127-013 |
| **3. Adapters** | ProfileBackedFlowRepository + tests | T127-014 — T127-016 |
| **4. DI wiring** | Rebind FlowRepository in both flavors | T127-017 — T127-018 |
| **5. Localization** | strings_wizard.xml keys | T127-019 |
| **6. Integration** | Extend HomeComponentLoadingStateTest | T127-020 |
| **7. Fitness** | Reflection walk, benchmarks | T127-021 — T127-023 |
| **8. Cleanup & docs** | doc-comments, server-roadmap, smoke | T127-024 — T127-027 |

**Total tasks**: 27 | **Parallel-safe tasks**: 16 marked `[P]`

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
- `Tag` enum with 9 values: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile`.
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
- `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` contains all 6 extension functions (identical to data-model.md § Query API):
  - `query(predicate)`
  - `byTag(tag)`
  - `byAllTags(tags)`
  - `byAnyTag(tags)`
  - `homeScreenTiles()`
  - `toolbar()`
- No Android imports.
- Compiles.
- No tests yet (unit tests in separate Phase-2 task).

**Dependencies**: T127-003, T127-004 | **[P]**

---

### T127-007 — Add ProfileMigrationV2toV3 object skeleton

**Trace**: Plan §Data model, FR-004.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/preset/serialization/ProfileMigrationV2toV3.kt` created.
- `ProfileMigrationV2toV3.migrate(v2Profile): ProfileV3` signature declared.
- `defaultTagsFor(component): Set<Tag>` signature declared (hardcoded when block per data-model.md).
- Exhaustive sealed-when (Kotlin compiler verifies).
- TODO placeholders OK at this stage.

**Dependencies**: T127-003, T127-004 | **[P]**

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

### T127-009 — Write ProfileWireFormatV3ContractTest (roundtrip)

**Trace**: Plan §Wire formats, FR-004, contracts/profile-v3.md.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/serialization/ProfileWireFormatV3ContractTest.kt`.
- Test reads `core/src/commonTest/resources/fixtures/profile-v3-sample.json`.
- Fixture contains v3 Profile with `schemaVersion=3`, multiple Components with `tags` fields.
- Deserialize → serialize → deserialize → assert byte-equal (roundtrip guarantee).
- Test passes (green).

**Dependencies**: T127-004, T127-006 | **[P]**

---

### T127-010 — Write profile-v3-sample.json fixture

**Trace**: Plan §Wire formats, contracts/profile-v3.md.

**Acceptance**:
- File: `core/src/commonTest/resources/fixtures/profile-v3-sample.json`.
- Contains valid v3 Profile JSON with `schemaVersion=3`, at least 3 Components (AppTile, Sos, Toolbar), each with `"tags"` array.
- Matches example in contracts/profile-v3.md.
- No parse errors when read by ProfileSerializer.

**Dependencies**: T127-004 | **[P]**

---

### T127-011 — Write ProfileMigrationV2toV3RoundtripTest + profile-v2-sample.json fixture

**Trace**: Plan §Wire formats, FR-004, contracts/profile-v3.md.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/serialization/ProfileMigrationV2toV3RoundtripTest.kt`.
- Fixture file: `core/src/commonTest/resources/fixtures/profile-v2-sample.json` (v2 Profile, no `tags` fields).
- Test: deserialize v2 fixture → migrate to v3 → serialize → deserialize → assert equals migrated v3 (idempotency proof per NFR-004).
- Test passes (green).

**Dependencies**: T127-004, T127-007 | **[P]**

---

### T127-012 — Write ProfileMigrationV2toV3BackwardCompatTest

**Trace**: Plan §Wire formats, FR-004, contracts/profile-v3.md.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/serialization/ProfileMigrationV2toV3BackwardCompatTest.kt`.
- Test: deserialize v2 fixture → no exception thrown.
- Migration writer produces valid v3 Profile (no null fields, tags populated).
- Verify tags match expected defaults for each Component subtype.
- Test passes (green).

**Dependencies**: T127-004, T127-007 | **[P]**

---

### T127-013 — Implement ProfileMigrationV2toV3 hardcoded mapping

**Trace**: Plan §Data model, FR-004.

**Acceptance**:
- `defaultTagsFor()` when block filled with all 6 existing subtypes (AppTile, Sos, Toolbar, FontSize, LauncherRole, Theme).
- Each case returns correct tags per data-model.md.
- `migrate()` function compiles (Kotlin sealed exhaustiveness enforced).
- T127-011 and T127-012 tests pass.

**Dependencies**: T127-007, T127-011, T127-012

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
  - `homeScreenTiles()` returns tiles but not Toolbar.
  - `toolbar()` returns Toolbar or null.
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
- **FR-004**: Migration writer + wire-format v2→v3 — T127-007, T127-011, T127-012, T127-013 ✓
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

- **profile-v3.md contract**:
  - Roundtrip test: T127-009 ✓
  - Backward-compat test: T127-012 ✓
  - Fixture: T127-010 ✓
- **profile-v2.md (implicit from v2→v3 migration)**:
  - Migration roundtrip: T127-011 ✓
  - Fixture: T127-011 ✓

### Non-Functional Requirements

- **NFR-001** (domain isolation): T127-021 (fitness function via checklist-domain-isolation) ✓
- **NFR-002** (ProfileBackedFlowRepository emissions): T127-015 (unit test FakeProfileStore) ✓
- **NFR-003** (Query performance < 1 ms): T127-022 (micro-benchmark) ✓
- **NFR-004** (Migration idempotency): T127-011 (roundtrip), T127-012 (backward-compat) ✓

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

**Суть.** 27 задач разложены в 8 фаз для реализации ECS-паттерна на `Profile`: добавить `Tag` enum (9 значений), поле `tags: Set<Tag>` на Components, query API (6 функций), новый `ProfileBackedFlowRepository` адаптер, миграцию v2→v3, локализованные строки wizard'а, тесты (contract, unit, integration, fitness).

**Конкретика, которую стоит запомнить:**
- **27 задач** в 8 фазах: Foundation (2) → Domain types (6) → Wire format (5) → Adapters (3) → DI wiring (2) → Localization (1) → Integration (1) → Fitness (3) → Cleanup/docs (4).
- **16 задач параллельные** (`[P]`), безопасно независимые, могут идти одновременно.
- **`Tag` enum**: 9 значений (`Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile`), hardcoded в коде.
- **`Component.tags` дефолты**: `AppTile → {Presentation, Tile}`, `Sos → {Presentation, Tile, Safety, Emergency}`, `Toolbar → {Presentation}` (без `Tile`), `FontSize → {Appearance, Accessibility}`, `LauncherRole → {System}`, `Theme → {Appearance}`.
- **Query API** 6 функций: `query(predicate)`, `byTag`, `byAllTags`, `byAnyTag`, `homeScreenTiles`, `toolbar` — все extension-функции на `Profile`.
- **Migration v2→v3** — hardcoded mapping в `ProfileMigrationV2toV3.defaultTagsFor()`, идемпотентная (T127-011), backward-compat тест (T127-012).
- **ProfileBackedFlowRepository** читает `ProfileStore.observe().filterNotNull()`, проецирует query результат, заменяет `ConfigBackedFlowRepository` в DI обоих flavor'ов.
- **Тесты**: 3 контракта (roundtrip v3, migration roundtrip, backward-compat v2), 3 юнит (Query API, ProfileBackedFlowRepository, ComponentTagsFitnessTest), 1 интеграция (HomeComponentLoadingStateTest), 1 микробенчмарк (< 1 мс на query).

**На что смотреть с осторожностью:**
- T127-026, T127-027 помечены `[deferred-physical-device]` — требуют Xiaomi Redmi Note 11 физически (adb id `17f33878`); AI не может закрыть, только владелец.
- Migration writer (T127-013) должен покрывать все 6 existing субтипов — Kotlin sealed exhaustiveness поймёт, но проверить руками перед commit.
- `schemaVersion: 2 → 3` — one-way door per rule 5; downgrade не поддерживается (стандартно для Android).
- `strings_wizard.xml` ключи должны быть полными — grep по TASK-126 коду перед T127-019 (не угадаем все ключи заранее).
- Null-handling в `ProfileBackedFlowRepository` (T127-014): `filterNotNull()` критичен для SEQ-4 контракта (Loading, не Error).
