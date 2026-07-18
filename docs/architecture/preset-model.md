# Preset / Profile / Component Architecture

<!-- AI-TLDR:BEGIN -->

## AI TL;DR

**What we're modelling**: launcher configuration as **canonical ECS** (entity = free bag of components + tags; composition after assembly). Foundation: TASK-120 (Component / Provider / Profile) → TASK-127 (Tag / Query / hierarchy / honest state) → **TASK-136 ([ADR-013](../adr/ADR-013-canonical-ecs.md)) made it canonical**: `Entity` is a free bag, `Component` is a `sealed interface` with no `tags` member, tags live on the entity, apply-state is a `LifecycleState` component. We do NOT use a runtime ECS framework — we run a **small in-house core (~200–400 LOC) that mirrors Fleks's API vocabulary** (`core/preset/ecs/`), so a future swap to Fleks is a real exit ramp.

**The one mental model to keep: ECS ≈ a database table.**

| Database | ECS | Here |
|---|---|---|
| table | World | `Profile` (its `entities` list) |
| row | Entity | `Entity` (id + free bag of components + tags) |
| columns | Components | `Component.AppTile(packageName, labelKey)`, … |
| `WHERE tag='Tile'` | Query | `byTag(Tag.Tile)` / `profile.family { all(Tag.Tile) }` |
| foreign key `parent_id` | `Parent`/`Children` | `Entity.parentId` |
| stored procedure | System | `ReconcileEngine` + `Provider` |

Storage is **flat**. The screen tree is *computed* by queries, never nested in the wire format — same pattern as Bevy / Unity DOTS `Parent` and Android Launcher3's `favorites.container`.

**Core types** (all in `core/src/commonMain/kotlin/com/launcher/preset/`):

- **Pool** — catalog of `Blueprint`s (bundled `pool.json`, source of truth for available components).
- **Blueprint** — a **Bundle**: `id` + `components: List<Component>` + `tags: Set<Tag>` + wizardBehavior + critical + requires. A spawn template only; after `ProfileFactory` spawns an entity from it the bundle identity is discarded (Bevy: "zero runtime significance after creation").
- **Preset** — shareable JSON template referencing Pool entries (`schemaVersion` + wizardFlow + settingsMap + activeComponents). Shareable per CLAUDE.md rule 9. `ActiveComponentEntry` declares `poolRef` + `paramsOverride` + `parentRef` — **no `status`** (apply-state is injected by the factory).
- **Profile** — device-local instance built from `Preset + Pool` by `ProfileFactory`. Source of truth for runtime UI/behaviour state. Persisted, `schemaVersion = 2`. Its `entities: List<Entity>` is the World.
- **Entity** — a row: `id` + `components: List<Component>` (free bag) + `tags: Set<Tag>` + `parentId` + wizardBehavior + critical. **No single `component` field; no `status` field.** At most one component per Kotlin type (CL-3, fitness-enforced) ⇒ `entity.get<T>()` is unambiguous.
- **Component** — `sealed interface`, closed set: **11 data subtypes** (`AppTile`, `Sos`, `FontSize`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`, `Workspace`, `Flow`, `ToolbarButton`) + the state component **`LifecycleState`**. No `tags` member (tags moved to the entity). `LauncherRole` / `StatusBarPolicy` are `data object`.
- **LifecycleState** — the apply-state as a **component** in the bag (`Pending`/`Applied`/`Skipped`/`Unverifiable` = data objects; `Failed(reason)`). Replaces the old `ComponentStatus` enum + `Entity.status` field. Transitioned by the System via `profile.setState(id, …)`. No Provider (like the structural subtypes).
- **own ECS core** (`preset/ecs/`) — `entity(id){ component(); tag(); parent() }` spawn DSL, `entity.get<T>()`, `entity.with(c)` / `without<T>()`, `profile.family { all/any/none }`. Fleks-shaped vocabulary, concrete to our types, ≤ 400 LOC (fitness), carries `// TODO(ecs-fleks-migration)` on `World.kt`.
- **Provider** — per-platform effector (`Provider<T : Component>` port). Fallback chain vendor → platform → NoOp. **Structural subtypes and `LifecycleState` have no Provider by design** — nothing to apply to the OS.
- **ReconcileEngine** — the System. Iterates each entity's domain-data components, dispatches through `ProviderRegistry`, records the outcome by swapping the entity's `LifecycleState`. Modes: `Wizard`, `BootCheck`, `Single`, `RemotePush`.
- **Tag** — **13 values**: 8 semantic (Presentation / Appearance / System / Safety / Capabilities / Communication / Accessibility / Emergency) + 5 structural (Tile / Toolbar / Workspace / Flow / ToolbarButton). A zero-data marker on the **entity**, assigned explicitly at spawn (bundle) or by composing code — never auto-derived from a component.
- **Query** (`preset/query/ProfileQuery.kt`) — tag selectors (`byTag`, `byAllTags`, `byAnyTag`, `byNotTag`) + hierarchy selectors (`children`, `roots`, `workspace`, `flows`, `tilesOf`, `toolbar`, `toolbarButtons`), reading `entity.tags` + `entity.get<T>()`. Queries are **never persisted**.

**Three orthogonal axes** (do NOT conflate them):

| Axis | Where declared | Answers | Example |
|------|----------------|---------|---------|
| **Lifecycle** | `Preset.wizardFlow` / `settingsMap` / `activeComponents` + the `LifecycleState` component | WHEN / in what apply-state | AppTile in `wizardFlow` = asked during first-run; `LifecycleState.Applied` = applied on this device |
| **Semantic** | `Entity.tags: Set<Tag>` | WHAT the component is about | entity tagged `[Presentation, Tile, Communication]` = a visible messaging tile |
| **Structural** | `Entity.parentId` + Workspace/Flow/ToolbarButton | WHERE on the screen it lives | entity with `parentId = "flow-calls"` = a tile on the Calls tab |

**Two rules that are easy to get wrong**:

1. **Render gating** — tags say *what* a component is; `LifecycleState` says whether this device managed to apply it. `homeScreenTiles()` / `tilesOf()` exclude `Failed` and `Skipped` (an elderly user must never face a dead button). `Pending` / `Applied` / `Unverifiable` / absent all render.
2. **`Unverifiable` is honest, not a bug** — Android exposes no read-back for some settings (hiding the status bar is a chain of intents with no query API). The provider returns `Outcome.NeedsUserConfirmation`, the wizard asks the human, the engine records `LifecycleState.Unverifiable` instead of a fictional `Applied`. `BootCheck` skips those entities; they are re-verified only on an explicit Settings action.

**The screen, end to end** (owner's target shape):

```
ws-main        Workspace                          parentId = null
├── flow-calls Flow(order 0)                      parentId = ws-main
│   ├── tile   AppTile(com.whatsapp)              parentId = flow-calls
│   └── sos    Sos                                parentId = flow-calls
├── flow-apps  Flow(order 1)                      parentId = ws-main
└── toolbar    Toolbar                            parentId = ws-main
    ├── btn-1  ToolbarButton(target=flow-calls)   parentId = toolbar
    └── btn-2  ToolbarButton(target=flow-apps)    parentId = toolbar
```

Stored as a flat list of entities; `ProfileBackedFlowRepository` projects it onto the existing `FlowDescriptor(id, name, slots)` UI contract — one descriptor per Flow — so no port and no UI changed. A profile with **no** Flow entities (the simple-launcher case) yields one synthetic descriptor holding every tile.

**Data flow** (happy path):
```
Pool (assets/pool.json) + Preset → ProfileFactory.create() → Profile (each entity spawned with LifecycleState.Pending)
    ↓
ReconcileEngine.run(mode) → per entity, per data component → Provider.apply() → Outcome
    ↓
Profile updated (LifecycleState swapped per entity), persisted via ProfileStore
    ↓
UI reads Profile via Query selectors (HomeScreen ← ProfileBackedFlowRepository)
```

**Key decisions**:

- **Canonical ECS, free-bag Entity** — composition after assembly ([ADR-013](../adr/ADR-013-canonical-ecs.md), supersedes ADR-012).
- **`sealed interface Component`, closed set** — exhaustive `when` for serialization + coverage survives; composition gained.
- **Tags on the entity** (`Set<Tag>`), assigned explicitly — no auto-derivation.
- **Apply-state as a `LifecycleState` component** — no special status field (CL-5).
- **Hierarchy by reference, storage flat** (`Entity.parentId`) — the tree is computed, never nested.
- **Own ECS core, Fleks-shaped** — not Fleks-direct (game runtime + Kotlin 2.4.0); swap seam documented.
- **`schemaVersion` stays 2, no migrator** — pre-release clean-in-place (Article XX); on first ship the shipped shape becomes the v1 baseline.

**Rejected**:

- ECS runtime frameworks (Fleks/Ashley/Artemis) for the *runtime* — game-loop scheduler we don't need + Kotlin 2.4.0. We borrow the *pattern + API vocabulary* only.
- **Type-grouped (archetype/sparse-set) storage** — an in-memory perf layout irrelevant at ~20–40 entities; an exit ramp if scale ever demands it.
- **Nested containers** for the screen tree (`Flow` holding its tiles) — breaks cross-cutting queries and tile moves; fights the table model.

**Open** (deferred, with owners):

- **Lenient reader** — an older reader currently fails loud on an unknown Tag / Component type. Safe while a Profile never leaves the device that wrote it; **mandatory before admin push or preset sharing** — TASK-131.
- **Preset → Profile update** (preset v2 ships, installed profiles must catch up) — TASK-130.
- **Add-flow UX / empty slots** — TASK-134. Do NOT model an empty slot as an entity; absence is absence.
- **First data-level multi-component consumer** — welcome additively (the model's intrinsic shape; today exercised by tags + two-real-type composition in tests).

**How AI should use this file**:

- Routine questions about the model → **read TL;DR only**, stop here.
- Adding a new `Component` subtype or `Tag` → jump to §4 / §5 checklists.
- Question about `ConfigDocument` legacy → §7.
- Confused about "why does this component have both a lifecycle position and a tag" → the three-axes table above.

<!-- AI-TLDR:END -->

## 2. Three Orthogonal Dimensions

The most common confusion when reading this codebase: mistaking **lifecycle position** for **semantic tag** for **structural place**. They are independent axes (see the TL;DR table).

### Object structure (canonical ECS)

```
Preset {                            // shareable JSON template
    schemaVersion: 2,
    wizardFlow:  [ { poolRef, paramsOverride } ],   // ← LIFECYCLE: shown during first-run
    settingsMap: [ { poolRef, paramsOverride } ],   // ← LIFECYCLE: shown in Settings
    activeComponents: [ { poolRef, paramsOverride, parentRef } ]  // ← LIFECYCLE: applied on device
}

Pool {                              // catalog of Bundles (assets/pool.json)
    Blueprint {
        id: "tile-whatsapp",
        components: [ { type: "AppTile", packageName: "com.whatsapp", labelKey: "..." } ],
        tags: ["Presentation", "Tile"]              // ← SEMANTIC: stamped on the entity at spawn
    }
}

Profile {                           // device-local instance
    entities: [
        Entity {
            id: "tile-whatsapp",
            components: [                             // ← free bag
                { type: "AppTile", packageName: "com.whatsapp", labelKey: "..." },
                { type: "LifecycleState.Applied" }   // ← apply-state IS a component
            ],
            tags: ["Presentation", "Tile"],          // ← SEMANTIC, on the entity
            parentId: "flow-calls"                    // ← STRUCTURAL
        }
    ]
}
```

### Rule of thumb for AI

- Adding a component to `wizardFlow` / `settingsMap` / `activeComponents` — you're declaring **WHEN**.
- Adding tags to the **entity/bundle** — you're declaring **WHAT**.
- Setting `parentId` — you're declaring **WHERE**.
- **Never conflate them.** If you feel tempted to introduce a `wizardOnly: true` tag or a `Presentation` bucket in Preset, stop — that's the conflation.

## 3. Data Flow

```
1. App startup
2. Pool loaded from assets/pool.json (BundledPoolSource)
3. Preset loaded (bundled seed OR file OR share intent OR network — via PresetSource port)
4. ProfileFactory.create(preset, pool) → Profile
   (each active entry's bundle-ref expands into a flat component set + paramsOverride
    + parentRef → free-bag Entity; each spawned entity gets LifecycleState.Pending)
5. ReconcileEngine.run(mode) iterates entities, dispatches each data component via ProviderRegistry
   - Wizard: uses InteractionSink for Interactive steps
   - BootCheck: only critical entities (LauncherRole, permissions), skips Unverifiable
   - Single: one entity (Settings edit path)
   - RemotePush: applies ChangeItems from admin push
6. Provider.apply(component, profile) → Outcome
7. Engine records the outcome by swapping the entity's LifecycleState; persisted via ProfileStore
8. UI reads Profile via Query (ProfileBackedFlowRepository) → HomeScreen renders
```

## 4. Adding a New Component (checklist for AI)

1. Define a new `Component.MyNewType` data subtype in `core/preset/model/Component.kt` — **no `tags` member** (tags live on the entity). Keep it nested so `@SerialName` discriminators stay stable.
2. Add a `Blueprint` entry to `pool.json` with `components: [{ type: "MyNewType", … }]` + the `tags` the spawned entity should carry.
3. Implement `MyNewTypeProvider : Provider<Component.MyNewType>` (unless it is structural / state — those have no Provider).
4. Register the Provider in DI via `@IntoMap` + the component-key binding.
5. Add a unit test covering `check` and `apply` outcomes.
6. Update `ComponentProviderCoverageTest` — add the subtype to the sample list (and to the exempt set if it is structural / state).
7. **No changes needed** in `ReconcileEngine`, `ProviderRegistry`, or `ProfileFactory` (fitness per rule 4 MVA — if you find yourself editing engine code, the abstraction is broken).

## 5. Adding a New Tag

1. Add an enum value to `core/preset/model/Enums.kt` `Tag` — **additive only** (rule 5). Never rename or remove.
2. If commonly queried — add a convenience selector to `ProfileQuery.kt`.
3. Update `pool.json` bundles that should stamp the new tag onto their spawned entities.
4. No profile migration needed (tags are additive within schemaVersion 2).
5. Document the new tag in the glossary (§6).

## 6. Tag Glossary

`Blueprint.tags` in Pool declarations is authoritative — this glossary guides authoring.

| Tag | Meaning | Example entities |
|-----|---------|--------------------|
| `Presentation` | Visible on the home screen | AppTile, Sos, Toolbar |
| `Appearance` | Visual theming | FontSize, Theme |
| `System` | OS-level settings | LauncherRole, StatusBarPolicy, Language |
| `Safety` | Emergency / protective | Sos |
| `Capabilities` | Feature-gates for cloud/pairing | (future) |
| `Communication` | Messaging / calling apps and contacts | AppTile (WhatsApp, Phone) |
| `Accessibility` | Settings addressing disabilities | FontSize |
| `Emergency` | Highest-severity actionable | Sos |
| `Tile` / `Toolbar` / `Workspace` / `Flow` / `ToolbarButton` | Structural role on the screen | the matching structural entities |

## 7. Deprecated: ConfigDocument path

- **ConfigDocument was an MVP hack** for cloud-delivered configuration (spec 008 / 009 era).
- **Replaced** by Profile-as-source-of-truth. See `docs/dev/server-roadmap.md` § SRV-CONFIG-DEPRECATION.
- **Do not add new `ConfigDocument` usage.** If you must touch existing usage, add a `// TODO(config-deprecation)` comment.

## 8. Related Files

Code:

- `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/LifecycleState.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Preset.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/ecs/` (EntityDsl, Family, World)
- `core/src/commonMain/kotlin/com/launcher/preset/engine/ReconcileEngine.kt`, `ProfileFactory.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/port/Provider.kt`, `ProviderRegistry.kt`
- `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt`

Docs / ADRs:

- [ADR-013 — Canonical ECS](../adr/ADR-013-canonical-ecs.md) (current)
- [ADR-012 — Tagged-Component vs Canonical ECS](../adr/ADR-012-tagged-component-model-vs-canonical-ecs.md) (superseded, history)

Backlog / Specs:

- TASK-136 — canonical ECS (`specs/task-136-ecs-canonical-foundational-model/`)
- TASK-120 / TASK-127 — predecessors (superseded by TASK-136)
