# Implementation Plan: Canonical ECS — foundational launcher-config model

**Branch**: `task-136-ecs-canonical-foundational-model` | **Date**: 2026-07-18 | **Spec**: [spec.md](spec.md)
**Backlog task**: [task-136](../../backlog/tasks/task-136%20-%20Decision-ECS-canonical-foundational-model.md) (Decision block = authority)
**Supersedes**: TASK-120, TASK-127, ADR-012.

---

## Summary

Replace the tagged-component **discriminated-union** model of TASK-120/127 (`Entity` carries exactly one `Component`, `tags` live on the `Component`, `status` is a field) with **canonical ECS**: an `Entity` is a **free bag** of components + a set of zero-data tag markers, composed after assembly, with apply-state itself expressed as a component the System transitions. Implemented as a **small in-house core (~200–400 LOC) mirroring Fleks's API shape** (swap-compatible), not Fleks-direct. One coherent **big-bang** rewrite of `Entity` / `Component` / `Blueprint` / `ProfileFactory` / serialization / queries + every consumer + fixtures + bundled presets, plus a **CLEANUP INVENTORY** (docs, ADRs, superseded-by pointers) — all cheap because the app is pre-release (Article XX: no users, no persisted prod data, no migrator).

This plan **projects** the TASK-136 Decision block; it does not re-open OQ-1..OQ-7 or CL-1..CL-5. The one point the Decision explicitly deferred to plan — **the concrete state encoding** — is decided here (§ State encoding decision).

---

## Technical Context

**Language/Version**: Kotlin 2.0.21 (Multiplatform: `commonMain` + `androidMain`). No Kotlin bump (Fleks-direct would need 2.4.0 — rejected, OQ-1).
**Primary Dependencies**: kotlinx.serialization (polymorphic `Component`, `classDiscriminator="type"`), kotlinx.coroutines (`ProfileStore.observe()` Flow). **No new gradle deps** (own core is plain Kotlin).
**Storage**: `ProfileStore` = local file-based JSON on device (existing). Pool = bundled asset (`pool.json`). Bundled presets = `bundled-presets/*.json`.
**Testing**: JVM unit tests (`./gradlew :core:test`); zero Android imports in `Entity`/`Component`/`Blueprint`/ecs-core/query (rule 1, fitness-enforced).
**Target Platform**: Android (minSdk 24, target 34). Pure-domain refactor — no device/OEM/permission surface touched.
**Performance**: `Profile.family { … }` / query over the entity bag < 1 ms at MVP scale (~20–40 entities). Linear scan; indexing is an exit ramp under scale (NFR-002).
**Constraints**: `schemaVersion` field stays present, **value unchanged (= 2)**; no migrator, no backward-compat reader (Article XX pre-MVP override; owner directive 2026-07-18). Own ECS core ≤ ~400 LOC (NFR-003).
**Scale/Scope**: MVP Profile ~20–40 entities; each currently carries exactly one data-component + tags (multi-data-component is a free capability, no product consumer yet — meta-minimization tension acknowledged in Decision Rationale 6).

---

## State encoding decision (the point the Decision left to plan — CL-5 / FR-STATE)

**Decision: a single `LifecycleState` sealed component, a member of the closed `Component` set, stored in the entity bag and transitioned by the System (`ReconcileEngine`). NOT a set of state-tags.**

```kotlin
@Serializable @SerialName("LifecycleState")
sealed interface LifecycleState : Component {
    @Serializable @SerialName("Pending")       data object Pending : LifecycleState
    @Serializable @SerialName("Applied")       data object Applied : LifecycleState
    @Serializable @SerialName("Skipped")       data object Skipped : LifecycleState
    @Serializable @SerialName("Unverifiable")  data object Unverifiable : LifecycleState
    @Serializable @SerialName("Failed")        data class  Failed(val reason: FailReason) : LifecycleState
}
```

**Rationale (one line)**: apply-state is **mutually exclusive** and `Failed` **carries data (`reason`)**, so the at-most-one-per-type bag slot (CL-3) is its natural home — `entity.get<LifecycleState>()` returns the one current state, `Failed`'s reason rides along, and the System swaps one component to transition; **state-tags are rejected** because a `Set<Tag>` cannot enforce mutual exclusivity (it would permit `Applied` + `Failed` simultaneously) and `Failed`'s data would force a mixed tag-plus-component representation of one concept.

**Consequences woven through this plan**:
- `Entity.status: ComponentStatus` field is **deleted** (CL-5: "no special status fields"). The `ComponentStatus` enum is retired; its five cases become the `LifecycleState` variants (semantics preserved, incl. `Unverifiable`/FR-014 and `Failed(reason)`).
- `LifecycleState` is the **12th** member of the closed `sealed interface Component` — but it is a *state* component (no `Provider`, like the structural subtypes), not one of the 11 *domain-data* subtypes FR-002 enumerates. Coverage fitness treats it under "serializer required; Provider where applicable".
- `ProfileFactory` spawns each reconcilable entity with `LifecycleState.Pending` already in the bag (mirrors the old `status = Pending` default).
- Render gating (`tilesOf`/`homeScreenTiles`) reads `entity.get<LifecycleState>()` and hides `Failed`/`Skipped` (unchanged behaviour, new access path).
- Structural-only entities (Workspace/Flow/Toolbar skeleton) MAY omit `LifecycleState`; absence is treated as "renders" (Pending-equivalent), so the degenerate profile path is unchanged.

---

## Architecture

### Module map (no new gradle module — Article V §3: none of the five criteria met)

```
core/src/commonMain/kotlin/com/launcher/
├── preset/
│   ├── ecs/                              [NEW package — the own ~200-400 LOC core, Fleks-shaped]
│   │   ├── EntityDsl.kt                  [NEW] entity(id){ component(); tag(); parent() } spawn DSL
│   │   │                                   + Entity.with(c) / Entity.without<T>() / Entity.get<T>()
│   │   ├── Family.kt                     [NEW] Family { all(...); any(...); none(...) } matcher DSL
│   │   │                                   (tags + component types) — mirrors Fleks world.family{}
│   │   └── World.kt                      [NEW] World = view over Profile's entity bag; carries the
│   │                                        // TODO(ecs-fleks-migration) seam (FR-012)
│   ├── model/
│   │   ├── Component.kt                  [REWRITE] sealed class → sealed interface; drop `tags`
│   │   │                                   from all subtypes; add LifecycleState state-component
│   │   ├── Profile.kt                    [REWRITE] Entity: one Component → components: List<Component>
│   │   │                                   + tags: Set<Tag>; drop `status` field; mark()/replaceComponent()
│   │   │                                   → with()/without()/setState() over the bag
│   │   ├── Pool.kt                       [REWRITE] Blueprint: one component → components + tags (Bundle)
│   │   ├── Enums.kt                      [MODIFIED] delete ComponentStatus enum (→ LifecycleState);
│   │   │                                   Tag enum unchanged (13 values, still enum)
│   │   ├── Outcome.kt                    [UNCHANGED] NeedsUserConfirmation stays
│   │   └── ValidationError.kt            [MODIFIED] read component via get<T>() — bodies only
│   ├── engine/
│   │   ├── ProfileFactory.kt            [REWRITE] spawn: bundle expands to a FLAT component set
│   │   │                                   (+ inline components + paramsOverride + parentRef), bundle
│   │   │                                   discarded; validateHierarchy reads via get<T>()
│   │   ├── ReconcileEngine.kt           [REWRITE] iterate (entity × its components) → Provider per
│   │   │                                   component; record state by swapping LifecycleState (not status)
│   │   ├── PresetValidator.kt           [MODIFIED] decl.component → decl.components; get<T>() reads
│   │   └── PresetDiff.kt                [MODIFIED] decl.component → decl.components
│   ├── port/
│   │   ├── Provider.kt                  [UNCHANGED] Provider<T : Component> — applies ONE component
│   │   └── ProviderRegistry.kt          [UNCHANGED] resolve(component) — per-component, unchanged
│   └── query/
│       └── ProfileQuery.kt              [REWRITE] byTag/byAllTags/byAnyTag/byNotTag over entity.tags;
│                                          flows/toolbarButtons via get<T>(); render gating via get<LifecycleState>()
├── adapters/
│   └── flow/
│       └── ProfileBackedFlowRepository.kt [MODIFIED] flowEntity.component → get<Component.Flow>();
│                                            toSlot when(component) → get<Component.AppTile>()
└── api/FlowRepository.kt                [UNCHANGED] port contract intact

app/src/main/java/com/launcher/app/
├── wizard/WizardScreen.kt              [MODIFIED] when(pc.component) → when over pc.get<T>() /
│                                          the domain-data component in the bag
└── wizard/PostWizardKioskApply.kt      [MODIFIED] pc.component is X → pc.get<X>() != null

core/src/androidMockBackend/kotlin/com/launcher/di/BackendInit.kt   [UNCHANGED — binding shape intact]
core/src/androidRealBackend/kotlin/com/launcher/di/BackendInit.kt   [UNCHANGED — binding shape intact]

core/src/commonMain/**/pool.json + bundled-presets/*.json          [REWRITE in place — Bundle shape]
core/src/commonTest/.../fixtures/**/*.json                          [REWRITE in place — entity-grouped]

docs/architecture/preset-model.md      [REWRITE in place — canonical ECS, drop "not canonical" text]
docs/adr/ADR-012-*.md                  [MODIFIED — "Superseded by ADR-013" header]
docs/adr/ADR-013-canonical-ecs.md      [NEW]
backlog/tasks/task-120*, task-127*     [MODIFIED — superseded-by: TASK-136]
```

> **Not a consumer (false positives)**: every `child.component` in `ui/RootContent.kt` and `RootComponent.kt:153` is Decompose's navigation `component`, unrelated to `Entity.component`. Confirmed by inspection — excluded from the rewrite.

### Layered shape — arrows point downward (rule 1 domain isolation)

```
        app (androidMain): WizardScreen, PostWizardKioskApply, DI
                    │  reads entity.get<T>()
                    ▼
        adapters: ProfileBackedFlowRepository  ──► api/FlowRepository (port, unchanged)
                    │
                    ▼
        preset/query: ProfileQuery (named selectors)
                    │ built on
                    ▼
        preset/engine: ProfileFactory (spawn) · ReconcileEngine (System) · validators
                    │ operate over
                    ▼
        preset/ecs: World · Family DSL · Entity compose ops   ◄─ the own core (Fleks-shaped)
                    │ over
                    ▼
        preset/model: Entity (free bag) · Component (sealed interface) · Tag · Blueprint · LifecycleState
```

Every layer is pure Kotlin, zero `import android.*`. The ecs core sits at the bottom (generic World shape) and is referenced downward-only. `Provider`/`ProviderRegistry`/`ProfileStore` stay ports (domain interfaces), so adapters implement them without leaking SDK types up.

### Where the "World" lives, and the Fleks seam

The **World** is the entity collection inside a `Profile` (spec Key Entities: "Profile = World"). `Profile` keeps its wire metadata (`schemaVersion`, `basedOnPreset`, `presetVersion`, `layoutKey`, snapshots) and exposes its entities as the World the ecs primitives operate on. Call-site **vocabulary** mirrors Fleks so the exit ramp is real:

- spawn: `entity(id) { component(AppTile(...)); tag(Tag.Tile); parent("flow-calls") }` ≈ Fleks `world.entity {}`
- typed access: `entity.get<Component.AppTile>()` ≈ Fleks `entity[AppTile]`
- query: `profile.family { all(Tag.Tile); none(Tag.Toolbar) }` ≈ Fleks `world.family { all(...) }`
- compose: `entity.with(TestFlag)` / `entity.without<LifecycleState>()`

Internals stay **concrete to our `Component`/`Tag`** (not generic over a component base) — a second component universe is a speculative abstraction rule 4 forbids; the Fleks-shaped *vocabulary*, not generic internals, is what preserves swap-compatibility. The seam:

```kotlin
// TODO(ecs-fleks-migration): World internals swappable to Fleks;
// cost = Kotlin 2.0→2.4 upgrade + String→Int id remap; persistence stays ours.
```

### The engine's per-component dispatch (was per-entity)

`ReconcileEngine` today resolves one `Provider` per entity (one component). In the free-bag model it iterates **each domain-data component in an entity's bag**, resolves a `Provider` per component through the unchanged `ProviderRegistry`, applies it, and records the result by **swapping the entity's `LifecycleState`** (not writing a `status` field). Structural components (Workspace/Flow/ToolbarButton) and `LifecycleState` itself resolve to `NoOpProvider` (no OS effect) — same fallback as today. At MVP every entity holds exactly one data-component, so the loop is behaviourally identical to the current one; the shape generalises for free.

---

## Data model

See [`data-model.md`](data-model.md). Key shapes:

- **`Entity`** = `Entity(id: String, components: List<Component>, tags: Set<Tag>, parentId: String? = null, wizardBehavior: WizardBehavior, critical: Boolean)` — **free bag**, no `status` field.
- **`Component`** = `sealed interface`, closed set: the **11 domain-data** subtypes (unchanged data, `tags` field removed) + **`LifecycleState`** state component. Exhaustive `when` for serialization/coverage preserved.
- **`Tag`** = unchanged 13-value enum, now `Set<Tag>` on `Entity`.
- **`Blueprint`** = Bundle: `Blueprint(id, components: List<Component>, tags: Set<Tag>, wizardBehavior, critical, requires, required)` — spawn template only, not retained identity.
- **`Profile`** = wire metadata + `entities: List<Entity>` (renamed from `components`); `mark()`/`replaceComponent()` → `setState()`/`with()`/`without()` bag ops.
- **`LifecycleState`** = state encoding (§ above), replaces `ComponentStatus` enum.

---

## Wire formats

- [`contracts/profile-serialization.md`](contracts/profile-serialization.md) — Profile serialized JSON, **entity-grouped, mirror of Fleks `Snapshot`**: `{ schemaVersion, …, entities:[ { id, parentId, components:[ {type,…} ], tags:[…] } ] }`. kotlinx polymorphic (`classDiscriminator="type"`), zero custom serializer. `schemaVersion` present, **value stays 2, no bump, no migrator, no backward-compat test** (Article XX pre-MVP; owner directive). Fail-loud on unknown `type`/`Tag` (honest contract inherited from TASK-127; lenient reader remains a future step before cross-device exchange). Roundtrip + at-most-one-per-type + coverage fitness are the contract tests.
- [`contracts/ecs-world-core.md`](contracts/ecs-world-core.md) — the own core's public surface: `entity{}` spawn DSL, `get<T>()`, `with/without`, `Family { all/any/none }`, the `TODO(ecs-fleks-migration)` seam, LOC budget.
- [`contracts/query-api.md`](contracts/query-api.md) — `ProfileQuery` selector surface over `entity.tags` + `entity.components`, hierarchy selectors, render gating via `LifecycleState`.

**Rewritten in place (not migrated)** — `pool.json` (blueprints as bundles), `bundled-presets/*.json`, all wire fixtures. Per rule 9 (preset-readiness): rewritten `pool.json` / `bundled-presets/*.json` MUST keep the `ConfigSource` / `BundledSource`-as-one-of-many loading shape and retain a `// TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace` seam at the `BundledSource` site — the ECS reshape is internal to the artifact, the shareable-adapter seam is not touched.

Zero-knowledge (rule 13) unaffected: the whole `Profile` is one opaque blob; the server never parses entities. No server endpoint touched → `checklist-zero-knowledge-server` / `checklist-server-hardening` N/A.

---

## Dependency impact

**No new gradle dependencies.** The own ECS core is plain Kotlin (`entity{}` DSL + `Family` matcher + compose ops) over existing kotlinx.serialization + coroutines. Article XIII "add nothing" outcome — preferred, documented. Fleks is **not** added (OQ-1: game runtime + Kotlin 2.4.0 requirement; own core instead).

---

## Test strategy

Per CLAUDE.md §6 (mock-first) + §7 (fitness functions), Article X preferred order.

### Contract / wire tests (`core/src/commonTest/.../preset/wire/`)
- **`ProfileSerializationRoundtripTest`** — mixed profile (tiles, flow, toolbar buttons, an entity carrying a data-component + a state component + several tags) → entity-grouped JSON → `Profile`, assert equal. Json config mirrors `DataStoreProfileStore` exactly. Fixtures rewritten in place under `fixtures/profile-wire-format/`.
- **Fail-loud pins** — unknown `type` / unknown `Tag` value → `SerializationException` (documented honest contract; lenient reader deferred).
- **REMOVED**: any migration / backward-compat test (Article XX — nothing to migrate).

### Unit tests
- **`EntityCompositionTest`** (Capability Story 1) — multi-tag membership (`byAllTags`/`byAnyTag`); add test-only `TestFlag` (commonTest-only fake) to an `AppTile` entity and to a `Workspace` entity via `with()`, `get<TestFlag>()` finds it, base component still present, no subtype gained a field.
- **`ProfileQueryTest`** (Capability Story 2) — `byTag`/`byAllTags`/`byAnyTag`/`byNotTag` over `entity.tags`; `get<Component.Flow>()?.order`; empty/not-present cases.
- **`ProfileFactoryTest`** (Capability Story 3) — spawn free bag from bundle + inline components + `paramsOverride` + `parentRef`; `(Bundle, Extra)` flattened; bundle id not retained as identity; two entries from one bundle are independent.
- **`ProfileQueryHierarchyTest` / `ValidateHierarchyTest`** (Capability Story 5) — `flows`/`tilesOf`/`toolbarButtons`/`children`/`roots` parity with TASK-127; `DanglingParentRef`/`CircularParentRef`/`DanglingTargetRef` preserved.
- **`ReconcileEngineStateTest`** (FR-STATE) — provider `Ok` → entity gains `LifecycleState.Applied`; `Failed` → `Failed(reason)` with data; `NeedsUserConfirmation` → `Unverifiable`; `BootCheck` skips `Unverifiable`; render gating hides `Failed`/`Skipped`.

### Fitness functions (rule 7)
- **`ComponentCoverageFitnessTest`** — every `Component` subtype has a serializer and (where applicable) a `Provider`; exhaustive `when` compiles (SC-006).
- **`AtMostOneComponentPerTypeFitnessTest`** (CL-3 / FR-015d) — no entity holds two components of the same type (guarantees `get<T>()` unambiguity).
- **`TagConsistencyFitnessTest`** (CL-4 / FR-015e) — tags assigned only explicitly at spawn / by composing code; no auto-derivation from components.
- **Import-guard** (`checklist-domain-isolation` + source-set placement) — `Entity`/`Component`/`Blueprint`/ecs-core zero Android imports (SC-008).
- **Core LOC budget** — `preset/ecs` package ≤ ~400 LOC (NFR-003, SC-011); `TODO(ecs-fleks-migration)` seam present.

### Consistency (cleanup) checks
- Grep SC-007 — no "tagged-component" / "not canonical ECS" / "discriminated union" describing the *current* model in `docs/architecture/preset-model.md`; `ADR-012` has "Superseded by ADR-013"; `ADR-013` exists; `TASK-120`/`TASK-127` carry `superseded-by: TASK-136`.
- Grep SC-004 — no `entity.component as? X` remains anywhere; SC-009 — `schemaVersion` present = 2, no migration writer / backward-compat reader in the tree.

Every port already has a fake + real adapter (`FakeProfileStore`, `NoOpProvider`); the refactor preserves that (rule 6).

---

## Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R-1 | Big-bang rewrite leaves a consumer on the old `entity.component` shape → compile break mid-migration | High (mechanical) | Low | Big-bang is a single coherent PR; `SC-004` grep gate + compiler catch every callsite. Consumer inventory enumerated in Migration plan below. |
| R-2 | `LifecycleState` in the polymorphic `components` list collides with a data component of the same JSON `type` | Low | Medium | Distinct `@SerialName`s; `AtMostOneComponentPerTypeFitnessTest` is per Kotlin type, `LifecycleState` is its own type — no collision. |
| R-3 | Own ECS core creeps past 400 LOC toward a game runtime (regress to rejected Fleks-use-case) | Medium | Low | NFR-003 LOC-budget fitness fails the build; Family DSL + compose ops only, no system scheduler. |
| R-4 | Multi-data-component capability has no product consumer → speculative abstraction (rule 4 / meta-minimization) | Certain (acknowledged) | Low | **Consciously accepted** in Decision Rationale 6 (canonical multi-component is the model's intrinsic shape, not a bolted-on layer; pays down the "correction tax"). Exercised today by tags + a test-only `TestFlag`. Recorded as an Article XVII-style deviation (Complexity Tracking below). |
| R-5 | Fixtures / bundled presets rewritten to Bundle shape drift from code → runtime mismatch on smoke | Medium | Medium | Roundtrip + `ProfileFactory` tests run over the real rewritten fixtures; big-bang keeps them in one PR. |
| R-6 | Unknown `type`/`Tag` fail-loud crashes a future cross-device reader (rule 5 backward-read) | Low (pre-release, same binary) | High (post-sharing) | Honest fail-loud pinned by contract tests; lenient reader is a hard trigger before admin-push / preset-sharing (inherited TASK-131 note). |
| R-7 | Dropping `status` field breaks a persisted dev profile on next read | Low | None | Article XX pre-MVP: dev `ProfileStore` is wiped by fresh install; no migrator owed. |
| R-8 | Rewritten `pool.json` / presets lose the `BundledSource`-as-one-of-many seam (rule 9) | Low | Medium | Explicit FR: keep `ConfigSource` loading shape + `// TODO(shareability)` seam; `checklist-preset-readiness` re-checked. |

---

## Migration / rewrite plan (ordered)

Big-bang, one coherent PR (FR-016). Suggested internal order for reviewable diff:

1. **model/ecs core** — `Component` sealed class → sealed interface, drop `tags` from 11 subtypes, add `LifecycleState`; `Entity` free bag (drop `status`); `Blueprint` → Bundle; delete `ComponentStatus`; new `preset/ecs/` package (`EntityDsl`, `Family`, `World` + seam).
2. **engine** — `ProfileFactory` spawn (flat bundle expansion, no base/extra); `ReconcileEngine` per-component dispatch + `LifecycleState` transitions; `validateHierarchy`, `PresetValidator`, `PresetDiff` read via `get<T>()`.
3. **query** — `ProfileQuery` selectors over `entity.tags` + `get<T>()`; render gating via `get<LifecycleState>()`; add typed `Entity.get<T>()`.
4. **consumers** — `ProfileBackedFlowRepository` (`get<Component.Flow>()`, `toSlot`); `WizardScreen` `when(pc.component)`; `PostWizardKioskApply` `is X`. (DI bindings + `FlowRepository` port unchanged.)
5. **assets** — `pool.json` blueprints → bundles; `bundled-presets/*.json` entity-grouped, keep `BundledSource` + `// TODO(shareability)` seam; all wire fixtures entity-grouped.
6. **tests** — rewrite roundtrip/query/factory/engine/fitness suites; delete migration tests.
7. **CLEANUP INVENTORY** (FR-017..FR-021, executes in this PR):
   - `docs/architecture/preset-model.md` — rewrite in place to canonical ECS; drop "tagged-component / not-canonical-ECS / discriminated union" descriptions; update AI-TLDR.
   - `ADR-012` — add "Superseded by ADR-013" header; write **`ADR-013`** (canonical ECS adopted; reverses ADR-012 on composition-need + pre-release timing).
   - `TASK-120`, `TASK-127` frontmatter — `superseded-by: TASK-136`.
   - Downstream notice (FR-021): TASK-69/71/68/19 get a "See TASK-136 Decision" pointer via `dependencies:` (their own spec edits are out of scope here).

**Consumer inventory (grep-verified 2026-07-18)** — real `Entity.component` / `Blueprint.component` / `ComponentStatus` sites: `ProfileQuery.kt`, `ProfileFactory.kt`, `ReconcileEngine.kt`, `PresetValidator.kt`, `PresetDiff.kt`, `ProfileBackedFlowRepository.kt`, `WizardScreen.kt`, `PostWizardKioskApply.kt`, `Profile.kt` (`mark`/`replaceComponent`), plus fixtures/presets. `Provider`/`ProviderRegistry`/`FlowRepository` ports and DI bindings keep their signatures.

---

## Required Context Review

- [CLAUDE.md](../../CLAUDE.md) — rule 1 (domain isolation), rule 3 (one-way door + exit ramp), rule 4 (MVA), rule 5 (wire-format versioning), rule 9 (preset-readiness), rule 11 (Decision→Done + superseded-by), rule 13 (zero-knowledge — Profile stays opaque).
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — **Article XX** (pre-MVP no-migration override — the authority for "no migrator/no bump"), Article XI (anti-abstraction), Article XVI (Constitution Check), Article XVII (deviation for the acknowledged multi-component capability).
- [docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md](../../docs/adr/ADR-012-tagged-component-model-vs-canonical-ecs.md) — the decision this task reverses (gets "Superseded by ADR-013").
- [docs/architecture/preset-model.md](../../docs/architecture/preset-model.md) — the live model doc rewritten in place (FR-017).
- [backlog/tasks/task-136 …](../../backlog/tasks/task-136%20-%20Decision-ECS-canonical-foundational-model.md) — Decision block (authority); [task-120](../../backlog/tasks/task-120%20-%20Decision-Component-Preset-Profile-foundational-model.md) / [task-127](../../backlog/tasks/task-127%20-%20HomeActivity-config-load-failure-post-wizard-TASK-126-regression.md) — superseded predecessors.
- [specs/task-127-ecs-foundation/plan.md](../task-127-ecs-foundation/plan.md) — closest format analogue (this plan follows its shape).
- **Omitted (explained)**: `docs/compliance/*` (no permission/data-collection change — pure domain refactor); `docs/dev/server-*` and `server-log.md` (no server touch point — Profile is an opaque blob, no endpoint added/modified); `docs/product/*` (no user-facing behaviour change).

---

## Constitution Check

*GATE: must pass before implementation. Run by `procedure-constitution-check` 2026-07-18 against this plan.*

| Gate | Status | Justification |
|------|--------|---------------|
| G-1 Architecture | **PASS** | In-place rewrite within existing `core/preset/*` + one new sub-package `preset/ecs/`; **no new gradle module** (Article V §3 — none of the five criteria met). Boundaries explicit, arrows downward (rule 1 diagram). Ports (`Provider`/`ProviderRegistry`/`FlowRepository`/`ProfileStore`) unchanged. |
| G-2 Core/System Integration | **N/A** | No system events, no `BroadcastReceiver`, no lifecycle callbacks introduced or changed. `ReconcileEngine`/`Provider` roles unchanged (FR-011). Pure domain reshape. |
| G-3 Configuration | **PASS** | Wire format changes (free-bag entity, entity-grouped serialization, Bundle) are handled under **Article XX pre-MVP override**: `schemaVersion` field present, **no bump, no migrator, no backward-compat** — explicitly licensed (owner directive + Article XX §2). Roundtrip + at-most-one-per-type + coverage contract tests present. `BundledSource`/`ConfigSource` shape + `// TODO(shareability)` seam preserved (rule 9). |
| G-4 Required Context Review | **PASS** | Links present: CLAUDE.md rules (1,3,4,5,9,11,13), Article XX/XI/XVI/XVII, ADR-012, preset-model.md, TASK-136/120/127, TASK-127 plan. Omissions (compliance/server/product) explained. |
| G-5 Accessibility | **PASS** | **No new UI surface** — model/serialization refactor only; `FlowDescriptor` UI contract unchanged (Assumptions). Render gating (`Failed`/`Skipped` hidden) preserved via `LifecycleState` — the elderly-user "no dead button" invariant is kept, not weakened. |
| G-6 Battery/Performance | **PASS** | No background work, no polling, no new deps. Query stays linear scan over ~20–40 entities < 1 ms (NFR-002). `BootCheck` still skips `Unverifiable` — cold-start work not increased. |
| G-7 Testing | **PASS** | Contract (entity-grouped roundtrip, fail-loud pins — no migration tests per Article XX); unit (composition, query, spawn, hierarchy+validation, engine state transitions); fitness (coverage, at-most-one-per-type, tag-consistency, import-guard, LOC budget). Every port keeps fake + real (rule 6). |
| G-8 Simplicity | **PASS with documented deviation** | Own core is minimal (Family DSL + compose ops, ≤400 LOC, no scheduler) and concrete-to-`Component` (no speculative generic universe). **One acknowledged tension** (Article XI §2): the multi-data-component capability has no current product consumer — **consciously accepted** in Decision Rationale 6 as the model's intrinsic canonical shape paying down a recurring "correction tax", not a bolted-on abstraction. Recorded below (Complexity Tracking) as an Article XVII deviation. |

**OVERALL: 7 PASS, 1 N/A, 0 FAIL — plan is COMPLETE.**

### Complexity Tracking (Article XVII deviation)

| Deviation | Article | Reason | Removal condition |
|-----------|---------|--------|-------------------|
| Adopt canonical multi-data-component composition with no current product consumer of *data-level* (vs tag-level) multi-component | XI §2 (no abstraction without current consumer) | Decision Rationale 6 (owner, 2026-07-18): a bespoke non-canonical model imposes an ongoing correction-tax on a novice owner + AI agents (repeatedly observed this session); a documented industry-standard model removes it. Canonical multi-component is the model's *intrinsic shape*, not an added layer. One cheap pre-release rewrite now vs paying the tax forever. | Not applicable — this is the chosen canonical model; the first data-level consumer (a future cross-cutting data component) lands additively. Revert path documented as an exit ramp (re-collapse `components: List` → single, viable only pre-release). |

---

## Rollout / verification

1. **Build gates**: `./gradlew :core:test` green; `:app` mock/real backend variants compile (big-bang consumers migrated).
2. **Fitness gates**: coverage, at-most-one-per-type, tag-consistency, import-guard, LOC-budget all green.
3. **Contract gate**: entity-grouped roundtrip + fail-loud pins.
4. **Consistency gates**: SC-004 (no `as? Component`), SC-007 (no stale doc descriptions; ADR-013 + superseded-by), SC-009 (schemaVersion=2, no migrator).
5. **Emulator smoke** (deferred, `[deferred-local-emulator]`): fresh install → wizard → HomeScreen shows tiles from the rewritten bundled preset.
6. **Backlog sync**: `pre-pr-backlog-sync` before PR — `[auto:checklist]` + `[auto:deferred-*]` regenerated; status → Verification/Done per gates.

Device matrix N/A (pure domain refactor — see spec § OEM / Device Matrix).

---

## Project Structure

```
specs/task-136-ecs-canonical-foundational-model/
├── spec.md                       # /speckit.specify → clarify output (+ Scope section added this pass)
├── plan.md                       # this file
├── data-model.md                 # concrete Kotlin shapes (Entity/Component/Blueprint/LifecycleState/DTO)
├── contracts/
│   ├── profile-serialization.md  # entity-grouped JSON wire contract + roundtrip/coverage/at-most-one tests
│   ├── ecs-world-core.md         # own core public API (entity{}, get<T>, Family), Fleks seam, LOC budget
│   └── query-api.md              # ProfileQuery selector surface + render gating
├── quickstart.md                 # dev verification (:core test commands from spec Local Test Path)
└── tasks.md                      # /speckit.tasks output — NOT created here
```

**Structure Decision**: extend the existing `core` KMP module; new files under `preset/ecs/`. No new gradle module (Article V §3 — none of the five criteria met; extension functions + one small package + data reshape do not warrant a module split, rule 4 MVA).

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски (для владельца)

**Что этот план описывает.** КАК переписать фундамент на канонический ECS (спека говорит ЧТО и ЗАЧЕМ). Сущность становится «мешком» компонентов + набором тегов, статус превращается из поля в компонент-маркер, который двигает движок.

**Одно решение, которое я принял здесь** (Decision оставил его плану): **статус применения = один компонент `LifecycleState`** (`Pending`/`Applied`/`Failed(причина)`/`Skipped`/`Unverifiable`) в мешке, а НЕ набор тегов. Почему: статус — взаимоисключающий (сущность в одном состоянии за раз), а `Failed` несёт данные (причину). Теги — это множество без данных, они бы позволили «Applied и Failed одновременно» и не дали бы прицепить причину. Один компонент решает обе проблемы чисто.

**Границы.** Никаких новых gradle-модулей (просто новый пакет `preset/ecs/` на ~300 строк в форме Fleks). Ноль новых зависимостей. Сервер не трогается (профиль — непрозрачный blob). UI и экраны не меняются. Мигратор не пишем — приложение не выпущено (Article XX разрешает).

**Одна честная оговорка** (записал как отклонение по Article XVII): многокомпонентная композиция «на уровне данных» пока не имеет продуктового потребителя — это осознанно принято владельцем как «intrinsic shape» канонической модели, платит за «налог на постоянные исправления» ad-hoc модели. Сегодня используется тегами + тестовым fake-компонентом.

**Проверка конституции: 7 PASS, 1 N/A, 0 FAIL — план полный.**
<!-- NOVICE-SUMMARY:END -->
