# Implementation Plan: ECS Foundation (Entities, Tags, Query, Hierarchy) + HomeScreen Rewire

**Branch**: `task-127-home-config-load-fix` | **Date**: 2026-07-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/task-127-ecs-foundation/spec.md`
**Backlog task**: [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md)

---

## Summary

Two goals in one pass (owner decision 2026-07-16, spec Clarifications Q7-Q10):

1. **Fix** the TASK-52 regression introduced by TASK-126 (wizard writes `Profile`, HomeScreen reads `ConfigDocument` → Error UI). The regression fires in **`HomeComponent.launchLoadFlows()` → `loadFlows()`** (one-shot, 3s timeout → `Error`), so the new `ProfileBackedFlowRepository` implements **all four `FlowRepository` methods** — `loadFlows` awaits the first non-null Profile; `observeFlows` is the hot path. The port itself is **unchanged**.
2. **Complete the ECS foundation** while the wire format has no live users: `Component.tags: Set<Tag>` + `Profile.query` (tags/query), **`Entity.parentId` hierarchy** (flat storage, computed tree), **three structural Component subtypes** (`Workspace`, `Flow`, `ToolbarButton`), **`ComponentStatus.Unverifiable` + `Outcome.NeedsUserConfirmation`** (honest state for settings with no OS read-back), and the **ECS rename** (`ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint`).

All of (2) are wire-format-affecting changes: free pre-release, each costs a migration writer post-release (rule 5). This is the owner's own meta-rule applied — defer only what later becomes *appending*, not *rewriting*.

**Wire format**: `schemaVersion` **stays 2** — `tags`, `parentId`, new `type` discriminators and the new `status` value are all additive (`Profile.CURRENT_SCHEMA_VERSION = 2` matches the immutable TASK-120 Decision). **No migration writer** (Clarification Q6, rule 4 MVA); tags-defaults live only in Component constructor-defaults. `ConfigDocument` code stays for future admin push but is no longer bound to `FlowRepository`.

---

## Technical Context

**Language/Version**: Kotlin 2.0 (Multiplatform: `commonMain` + `androidMain`).
**Primary Dependencies**: kotlinx.serialization (Profile JSON), kotlinx.coroutines (Flow API on `ProfileStore.observe()`). No new deps.
**Storage**: `ProfileStore` = local file-based JSON on device (existing from TASK-120). Pool = bundled asset (`pool.json` in APK).
**Testing**: JVM unit tests (`./gradlew :core:test`), Robolectric instrumented (`:core:connectedAndroidTest`), manual on emulator + Xiaomi Redmi Note 11 physical device.
**Target Platform**: Android (minSdk 24, target 34). Zero Android imports in Query API / Tag / `ProfileBackedFlowRepository` per rule 1 (domain isolation).
**Project Type**: Mobile app (Android launcher). This spec touches `core` KMP module + `app` Android DI wiring.
**Performance Goals**: `profile.query(...)` under 1 ms at MVP scale (~20 Components in Profile). Linear scan; indexing deferred (see research.md).
**Constraints**: `schemaVersion: 2` — unchanged (additive `tags` field, no bump; matches `Profile.CURRENT_SCHEMA_VERSION = 2` + TASK-120 Decision). Никакого migration writer per Clarification Q6 (MVP не релизнут — нет потребителя миграции per rule 4 MVA). Migration writer добавляется post-release при первом breaking change (v2 → v3). Zero Android imports in domain layer.
**Scale/Scope**: MVP Profile ~10-20 Components. HomeScreen renders 3-8 tiles + 1 Toolbar. Query invoked on every Profile emission (rare — user edits) + on cold start.

---

## Architecture

### Module map

```
core/src/commonMain/kotlin/com/launcher/
├── preset/
│   ├── model/
│   │   ├── Enums.kt                     [MODIFIED] add @Serializable Tag enum (13 values);
│   │   │                                  ComponentStatus += Unverifiable (FR-014)
│   │   ├── Component.kt                 [MODIFIED] abstract tags: Set<Tag> on 11 subtypes;
│   │   │                                  LauncherRole + StatusBarPolicy: object → data class;
│   │   │                                  NEW subtypes Workspace / Flow / ToolbarButton (FR-013)
│   │   ├── Profile.kt                   [MODIFIED] ProfileComponent → Entity + parentId (FR-011/015)
│   │   ├── Pool.kt                      [MODIFIED] ComponentDeclaration → Blueprint (FR-015)
│   │   ├── Outcome.kt                   [MODIFIED] += NeedsUserConfirmation (FR-014)
│   │   ├── ValidationError.kt           [MODIFIED] += DanglingParentRef / CircularParentRef /
│   │   │                                  DanglingTargetRef + toI18nKey branches (FR-016)
│   │   └── Preset.kt                    [MODIFIED] doc-comment ref preset-model.md
│   ├── engine/
│   │   ├── ProfileFactory.kt            [MODIFIED] rename fallout; hierarchy validation on create
│   │   ├── ReconcileEngine.kt           [MODIFIED] rename fallout; NeedsUserConfirmation → Unverifiable;
│   │   │                                  BootCheck skips Unverifiable (FR-014)
│   │   ├── ReconcileState.kt            [MODIFIED] rename fallout
│   │   └── PresetValidator.kt           [MODIFIED] hierarchy checks (FR-016)
│   ├── port/
│   │   └── InteractionSink.kt           [MODIFIED] rename fallout
│   └── query/
│       └── ProfileQuery.kt              [NEW] tag + hierarchy selectors (FR-005, FR-012)
├── api/
│   └── FlowRepository.kt                [UNCHANGED] existing port (real location — NOT adapters/flow/)
└── adapters/
    ├── config/
    │   └── ConfigBackedFlowRepository.kt [MODIFIED] add TODO(config-deprecation) comment (real location)
    └── flow/
        └── ProfileBackedFlowRepository.kt [NEW] reads ProfileStore, projects Workspace→Flow→tiles

core/src/androidMockBackend/kotlin/com/launcher/di/
└── BackendInit.kt                       [MODIFIED] rebind single<FlowRepository> (~line 165)
core/src/androidRealBackend/kotlin/com/launcher/di/
└── BackendInit.kt                       [MODIFIED] rebind single<FlowRepository> (~line 275)

app/src/main/java/com/launcher/app/      [MODIFIED — rename fallout only]
├── wizard/{WizardViewModel,WizardScreen}.kt
├── settings/PendingChecklistViewModel.kt
└── preset/task120/PresetBootstrap.kt

docs/
├── architecture/
│   └── preset-model.md                  [NEW] two-dimensions doc (already merged)
└── dev/
    └── server-roadmap.md                [MODIFIED] SRV-CONFIG-DEPRECATION entry (already merged)
```

> Note (audit 2026-07-16): earlier revision listed fictional paths — `core/adapters/flow/FlowRepository.kt`, `preset/serialization/ProfileSerializer.kt`, `app/.../di/{Mock,Real}BackendModule.kt`. None exist. There is no `ProfileSerializer` class — kotlinx Json config lives inline in `DataStoreProfileStore` (app module) and must be mirrored exactly by contract tests (see contract § Serializer configuration).

> **Rename blast radius** (FR-015, measured 2026-07-16): `ProfileComponent` — 51 usages / 14 files; `ComponentDeclaration` — 42 usages / 13 files. Mechanical IDE rename, zero wire impact (Kotlin class names never appear in JSON; `@SerialName` discriminators unchanged). Done as a **dedicated first-phase commit** so the semantic changes that follow read cleanly in review.

### Port-adapter shape

```
                     ┌───────────────────────────────────┐
HomeComponent ──────►│  FlowRepository (port, UNCHANGED) │
                     │  loadFlows(): List<FlowDescriptor>│  ← one-shot path: here the
                     │  observeFlows(): Flow<…>          │    TASK-52 regression fires
                     │  availableTemplates(presetId)     │
                     │  addFlow(templateId)              │
                     └───────────────────────────────────┘
                                  ▲
                                  │ implements
                     ┌────────────┴──────────────┐
                     │ ProfileBackedFlowRepository│
                     │  (NEW — bound in DI)       │
                     └────────────┬──────────────┘
                                  │ reads
                     ┌────────────▼──────────────┐
                     │  ProfileStore (existing)   │
                     │  observe(): Flow<Profile?> │
                     └────────────────────────────┘

ConfigBackedFlowRepository stays in codebase (unbound from FlowRepository).
Marked // TODO(config-deprecation): SRV-CONFIG-DEPRECATION for future removal.
```

### Data flow — happy path (SEQ-1)

```
WizardHostActivity
  ↓ onConfirmLastStep
WizardViewModel
  ↓ engine.run(RunMode.Wizard)
ReconcileEngine
  ↓ profileStore.save(finalProfile)   ← Profile becomes source of truth
ProfileStore (disk write)
  ↓ startActivity(HomeActivity)
HomeActivity → HomeComponent
  ↓ launchLoadFlows(): loadFlows() [3s timeout] + observeFlows() [hot]
ProfileBackedFlowRepository
  ↓ profileStore.observe().filterNotNull()  (loadFlows: .first(); observe: .map)
  ↓ profile.homeScreenTiles()          ← query API (excludes Failed/Skipped)
List<FlowDescriptor> / Flow<List<FlowDescriptor>>
  ↓
HomeLoadingState.Ready(flows)
```

**Behaviour of the remaining port methods** (parity, no scope growth): `availableTemplates(presetId)` keeps the existing static template-catalogue semantics (as in `ConfigBackedFlowRepository`); `addFlow(templateId)` throws `error(...)` exactly like the only existing implementation does today (`ConfigBackedFlowRepository.addFlow`, line ~95) — Profile-based addFlow is a separate future task (`TODO(profile-add-flow)`).

**Persistently absent Profile** (wizard skipped / crashed pre-save): `loadFlows()` suspends on `.first()` → HomeComponent's existing 3s timeout → `Error` + Retry (TASK-52 UX, unchanged). Deliberate: no eternal-Loading gap.

### Rule 1 verification (domain isolation)

All new files in `core/preset/model/`, `core/preset/query/`, `core/preset/serialization/` are pure Kotlin — zero `import android.*`. `ProfileBackedFlowRepository` in `core/adapters/flow/` also zero Android imports (`FlowRepository` port is domain-level; `ProfileStore` is a port too, not a vendor SDK). Verified by `checklist-domain-isolation` on plan-level review + build-time fitness function (source-set placement).

---

## Data model

See [`data-model.md`](data-model.md).

Key types:
- `Tag` enum — **13 values**: 8 semantic + 5 structural (`Tile`, `Toolbar`, `Workspace`, `Flow`, `ToolbarButton`). Additive-only per rule 5, with the honest unknown-value caveat in the contract.
- `Component.tags: Set<Tag>` with per-subtype defaults (single source of truth: constructors) on **11 subtypes** — 8 existing (incl. `Language`, `StatusBarPolicy`) + 3 new structural. `LauncherRole`/`StatusBarPolicy` object → data class (wire-compatible).
- **`Entity`** (was `ProfileComponent`) with **`parentId: String?`** — flat storage, computed tree (FR-011). **`Blueprint`** (was `ComponentDeclaration`).
- `Component.Workspace` / `.Flow(titleKey, layoutKey, order)` / `.ToolbarButton(targetFlowId, labelKey, order)` — structural subtypes (FR-013). `layoutKey` moves onto `Flow`; `Profile.layoutKey` kept as legacy fallback.
- `ComponentStatus.Unverifiable` + `Outcome.NeedsUserConfirmation` (FR-014).
- `Profile` query API (extension functions): tag selectors + hierarchy selectors (`children`, `roots`, `workspace`, `flows`, `tilesOf`, `toolbar`, `toolbarButtons`). `homeScreenTiles()`/`tilesOf()` exclude `Failed`/`Skipped` (render gating).
- `ValidationError.DanglingParentRef` / `.CircularParentRef` / `.DanglingTargetRef` (FR-016).
- **No migration writer** — per Clarification Q6: everything above is additive → `schemaVersion` stays 2.

---

## Wire formats

- [`contracts/profile-v2.md`](contracts/profile-v2.md) — Profile serialized JSON `schemaVersion: 2` (значение из shipped кода; всё новое — additive, без bump'а). Documents the real `Profile`/`Entity` shape, `tags` + **`parentId`** per entity, three new `type` discriminators, the fifth `status` value, constructor-defaults, hierarchical fixture (workspace → flows → tiles + toolbar → buttons) and the degenerate one-level case. **Честный** forward-compat: unknown key → ignored; unknown Tag value / Component type / status → fail-loud `SerializationException`, lenient reader required before cross-device artifacts ship (Risk R-8, TASK-131). **Никакой migration writer**: MVP не релизнут, отложено до post-release breaking change.

Not touched:
- `pool.json` — bundled build-time artefact, additive optional `tags` field on `ComponentDeclaration`; no schemaVersion bump (per Clarification Q2).
- `ConfigDocument` — unchanged (stable for admin push scenarios).

---

## Dependency impact

**No new gradle dependencies.** Feature is pure Kotlin extensions on existing types + one new adapter class (no migration writer per Q6). Reuses existing kotlinx.serialization + kotlinx.coroutines Flow already in `core` module.

Per Article XIII (Dependency Restraint): the "add nothing" outcome is preferred; documented explicitly.

---

## Test strategy

Per CLAUDE.md rules §6 (mock-first) + §7 (fitness functions).

### Contract tests (`core/src/commonTest/kotlin/com/launcher/preset/wire/` — existing wire-test package)

- **`ProfileSchemaV2RoundtripTest`** (naming per existing `PoolSchemaV2RoundtripTest` / `PresetSchemaV2RoundtripTest` pattern): roundtrip of the **hierarchical** fixture (workspace + 2 flows + tiles + toolbar + 2 buttons + an `Unverifiable` entity). Json settings MUST mirror `DataStoreProfileStore` exactly (`classDiscriminator="type"`, `ignoreUnknownKeys=true`, `encodeDefaults=true`). Fixtures: `core/src/commonTest/resources/fixtures/profile-wire-format/profile-v2-hierarchy.json` + `profile-v2-no-tags.json`.
- **Compat pins**: missing `tags` → constructor-defaults; missing `parentId` → `null` (root, degenerate profile still reads); `{"type":"LauncherRole"}` with no fields → deserializes (pins object → data class conversion).
- **Fail-loud pins** (per contract § Forward compat): unknown Tag value / unknown Component `type` / unknown `status` → `SerializationException`. Keeps the lenient-reader deferral a documented decision (TASK-131).
- **REMOVED**: `ProfileMigrationV2toV3RoundtripTest` + `ProfileMigrationV2toV3BackwardCompatTest` — нет migration writer, нечего тестировать. Constructor-defaults покрыты через `ComponentTagsFitnessTest` (см. Fitness functions).

### Unit tests

- **`ProfileQueryTest`**: `byTag` (single), `byAllTags` (AND), `byAnyTag` (OR), `byNotTag`, `homeScreenTiles`, `toolbar`, empty result, tag-not-present, empty Profile, **render gating** (`Failed`/`Skipped` excluded, `Pending`/`Unverifiable` included).
- **`ProfileHierarchyQueryTest`** (FR-012): `children(parentId)`, `roots()`, `workspace()`, `flows()` ordering by `order`, `tilesOf(flowId)` isolation (tiles of another flow not returned), `toolbarButtons()` ordering, orphan entity (dangling `parentId`) silently absent from `children()`, degenerate profile (no Flow entities) → `homeScreenTiles()` returns all tiles.
- **`ReconcileEngineUnverifiableTest`** (FR-014): provider returns `NeedsUserConfirmation` → status becomes `Unverifiable`, never `Applied`; `BootCheck` skips `Unverifiable` entities; `RunMode.Single` re-checks them.
- **`ProfileFactoryHierarchyValidationTest`** (FR-016): dangling `parentId` → `DanglingParentRef`; parent cycle → `CircularParentRef`; `ToolbarButton.targetFlowId` pointing nowhere → `DanglingTargetRef`.
- **`ProfileBackedFlowRepositoryTest`** (JVM, uses existing `core/src/commonTest/kotlin/com/launcher/preset/fakes/FakeProfileStore.kt`):
  - `loadFlows()` — Profile already saved → returns mapped flows immediately (the regression path).
  - `loadFlows()` — store stays null → suspends (caller timeout responsibility; test with runTest + advanceTime).
  - `observeFlows()` — non-null Profile → emits list; null emission upstream → no downstream emission (`filterNotNull`).
  - **Hierarchical projection**: workspace + 2 flows + tiles → two `FlowDescriptor`s, each carrying only its own slots, ordered by `Flow.order`.
  - **Degenerate projection**: profile with tiles but no `Flow` entities → one synthetic `FlowDescriptor` with all tiles.
  - Profile update sequence (two saves) → repository emits two lists.
  - `addFlow` → throws (parity pin with `ConfigBackedFlowRepository`).
- **`ComponentTagsFitnessTest`**: reflection walk over `Component` sealed subclasses; instantiate each (dummy values for required params like `AppTile.packageName`); assert `tags` default is non-empty. Prevents accidental `emptySet()` regressions.

### Integration tests (JVM commonTest — real location `core/src/commonTest/kotlin/com/launcher/ui/navigation/HomeComponentLoadingStateTest.kt`, NOT `:core:androidTest`)

- **`HomeComponentLoadingStateTest`** (existing test class from TASK-52) — add new scenario:
  - `postManifestWizardReconcile_profileSeeded_homeReady`: seed `ProfileStore` with one `AppTile(Settings)`, wire `ProfileBackedFlowRepository`, verify `HomeComponent` transitions `Loading → Ready` with non-empty flows.
  - Existing config-based scenarios stay green (`ConfigBackedFlowRepository` not removed).

### Fitness functions

- **Component-tags non-empty check**: `ComponentTagsFitnessTest` (see above).
- **Source-set placement**: JVM tests import only `commonMain` sources for `Tag`, `Component.tags`, `Profile.query`, `ProfileBackedFlowRepository`. Compilation fails if Android import sneaks in.
- **Query performance**: `ProfileQueryBenchmarkTest` — micro-benchmark on 20-Component fixture, assert p95 < 1 ms (SC-008).

### Manual verification

- Emulator smoke (pixel_5_api_34 AVD): fresh install → wizard → HomeScreen shows tiles.
- Physical Xiaomi Redmi Note 11 (adb id `17f33878`): same flow — SC-001 and SC-002.

---

## Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R-1 | Component subtype declared without `tags` default (empty set) → invisible to queries | Medium | High | `ComponentTagsFitnessTest` (reflection walk) catches this at build time. **Single source of truth** — constructor-defaults, no separate migration mapping to keep in sync. |
| R-2 | ~~Migration writer race~~ | — | — | **REMOVED**: нет migration writer. `schemaVersion: 2` unchanged (`tags` additive). |
| R-3 | ~~`ProfileStore.observe()` semantic differs from assumption~~ **VERIFIED 2026-07-16**: `ProfileStore.observe(): Flow<Profile?>` confirmed ([ProfileStore.kt:7](../../core/src/commonMain/kotlin/com/launcher/preset/port/ProfileStore.kt#L7)); DataStore emits `null` while key absent. | — | — | Closed. `filterNotNull` + `loadFlows().first()` design stands. |
| R-4 | Query linear scan performance degrades if Profile grows past MVP scale (100+ Components) | Low (MVP) | Low (MVP) | Documented exit ramp in research.md § "Query indexing" — indexed lookup as additive change; no wire-format break required. |
| R-5 | Xiaomi MIUI kills HomeActivity between wizard finish and HomeComponent init | Low | High | `HomeComponent` reads `Profile` from disk on init (not from in-memory hand-off) — surviving process death by design (state-management pattern from TASK-52). |
| R-6 | `Toolbar` Component in Profile but `Profile.toolbar()` returns null (e.g., someone sets `tags = emptySet()`) | Low | Low | Covered by `ComponentTagsFitnessTest`; runtime null tolerated (Toolbar is optional UI element). |
| R-7 | Post-release breaking change полей Component без migration writer → users lose data on upgrade | High | High | **Deferred to post-release**: pre-release (сейчас) — `schemaVersion: 2` unchanged, каждое breaking change = сброс dev `ProfileStore`. Первый post-release breaking change = обязательный migration writer (`ProfileMigrationV2toV3`) + bump. Owner aware через Clarification Q6. |
| R-8 | Unknown Tag value / Component type / status crashes older reader (`SerializationException` — kotlinx enum collections have no per-element leniency) → cross-device artifact exchange breaks rule 5 backward-read guarantee | Low (pre-release, same-device) | High (post-sharing) | Contract states fail-loud honestly + contract tests pin it. **Hard trigger**: lenient reader (TASK-131) MUST ship before admin push (spec-009) or preset import/share (rule 9) — recorded in contract § Forward compat. |
| R-9 | Rename (93 usages / ~25 files) collides with in-flight work on TASK-126 branches → merge pain | Medium | Low | Rename lands as a **dedicated first commit** (mechanical, IDE-driven), before semantic changes. `git mv`-free — pure symbol rename; conflicts resolve trivially. |
| R-10 | Hierarchy adds structural entities that existing presets/pool do not declare → bundled `pool.json` + `default preset` must gain `Workspace`/`Flow`/`Toolbar`/`ToolbarButton` blueprints, else HomeScreen falls back to the degenerate one-flow path silently | Medium | Medium | Degenerate path is **explicitly supported and tested** (`homeScreenTiles()` with no Flow entities → all tiles, no toolbar) — bundled preset can migrate to the hierarchical shape incrementally. Emulator/physical smoke covers whichever shape ships. |
| R-11 | `Unverifiable` semantics leak: a provider returns `NeedsUserConfirmation` from `check()` on **every** BootCheck for a component the user never confirmed → status flips Pending↔Unverifiable | Low | Medium | Engine rule (FR-014): only the wizard/Settings interactive path may record `Unverifiable` (after explicit user confirmation); `BootCheck` skips entities already `Unverifiable` and does not create the status itself. Pinned by `ReconcileEngineUnverifiableTest`. |

---

## Required Context Review

- [CLAUDE.md](../../CLAUDE.md) — rules §1 (domain isolation), §4 (MVA), §5 (wire-format versioning), §9 (shareability-readiness).
- [ADR-011](../../docs/adr/ADR-011-ai-owner-collaboration-conventions.md) — sequences + checklist-in-chat convention (used in this spec).
- [ADR-012](../../docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md) — vocabulary + design deviation from canonical ECS (Bevy/Flecs/Unity DOTS). Latent one-way door: single-component-per-entity model blocks future multi-component composition.
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Article XI (meta-minimization), Article XVI (Constitution Check), Article XVII (deviations).
- [docs/architecture/preset-model.md](../../docs/architecture/preset-model.md) — two-dimensions model (lifecycle vs semantic). Just merged into this branch.
- [docs/dev/server-roadmap.md § SRV-CONFIG-DEPRECATION](../../docs/dev/server-roadmap.md) — future ConfigDocument removal path.
- [backlog/tasks/task-120](../../backlog/tasks/task-120%20-%20Decision-Component-Preset-Profile-foundational-model.md) — Decision block on which this plan builds additively.
- [specs/task-49-cloud-feature-inventory-offline-first/](../../specs/task-49-cloud-feature-inventory-offline-first/) — reference for backend substitution style (not directly touched here, but architectural precedent).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Run by `procedure-constitution-check` 2026-07-16 (re-run same day against the **expanded** ECS-foundation scope — Q7-Q10).

| Gate | Status | Justification |
|------|--------|---------------|
| G-1 Architecture | **PASS** | Extension functions + one new adapter + additive Component subtypes in existing `core/preset/*` and `core/adapters/flow/`. No new gradle module — Article V §3 criteria none apply. `FlowRepository` port + implementation shape preserved. |
| G-2 Core / System Integration | **N/A** | No new system events, no `BroadcastReceiver`, no lifecycle callbacks. `StatusBarPolicy`'s intent chain is an existing Provider concern; FR-014 only changes how its `Outcome` is recorded. |
| G-3 Configuration | **PASS** (re-checked for expanded scope) | `schemaVersion: 2` unchanged — `tags`, `parentId`, три новых `type` discriminator'а и статус `Unverifiable` **все аддитивны** (rule 5 разрешает аддитивные изменения без bump'а). Migration writer **отсутствует** per Clarification Q6 (MVP не релизнут, нет потребителя; rule 4 MVA). Tests: hierarchical roundtrip + missing-`tags`/missing-`parentId` compat + object→data-class compat + три fail-loud pins (R-8). Constructor-defaults = единственный источник истины для tags. Pool override via embedded `component` object (Q2) — поля в `Blueprint` не добавляются. Post-release policy: R-7. |
| G-4 Required Context Review | **PASS** | Links present: CLAUDE.md (rules 1, 4, 5, 9), ADR-011, constitution.md (XI, XVI, XVII), preset-model.md, server-roadmap.md, TASK-120 Decision, task-49 as precedent. No permissions change (no compliance doc needed). |
| G-5 Accessibility | **PASS** | **No new UI surfaces**: flow switching reuses the existing `BottomFlowBar` + `HomeComponent.selectFlow` ([HomeScreen.kt:62-69](../../core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt#L62)), already senior-safe per TASK-52. US-3 (wizard localization) → readable strings; SC-002 verifies. **Render gating (FR-012) is an accessibility win** — a `Failed`/`Skipped` tile never reaches an elderly user as a dead button. `Unverifiable` UX reuses the existing wizard Interactive path. `FontSize` carries `Tag.Accessibility` — fitness-verified. |
| G-6 Battery / Performance | **PASS** | Event-driven (`ProfileStore.observe()` on user edit); no polling; no new background work; zero new deps. No migration cost. NFR-003 (< 1 ms) extended to hierarchy selectors; NFR-005 (depth-agnostic queries, terminating cycle validation). `BootCheck` skipping `Unverifiable` strictly **reduces** cold-start work. SC-008 benchmark. |
| G-7 Testing | **PASS** (re-checked for expanded scope) | Contract (hierarchical roundtrip, missing-`tags`/`parentId` compat, object→data-class compat, 3 fail-loud pins — no migration tests per Q6); unit (Query API + render gating, **hierarchy queries** incl. orphans/ordering/isolation, **Unverifiable engine rules**, **hierarchy validation**, Profile-backed repo incl. `loadFlows` + both projections); integration (`HomeComponentLoadingStateTest`, JVM commonTest); fitness (`ComponentTagsFitnessTest` reflection walk over 11 subtypes); micro-benchmark. Existing `FakeProfileStore` for adapter testing; every port already has fake + real. |
| G-8 Simplicity | **PASS** (re-checked for expanded scope) | Hierarchy = **one nullable field + query helpers**, NOT nested containers (research.md R-7 rejects the heavier option explicitly). `ProfileQueryService` rejected (R-1). Migration writer rejected (Q6/R-2). Linear scan over index (R-4). Rename **removes** ambiguity rather than adding structure (R-9). Rule 4 Test 1: inlining the Query API loses closed-set Tag type-safety + the single definition of render gating — kept. Test 2: swap to member methods ≈ 1 hour. **Article XI note**: the expansion is not speculative — each item has a current consumer (US-4 target screen; `StatusBarPolicy` for `Unverifiable`; a demonstrated 3-way naming ambiguity) and each is wire-format-affecting, so deferral would mean a migration writer, not an append. |

**OVERALL: 7 PASS, 1 N/A, 0 FAIL — plan is COMPLETE.**

No Complexity Tracking entries needed.

---

## Project Structure

### Documentation (this feature)

```
specs/task-127-ecs-foundation/
├── spec.md              # /speckit.specify → /speckit.clarify → /speckit.scenarios output
├── plan.md              # this file (/speckit.plan)
├── research.md          # Phase 0 output (query indexing exit ramp, migration writer approach)
├── data-model.md        # Phase 1 output (Tag / Component.tags / migration mapping)
├── contracts/
│   └── profile-v2.md    # Phase 1 output (Profile wire-format schemaVersion=2 + optional tags, no migration writer per Q6)
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code

```
core/src/commonMain/kotlin/com/launcher/
├── preset/model/            [Component.kt, Preset.kt, Enums.kt modified; Profile.kt unchanged]
├── preset/query/            [ProfileQuery.kt new]
├── adapters/config/         [ConfigBackedFlowRepository.kt annotated — existing location]
└── adapters/flow/           [ProfileBackedFlowRepository.kt new]

core/src/commonTest/kotlin/com/launcher/
├── preset/query/            [ProfileQueryTest.kt, ProfileQueryBenchmarkTest.kt new]
├── preset/model/            [ComponentTagsFitnessTest.kt new]
├── preset/wire/             [ProfileSchemaV2RoundtripTest.kt new — existing wire-test package]
├── adapters/flow/           [ProfileBackedFlowRepositoryTest.kt new]
└── ui/navigation/           [HomeComponentLoadingStateTest.kt extended — existing location (JVM, not androidTest)]

core/src/commonTest/resources/fixtures/profile-wire-format/
├── profile-v2-sample.json   [new — roundtrip fixture]
└── profile-v2-no-tags.json  [new — constructor-defaults fixture]

core/src/androidMockBackend/kotlin/com/launcher/di/
└── BackendInit.kt           [modified — rebind FlowRepository]
core/src/androidRealBackend/kotlin/com/launcher/di/
└── BackendInit.kt           [modified — rebind FlowRepository]

app/src/main/java/com/launcher/app/
└── (WizardViewModel.kt debug logging removed as fix stabilises)

core/src/commonMain/composeResources/values/
└── strings_wizard.xml       [modified — EXISTING file; add missing wizard keys found by grep]
```

**Structure Decision**: Extend existing `core` KMP module with new files under `preset/query/` and `preset/serialization/`. No new gradle module — per rule 4 (MVA), extension methods + one adapter class do not warrant module split. Per Article V §3 criteria (ownership boundary, build isolation, independent enable/disable, stable API, material testability gain): none apply, so a new module would be premature abstraction.

---

## Complexity Tracking

*Fill ONLY if Constitution Check has violations. None anticipated for this plan — Query API is additive, no new modules, no new external deps.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |

---

## Rollout / verification

1. **Build gates**: `./gradlew :core:test` green, `./gradlew :app:assembleMockBackendDebug` green.
2. **Fitness gates**: `ComponentTagsFitnessTest` (subtypes with non-empty tags), `ProfileQueryBenchmarkTest` (p95 < 1 ms), source-set placement compile check.
3. **Contract gate**: `ProfileSchemaV2RoundtripTest` roundtrip + missing-tags + fail-loud pins.
4. **Integration gate**: `HomeComponentLoadingStateTest.postManifestWizardReconcile_profileSeeded_homeReady` green.
5. **Emulator smoke**: pixel_5_api_34 AVD — fresh install → wizard → HomeScreen shows tiles + localized wizard strings.
6. **Physical smoke**: Xiaomi Redmi Note 11 (adb id `17f33878`) — same flow. Closes SC-001 + SC-002.
7. **Backlog sync**: `pre-pr-backlog-sync` — verify `[auto:deferred-local-emulator]` and `[auto:deferred-physical-device]` gates, status → Verification (PR merged) → Done (physical smoke passes).

---

## TL;DR для владельца

**Что делаем**: два дела за один заход. (1) **Чиним** экран после мастера — он читал старую модель (`ConfigDocument`), которую мастер не заполняет, отсюда «Не удалось загрузить настройки». (2) **Достраиваем ECS-фундамент**, пока формат хранения не у пользователей: иерархия (workspace → вкладки → плитки, тулбар → кнопки), честный статус для непроверяемых настроек, ECS-нейминг. Всё из пункта 2 меняет формат — сейчас бесплатно, после релиза каждое = программа-переселенец для профилей живых пользователей.

**Ментальная модель**: профиль — это **таблица**. Строка = `Entity` (плитка / вкладка / кнопка), колонки = данные компонента, `parentId` = внешний ключ, запрос = `SELECT … WHERE`. Дерево не хранится вложенно — оно вычисляется. Так делают Bevy, Unity DOTS и сам Android (лаунчер хранит иконки плоской таблицей с колонкой «контейнер»).

**Что нового в коде**:
- `Tag` enum — **13 ярлыков**: 8 смысловых (о чём объект: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency`) + 5 структурных (какая это часть экрана: `Tile, Toolbar, Workspace, Flow, ToolbarButton`).
- `Component.tags` — на **всех 11** субтипах (8 существующих + 3 новых). Два «синглтона» (`LauncherRole`, `StatusBarPolicy`) станут обычными классами — иначе на них нельзя навесить теги; со старыми данными совместимо.
- **Три новых типа объектов**: `Workspace` (корень экрана), `Flow` (вкладка со своей сеткой 2×3 и порядком), `ToolbarButton` (кнопка со ссылкой `targetFlowId` на свою вкладку). Сетка `layoutKey` переезжает на `Flow`.
- **`Entity.parentId`** — колонка «родитель». Дерево (`Workspace → Flow → плитки`, `Toolbar → кнопки`) вычисляется запросами `children` / `flows` / `tilesOf` / `toolbarButtons`, а не хранится вложенно.
- **Переименование**: `ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint` (93 механические правки; формат хранения не меняется). Слово «component» значило три разные вещи.
- **Пятый статус `Unverifiable`** — честное «не знаю» для настроек без обратной связи от Android (системная шторка). Провайдер отвечает `NeedsUserConfirmation` → пользователь жмёт «Я включил» → статус `Unverifiable`, а не враньё «применено». Проверка при старте такие пропускает.
- `Profile.query { ... }` + селекторы. `homeScreenTiles()`/`tilesOf()` **не показывают** плитки со статусом `Failed`/`Skipped` — пожилой пользователь не увидит мёртвую кнопку.
- **Валидация иерархии**: ссылка на несуществующего родителя, цикл, кнопка на несуществующую вкладку — три типизированные ошибки при сборке профиля.
- `ProfileBackedFlowRepository` — новый адаптер, реализует **все четыре** метода существующего порта. Главное: `loadFlows()` (именно там рождалась ошибка — разовая загрузка с таймаутом 3 сек) теперь ждёт первый непустой Profile и возвращает вкладки с плитками.
- **UI менять не нужно**: `BottomFlowBar` и переключение вкладок (`selectFlow`) уже существуют с TASK-52 — мы просто наполняем их данными из профиля.
- **Никакой миграции** (Q6): `schemaVersion` **остаётся 2** — всё новое аддитивно.
- `ConfigBackedFlowRepository` **остаётся** в коде для будущего «админ пушит настройки», но `HomeScreen` его больше не использует.

**Что новых зависимостей**: ноль. Всё чистый Kotlin. Никаких новых gradle-модулей.

**Как проверяем**:
- JVM unit-тесты (запрос по тегам, roundtrip формата v2, дефолты для каждого компонента, фиксация fail-loud поведения на незнакомом теге/типе).
- JVM-тест `HomeComponentLoadingStateTest` (commonTest) с новым сценарием «после мастера → плитки видны».
- Ручной smoke на эмуляторе + физический Xiaomi (обязателен для закрытия SC-001 / SC-002).

**Что «one-way door»**: пока pre-release — none (можем менять formats свободно, сбрасывая dev `ProfileStore`). Post-release каждое breaking change полей = one-way door + migration writer + schemaVersion bump. `schemaVersion: 2` уже в формате (rule 5 требование выполнено с TASK-120) — это обеспечивает возможность будущей миграции. Честное ограничение: незнакомый тег/тип компонента роняет старого читателя — «снисходительный» читатель обязателен до того, как профили начнут ходить между устройствами (Risk R-8).

**Что откладываем**:
- Удаление `ConfigDocument` полностью — отдельная задача SRV-CONFIG-DEPRECATION в server-roadmap.
- Индексацию запросов (сейчас линейный проход по ~20 компонентам — достаточно). Exit ramp есть.
- Разделение `Toolbar` на отдельные кнопки — отдельная задача.
