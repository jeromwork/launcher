# Tasks: ECS Tags Foundation + HomeScreen Query Rewire

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Backlog task**: [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md)

---

## Phases Overview

| Phase | Description | Tasks |
|-------|-----------|-------|
| **0. Foundation** | Gradle/DI setup, no behaviour | T127-001 — T127-002 |
| **1. Domain types** | Pure-Kotlin enums, models, ports | T127-003 — T127-008 |
| **2. Wire format** | Contracts + tests | T127-009 — T127-010 (T127-011..T127-013 removed) |
| **3. Adapters** | ProfileBackedFlowRepository + tests | T127-014 — T127-016 |
| **4. DI wiring** | Rebind FlowRepository in both flavors | T127-017 — T127-018 |
| **5. Localization** | strings_wizard.xml keys | T127-019 |
| **6. Integration** | Extend HomeComponentLoadingStateTest | T127-020 |
| **7. Fitness** | Reflection walk, benchmarks | T127-021 — T127-023 |
| **8. Cleanup & docs** | doc-comments, server-roadmap, smoke | T127-024 — T127-027 |

**Total tasks**: 23 active (4 removed per Clarification Q6: no migration writer) | **Parallel-safe tasks**: 16 marked `[P]`

> **Revision 2026-07-16 (deep pre-implement audit)**: file paths, Component subtype list, contract test names, and `ProfileBackedFlowRepository` method surface corrected against real code on the branch. `schemaVersion` stays **2** (additive `tags`, matches `Profile.CURRENT_SCHEMA_VERSION` + TASK-120 Decision); contract renamed `profile-v1.md → profile-v2.md`.

---

## Phase 0 — Foundation

### T127-001 — Verify ProfileStore contract for null emission

**Trace**: Plan §Data flow, FR-006, Assumption: "`ProfileStore.observe()` returns `Flow<Profile?>` with nulls on cold start".

**Acceptance**:
- ~~Read TASK-126 code~~ **DONE 2026-07-16 (audit)**: `ProfileStore.observe(): Flow<Profile?>` confirmed ([ProfileStore.kt:7](../../core/src/commonMain/kotlin/com/launcher/preset/port/ProfileStore.kt#L7)); `DataStoreProfileStore` emits `null` while the DataStore key is absent. Assumption holds; no deviation.
- Remaining: verify `ReconcileEngine.run(RunMode.Wizard)` actually calls `profileStore.save(finalProfile)` before `HomeActivity` starts (spec § Assumptions) — one grep + note.

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
- `@Serializable enum class Tag` with **10 values**: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar` (annotation matches the file's existing enums).
- Compiles without Android imports.
- `checklist-domain-isolation` passes on this enum (verified by running checklist in tasks-phase).

**Dependencies**: none | **[P]**

---

### T127-004 — Add tags field to Component sealed hierarchy

**Trace**: Plan §Data model, FR-002.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt` modified.
- Abstract `val tags: Set<Tag>` added to `Component` sealed class.
- **All 8 real subtypes** have constructor-default values per data-model.md: `AppTile → {Presentation, Tile}`, `FontSize → {Appearance, Accessibility}`, `Sos → {Presentation, Tile, Safety, Emergency}`, `Toolbar → {Presentation, Toolbar}`, `LauncherRole → {System}`, `Theme → {Appearance}`, **`Language → {System}`**, **`StatusBarPolicy → {System}`** (последние два раньше пропускались в документах).
- **`LauncherRole` and `StatusBarPolicy`: `object` → `data class`** with single `tags` param defaulted — objects cannot carry overridable constructor-defaults. Wire-compatible: old JSON `{"type":"LauncherRole"}` still deserializes (all params defaulted); `@SerialName` values preserved.
- Compiles; existing callers of `Component.LauncherRole` / `Component.StatusBarPolicy` updated from object references to `LauncherRole()` / `StatusBarPolicy()` constructor calls (grep usages, incl. `when`-branches: `is` checks keep working).

**Dependencies**: T127-003 | **[P]**

---

### T127-005 — Pool.json tags override: verify via embedded component + doc-comment (NO ComponentDeclaration change)

**Trace**: Plan §Data model, FR-003 (restated 2026-07-16).

**Restated by audit**: [Pool.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt) `ComponentDeclaration` embeds `component: Component` directly — a declaration overrides tags by including `"tags": [...]` **inside the embedded component object**. No new field, no Pool schema change (rule 4 MVA).

**Acceptance**:
- Test (can live in `PoolSchemaV2RoundtripTest` or a new case): pool.json declaration whose embedded component carries explicit `"tags"` → deserialized `Component.tags` equals the override, not the constructor default.
- Doc-comment on `ComponentDeclaration` explains the override mechanism vs Component constructor-default.
- Existing bundled `pool.json` (no `tags` fields) deserializes unchanged — constructor-defaults apply.

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
  - `byNotTag(tag)` — plain predicate exclusion (label-selector style; NOT "canonical ECS `Without<T>`" — framing dropped per audit, see ADR-012)
  - `homeScreenTiles()` — `byAllTags({Presentation, Tile})` **excluding `ComponentStatus.Failed/Skipped`** (render gating per data-model.md § Render gating)
  - `toolbar()` — implemented as `byTag(Tag.Toolbar).firstOrNull()`, **no `is Toolbar` type check**; query-API level only, HomeComponent does NOT consume it in TASK-127
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

### T127-009 — Write ProfileSchemaV2RoundtripTest (roundtrip + fail-loud pins)

**Trace**: Plan §Wire formats, FR-004, contracts/profile-v2.md.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/preset/wire/ProfileSchemaV2RoundtripTest.kt` (existing wire-test package; naming per `PoolSchemaV2RoundtripTest`).
- `Json` settings mirror `DataStoreProfileStore` exactly: `classDiscriminator="type"`, `ignoreUnknownKeys=true`, `encodeDefaults=true` (contract § Serializer configuration).
- Test reads `core/src/commonTest/resources/fixtures/profile-wire-format/profile-v2-sample.json`.
- Fixture contains v2 Profile with `schemaVersion=2`, real shape (`basedOnPreset`/`presetVersion`/`layoutKey`, ProfileComponent wrappers), Components with `tags` fields.
- Deserialize → serialize → deserialize → assert equal (roundtrip guarantee).
- Missing-tags case: `profile-v2-no-tags.json` → Component equals constructor-default tags.
- **Fail-loud pins** (contract § Forward compat): `"tags": ["FutureTag"]` → `SerializationException`; `"type": "FutureComponent"` → `SerializationException`.
- Tests pass (green).

**Dependencies**: T127-004, T127-006 | **[P]**

---

### T127-010 — Write profile-v2 fixtures

**Trace**: Plan §Wire formats, contracts/profile-v2.md.

**Acceptance**:
- Files: `core/src/commonTest/resources/fixtures/profile-wire-format/profile-v2-sample.json` + `profile-v2-no-tags.json`.
- `profile-v2-sample.json`: valid v2 Profile JSON with `schemaVersion=2`, real field names (`basedOnPreset`, `presetVersion`, `layoutKey`; ProfileComponent wrappers `{id, component, wizardBehavior, critical, status}`), at least 3 Components (AppTile, Sos, Toolbar), each with `"tags"` array. Matches example in contracts/profile-v2.md (no `presetId`, no `targetPhone`, no `buttons` — those were fictional).
- `profile-v2-no-tags.json`: same shape, `tags` omitted everywhere.
- No parse errors when read with the normative Json settings.

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
- Class implements the **existing, unchanged** `FlowRepository` port ([api/FlowRepository.kt](../../core/src/commonMain/kotlin/com/launcher/api/FlowRepository.kt)) — **all four methods** (audit: earlier revision invented `observeToolbar()` which is NOT in the port, and omitted three real methods):
  - `loadFlows()`: `profileStore.observe().filterNotNull().first()` → `homeScreenTiles()` → map to `FlowDescriptor`. **This is the regression path** (`HomeComponent.launchLoadFlows()`, 3s timeout).
  - `observeFlows()`: `profileStore.observe().filterNotNull().map { it.homeScreenTiles() … }`.
  - `availableTemplates(presetId)`: existing static template-catalogue semantics (parity with `ConfigBackedFlowRepository`).
  - `addFlow(templateId)`: `error(...)` — parity with the only existing impl (`ConfigBackedFlowRepository.addFlow` throws too); mark `TODO(profile-add-flow)`.
- `toFlowDescriptor(pc: ProfileComponent)` mapping implemented (AppTile/Sos → FlowDescriptor id/label; label via `labelKey` resource key).
- No Android imports (pure Kotlin + port interfaces).
- Compiles.

**Dependencies**: T127-006

---

### T127-015 — Write ProfileBackedFlowRepositoryTest (JVM, FakeProfileStore)

**Trace**: Plan §Test strategy, FR-006, NFR-002.

**Acceptance**:
- Test file: `core/src/commonTest/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepositoryTest.kt`.
- Uses **existing** `FakeProfileStore` (`core/src/commonTest/kotlin/com/launcher/preset/fakes/FakeProfileStore.kt`).
- Test 1: store holds Profile with one AppTile → `loadFlows()` returns 1-item list (regression path).
- Test 2: store stays null → `loadFlows()` suspends (runTest: no completion before timeout advance) — pins the "caller timeout owns absent-Profile" contract.
- Test 3: store emits null → `observeFlows()` does NOT emit (filterNotNull working).
- Test 4: two sequential saves → `observeFlows()` emits two lists in sequence.
- Test 5: Profile with `Failed`/`Skipped` tile components → excluded from both `loadFlows()` and `observeFlows()` output (render gating).
- Test 6: `addFlow("x")` throws (parity pin).
- All tests pass (green).

**Dependencies**: T127-006, T127-014

---

### T127-016 — Add TODO(config-deprecation) comment to ConfigBackedFlowRepository

**Trace**: Plan §Adapters, FR-007, FR-010.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt` (real location — NOT `adapters/flow/`): add inline comment.
- Comment: `// TODO(config-deprecation, SRV-CONFIG-DEPRECATION): ConfigDocument stays for admin push (spec 009); remove entirely when unified Profile-sync ships.`
- Class NOT deleted, existing tests NOT removed.
- Compiles.

**Dependencies**: none | **[P]**

---

## Phase 4 — DI Wiring

### T127-017 — Rebind FlowRepository to ProfileBackedFlowRepository in androidMockBackend BackendInit

**Trace**: Plan §DI wiring, FR-007.

**Acceptance**:
- File: `core/src/androidMockBackend/kotlin/com/launcher/di/BackendInit.kt` (~line 165; real location — `app/.../di/MockBackendModule.kt` does not exist).
- Change single binding: `single<FlowRepository> { ProfileBackedFlowRepository(profileStore = get()) }`.
- Verify `ProfileStore` is resolvable in this Koin scope (it is currently bound in `app` `PresetModule` — if not visible from core backend module, inject via constructor from the app-level module; document the chosen wiring).
- Old binding to `ConfigBackedFlowRepository` removed or commented with note.
- App compiles (mockBackend flavor). No DI conflicts.

**Dependencies**: T127-014

---

### T127-018 — Rebind FlowRepository to ProfileBackedFlowRepository in androidRealBackend BackendInit

**Trace**: Plan §DI wiring, FR-007.

**Acceptance**:
- File: `core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt` (~line 275; real location — `app/.../di/RealBackendModule.kt` does not exist).
- Change single binding: `single<FlowRepository> { ProfileBackedFlowRepository(profileStore = get()) }` (same `ProfileStore`-visibility check as T127-017).
- Old binding to `ConfigBackedFlowRepository` removed or commented with note.
- App compiles (realBackend flavor). No DI conflicts.

**Dependencies**: T127-014, T127-017

---

## Phase 5 — Localization

### T127-019 — Extend EXISTING strings_wizard.xml with missing wizard keys

**Trace**: Plan §Localization, FR-008, US-3.

**Acceptance**:
- File: `core/src/commonMain/composeResources/values/strings_wizard.xml` — **already exists** (audit); task is to grep TASK-126 wizard code for used keys and add the missing ones, not create the file.
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
- File: `core/src/commonTest/kotlin/com/launcher/ui/navigation/HomeComponentLoadingStateTest.kt` (real location — JVM commonTest, NOT androidTest).
- Add new test method: `postManifestWizardReconcile_profileSeeded_homeReady()`.
- Setup: wire `ProfileBackedFlowRepository`, seed `FakeProfileStore` with one `AppTile(packageName = "com.android.settings", labelKey = "tile_settings")` (default tags apply).
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
- Uses reflection to walk all `Component` sealed subclasses (`Component::class.sealedSubclasses` — 8 expected).
- For each subclass: instantiate supplying dummy values for required params (e.g. `AppTile.packageName`, `Theme.paletteSeedHex`) while leaving `tags` at its default; assert `tags` is non-empty.
- Prevent accidental `emptySet()` regressions; test fails if a new subtype lacks a `tags` default.
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
  - **Render gating**: tile with `status = Failed` or `Skipped` excluded from `homeScreenTiles()`; `Pending`/`Applied` included.
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
- **FR-003**: pool.json tags override via embedded component (no ComponentDeclaration change) — T127-005 ✓
- **FR-004**: schemaVersion: 2 unchanged (additive tags) + constructor-defaults (no migration writer) — T127-004 (constructor defaults), T127-009 (roundtrip + missing-tags + fail-loud pins), T127-021 (fitness verifies non-empty defaults) ✓
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

- **profile-v2.md contract**:
  - Roundtrip test: T127-009 ✓
  - Missing-tags-field test (constructor defaults): T127-009 ✓
  - Fail-loud pins (unknown Tag / unknown type): T127-009 ✓
  - Fixtures: T127-010 ✓
- **[REMOVED]** migration tests — не пишется migration writer (Q6).

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

**Суть.** 23 задачи разложены в 8 фаз для реализации tagged-component model (ECS-inspired) на `Profile`: добавить `Tag` enum (10 значений включая `Tag.Toolbar`), поле `tags: Set<Tag>` на **все 8** Component subtypes, query API (7 функций, стиль label-selectors как в Kubernetes), новый `ProfileBackedFlowRepository` адаптер (**все 4 метода** существующего порта, включая `loadFlows()` — реальный путь регрессии), локализованные строки wizard'а, тесты (contract, unit, integration, fitness). **Никакой migration writer**: `schemaVersion: 2` остаётся (tags аддитивно), MVP не релизнут (T127-007/011/012/013 удалены). Terminology + latent one-way door задокументированы в [ADR-012](../../docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md).

**Конкретика, которую стоит запомнить:**
- **23 активные задачи** в 8 фазах: Foundation (2) → Domain types (6) → Wire format (2, T127-011/012/013 removed) → Adapters (3) → DI wiring (2) → Localization (1) → Integration (1) → Fitness (3) → Cleanup/docs (4). 4 задачи `[REMOVED]` per Clarification Q6.
- **16 задач параллельные** (`[P]`), безопасно независимые, могут идти одновременно.
- **`Tag` enum**: 10 значений (`Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar`), hardcoded в коде.
- **`Component.tags` дефолты (8 подтипов)**: `AppTile → {Presentation, Tile}`, `Sos → {Presentation, Tile, Safety, Emergency}`, `Toolbar → {Presentation, Toolbar}`, `FontSize → {Appearance, Accessibility}`, `LauncherRole → {System}`, `Theme → {Appearance}`, `Language → {System}`, `StatusBarPolicy → {System}`. `LauncherRole` и `StatusBarPolicy`: object → data class (wire-совместимо).
- **Query API** 7 функций: `query(predicate)`, `byTag`, `byAllTags`, `byAnyTag`, `byNotTag`, `homeScreenTiles` (исключает `Failed`/`Skipped` — render gating), `toolbar` — все extension-функции на `Profile`. `toolbar()` через `byTag(Tag.Toolbar).firstOrNull()`, без `is Toolbar`; HomeComponent его в TASK-127 не потребляет.
- **Никакой migration writer** (per Clarification Q6): `schemaVersion: 2` остаётся (tags — аддитивное поле), MVP не релизнут. Отсутствие `tags` в JSON = kotlinx.serialization подставляет constructor-default (единственный источник истины). Первая migration появится post-release (v2 → v3).
- **ProfileBackedFlowRepository** реализует все 4 метода порта: `loadFlows()` = `.filterNotNull().first()` (путь регрессии, 3s timeout у HomeComponent), `observeFlows()` = hot path, `availableTemplates` = существующий каталог, `addFlow` = throws (parity). Заменяет `ConfigBackedFlowRepository` в DI обоих flavor'ов (`BackendInit.kt` в core, не app).
- **Тесты**: 1 контракт-класс (roundtrip v2 + missing-tags + 2 fail-loud pins), 3 юнит (Query API + render gating, ProfileBackedFlowRepository 6 кейсов, ComponentTagsFitnessTest), 1 интеграция (HomeComponentLoadingStateTest, JVM commonTest), 1 микробенчмарк (< 1 мс на query).

**На что смотреть с осторожностью:**
- T127-026, T127-027 помечены `[deferred-physical-device]` — требуют Xiaomi Redmi Note 11 физически (adb id `17f33878`); AI не может закрыть, только владелец.
- Constructor-defaults на Component subtypes (T127-004) — единственный источник истины для tags. `ComponentTagsFitnessTest` (T127-021) через reflection гарантирует non-empty defaults. Если добавляется новый subtype без `tags` default — тест упадёт на build.
- `schemaVersion: 2` — не трогаем (значение из кода TASK-120). Пока pre-release — каждое breaking dev-change = сброс dev `ProfileStore` (`adb uninstall`), не migration writer. Post-release первый breaking change = первый migration writer + bump `2 → 3`. Незнакомый тег/тип у старого читателя = fail-loud (зафиксировано тестами); lenient-читатель обязателен до cross-device обмена (plan R-8).
- `strings_wizard.xml` ключи должны быть полными — grep по TASK-126 коду перед T127-019 (не угадаем все ключи заранее).
- Null-handling в `ProfileBackedFlowRepository` (T127-014): `filterNotNull()` критичен для SEQ-4 контракта (Loading, не Error).
