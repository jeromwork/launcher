# Tasks: Canonical ECS — foundational launcher-config model (TASK-136)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Data model**: [data-model.md](data-model.md)
**Contracts**: [profile-serialization.md](contracts/profile-serialization.md) · [ecs-world-core.md](contracts/ecs-world-core.md) · [query-api.md](contracts/query-api.md)
**Backlog task**: [task-136](../../backlog/tasks/task-136%20-%20Decision-ECS-canonical-foundational-model.md) (Decision block = authority)
**Supersedes**: TASK-120, TASK-127, ADR-012.

---

## Phases Overview

| Phase | Description | Tasks |
|-------|-----------|-------|
| **0. Verify** | Consumer-inventory grep against real code, no behaviour | T136-001 |
| **1. Core model reshape** | `Component` sealed interface, `LifecycleState`, `Entity` free bag, `Blueprint` bundle, `Profile` accessors, `Preset`/`ActiveComponentEntry` status-field removal | T136-002 — T136-006, T136-046 |
| **2. Own ECS World core** | `preset/ecs/` — spawn DSL, `get<T>()`/compose ops, `Family`, World seam | T136-007 — T136-010 |
| **3. Engine** | `ProfileFactory` spawn, `ReconcileEngine` state transitions, validators | T136-011 — T136-013 |
| **4. Query API** | Tag selectors + hierarchy selectors + render gating | T136-014 — T136-015 |
| **5. Serialization + wire tests** | Entity-grouped serializer, roundtrip/placement/fail-loud, migration removal | T136-016 — T136-021 |
| **6. Consumers** | `ProfileBackedFlowRepository`, `WizardScreen`, `PostWizardKioskApply` + app status-consumers (`PendingChecklistViewModel`, `WizardViewModel`, `FirstLaunchActivity`) | T136-022 — T136-024, T136-047 — T136-049 |
| **7. Assets in place** | `pool.json` bundles, `bundled-presets/*.json`, fixtures + golden | T136-025 — T136-027 |
| **8. Unit + fitness tests** | Composition, query, spawn, hierarchy, engine-state; 6 fitness functions | T136-028 — T136-038 |
| **9. Cleanup inventory** | preset-model.md rewrite, ADR-013, superseded-by, downstream notice | T136-039 — T136-042 |
| **10. Gates + smoke** | Consistency grep gates, build gate, emulator smoke | T136-043 — T136-045 |

**Total tasks**: 49 | **Parallel-safe**: 22 marked `[P]`
**Deferred markers**: 1 × `[deferred-local-emulator]` (T136-045). No `[deferred-physical-device]` — pure `:core` JVM domain refactor, no OEM/permission/HOME-role surface (spec § OEM / Device Matrix = N/A).

> **Punch-list addendum (post-`speckit-analyze`, 2026-07-18)**: T136-046 (Preset/`ActiveComponentEntry` status-field removal — a Preset JSON wire-format change) and T136-047–T136-049 (three app-layer `Entity.status`/`ComponentStatus` consumers the original inventory missed) close the cross-artifact-trace gaps flagged by the analyze punch-list (caveats 1 & 2). IDs are appended (no renumber) and slotted into their logical phase (1 and 6) via dependencies.

> **Big-bang note (FR-016)**: this is one coherent PR. The phase order is a *reviewable-diff* ordering, not a set of separately-shippable slices — the tree does not compile until Phase 6 consumers are migrated. The build gate (T136-044) is the single integration checkpoint.

> **ID convention**: `T136-NNN` mirrors TASK-127's `T127-NNN` (task-id-prefixed, per this repo's established pattern).

---

## Phase 0 — Verify (no behaviour)

### T136-001 — [x] [P] Verify consumer inventory against real code

**Trace**: Plan §Migration/rewrite plan (Consumer inventory), FR-016.

**Acceptance**:
- Grep confirms every `Entity.component` / `Blueprint.component` / `ComponentStatus` / `Entity.status` / `.component.tags` / `component as? Component.` / `.mark(` / `.replaceComponent(` site matches the plan's Consumer inventory: `ProfileQuery.kt`, `ProfileFactory.kt`, `ReconcileEngine.kt`, `PresetValidator.kt`, `PresetDiff.kt`, `ProfileBackedFlowRepository.kt`, `WizardScreen.kt`, `PostWizardKioskApply.kt`, `Profile.kt`, `Preset.kt` (`ActiveComponentEntry.status`), **plus the three app-layer status readers** `wizard/WizardViewModel.kt`, `firstlaunch/FirstLaunchActivity.kt`, `settings/PendingChecklistViewModel.kt`, plus fixtures/presets.
- Confirm Decompose false-positives excluded: `child.component` in `ui/RootContent.kt` and `RootComponent.kt:153` are navigation `component`, NOT `Entity.component` (plan false-positives note).
- Any newly-discovered callsite beyond the inventory is recorded in the PR description before migration proceeds.

**Dependencies**: none | **[P]**

---

## Phase 1 — Core model reshape (`core/src/commonMain/.../preset/model/`)

### T136-002 — [x] `Component`: sealed class → sealed interface, drop `tags` from 11 subtypes

**Trace**: FR-002, FR-003, data-model §2.

**Acceptance**:
- `Component.kt`: `sealed class Component { abstract val tags }` → `sealed interface Component` with **no `tags` member**.
- All 11 domain-data subtypes (`AppTile`, `FontSize`, `Sos`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`, `Workspace`, `Flow`, `ToolbarButton`) keep every data field verbatim; only the `tags` constructor parameter is removed. Subtypes stay nested (`Component.AppTile`) so `@SerialName` discriminators and references are byte-identical (data-model §2 nesting note).
- `LauncherRole` / `StatusBarPolicy` remain `data class` (no churn).
- `AdminLocked` is NOT added (CL-1 retracted).
- `./gradlew :core:compileKotlinJvm` fails only at the (yet-unmigrated) tags/status consumers — the type itself compiles.

**Dependencies**: none

---

### T136-003 — [x] Add `LifecycleState` sealed component; delete `ComponentStatus` enum

**Trace**: FR-STATE, CL-5, Plan §State encoding decision, data-model §5.

**Acceptance**:
- New `LifecycleState : Component` sealed interface with 5 variants: `Pending`, `Applied`, `Skipped`, `Unverifiable` (`data object`) + `Failed(reason: FailReason)` (`data class`), each with its own `@SerialName` distinct from the 11 data components.
- `enum class ComponentStatus { Pending, Applied, Failed, Skipped, Unverifiable }` in `Enums.kt` **deleted** (five semantics preserved as `LifecycleState` variants, incl. `Unverifiable`/FR-014).
- `FailReason` (existing) rides on `Failed`. `Outcome.NeedsUserConfirmation` (existing) unchanged.
- `LifecycleState` is the 12th member of the closed `Component` set; exhaustive `when` still compiles.

**Dependencies**: T136-002

---

### T136-004 — [x] `Entity` → free bag (`components: List<Component>` + `tags: Set<Tag>`), delete `status`

**Trace**: FR-001, FR-013, data-model §1.

**Acceptance**:
- `Entity(id: String, components: List<Component> = emptyList(), tags: Set<Tag> = emptySet(), parentId: String? = null, wizardBehavior = AutoApply, critical = false)`.
- `component: Component` field removed; `status: ComponentStatus` field removed (apply-state is a `LifecycleState` in the bag, CL-5).
- `id` stays `String` (FR-013 — stable for serialization + zero-knowledge, not Fleks `Int`); `parentId` survives (flat storage, tree computed).
- `Tag` enum in `Enums.kt` unchanged (13 values); only its home moved to `Entity`.

**Dependencies**: T136-002, T136-003

---

### T136-005 — [x] `Blueprint` → Bundle (`components: List<Component>` + `tags: Set<Tag>`)

**Trace**: FR-004, data-model §4.

**Acceptance**:
- `Blueprint(id, components: List<Component> = emptyList(), tags: Set<Tag> = emptySet(), wizardBehavior, critical, descriptionKey, requires, required)` in `Pool.kt`.
- Single `component: Component` field removed. `requires`/`required` survive (TASK-126). `Pool` shape (`declarations: List<Blueprint>`, `byId`) unchanged.
- Bundle is a spawn template only — no identity retained (verified: Bevy Bundle "zero runtime significance after creation").

**Dependencies**: T136-002

---

### T136-006 — [x] `Profile`: rename `components` → `entities`; `mark`/`replaceComponent` → `setState`/`with`/`without`

**Trace**: FR-001, data-model §6.

**Acceptance**:
- `Profile.components: List<Entity>` → `entities: List<Entity>`. `schemaVersion` (=2), `basedOnPreset`, `presetVersion`, `layoutKey`, `preWizardSnapshot`, `snapshotTimestamp`, `unknownRefs`, `state` all survive. `CURRENT_SCHEMA_VERSION = 2` unchanged.
- `mark(id, ComponentStatus)` → `setState(id, LifecycleState)`; `replaceComponent(id, Component)` → `with(id, Component)`; add `without(id, KClass<out Component>)`. Bodies built on the ecs compose primitives (Phase 2).
- Method bodies may be stubbed here and completed in T136-008 (compose ops) — mark the stub with a `TODO` resolved by dependency.

**Dependencies**: T136-004

---

### T136-046 — [x] `Preset`/`ActiveComponentEntry`: remove `status` field (Preset wire-format change)

**Trace**: FR-STATE, CL-5, SC-009, data-model §"Delete / rename summary", contracts/profile-serialization.md §Removed vs TASK-127.

**Punch-list origin**: analyze caveat 1 (real drift). `ActiveComponentEntry.status: ComponentStatus = ComponentStatus.Pending` ([Preset.kt:53](../../core/src/commonMain/kotlin/com/launcher/preset/model/Preset.kt#L53)) is a **serialized** field referencing the `ComponentStatus` enum deleted in T136-003 — the original tasks.md/data-model.md missed it.

**Acceptance**:
- `ActiveComponentEntry.status: ComponentStatus` field **removed** entirely. In canonical ECS the preset entry declares only WHAT to spawn (`poolRef` + `paramsOverride` + `parentRef`); the initial apply-state is injected by `ProfileFactory` as a `LifecycleState.Pending` component in the entity bag (T136-011), never carried in the preset.
- This is a **Preset JSON wire-format change** (a serialized field is removed). Pre-release clean-in-place per Article XX: **no migrator, no `Preset.CURRENT_SCHEMA_VERSION` bump** (stays 2) — consistent with the Profile/Pool removal in T136-021. Old preset JSON carrying a stray `"status"` key is silently ignored (`ignoreUnknownKeys=true`); presets that omit it (all bundled presets do) are unaffected.
- No `import com.launcher.preset.model.ComponentStatus` remains in `Preset.kt`.
- Grep-verify: the only production `ActiveComponentEntry(..., ComponentStatus.X)` construction site was `ProfileFactory.kt:34` (rewritten by T136-011); every other construction (tests, fixtures) already uses the 1–2-arg form and needs no change.
- **No bundled-preset / `pool.json` fixture rewrite for this field** — grep confirmed none of `app/src/main/assets/preset/**` (nor `core/**/assets/presets/**`) serialize a `status` on active-component entries. (Asset rewrites for the bundle/entity-grouped shape are the separate T136-025/026 concern.)

**Dependencies**: T136-003

---

## Phase 2 — Own ECS World core (`core/src/commonMain/.../preset/ecs/` — NEW package)

> Contract: [ecs-world-core.md](contracts/ecs-world-core.md). Concrete to `Component`/`Tag` (no generic universe, rule 4). Zero Android imports (NFR-001).

### T136-007 — [x] `EntityDsl.kt`: `entity(id){}` spawn DSL + `EntityBuilder`

**Trace**: FR-005 (spawn support), FR-012, contracts/ecs-world-core.md §Spawn.

**Acceptance**:
- `fun entity(id: String, block: EntityBuilder.() -> Unit): Entity` with `EntityBuilder { component(c); tag(t); parent(id?); var wizardBehavior; var critical }`.
- `component(c)` enforces at-most-one-per-type at build (last-wins or reject — pinned by T136-033).
- Mirrors Fleks `world.entity {}` vocabulary.

**Dependencies**: T136-004

---

### T136-008 — [x] Compose ops: `Entity.get<T>()` / `with` / `without` / `withTag` / `withoutTag`

**Trace**: FR-006, FR-007, data-model §8, contracts/ecs-world-core.md.

**Acceptance**:
- `inline fun <reified T : Component> Entity.get(): T? = components.filterIsInstance<T>().firstOrNull()`.
- `fun Entity.with(c: Component): Entity` (add / replace same-type — upholds at-most-one-per-type); `inline fun <reified T> Entity.without(): Entity`; `withTag`/`withoutTag`.
- `Profile.setState`/`with`/`without` (T136-006 stubs) now delegate to these ops.
- No field added to any `Component` subtype (composition is bag-level).

**Dependencies**: T136-007, T136-006

---

### T136-009 — [x] `Family.kt`: `FamilyBuilder { all/any/none }` + `Profile.family {}`

**Trace**: FR-008 (basis), FR-012, contracts/ecs-world-core.md §Query.

**Acceptance**:
- `class FamilyBuilder { fun all(vararg t: Tag); fun any(vararg t: Tag); fun none(vararg t: Tag) }`; `fun Profile.family(block: FamilyBuilder.() -> Unit): List<Entity>` — linear scan over `entities`.
- Mirrors Fleks `world.family { all/any/none }` vocabulary.

**Dependencies**: T136-004

---

### T136-010 — [x] `World.kt`: World = Profile's entity bag + `TODO(ecs-fleks-migration)` seam

**Trace**: FR-012, FR-013, contracts/ecs-world-core.md §World seam.

**Acceptance**:
- `World.kt` documents "the World is `Profile`'s entity bag" and carries verbatim:
  `// TODO(ecs-fleks-migration): World internals swappable to Fleks; cost = Kotlin 2.0→2.4 upgrade + String→Int id remap; persistence stays ours.`
- No per-frame system scheduler, no generic component universe, no archetype/sparse-set storage (contract §"deliberately does NOT have").

**Dependencies**: T136-009

---

## Phase 3 — Engine (`core/src/commonMain/.../preset/engine/`)

### T136-011 — [x] `ProfileFactory` spawn: flat bundle expansion + inline + `paramsOverride` + `parentRef`

**Trace**: FR-005, Capability Story 3, data-model §7.

**Acceptance**:
- A preset entry's `Blueprint`-ref expands into its component set, plus optional inline components, **all flattened into one set** (no "base vs extra", CL-2), + `paramsOverride` merge + `parentRef` → free-bag `Entity` via `entity(id){}`.
- Bundle id NOT retained as entity identity; two entries from one bundle are independent entities.
- Each reconcilable entity spawned with `LifecycleState.Pending` already in the bag (mirrors old `status = Pending` default). This is now the **only** source of initial apply-state — the preset no longer carries it (T136-046 removed `ActiveComponentEntry.status`).
- The `wizardFlow`→`activeComponents` fallback construction (`ProfileFactory.kt:34`, `ActiveComponentEntry(wf.poolRef, wf.paramsOverride, ComponentStatus.Pending)`) drops its 3rd arg → `ActiveComponentEntry(wf.poolRef, wf.paramsOverride)`; the `status = entry.status` Entity field (`ProfileFactory.kt:49`) is replaced by the spawned `LifecycleState.Pending` component.
- `applyOverride` now merges into the targeted component obtained via `get<T>()`.

**Dependencies**: T136-005, T136-008, T136-046

---

### T136-012 — [x] `ReconcileEngine`: per-component dispatch + `LifecycleState` transitions

**Trace**: FR-011, FR-STATE, Plan §engine per-component dispatch.

**Acceptance**:
- Iterate each domain-data component in an entity's bag; resolve a `Provider` per component through the unchanged `ProviderRegistry`; apply.
- Record result by swapping the entity's `LifecycleState` via `profile.setState(id, …)` — NOT by writing a `status` field. `Ok`→`Applied`, `Failed`→`Failed(reason)`, `NeedsUserConfirmation`→`Unverifiable`.
- Structural components (`Workspace`/`Flow`/`ToolbarButton`) and `LifecycleState` resolve to `NoOpProvider` (no OS effect) — same fallback as today.
- `BootCheck` still skips `Unverifiable`. `Provider<T : Component>` / `ProviderRegistry.resolve` signatures unchanged (ports intact).

**Dependencies**: T136-008, T136-006

---

### T136-013 — [x] `validateHierarchy` + `PresetValidator` + `PresetDiff` read via `get<T>()`

**Trace**: FR-014, data-model §7.

**Acceptance**:
- `ProfileFactory.validateHierarchy()` reads components via `get<T>()` (`it.get<Component.Flow>() != null`, `entity.get<Component.ToolbarButton>()`); preserves typed errors `DanglingParentRef`, `CircularParentRef`, `DanglingTargetRef`.
- `PresetValidator` / `PresetDiff`: `decl.component` → `decl.components`.

**Dependencies**: T136-011, T136-008

---

## Phase 4 — Query API (`core/src/commonMain/.../preset/query/ProfileQuery.kt`)

> Contract: [query-api.md](contracts/query-api.md).

### T136-014 — [x] Tag selectors over `entity.tags`

**Trace**: FR-008, contracts/query-api.md §Tag selectors.

**Acceptance**:
- `query`, `byTag`, `byAllTags`, `byAnyTag`, `byNotTag` read `it.tags` (was `it.component.tags`). Thin wrappers over `family {}` where convenient.
- Linear scan (~20–40 entities), < 1 ms (NFR-002).

**Dependencies**: T136-009, T136-004

---

### T136-015 — [x] Hierarchy selectors via `get<T>()` + render gating via `LifecycleState`

**Trace**: FR-009, contracts/query-api.md §Hierarchy + Render gating.

**Acceptance**:
- `children`, `roots`, `workspace`, `flows` (sortedBy `get<Flow>()?.order`), `toolbar`, `toolbarButtons` (sortedBy `get<ToolbarButton>()?.order`), `tilesOf`, `homeScreenTiles` — same TASK-127 semantics, component reads via `get<T>()`.
- Render gating: `isHiddenFromScreen()` reads `get<LifecycleState>()`, hides `Failed`/`Skipped`; `Pending`/`Applied`/`Unverifiable`/absent → renders. Degenerate profile (no `Flow`) → all tiles (US-1 parity).

**Dependencies**: T136-014, T136-008

---

## Phase 5 — Serialization + wire tests (`core/src/commonMain` + `core/src/commonTest/.../preset/wire/`)

> Contract: [profile-serialization.md](contracts/profile-serialization.md).

### T136-016 — [x] Entity-grouped polymorphic serialization config

**Trace**: FR-010, contracts/profile-serialization.md §Shape.

**Acceptance**:
- `Profile`/`Entity`/`Component` are the `@Serializable` wire types (no separate DTO). JSON is entity-grouped: `{ schemaVersion, …, entities:[ {id, parentId, components:[{type,…}], tags:[…]} ] }`.
- kotlinx polymorphic, `classDiscriminator="type"`, `ignoreUnknownKeys=true`, `encodeDefaults=true` — **zero custom serializer**. Json config mirrors `DataStoreProfileStore` exactly.
- `LifecycleState` variants serialize by their own `@SerialName`, distinct from the 11 data components. `schemaVersion` stays `2`.

**Dependencies**: T136-004, T136-005, T136-003

---

### T136-017 — [x] [P] `ProfileSerializationRoundtripTest` + fixtures (contract test 1)

**Trace**: SC-003, Capability Story 4, contracts/profile-serialization.md §1.

**Acceptance**:
- Mixed profile (workspace + 2 flows + tiles + toolbar + 2 buttons + one entity carrying a data-component **and** a `LifecycleState` **and** ≥3 tags) → JSON → `Profile`, `assertEquals`.
- New fixtures authored in place under `core/src/commonTest/resources/fixtures/profile-wire-format/`.

**Dependencies**: T136-016

---

### T136-018 — [x] [P] Polymorphic-list-placement test (contract test 2)

**Trace**: contracts/profile-serialization.md §2, Capability Story 4 scenario 2.

**Acceptance**:
- An entity with several components serializes them as one polymorphic array inside the entity (`entities[i].components:[…]`), not as separate top-level per-type tables. Assert JSON shape.

**Dependencies**: T136-016

---

### T136-019 — [x] [P] Fail-loud pins test (contract test 5)

**Trace**: contracts/profile-serialization.md §5, Edge Cases (unknown type/Tag).

**Acceptance**:
- Unknown component `type` → `SerializationException`; unknown `Tag` value → `SerializationException` (honest contract inherited from TASK-127; lenient reader is a documented future step, not built here).

**Dependencies**: T136-016

---

### T136-020 — [x] Update `Pool`/`Blueprint` roundtrip tests to bundle shape

**Trace**: FR-020, Plan §Test strategy ("rewrite roundtrip suites").

**Acceptance**:
- `PoolRoundtripTest`, `PoolSchemaV2RoundtripTest`, `ProfileSchemaV2RoundtripTest`, and `roundtrip/Fixtures.kt` updated to the bundle / free-bag shape (Blueprint `components:[…]`+`tags`, Entity `components:[…]`+`tags`).
- **Assumption flagged**: plan explicitly names only *Profile* serialization tests; Pool roundtrip updates are implied by "rewrite roundtrip suites" — treated as in-scope because `Blueprint` reshape breaks them.

**Dependencies**: T136-005, T136-016

---

### T136-021 — [x] Delete Profile/Pool migration + backward-compat readers; grep-verify none remain

**Trace**: SC-009, NFR-004, FR-010, contracts/profile-serialization.md §Removed vs TASK-127.

**Acceptance**:
- Delete `roundtrip/BackwardCompatTest.kt` (Profile v-prev reader), `wire/PoolSchemaV1ReadV2Test.kt` (pre-bundle v1→v2 reader), and any `ProfileMigration*` test/source.
- No migration writer / backward-compat reader for `Profile`/`Pool` remains in the tree (grep). `schemaVersion` field present = 2, not bumped.
- **`PresetSchemaV1ReadV2Test` is kept** — its v1→v2 concern (`hintFlow` + `wizardPresentation`, TASK-126) is orthogonal to this task, and it does not reference `status`, so it stays green. Note: `Preset` **IS** reshaped by this task — `ActiveComponentEntry.status` is removed (T136-046, a Preset JSON wire-format change), but that field was default-omitted from every bundled preset JSON and `ignoreUnknownKeys=true` means a stray legacy `status` key is silently dropped on read, so no Preset backward-compat reader is needed (pre-release clean-in-place, Article XX — consistent with the Profile/Pool removals above). Correction of the earlier draft's claim that "`Preset` is not reshaped".

**Dependencies**: T136-020

---

## Phase 6 — Consumers (adapters + UI)

### T136-022 — [x] `ProfileBackedFlowRepository`: `get<Component.Flow>()` + `toSlot`

**Trace**: FR-016, data-model §7.

**Acceptance**:
- `flowEntity.component as? Component.Flow` → `flowEntity.get<Component.Flow>()`; `toSlot`'s `when(component)` → `entity.get<Component.AppTile>()`.
- `FlowRepository` port contract unchanged (`api/FlowRepository.kt` intact).

**Dependencies**: T136-015

---

### T136-023 — [x] `WizardScreen`: `when(pc.component)` → `get<T>()`

**Trace**: FR-016, data-model §7.

**Acceptance**:
- The big `when(pc.component)` rewritten over the entity's domain-data component obtained via `pc.get<T>()`.
- No `entity.component` reference remains in `WizardScreen.kt`.

**Dependencies**: T136-008

---

### T136-024 — [x] `PostWizardKioskApply`: `comp is X` → `pc.get<X>()`, `mark(…, ComponentStatus)` → `setState(…, LifecycleState)`

**Trace**: FR-016, FR-STATE, data-model §7.

**Acceptance**:
- `profile.components` → `profile.entities`; the per-entity kiosk detection `comp is StatusBarPolicy || comp is LauncherRole` → `pc.get<Component.StatusBarPolicy>() != null || pc.get<Component.LauncherRole>() != null`, and the component passed to `registry.resolve(...)` / `provider.apply(...)` is obtained via `pc.get<T>()` (free bag; no single `pc.component`).
- The four `current.mark(pc.id, ComponentStatus.Applied/Failed/Skipped/Unverifiable)` calls → `current.setState(pc.id, LifecycleState.Applied/Failed(…)/Skipped/Unverifiable)` (mark→setState per T136-006; `ComponentStatus` deleted per T136-003). `import com.launcher.preset.model.ComponentStatus` removed.
- DI bindings (`BackendInit.kt` mock/real) unchanged — binding shape intact.

**Dependencies**: T136-006, T136-008

---

> **Punch-list app-layer status-consumers (T136-047–T136-049)** — analyze caveat 2 (real drift). These three production files read `Entity.status` / `ComponentStatus` and were **missing** from the original Phase-6 inventory. Each reads the deleted `Entity.status` field and must derive apply-state from `entity.get<LifecycleState>()`; each also references `profile.components` (renamed to `entities`, T136-006).

### T136-047 — [x] `PendingChecklistViewModel` (+ test): `Entity.status` → `get<LifecycleState>()`

**Trace**: FR-016, FR-STATE, data-model §7.

**Acceptance**:
- [`PendingChecklistViewModel.kt`](../../app/src/main/java/com/launcher/app/settings/PendingChecklistViewModel.kt): `import ComponentStatus` removed; `profile.components` → `profile.entities`; `needsAttention()` predicate `status != ComponentStatus.Applied` → `get<LifecycleState>() !is LifecycleState.Applied` (absent state treated as not-Applied, i.e. still needs attention); the `Item.status: ComponentStatus` field and its `status = pc.status` population retype to `LifecycleState` (or drop if the UI does not render the concrete state — pin during impl).
- [`PendingChecklistViewModelTest.kt`](../../app/src/test/java/com/launcher/app/settings/PendingChecklistViewModelTest.kt) reshaped: `ComponentStatus.Pending/Failed/Applied` fixture entities → entities carrying `LifecycleState.Pending/Failed(...)/Applied` in the bag; assertions on `Item.status` retyped accordingly.

**Dependencies**: T136-006, T136-008

---

### T136-048 — [x] `WizardViewModel` (+ test): `Entity.status`/`profile.components` reads

**Trace**: FR-016, FR-STATE, data-model §7.

**Acceptance**:
- [`WizardViewModel.kt`](../../app/src/main/java/com/launcher/app/wizard/WizardViewModel.kt): `profile.components` → `profile.entities` (incl. `currentComponents = profile.entities`); the debug-log `"${it.id}:${it.status}:${it.wizardBehavior}"` (line ~101) reads `it.get<LifecycleState>()` instead of the deleted `it.status` (or drops the state from the log line).
- [`WizardViewModelTest.kt`](../../app/src/test/java/com/launcher/app/preset/task126/WizardViewModelTest.kt) reshaped to the free-bag `Entity`/`Preset` (no `status` arg) shape; existing `ActiveComponentEntry("font")` etc. constructions are already status-free and compile once the model reshapes.

**Dependencies**: T136-006, T136-008

---

### T136-049 — [x] `FirstLaunchActivity`: wizard-done gate via `get<LifecycleState>()`

**Trace**: FR-016, FR-STATE, data-model §7.

**Acceptance**:
- [`FirstLaunchActivity.kt`](../../app/src/main/java/com/launcher/app/firstlaunch/FirstLaunchActivity.kt) `proceedToHome()` (line ~645): `profile.components.none { … pc.status != ComponentStatus.Applied }` → `profile.entities.none { … pc.get<LifecycleState>() !is LifecycleState.Applied }` (an Interactive component with no/other state ⇒ wizard not done). `import`/fully-qualified `ComponentStatus` reference removed.
- No test exists for this Activity today; behaviour is exercised by the build gate (T136-044) + emulator smoke (T136-045).

**Dependencies**: T136-006, T136-008

---

## Phase 7 — Assets rewritten in place

### T136-025 — [x] `pool.json` blueprints → bundles; keep `BundledSource` + `TODO(shareability)` seam

**Trace**: FR-020, rule 9, Plan §Wire formats, Risk R-8.

**Acceptance**:
- `app/src/main/assets/preset/pool.json` rewritten: each blueprint carries `components:[{type,…}]` + `tags:[…]` (bundle shape).
- `ConfigSource` / `BundledSource`-as-one-of-many loading shape retained; the `// TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace` seam kept at the `BundledSource` site (rule 9 — the ECS reshape is internal to the artifact).

**Dependencies**: T136-005

---

### T136-026 — [x] `bundled-presets/*.json` entity-grouped

**Trace**: FR-020, Plan §Wire formats.

**Acceptance**:
- `bundled-presets/launcher.json`, `simple-launcher.json`, `workspace.json` rewritten to entity-grouped shape (`entities:[ {id, parentId, components:[…], tags:[…]} ]`).
- Tags declared explicitly per entity (CL-4, no auto-derivation).

**Dependencies**: T136-016

---

### T136-027 — [x] Rewrite wire fixtures + `simple-launcher-profile-golden.json`

**Trace**: FR-020, Plan §Migration step 5.

**Acceptance**:
- `core/src/androidUnitTest/resources/fixtures/simple-launcher-profile-golden.json` and any remaining Profile/Pool wire fixtures rewritten to the free-bag / bundle / entity-grouped shape.
- Golden fixture round-trips through the new serializer (used by T136-017 or its own golden test).

**Dependencies**: T136-016

---

## Phase 8 — Unit + fitness tests

> Each contract gets ≥1 test task: serialization → T136-017/018/019/032/033; ecs-world-core → T136-028/036/037; query-api → T136-028/029/031.

### T136-028 — [x] [P] `EntityCompositionTest` (Capability Story 1)

**Trace**: SC-001, FR-006, FR-007, contracts/ecs-world-core.md.

**Acceptance**:
- *Tags:* an `Entity` carries several `Tag` markers at once; `byAllTags`/`byAnyTag` find it by any valid combination.
- *Component (test-only fake):* add a `TestFlag : Component` fake (commonTest-only, never shipped) to an `AppTile` entity and to a `Workspace` entity via `with()`; `get<TestFlag>()` finds it, base component still present via `get<T>()`, no subtype gained a field.

**Dependencies**: T136-014, T136-008

---

### T136-029 — [x] [P] `ProfileQueryTest` (Capability Story 2)

**Trace**: SC-002, FR-008, NFR-002, contracts/query-api.md.

**Acceptance**:
- `byTag`/`byAllTags`/`byAnyTag`/`byNotTag` over `entity.tags` (AND/OR/NOT/empty/not-present); `get<Component.Flow>()?.order` returns order; typed access returns component-or-`null` with no manual `as?`.
- Update the existing `ProfileQueryBenchmarkTest` (a real consumer on the old `Entity` shape) to the free-bag shape; it also carries NFR-002 (< 1 ms linear scan at ~20–40 entities) evidence.

**Dependencies**: T136-014

---

### T136-030 — [x] [P] `ProfileFactoryTest` (Capability Story 3)

**Trace**: SC-005, FR-005.

**Acceptance**:
- Spawn free bag from bundle (2 components) + 1 inline + `paramsOverride` + `parentRef` → `Entity` with 3 components; `(Bundle, Extra)` flattened; bundle id not retained as identity; two entries from one bundle are independent bags; each reconcilable entity carries `LifecycleState.Pending`.

**Dependencies**: T136-011

---

### T136-031 — [x] [P] `ProfileQueryHierarchyTest` + `ValidateHierarchyTest` (Capability Story 5)

**Trace**: SC-005, FR-009, FR-014, contracts/query-api.md §Semantics pinned.

**Acceptance**:
- Profile `Workspace` + 3×`Flow` + tiles + `Toolbar` + 3×`ToolbarButton`: `flows()`→3 ordered, `tilesOf(flowId)`→own tiles only (minus `Failed`/`Skipped`), `toolbarButtons()`→3, `children`/`roots` parity with TASK-127; orphan `parentId` silently absent, no crash.
- `validateHierarchy()` returns `DanglingParentRef`/`CircularParentRef`/`DanglingTargetRef`.

**Dependencies**: T136-015, T136-013

---

### T136-032 — [x] [P] `ReconcileEngineStateTest` (FR-STATE)

**Trace**: FR-STATE, FR-011, Plan §Test strategy.

**Acceptance**:
- Provider `Ok` → entity gains `LifecycleState.Applied`; `Failed` → `Failed(reason)` with data intact; `NeedsUserConfirmation` → `Unverifiable`; `BootCheck` skips `Unverifiable`; render gating hides `Failed`/`Skipped`.

**Dependencies**: T136-012, T136-015

---

### T136-033 — [x] [P] `ComponentCoverageFitnessTest` (contract test 4)

**Trace**: SC-006, FR-015a, contracts/profile-serialization.md §4.

**Acceptance**:
- Every `Component` subtype (11 data + `LifecycleState`) has a working serializer; exhaustive `when` compiles; each data subtype needing an effector has a `Provider` (structural + state components exempt / `NoOpProvider`).
- Reworks / replaces existing `ComponentProviderCoverageTest`.

**Dependencies**: T136-003, T136-016

---

### T136-034 — [x] [P] `AtMostOneComponentPerTypeFitnessTest` (contract test 3)

**Trace**: FR-015d, CL-3, contracts/profile-serialization.md §3, contracts/ecs-world-core.md.

**Acceptance**:
- No entity (across every fixture + `ProfileFactory` output) holds two components of the same Kotlin type ⇒ `get<T>()` unambiguous. `LifecycleState` is its own type — no collision with data components (Risk R-2).

**Dependencies**: T136-008, T136-026, T136-027

---

### T136-035 — [x] [P] `TagConsistencyFitnessTest`

**Trace**: FR-015e, CL-4, contracts/ecs-world-core.md.

**Acceptance**:
- Tags assigned only explicitly (bundle at spawn / composing code); no auto-derivation from components.
- Reworks / replaces existing `ComponentTagsFitnessTest` (which asserted tags-on-component — now obsolete).

**Dependencies**: T136-004, T136-026

---

### T136-036 — [x] [P] `paramsOverride` schema roundtrip fitness

**Trace**: FR-015c.

**Acceptance**:
- `paramsOverride` merge is schema-roundtrip stable (override → spawn → serialized component matches expected). Carries the existing TASK-127 paramsOverride fitness coverage forward onto the free-bag shape.

**Dependencies**: T136-011, T136-016

---

### T136-037 — [x] [P] Core LOC-budget fitness + `TODO(ecs-fleks-migration)` seam grep

**Trace**: SC-011, NFR-003, FR-012, contracts/ecs-world-core.md §Invariants.

**Acceptance**:
- `preset/ecs/` package ≤ ~400 LOC (fitness fails the build above budget — guards against regressing to a game runtime, Risk R-3).
- Grep confirms the `TODO(ecs-fleks-migration)` seam present on `World.kt`.

**Dependencies**: T136-010

---

### T136-038 — [x] [P] Domain-isolation import-guard on `Entity`/`Component`/`Blueprint`/`preset/ecs`

**Trace**: SC-008, FR-015b, NFR-001.

**Acceptance**:
- `checklist-domain-isolation` + source-set placement: zero `import android.*` / vendor SDK in `Entity`, `Component`, `Blueprint`, `LifecycleState`, and the `preset/ecs/` core.

**Dependencies**: T136-002, T136-010

---

## Phase 9 — Cleanup inventory (executes in this PR — FR-017..FR-021)

### T136-039 — [x] [P] Rewrite `docs/architecture/preset-model.md` in place to canonical ECS

**Trace**: FR-017, SC-007.

**Acceptance**:
- `docs/architecture/preset-model.md` rewritten to canonical ECS. All "tagged-component" / "not canonical ECS" / "discriminated union" descriptions of the *current* model removed (else future agent drifts). AI-TLDR block updated to the new model.
- `data-model.md` (this spec) already canonical — no stale text.

**Dependencies**: T136-006

---

### T136-040 — [x] [P] Write `ADR-013`; mark `ADR-012` "Superseded by ADR-013"

**Trace**: FR-018, SC-007.

**Acceptance**:
- New `docs/adr/ADR-013-canonical-ecs.md` — canonical ECS adopted, reverses ADR-012 on (a) composition need + (b) pre-release timing.
- `docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md` gets a "Superseded by ADR-013" header (file kept as history, rule 11).

**Dependencies**: none | **[P]**

---

### T136-041 — [x] [P] Set `superseded-by: TASK-136` on TASK-120 + TASK-127

**Trace**: FR-019, SC-007.

**Acceptance**:
- `backlog/tasks/task-120*.md` and `task-127*.md` frontmatter get `superseded-by: TASK-136` (kept as archive, rule 11).
- TASK-136 frontmatter `decision-supersedes: [TASK-120, TASK-127]` already present — verify.

**Dependencies**: none | **[P]**

---

### T136-042 — [x] [P] Downstream notice: TASK-69/71/68/19 → "See TASK-136 Decision"

**Trace**: FR-021, rule 11 cross-task references.

**Acceptance**:
- TASK-69, TASK-71, TASK-68, TASK-19 get a `dependencies:` pointer / "See TASK-136 Decision" prose (their own spec edits are OUT of scope — notice only).

**Dependencies**: none | **[P]**

---

## Phase 10 — Gates + smoke

### T136-043 — [x] Consistency grep gates (SC-004 / SC-007 / SC-009)

**Trace**: SC-004, SC-007, SC-009, Plan §Rollout.

**Acceptance**:
- SC-004: no `entity.component as? X` anywhere in the tree.
- SC-007: no stale "tagged-component"/"not canonical ECS"/"discriminated union" describing the current model in live docs; `ADR-012` has "Superseded by ADR-013"; `ADR-013` exists; TASK-120/127 carry `superseded-by`.
- SC-009: `schemaVersion` present = 2 (not bumped); no migration writer / backward-compat reader for profiles in the tree.

**Dependencies**: T136-021, T136-039, T136-040, T136-041

---

### T136-044 — [x] Build gate: `:core:test` green + `:app` mock/real backend compile

**Trace**: SC-010, FR-016, Plan §Rollout.

**Acceptance**:
- `./gradlew :core:test` green; `:app` mock + real backend variants compile (all consumers migrated, big-bang coherent).
- The migrated app-layer status-consumers **compile and their unit tests pass**: `WizardViewModel` (+ `WizardViewModelTest`), `FirstLaunchActivity`, `PendingChecklistViewModel` (+ `PendingChecklistViewModelTest`) — i.e. `:app` test source set green, no dangling `Entity.status` / `ComponentStatus` reference in `:app`.
- No two parallel forms of `Entity` in the tree.

**Dependencies**: T136-022, T136-023, T136-024, T136-025, T136-026, T136-027, T136-028, T136-029, T136-030, T136-031, T136-032, T136-033, T136-034, T136-035, T136-036, T136-037, T136-038, T136-046, T136-047, T136-048, T136-049

---

### Emulator smoke (deferred)

> **[deferred-local-emulator]** T136-045 deferred until an AVD the session can drive is available (memory `reference_compose_ui_test_api_mismatch.md`; the AI session cannot visually verify a running HomeScreen). `pre-pr-backlog-sync` emits this as a `[auto:deferred-local-emulator]` AC; task stays in Verification until closed.

### T136-045 — [ ] [deferred-local-emulator] Emulator smoke — fresh install → wizard → HomeScreen tiles

**Trace**: Plan §Rollout step 5, SC-010.

**Acceptance**:
- Fresh install → wizard → HomeScreen shows tiles from the rewritten bundled preset; render gating (no dead `Failed`/`Skipped` button) visually correct. Owner / emulator run; AI session does not verify.

**Dependencies**: T136-044

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски (для владельца)

**Что это за файл.** Разбивка «как переписать фундамент на канонический ECS» на 49 конкретных шагов (`T136-001` … `T136-049`), 10 фаз. Спека говорила ЧТО, план — КАК, а этот файл — по шагам, в каком порядке, что считать сделанным.

**Добавлено после аудита (`speckit-analyze`, 2026-07-18).** Четыре шага дописаны по итогам punch-list'а: `T136-046` убирает поле `status` из `ActiveComponentEntry` в пресете (это удаляемый enum `ComponentStatus` жил прямо в JSON-пресете — изменение формата пресета, мигратор не пишем), а `T136-047`–`T136-049` переводят три экрана приложения (`PendingChecklistViewModel`, `WizardViewModel`, `FirstLaunchActivity`), которые читали удаляемое поле `Entity.status`, на чтение состояния из компонента `LifecycleState`.

**Порядок фаз** (это одна большая правка «big-bang» — код не соберётся, пока не доедем до фазы 6):
1. **Модель** (фаза 1): `Component` из класса → интерфейс без поля `tags`; статус `ComponentStatus` (enum) → компонент `LifecycleState`; `Entity` становится «мешком» без поля `status`; `Blueprint` → «бандл»; `Profile` переименовывает `components` → `entities`.
2. **Свой движок** (фаза 2): пакет `preset/ecs/` ~300 строк в форме Fleks — `entity{}`, `get<T>()`, `with/without`, `family{}` + шов для будущего переезда.
3. **Движок примирения** (фаза 3): рождение сущностей из бандла, состояние двигается сменой компонента-маркера.
4. **Запросы** (фаза 4): читают теги с сущности, статус — из компонента.
5. **Сериализация** (фаза 5): JSON, сгруппированный по сущностям; мигратор НЕ пишем (удаляем старые тесты совместимости).
6. **Потребители** (фаза 6): репозиторий потока + два экрана мастера переводятся на новый доступ.
7. **Ассеты** (фаза 7): `pool.json`, пресеты, фикстуры переписываются на месте.
8. **Тесты + fitness** (фаза 8): композиция, запросы, спавн, иерархия, состояние + 6 проверок-инвариантов (покрытие, «не больше одного компонента типа», теги-явно, бюджет 400 строк, изоляция домена).
9. **Уборка** (фаза 9): переписать доку модели, написать ADR-013, старым TASK-120/127 поставить «заменено TASK-136», downstream-задачам — указатель.
10. **Ворота** (фаза 10): grep-проверки чистоты, сборка, smoke на эмуляторе.

**Отложенное.** Один шаг помечен `[deferred-local-emulator]` — визуальная проверка на эмуляторе (её AI-сессия не делает сама). Проверок на реальном устройстве нет — это чистый доменный рефактор, железо не трогается.
<!-- NOVICE-SUMMARY:END -->
