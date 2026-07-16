# Tasks: ECS Foundation (Entities, Tags, Query, Hierarchy) + HomeScreen Rewire

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Data model**: [data-model.md](data-model.md) | **Contract**: [contracts/profile-v2.md](contracts/profile-v2.md) | **Backlog task**: [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md)

---

## Phases Overview

| Phase | Description | Tasks |
|-------|-----------|-------|
| **0. Verify** | Contract checks against real code, no behaviour | T127-001 |
| **1. Rename** | ECS naming, mechanical, dedicated commit | T127-002 — T127-003 |
| **2. Domain types** | Tag, tags-on-components, new subtypes, status, parentId | T127-004 — T127-010 |
| **3. Wire format** | Contract tests + fixtures | T127-011 — T127-012 |
| **4. Query API** | Tag + hierarchy selectors, render gating | T127-013 — T127-015 |
| **5. Engine** | Unverifiable rules, hierarchy validation | T127-016 — T127-019 |
| **6. Adapter** | ProfileBackedFlowRepository + tests | T127-020 — T127-022 |
| **7. DI + preset data** | Rebind FlowRepository, hierarchical bundled preset | T127-023 — T127-026 |
| **8. Integration + fitness** | HomeComponent test, fitness, benchmark, l10n | T127-027 — T127-030 |
| **9. Docs + smoke** | preset-model.md, server-roadmap, emulator/physical | T127-031 — T127-035 |

**Total tasks**: 35 active | **Parallel-safe**: 18 marked `[P]`

> **Revision 2026-07-16 (scope expansion Q7-Q10)**: rebuilt from 23 tasks (tags+query only) to 35 (full ECS foundation: hierarchy, structural subtypes, Unverifiable status, ECS rename). Q6 removals (migration writer) stay removed — this file is renumbered, not patched.

---

## Phase 0 — Verify

### T127-001 — Verify wizard-save + ProfileStore null contract

**Trace**: Plan §Data flow, FR-006, spec §Assumptions.

**Acceptance**:
- ~~`ProfileStore.observe(): Flow<Profile?>` emits null on cold start~~ **VERIFIED 2026-07-16** ([ProfileStore.kt:7](../../core/src/commonMain/kotlin/com/launcher/preset/port/ProfileStore.kt#L7); DataStore emits null while key absent).
- ~~`ReconcileEngine.run(RunMode.Wizard)` saves the Profile~~ **VERIFIED 2026-07-16** ([ReconcileEngine.kt:67](../../core/src/commonMain/kotlin/com/launcher/preset/engine/ReconcileEngine.kt#L67) — `store.save(profile)` inside the loop).
- Remaining: confirm `WizardViewModel` starts `HomeActivity` only after `ReconcileState.Done` (grep + note in PR).

**Dependencies**: none | **[P]**

---

## Phase 1 — ECS rename (mechanical, one dedicated commit)

### T127-002 — Rename ProfileComponent → Entity

**Trace**: FR-015, research.md R-9.

**Acceptance**:
- `ProfileComponent` → `Entity` across **51 usages / 14 files** (IDE rename): `Profile.kt`, `Component.kt` (KDoc), `ProfileFactory.kt`, `ReconcileEngine.kt`, `ReconcileState.kt`, `InteractionSink.kt`, both `FakeInteractionSink.kt`, `PendingChecklistViewModel.kt`, `WizardScreen.kt`, `WizardViewModel.kt`, `BootCheckReconcileTest.kt`, `WizardLocaleChangeTest.kt`, `PendingChecklistViewModelTest.kt`.
- **Wire format untouched** — no `@SerialName` on the wrapper; JSON keys unchanged.
- `./gradlew :core:compileKotlinJvm :app:compileMockBackendDebugKotlin` green.

**Dependencies**: none

---

### T127-003 — Rename ComponentDeclaration → Blueprint

**Trace**: FR-015, research.md R-9.

**Acceptance**:
- `ComponentDeclaration` → `Blueprint` across **42 usages / 13 files**: `Pool.kt`, `HintFlowEntry.kt`, `ProfileFactory.kt`, `PresetValidatorResultTest.kt`, `Fixtures.kt`, `PoolSchemaV2RoundtripTest.kt`, `WizardViewModel.kt`, `PresetBootstrapTest.kt`, `WizardDenialUxTest.kt`, `WizardForceCloseResumeTest.kt`, `WizardLocaleChangeTest.kt`, `WizardResumeCheckTest.kt`, `WizardViewModelTest.kt`.
- **`Pool.declarations` field name kept** — renaming it would change `pool.json` keys (wire format).
- All existing tests green: `./gradlew :core:test :app:testMockBackendDebugUnitTest`.

**Dependencies**: T127-002

---

## Phase 2 — Domain types

### T127-004 — Add Tag enum (13 values)

**Trace**: FR-001, data-model.md §Tag.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt`: `@Serializable enum class Tag` with 13 values — semantic: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency`; structural: `Tile, Toolbar, Workspace, Flow, ToolbarButton`.
- Zero Android imports. Compiles.

**Dependencies**: T127-003 | **[P]**

---

### T127-005 — Add ComponentStatus.Unverifiable + Outcome.NeedsUserConfirmation

**Trace**: FR-014, data-model.md §ComponentStatus / §Outcome.

**Acceptance**:
- `Enums.kt`: `ComponentStatus` gains `Unverifiable` (5th value).
- `Outcome.kt`: `object NeedsUserConfirmation : Outcome()`.
- Every exhaustive `when` over these types compiles (engine, UI, tests) — fix fallout.

**Dependencies**: T127-003 | **[P]**

---

### T127-006 — Add abstract tags to Component + defaults on 8 existing subtypes

**Trace**: FR-002, data-model.md §Component subtypes.

**Acceptance**:
- `Component.kt`: `abstract val tags: Set<Tag>` on the sealed class.
- Defaults: `AppTile → {Presentation, Tile}`, `FontSize → {Appearance, Accessibility}`, `Sos → {Presentation, Tile, Safety, Emergency}`, `Toolbar → {Presentation, Toolbar}`, `LauncherRole → {System}`, `Theme → {Appearance}`, `Language → {System}`, `StatusBarPolicy → {System}`.
- Compiles.

**Dependencies**: T127-004

---

### T127-007 — Convert LauncherRole + StatusBarPolicy: object → data class

**Trace**: FR-002, FR-013 (objects cannot carry overridable constructor-defaults).

**Acceptance**:
- Both become `data class X(override val tags: Set<Tag> = setOf(Tag.System)) : Component()`; `@SerialName` preserved.
- **Wire-compatible**: `{"type":"LauncherRole"}` (no fields) still deserializes — pinned by T127-011.
- Call sites updated from object reference to constructor call `LauncherRole()` / `StatusBarPolicy()`; `is`-checks keep working. Grep: `HandlerKey` maps, providers, fixtures, `PresetBootstrap`.
- Compiles; existing tests green.

**Dependencies**: T127-006

---

### T127-008 — Add Workspace / Flow / ToolbarButton subtypes

**Trace**: FR-013, data-model.md §Component subtypes.

**Acceptance**:
- `Component.kt` gains three `@Serializable @SerialName(...)` data classes per data-model.md: `Workspace(layoutKey = "single")`, `Flow(titleKey, layoutKey = "2x3", order = 0)`, `ToolbarButton(targetFlowId, labelKey, iconKey = null, order = 0)`.
- Default tags: `{Presentation, Workspace}` / `{Presentation, Flow}` / `{Presentation, ToolbarButton}`.
- `Toolbar.items` gets `= emptyList()` default + KDoc «legacy; superseded by ToolbarButton children».
- Compiles.

**Dependencies**: T127-006

---

### T127-009 — Add Entity.parentId

**Trace**: FR-011, data-model.md §Entity.

**Acceptance**:
- `Profile.kt`: `Entity` gains `val parentId: String? = null` (last param, defaulted → old JSON reads as root).
- KDoc: «hierarchy by reference; tree is computed by queries, never nested (research.md R-7)».
- `Profile.layoutKey` gets `// TODO(layout-key-migration)` KDoc — legacy fallback for profiles without Workspace/Flow.
- Compiles.

**Dependencies**: T127-002

---

### T127-010 — Add hierarchy ValidationError variants

**Trace**: FR-016, data-model.md §ValidationError.

**Acceptance**:
- `ValidationError.kt`: `DanglingParentRef(entityId, missingParentId)`, `CircularParentRef(cycle: List<String>)`, `DanglingTargetRef(buttonId, missingFlowId)`.
- `toI18nKey()` branches: `validator.error.dangling_parent_ref`, `validator.error.circular_parent_ref`, `validator.error.dangling_target_ref`.
- Compiles; exhaustive `when` over `ValidationError` fixed everywhere.

**Dependencies**: T127-003 | **[P]**

---

## Phase 3 — Wire format

### T127-011 — Write ProfileSchemaV2RoundtripTest (+ compat + fail-loud pins)

**Trace**: FR-004, contracts/profile-v2.md §Contract test surface.

**Acceptance**:
- File: `core/src/commonTest/kotlin/com/launcher/preset/wire/ProfileSchemaV2RoundtripTest.kt` (existing wire-test package; naming per `PoolSchemaV2RoundtripTest`).
- `Json` mirrors `DataStoreProfileStore` exactly: `classDiscriminator="type"`, `ignoreUnknownKeys=true`, `encodeDefaults=true`.
- Cases: (a) hierarchical roundtrip on `profile-v2-hierarchy.json` → deserialize→serialize→deserialize equal; (b) missing `tags` → constructor-defaults; (c) missing `parentId` → null; (d) `{"type":"LauncherRole"}` no fields → deserializes (object→data-class pin); (e) unknown Tag → `SerializationException`; (f) unknown `type` → `SerializationException`; (g) unknown `status` → `SerializationException`.
- Green.

**Dependencies**: T127-007, T127-008, T127-009

---

### T127-012 — Write profile-v2 fixtures

**Trace**: contracts/profile-v2.md §Examples.

**Acceptance**:
- `core/src/commonTest/resources/fixtures/profile-wire-format/profile-v2-hierarchy.json` — exactly the contract example: `schemaVersion 2`, `basedOnPreset`/`presetVersion`/`layoutKey`, workspace + 2 flows + 3 tiles + toolbar + 2 buttons + one `Unverifiable` entity, `parentId` wired.
- `profile-v2-no-tags.json` — same shape minus `tags`/`parentId` (degenerate/defaults case).
- Parse cleanly with the normative Json settings.

**Dependencies**: T127-008, T127-009 | **[P]**

---

## Phase 4 — Query API

### T127-013 — Implement ProfileQuery.kt (tag + hierarchy selectors)

**Trace**: FR-005, FR-012, data-model.md §Query API.

**Acceptance**:
- New file `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt`.
- Tag selectors: `query(predicate)`, `byTag`, `byAllTags`, `byAnyTag`, `byNotTag`.
- Hierarchy selectors: `children(parentId)`, `roots()`, `workspace()`, `flows()` (sorted by `Flow.order`), `toolbar()`, `toolbarButtons()` (sorted by `order`), `tilesOf(flowId)`.
- `homeScreenTiles(flowId: String? = null)` — first flow's tiles, or all tiles when no `Flow` entities exist (degenerate profile).
- **Render gating**: `tilesOf` / `homeScreenTiles` exclude `status == Failed || Skipped`; `Pending`/`Unverifiable` included.
- All extension functions on `Profile`; zero Android imports; compiles.

**Dependencies**: T127-004, T127-008, T127-009

---

### T127-014 — Write ProfileQueryTest (tags + render gating)

**Trace**: FR-005, US-2, SC-004.

**Acceptance**:
- `core/src/commonTest/kotlin/com/launcher/preset/query/ProfileQueryTest.kt`.
- Cases: `byTag`; `byAllTags` (AND); `byAnyTag` (OR); `byNotTag`; multi-tag membership (`Sos` found by each of its 4 tags); empty result; tag-not-present; empty Profile; **render gating** (`Failed`/`Skipped` excluded from `homeScreenTiles`, `Pending`/`Unverifiable` included); `toolbar()` returns the Toolbar entity with no `is Toolbar` check in the implementation.
- Green.

**Dependencies**: T127-013 | **[P]**

---

### T127-015 — Write ProfileHierarchyQueryTest

**Trace**: FR-012, US-4, SC-009.

**Acceptance**:
- `core/src/commonTest/kotlin/com/launcher/preset/query/ProfileHierarchyQueryTest.kt`.
- Fixture: workspace + 3 flows + tiles across flows + toolbar + 3 buttons.
- Cases: `roots()` → workspace only; `children(ws-main)` → flows + toolbar; `workspace()`; `flows()` ordered by `order`; `tilesOf(flow-calls)` returns only that flow's tiles (isolation); `toolbarButtons()` ordered; orphan entity (dangling `parentId`) absent from `children()` without crash; degenerate profile (no Flow entities) → `homeScreenTiles()` returns all tiles.
- Green.

**Dependencies**: T127-013 | **[P]**

---

## Phase 5 — Engine

### T127-016 — ReconcileEngine: NeedsUserConfirmation → Unverifiable

**Trace**: FR-014, plan §Risks R-11.

**Acceptance**:
- `ReconcileEngine.dispatchApply` / `dispatchCheck`: `Outcome.NeedsUserConfirmation` → `profile.mark(id, ComponentStatus.Unverifiable)`. Never `Applied`.
- Only the interactive path (Wizard / `RunMode.Single`) may record `Unverifiable`; `BootCheck` never creates it.
- Compiles.

**Dependencies**: T127-005

---

### T127-017 — ReconcileEngine: BootCheck skips Unverifiable

**Trace**: FR-014.

**Acceptance**:
- `runBootCheck` filter excludes `status == ComponentStatus.Unverifiable` (in addition to the existing `critical` filter) — no infinite re-nagging on cold start.
- `RunMode.Single` still re-checks such components (explicit Settings action).
- Compiles.

**Dependencies**: T127-016

---

### T127-018 — Write ReconcileEngineUnverifiableTest

**Trace**: FR-014, SC-011.

**Acceptance**:
- `core/src/commonTest/kotlin/com/launcher/preset/engine/ReconcileEngineUnverifiableTest.kt` (fake Provider returning `NeedsUserConfirmation`).
- Cases: wizard apply → status `Unverifiable` (not `Applied`); `BootCheck` over a profile containing an `Unverifiable` critical entity → provider NOT invoked, status unchanged; `RunMode.Single` on the same entity → provider invoked.
- Green.

**Dependencies**: T127-017 | **[P]**

---

### T127-019 — ProfileFactory + PresetValidator: hierarchy validation

**Trace**: FR-016, SC-012, US-4 AS-4.

**Acceptance**:
- `ProfileFactory.create` (or a helper it calls) validates after assembly: dangling `parentId` → `DanglingParentRef`; parent cycle → `CircularParentRef` (terminating on any input per NFR-005); `ToolbarButton.targetFlowId` not resolving to a `Flow` entity → `DanglingTargetRef`.
- Errors surface as values (domain never throws — existing convention).
- `PresetValidator` reuses the same checks for authoring-time validation (TASK-132 wires a build gate later).
- Test `core/src/commonTest/kotlin/com/launcher/preset/engine/ProfileFactoryHierarchyValidationTest.kt` covers all three + a valid hierarchy passing clean. Green.

**Dependencies**: T127-010, T127-013

---

## Phase 6 — Adapter

### T127-020 — Implement ProfileBackedFlowRepository

**Trace**: FR-006, US-1, US-4, data-model.md §ProfileBackedFlowRepository.

**Acceptance**:
- File: `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt`.
- Implements the **existing, unchanged** `FlowRepository` port — all four methods:
  - `loadFlows()` = `profileStore.observe().filterNotNull().first().toFlowDescriptors()` — **the regression path**.
  - `observeFlows()` = `observe().filterNotNull().map { it.toFlowDescriptors() }`.
  - `availableTemplates(presetId)` — parity with `ConfigBackedFlowRepository` (static catalogue).
  - `addFlow(templateId)` — `error(...)` + `// TODO(profile-add-flow): TASK-134`.
- `toFlowDescriptors()`: hierarchical projection (one `FlowDescriptor` per `Flow` entity, slots = `tilesOf(flow.id)`, name = `Flow.titleKey`, ordered by `order`); degenerate fallback (no `Flow` entities → single synthetic descriptor with all `homeScreenTiles()`).
- `Entity → SlotDescriptor`: `AppTile` → `Action.OpenApp(packageName)`, label from `labelKey`; `Sos` → SOS action; unmapped subtypes → `action = null` (placeholder card, existing UI contract).
- Zero Android imports. Compiles.

**Dependencies**: T127-013

---

### T127-021 — Write ProfileBackedFlowRepositoryTest

**Trace**: FR-006, NFR-002, SC-005, SC-009.

**Acceptance**:
- `core/src/commonTest/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepositoryTest.kt`, using existing `FakeProfileStore`.
- Cases: `loadFlows()` with saved profile → returns flows immediately (regression path); `loadFlows()` with null store → suspends (runTest, no completion); `observeFlows()` null → no emission; two saves → two emissions; **hierarchical projection** (workspace + 2 flows → 2 descriptors, slots isolated per flow, ordered); **degenerate projection** (tiles, no Flow entities → 1 descriptor with all tiles); render gating (Failed/Skipped tile absent from slots); `addFlow` throws.
- Green.

**Dependencies**: T127-020

---

### T127-022 — Mark ConfigBackedFlowRepository deprecated

**Trace**: FR-007, FR-010.

**Acceptance**:
- `core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt` (real location): add
  `// TODO(config-deprecation, SRV-CONFIG-DEPRECATION): ConfigDocument stays for admin push (spec 009); remove entirely when unified Profile-sync ships.`
- Class NOT deleted; its tests stay green.

**Dependencies**: none | **[P]**

---

## Phase 7 — DI + preset data

### T127-023 — Rebind FlowRepository in androidMockBackend

**Trace**: FR-007.

**Acceptance**:
- `core/src/androidMockBackend/kotlin/com/launcher/di/BackendInit.kt` (~line 165): `single<FlowRepository> { ProfileBackedFlowRepository(profileStore = get()) }`.
- Verify `ProfileStore` resolves in this Koin scope (currently bound in app-level `PresetModule`); if not visible, move/expose the binding and document the wiring in the PR.
- Old `ConfigBackedFlowRepository` binding removed (class stays).
- `./gradlew :app:assembleMockBackendDebug` green; no DI conflicts.

**Dependencies**: T127-020

---

### T127-024 — Rebind FlowRepository in androidRealBackend

**Trace**: FR-007.

**Acceptance**:
- `core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt` (~line 275): same rebind, same `ProfileStore`-visibility check.
- `./gradlew :app:assembleRealBackendDebug` green.

**Dependencies**: T127-023

---

### T127-025 — Add structural blueprints to bundled pool.json

**Trace**: FR-013, plan §Risks R-10.

**Acceptance**:
- Bundled `pool.json` gains blueprints for the structural entities the default preset needs: workspace, at least one flow, toolbar, toolbar buttons (stable ids, e.g. `ws-main`, `flow-*`, `toolbar-main`, `btn-*`).
- Doc-comment on `Blueprint` explains the tags-override mechanism (`"tags"` inside the embedded `component` object) vs constructor-default — closes FR-003.
- `Pool` schemaVersion unchanged (additive). `PoolSchemaV2RoundtripTest` still green; new declarations deserialize.

**Dependencies**: T127-008

---

### T127-026 — Make the default preset hierarchical

**Trace**: FR-013, US-4, plan §Risks R-10.

**Acceptance**:
- Default/bundled preset references the structural blueprints so `ProfileFactory` produces `Workspace` + `Flow`(s) + tiles with `parentId` set, plus `Toolbar` + `ToolbarButton`s when declared.
- **`parentId` wiring decision**: `ProfileFactory` must set it — either a new optional `parentRef` on the preset entry, or convention from the blueprint. Choose the additive option; document in the PR.
- Produced profile passes T127-019 validation.
- Degenerate one-flow presets keep working (no regression).

**Dependencies**: T127-019, T127-025

---

## Phase 8 — Integration + fitness

### T127-027 — Extend HomeComponentLoadingStateTest

**Trace**: US-1 AS-1, US-4 AS-2, SC-005, SC-010.

**Acceptance**:
- `core/src/commonTest/kotlin/com/launcher/ui/navigation/HomeComponentLoadingStateTest.kt` (real location — JVM commonTest, NOT androidTest).
- New: `postManifestWizardReconcile_profileSeeded_homeReady()` — seed `FakeProfileStore` with `AppTile("com.android.settings", "tile_settings")`, wire `ProfileBackedFlowRepository`, assert `Loading → Ready` with 1 flow.
- New: `hierarchicalProfile_rendersFlowsAndSwitches()` — workspace + 2 flows + toolbar buttons → `Ready` with 2 flows; `selectFlow(second)` → active flow changes.
- Existing config-based scenarios stay green.

**Dependencies**: T127-020, T127-023

---

### T127-028 — Write ComponentTagsFitnessTest

**Trace**: FR-002, NFR-001, SC-003.

**Acceptance**:
- `core/src/commonTest/kotlin/com/launcher/preset/model/ComponentTagsFitnessTest.kt`.
- Reflection walk over `Component::class.sealedSubclasses` — **expects 11**; instantiate each with dummy required params, assert non-empty default `tags`.
- Fails if a new subtype lacks a tags default.
- Green.

**Dependencies**: T127-008 | **[P]**

---

### T127-029 — Write ProfileQueryBenchmarkTest

**Trace**: NFR-003, NFR-005, SC-008.

**Acceptance**:
- `core/src/commonTest/kotlin/com/launcher/preset/query/ProfileQueryBenchmarkTest.kt`.
- Fixture ~40 entities (workspace + 3 flows + 30 tiles + toolbar + buttons).
- Measure `homeScreenTiles()`, `tilesOf()`, `flows()`, `toolbarButtons()`, `byTag()`; assert p95 < 1 ms.
- Green.

**Dependencies**: T127-013 | **[P]**

---

### T127-030 — Wizard string localization

**Trace**: FR-008, US-3, SC-002.

**Acceptance**:
- `core/src/commonMain/composeResources/values/strings_wizard.xml` — **file exists**; grep TASK-126 wizard code for used keys and add the missing ones.
- `wizard_step_of` as `<plurals>` (RU `one/few/many/other`); others as `<string>`; readable Russian values.
- Add a string for the `Unverifiable` confirmation prompt («Откройте настройки, включите …, вернитесь и нажмите "Я включил"») if the wizard renders one.
- Compose resources compile without warnings.

**Dependencies**: none | **[P]**

---

## Phase 9 — Docs + smoke

### T127-031 — Update docs/architecture/preset-model.md

**Trace**: FR-009, SC-006.

**Acceptance**:
- AI-TLDR block refreshed: two orthogonal dimensions (lifecycle `wizardFlow`/`settingsMap`/`activeComponents` vs semantic `Component.tags`) **plus** the third axis — structural hierarchy (`Entity.parentId`, Workspace/Flow/ToolbarButton).
- ECS ≈ database-table mental model table included.
- New vocabulary documented: `Entity`, `Blueprint`, `Unverifiable`.
- Plain Russian, readable by the owner.

**Dependencies**: T127-009 | **[P]**

---

### T127-032 — Doc-comments on Preset.kt / Component.kt

**Trace**: FR-009.

**Acceptance**:
- Both KDocs reference `docs/architecture/preset-model.md` and state the three axes (lifecycle / semantic tags / structural parentId).

**Dependencies**: T127-031 | **[P]**

---

### T127-033 — SRV-CONFIG-DEPRECATION entry in server-roadmap

**Trace**: FR-010, SC-006.

**Acceptance**:
- `docs/dev/server-roadmap.md` entry: remove `ConfigBackedFlowRepository`/`ConfigDocument` from the HomeScreen path when admin push migrates to Profile-based delivery. Trigger, cost, link back to TASK-127 FR-010.

**Dependencies**: T127-022 | **[P]**

---

### T127-034 — Emulator smoke: fresh install → wizard → HomeScreen

**Trace**: US-1, US-3, SC-002.

**Acceptance**:
- `./gradlew :app:installMockBackendDebug` on an available AVD (≤ API 34).
- Fresh install → wizard → HomeActivity shows tiles (not Error UI); wizard strings readable Russian (no raw `wizard_*`).
- **[deferred-local-emulator]** — AI session cannot verify visually.

**Dependencies**: T127-023, T127-027, T127-030

---

### T127-035 — Physical smoke: Xiaomi Redmi Note 11 (adb 17f33878)

**Trace**: US-1, US-4, SC-001, SC-002, SC-010.

**Acceptance**:
- Fresh install on device `17f33878` → wizard → HomeActivity shows tiles, no Error UI (SC-001).
- Wizard strings localized (SC-002).
- If the bundled preset ships a toolbar: tapping a toolbar button switches flows without Activity restart (SC-010).
- **[deferred-physical-device]** — requires the physical device.

**Dependencies**: T127-024, T127-027, T127-030

---

## Cross-Artifact Trace Summary

### Spec → Tasks coverage

- **FR-001** Tag enum (13) → T127-004 ✓
- **FR-002** Component.tags → T127-006, T127-007 ✓
- **FR-003** pool tags override via embedded component → T127-025 ✓
- **FR-004** schemaVersion 2 unchanged + constructor-defaults → T127-011, T127-012 ✓
- **FR-005** tag Query API → T127-013, T127-014 ✓
- **FR-006** ProfileBackedFlowRepository (4 methods) → T127-020, T127-021 ✓
- **FR-007** DI rebind → T127-023, T127-024 ✓
- **FR-008** strings_wizard.xml → T127-030 ✓
- **FR-009** preset-model.md → T127-031, T127-032 ✓
- **FR-010** SRV-CONFIG-DEPRECATION → T127-022, T127-033 ✓
- **FR-011** Entity.parentId → T127-009 ✓
- **FR-012** hierarchy query API → T127-013, T127-015 ✓
- **FR-013** Workspace/Flow/ToolbarButton → T127-008, T127-025, T127-026 ✓
- **FR-014** Unverifiable + NeedsUserConfirmation → T127-005, T127-016, T127-017, T127-018 ✓
- **FR-015** ECS rename → T127-002, T127-003 ✓
- **FR-016** hierarchy validation → T127-010, T127-019 ✓

**16/16 FRs covered.**

### User Stories → Test evidence

- **US-1** (install → wizard → home) → T127-027, T127-034, T127-035 ✓
- **US-2** (developer adds Component) → T127-014, T127-028 ✓
- **US-3** (wizard localization) → T127-030, T127-034, T127-035 ✓
- **US-4** (workspace + 3 flows + toolbar) → T127-015, T127-021, T127-026, T127-027, T127-035 ✓

### Contracts → Tests

- **profile-v2.md**: hierarchical roundtrip, missing-tags, missing-parentId, object-compat, 3 fail-loud pins → T127-011; fixtures → T127-012 ✓

### NFRs

- **NFR-001** domain isolation → T127-028 + checklist-domain-isolation ✓
- **NFR-002** emissions → T127-021 ✓
- **NFR-003 / NFR-005** perf + depth-agnostic → T127-029, T127-015 ✓
- **NFR-004** [REMOVED per Q6] ✓

---

## Manual Checkpoint Summary

| Checkpoint | Tasks | Status |
|---|---|---|
| Rename compiles | T127-002, T127-003 | `:core:test` + `:app:test*UnitTest` green |
| Build gates | T127-004 — T127-033 | all unit/contract tests pass |
| Fitness gates | T127-028, T127-029 | 11 subtypes tagged; p95 < 1 ms |
| Integration | T127-027 | JVM commonTest green |
| Emulator smoke | T127-034 | [deferred-local-emulator] |
| Physical smoke (Xiaomi) | T127-035 | [deferred-physical-device] |

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 35 задач в 9 фазах: (1) починить экран после мастера и (2) достроить ECS-фундамент, пока формат хранения не у пользователей.

**Порядок работы:**
- **Фаза 1 — переименование** отдельным коммитом: `ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint` (93 механические правки). Отдельно — чтобы смысловые изменения дальше читались в ревью чисто.
- **Фаза 2 — типы**: 13 тегов, теги на 11 подтипах, три новых типа (`Workspace`/`Flow`/`ToolbarButton`), колонка `parentId`, пятый статус `Unverifiable`, три новые ошибки валидации. Два «синглтона» становятся обычными классами (иначе на них нельзя навесить теги).
- **Фаза 3 — формат**: один тест-класс с иерархической фикстурой + совместимость + три «честных» пина (незнакомый тег/тип/статус роняют читателя — зафиксировано, а не спрятано).
- **Фаза 4 — запросы**: теги + иерархия (`children`, `flows`, `tilesOf`, `toolbarButtons`). Мёртвые плитки (`Failed`/`Skipped`) на экран не попадают.
- **Фаза 5 — движок**: «спроси человека» → статус `Unverifiable` (не враньё «применено»); проверка при старте такие пропускает; валидация иерархии.
- **Фазы 6-7 — адаптер и проводка**: новый `ProfileBackedFlowRepository` (все 4 метода порта; `loadFlows()` — тот самый путь, где рождалась ошибка), перепривязка DI, иерархический пресет.
- **Фазы 8-9 — тесты, документация, прогон** на эмуляторе и Xiaomi.

**Что важно:**
- `schemaVersion` **остаётся 2** — всё новое аддитивно, мигратор не пишем.
- **UI не трогаем**: `BottomFlowBar` и переключение вкладок уже есть с TASK-52 — наполняем их данными из профиля.
- Простой лаунчер (один flow, без тулбара) — тот же код, вырожденный случай.
- T127-034/035 помечены `[deferred-*]` — визуальную проверку закрывает владелец (Xiaomi `17f33878`).
- Открытый вопрос реализации (T127-023): `ProfileStore` сейчас байндится в app-модуле — проверить видимость из core backend-модуля.
- Открытый вопрос (T127-026): как `ProfileFactory` проставляет `parentId` — новое опциональное поле в записи пресета или соглашение по blueprint'у. Выбрать аддитивный вариант, задокументировать в PR.
