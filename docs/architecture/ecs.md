# ECS — Ecosystem Configuration Model (Entity · Component · System)

**This is the single source of truth for the ECS approach across the whole app ecosystem** — the launcher today, and every future ecosystem app that models configuration/state. It is named `ecs.md` (not `preset-model.md`) on purpose: the pattern is bigger than presets. If this file and any other doc disagree, **this file wins** — except on **wire-format versioning**, which is owned by [`wire-format.md`](wire-format.md) (that file wins there; this one does not restate its rules). When you change the model, update this file **in the same commit** (see §12).

<!-- AI-TLDR:BEGIN -->

## AI TL;DR

**THE ADOPTED APPROACH — the beacon (point agents here; do NOT re-decide this).** Our configuration architecture is a **two-layer, industry-precedented pattern**, not a bespoke invention:

1. **Data model = ECS** — entity = free bag of components + tags, queried by type/tag. Production-proven on mobile up to a **first-party API**: Apple **GameplayKit** (`GKEntity`/`GKComponent`/`GKComponentSystem`, official iOS/macOS/tvOS SDK since iOS 9); Unity DOTS shipped the mobile title *Detonation Racing*; our stack region (Kotlin/**Fleks** via KorGE) runs natively on Android+iOS. Non-game ECS precedent exists too (A-Frame, Meta Spatial SDK, robotics sim, AWS IoT Device Shadow).
2. **Execution engine = declarative desired-state reconciliation (the Kubernetes controller pattern)** — NOT a game loop. Each entity carries desired-vs-actual (like K8s `spec`/`status`); a reconcile control loop converges them; **each component type has a Terraform-style provider doing check→apply**. Our `ReconcileEngine` + `Provider` = exactly this.

**So "do it the way our approach works" = _"ECS-shaped data model + Kubernetes-style desired-state reconcile, per-component Terraform-style provider (check→apply)."_** Coverage proof (this pattern covers serialization / config-vs-runtime / check→apply / tag-type queries) and production references: §11. Honesty: the *fusion* of these two is not one textbook term — it's an observed convergence (cf. "ECS and K8s"; Google Prodspec "partially inspired by ECS"). We cite an **intersection of two established patterns**, we do not invent one. Consequence: **the in-house core is the permanent, correct choice**, not a placeholder awaiting a "real" engine (§11 Rejected).

---

**What we model**: launcher/app configuration as **canonical ECS** — an entity is a *free bag of components + tags*; composition happens after assembly. We do **not** use a runtime ECS framework; we run a **small in-house core (~200–400 LOC) that borrows ECS *vocabulary* (entity/component/tag/query) and Fleks's API *naming* for readability** (`core/preset/ecs/`). **The in-house core is the permanent, correct choice — not a placeholder waiting for a "real" engine** (research-verified, §11): no external ECS gives us the config layer, all are game runtimes (per-frame scheduler) irrelevant at ~20–40 entities, and the growth path is additive IaC-reconcile (Terraform/K8s/Puppet), not an engine swap. Foundation: TASK-120 → TASK-127 → **TASK-136 ([ADR-013](../adr/ADR-013-canonical-ecs.md)) made it canonical**.

**The one mental model: ECS ≈ a database table.**

| Database | ECS | Here |
|---|---|---|
| table | World | `Profile` (its `entities` list) |
| row | Entity | `Entity` (id + free bag of components + tags) |
| columns | Components | `Component.AppTile(packageName, labelKey)`, … |
| `WHERE tag='Tile'` | Query / Family | `byTag(Tag.Tile)` / `profile.family { all(Tag.Tile) }` |
| foreign key `parent_id` | `Parent`/`Children` | `Entity.parentId` |
| stored procedure | System | `ReconcileEngine` + `Provider` |

Storage is **flat**; the screen tree is *computed* by queries, never nested in the wire format (Bevy / Unity DOTS `Parent`, Android Launcher3 `favorites.container`).

**Core types** (`core/src/commonMain/kotlin/com/launcher/preset/`):
- **Pool** — catalog of `Blueprint`s (bundled `pool.json`).
- **Blueprint** — a **Bundle**: `id` + `components` + `tags` + wizardBehavior + critical + requires. **Spawn template only; discarded after spawn** (Bevy: "zero runtime significance after creation").
- **Preset** — shareable JSON template (`schemaVersion` + `wizardFlow` + `settingsMap` + `activeComponents`). Shareable per rule 9. Holds **presentation metadata** (see I2).
- **Profile** — device-local instance built from `Preset + Pool` by `ProfileFactory`. Runtime source of truth for behaviour + home render. Persisted; versioning per [`wire-format.md`](wire-format.md). `entities: List<Entity>` is the World. Keeps `basedOnPreset` + `presetVersion` (a pointer, see I4).
- **Entity** — a row: `id` + `components: List<Component>` + `tags: Set<Tag>` + `parentId` + wizardBehavior + critical. **No single `component`; no `status` field.** At most one component per Kotlin type (fitness-enforced) ⇒ `get<T>()` unambiguous.
- **Component** — `sealed interface`, closed set: 11 data subtypes (`AppTile`, `Sos`, `FontSize`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`, `Workspace`, `Flow`, `ToolbarButton`) + state component `LifecycleState`. No `tags` member.
- **LifecycleState** — apply-state **as a component** (`Pending`/`Applied`/`Skipped`/`Unverifiable` data objects; `Failed(reason)`). Replaced the old `ComponentStatus` enum + `Entity.status` field. Transitioned by the System via `profile.setState(id, …)`.
- **own ECS core** (`preset/ecs/`) — `entity(id){ … }` spawn DSL, `get<T>()`, `with(c)`/`without<T>()`, `family { all/any/none }`. Fleks-*named* for readability, ≤ 400 LOC (fitness). **Permanent home of the config/reconcile logic** — not a bridge (§11). The old `// TODO(ecs-fleks-migration)` framing on `World.kt` is superseded: Fleks would replace only the runtime half, leak vendor types into the domain (rule 1/2), and still leave us to build the config layer — see §11 Rejected.
- **Provider** — per-platform effector (`Provider<T : Component>`). Fallback vendor → platform → NoOp. Structural subtypes and `LifecycleState` have **no** Provider.
- **ReconcileEngine** — the System. Iterates each entity's data components, dispatches via `ProviderRegistry`, records outcome by swapping `LifecycleState`. Modes: `Wizard`, `BootCheck`, `Single`, `RemotePush`.
- **Tag** — 13 values (8 semantic + 5 structural). Zero-data marker on the **entity**, assigned explicitly at spawn — never auto-derived.
- **Query** (`preset/query/ProfileQuery.kt`) — tag + hierarchy selectors over `entity.tags` + `entity.get<T>()`. Never persisted.

**Three orthogonal axes** (never conflate — §2): **Lifecycle** (`wizardFlow`/`settingsMap`/`activeComponents` + `LifecycleState`) = WHEN/apply-state · **Semantic** (`Entity.tags`) = WHAT · **Structural** (`Entity.parentId`) = WHERE.

**RUNTIME INVARIANTS — the anti-re-derivation core (§9). Do NOT re-derive these; they are decided.**
- **I1** — The **Profile is self-contained for BEHAVIOUR + HOME render**: `ReconcileEngine`, `BootCheck`, and `ProfileBackedFlowRepository` read **only** the Profile.
- **I2** — **Presentation metadata lives on the PRESET, not the Profile.** `settingsMap` (`categoryKey`/`settingsIcon`/`sensitivity`) is a Preset field, re-read at runtime by Settings. The Profile is **not** presentation-self-contained *by design* (DRY; industry-standard "presentation stored once in the template").
- **I3** — **The transfer / share / remote-management unit is `Profile + its Preset` (a pairing), not the bare Profile.** Bundled presets exist on both ends (same app); custom presets are themselves shareable artifacts (rule 9) that travel alongside. This is Unity `EntityPrefabReference` / Flecs shared-template / korge-fleks `snapshot + AssetStore`.
- **I4** — **We mirror the korge-fleks `snapshot + AssetStore` pattern, reference-by-id** (verified against korge-fleks source, §11). Fleks *core* has no prefab/config concept — `Entity(id, version)` is a pure handle and a snapshot is `Snapshot(components, tags)`, so the **Blueprint/Bundle is discarded after spawn**. The by-id config separation is what **korge-fleks adds on top**: components store a `String` id (e.g. `Sprite.name` → "identifier for getting the sprite graphic from the AssetStore"), the heavy config lives in a separate `AssetStore`/`EntityFactory.entityConfigs` registry outside the snapshot, and `saveGameState` persists **only** `world.snapshot()` — config is resolved by id at runtime, never inlined. Mapping: Profile=snapshot, Pool+Preset=AssetStore/blueprint registry, `basedOnPreset`/`entity.id==poolRef`=the id reference. So the Profile retains `basedOnPreset` + `presetVersion` for updates (**TASK-130**). Precise: we mirror **korge-fleks** (its config layer), not Fleks core alone. The "preset forgotten after activation" (TASK-120 exploratory) was **superseded by TASK-127**.
- **I5** — `ProfileFactory` copies from the Pool **blueprint** into each entity: `components` (+ `paramsOverride` merged), `tags`, `parentId` (from `parentRef`), `wizardBehavior`, `critical`; injects `LifecycleState.Pending`. It does **not** copy `settingsMap`/`wizardFlow` presentation; the three-way lifecycle provenance (which list an entry came from) is **not** preserved on the entity.
- **I6** — Consumers read the Profile at runtime; **only Settings additionally reads `Preset.settingsMap`** (for grouping labels). Everything else is Profile-only.

**Class/app architecture (§10).** Layers: **domain** (`core/preset/`, pure Kotlin, zero Android) → **adapter** (`app/…/provider`, facades, DataStore, DI) → **UI** (Compose). UI/VM talk to **ports**, never to the engine or Android directly. Feature seam pattern (TASK-69): a feature exposes a purpose-shaped **gateway port** (e.g. `SettingsGateway`), behind which the engine lives as an adapter — so the engine is swappable without touching UI. Fitness: no `when(component)` and no Android calls in UI/engine; Component↔Provider coverage; at-most-one-per-type; ecs-core LOC budget.

**Industry grounding (§11).** Our model is mainstream, not invented: entity-grouped serialization (Bevy scene / Fleks snapshot / Unity), template-forgotten spawn + retained config-reference (korge-fleks snapshot+AssetStore, I4), reference-with-overrides over full-inline (inline reserved for export). The **engine** is the Kubernetes-controller / Terraform-provider **reconcile** family (§11b), with a coverage table proving it covers our cases; §11a lists mobile ECS precedent (Apple GameplayKit et al.); §11c records what's **Rejected** (external ECS engine as growth target; Rust-FFI ECS) so it isn't re-litigated. Cross-app: shared component registry/schema — why *this file exists* as the ecosystem schema.

**Open (deferred, with owners):**
- **Cloud/remote unit** — is what flows through the server the Profile, the Preset, the pair, or the deprecated `ConfigDocument`? Unresolved; **TASK-70** owns it. `config-ownership.md` still describes `ConfigDocument`; treat that as the cloud-layer question, not the local model.
- **Lenient reader** (TASK-131) — mandatory before admin push / preset sharing.
- **Preset → Profile update** (TASK-130) — depends on I4's retained pointer.
- **Inline-on-export** — if a Profile must reach a device without its Preset, materialise a self-contained blob at the export boundary only (do not change the storage model).

**How AI should use this file:**
- Routine ECS question → **read this TL;DR, stop here.**
- New `Component` / `Tag` → §4 / §5 checklists.
- Anything about self-containment / remote / "does Settings read the preset" → **§9 (do not re-derive — it's decided).**
- Class/port/gateway/DI question → §10.
- `ConfigDocument` legacy → §7.
- **Changing the model → §12 (sync rule).**

<!-- AI-TLDR:END -->

## 1. Scope — why "ECS", not "presets"

The launcher's configuration is the first ECS World in the ecosystem, but the pattern (entity = free bag of components + tags; systems reconcile; queries select; a shareable template spawns instances) is intended to be **shared across ecosystem apps**. Treat the type names and invariants here as the ecosystem contract. App-specific components/tags are additive per app; the *shape* (Entity/Component/System/Query/Blueprint/Preset/Profile) is common.

## 2. Three Orthogonal Dimensions

The most common confusion is mistaking **lifecycle position** for **semantic tag** for **structural place**. They are independent axes.

```
Preset {                            // shareable JSON template
    schemaVersion: 2,               // ← current shipped value; form + rules: wire-format.md
    wizardFlow:  [ { poolRef, paramsOverride } ],   // ← LIFECYCLE: shown during first-run
    settingsMap: [ { poolRef, categoryKey, settingsIcon?, sensitivity, paramsOverride? } ],  // ← LIFECYCLE + PRESENTATION (see I2)
    activeComponents: [ { poolRef, paramsOverride, parentRef } ]  // ← LIFECYCLE: applied on device
}
Pool { Blueprint { id, components:[…], tags:[…], wizardBehavior, critical } }   // SEMANTIC tags stamped at spawn
Profile { entities: [ Entity { id, components:[…, LifecycleState], tags:[…], parentId } ], basedOnPreset, presetVersion }
```

- Adding to `wizardFlow`/`settingsMap`/`activeComponents` → declaring **WHEN**.
- Adding tags to the bundle/entity → declaring **WHAT**.
- Setting `parentId` → declaring **WHERE**.
- **Never conflate.** A `wizardOnly: true` tag or a `Presentation` bucket in the Preset is the conflation smell.

Two rules easy to get wrong:
1. **Render gating** — tags say *what*; `LifecycleState` says whether *this device* applied it. `tilesOf()` excludes `Failed`/`Skipped` (an elderly user must never face a dead button). `Pending`/`Applied`/`Unverifiable`/absent all render.
2. **`Unverifiable` is honest, not a bug** — Android exposes no read-back for some settings; the engine records `Unverifiable` instead of a fictional `Applied`. `BootCheck` skips those; they re-verify only on an explicit Settings action.

## 3. Data Flow

```
Pool (assets/pool.json) + Preset → ProfileFactory.create() → Profile (each entity spawned LifecycleState.Pending)
    ↓
ReconcileEngine.run(mode) → per entity, per data component → Provider.apply() → Outcome
    ↓
Profile updated (LifecycleState swapped per entity), persisted via ProfileStore
    ↓
UI reads Profile via Query selectors (HomeScreen ← ProfileBackedFlowRepository)   // Profile-only, I1
```

Modes: **Wizard** (reads `Preset.wizardFlow`, uses `InteractionSink`), **BootCheck** (only critical entities, skips `Unverifiable`, Profile-only), **Single** (one entity — the Settings edit path), **RemotePush** (applies admin `ChangeItems`).

## 4. Adding a New Component (checklist)

1. Define `Component.MyNewType` in `Component.kt` — **no `tags` member**. Keep nested so `@SerialName` discriminators stay stable.
2. Add a `Blueprint` to `pool.json` with `components:[{type:"MyNewType", …}]` + the `tags` the spawned entity should carry.
3. Implement `MyNewTypeProvider : Provider<Component.MyNewType>` (unless structural/state — those have no Provider).
4. Register in DI via `@IntoMap` + component-key binding.
5. Unit-test `check` and `apply` outcomes.
6. Update `ComponentProviderCoverageTest` (add to sample list, or exempt set if structural/state).
7. **No changes in `ReconcileEngine`/`ProviderRegistry`/`ProfileFactory`** — if you must edit engine code, the abstraction is broken (rule 4).

## 5. Adding a New Tag

1. Add an enum value to `Enums.kt` `Tag` — **additive only** (rule 5); never rename/remove.
2. If commonly queried — add a selector to `ProfileQuery.kt`.
3. Update `pool.json` bundles that should stamp it.
4. No profile migration (tags are an additive change — see [`wire-format.md`](wire-format.md) §5).
5. Document it in §6.

## 6. Tag Glossary

`Blueprint.tags` in Pool declarations is authoritative — this guides authoring.

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

- **ConfigDocument was an MVP hack** for cloud-delivered configuration (spec 008/009 era).
- **Replaced** by Profile-as-source-of-truth. See `docs/dev/server-roadmap.md` § SRV-CONFIG-DEPRECATION.
- **Do not add new `ConfigDocument` usage.** Existing usage → `// TODO(config-deprecation)`.
- The cloud/remote *transport unit* (Profile? Preset? pair? ConfigDocument?) is an **open question owned by TASK-70** (§9, Open).

## 8. Object Structure (canonical example)

```
ws-main        Workspace                          parentId = null
├── flow-calls Flow(order 0)                      parentId = ws-main
│   ├── tile   AppTile(com.whatsapp)              parentId = flow-calls
│   └── sos    Sos                                parentId = flow-calls
└── toolbar    Toolbar                            parentId = ws-main
    └── btn-1  ToolbarButton(target=flow-calls)   parentId = toolbar
```
Stored flat; `ProfileBackedFlowRepository` projects it onto the `FlowDescriptor(id, name, slots)` UI contract (one per Flow) — no port and no UI changed. A profile with no Flow entities yields one synthetic descriptor holding every tile.

## 9. Runtime Invariants (decided — do NOT re-derive)

These are the invariants that kept getting re-derived because they lived scattered across TASK-120 discussion, config-ownership.md and code, never consolidated. They are **decided** (owner + TASK-127/136, industry-validated §11). Changing any of them is an architectural decision requiring a `decision-supersedes` task, not an ad-hoc edit.

- **I1 — Profile self-contained for behaviour + home.** `ReconcileEngine`, `BootCheck`, and `ProfileBackedFlowRepository` read only the Profile. The home screen and all applied behaviour need no Preset at runtime.
- **I2 — Presentation lives on the Preset.** `settingsMap` (`categoryKey`/`settingsIcon`/`sensitivity`) is a Preset field. Settings **re-reads the live Preset** at render time (`PendingChecklistViewModel` loads `presetSource.loadPreset(profile.basedOnPreset)` and reads `settingsMap.categoryKey`). Presentation is author metadata, identical for every device on that preset → kept once in the preset (DRY), **not** duplicated into every profile. Industry: Flecs "inherited components stored once in the template"; Unity `EntityPrefabReference` exists to avoid per-instance duplication.
- **I3 — Transfer/share/remote unit = Profile + Preset.** Not the bare Profile. Bundled presets are on both ends (same app); custom presets travel as shareable artifacts (rule 9). Cost (accepted, documented industry-wide): **load-order** — a Profile cannot render Settings / be fully managed without its Preset resolvable.
- **I4 — korge-fleks `snapshot + AssetStore`, reference-by-id** (source-verified, §11). Fleks core: `Entity(id, version)` pure handle, `Snapshot(components, tags)`, no prefab/config concept — Blueprint/Bundle discarded after spawn. korge-fleks adds the by-id config separation: a component holds a `String` id (`Sprite.name`), the asset/config lives in a separate `AssetStore` + `EntityFactory.entityConfigs: Map<String, EntityBlueprint>` registry, and `saveGameState` persists **only** `world.snapshot()` (components+tags with String ids) — config resolved by id at load, never inlined. Mapping: Profile=snapshot, Pool+Preset=config registry, `basedOnPreset`/`entity.id==poolRef`=reference. So the Profile keeps `basedOnPreset` + `presetVersion` for updates (**TASK-130**). We mirror **korge-fleks** (its config layer), not Fleks core alone. The "preset forgotten after activation" (TASK-120 exploratory) was **superseded by TASK-127** ("profile references the preset; both stored and shipped together").
- **I5 — What ProfileFactory copies.** From the Pool blueprint into each entity: `components` (+`paramsOverride` merged), `tags`, `parentId` (from `parentRef`), `wizardBehavior`, `critical`; injects `LifecycleState.Pending`. At the Profile: `basedOnPreset`, `presetVersion`, `layoutKey`, `entities`, `unknownRefs`. It **does not** copy `settingsMap`/`wizardFlow` presentation; provenance (which lifecycle list an entry came from) is **not** preserved on the entity.
- **I6 — Consumer read model.** Runtime consumers read the Profile. **Only Settings** additionally reads `Preset.settingsMap` (grouping labels). If you find any other consumer reaching into the Preset at render time, that's a smell — surface it.

**Exit ramp (I2/I3):** if a Profile must reach a device without its Preset (offline export, share-to-stranger, archive surviving preset deletion) → **inline-on-export**: materialise a self-contained blob at the export boundary only; do not change the storage model. This is the documented industry pattern (Unity delta-storage internally vs fully-baked entity scene for shipping).

## 10. Class / App Architecture (decisions, to avoid re-thinking)

**Layering** (arrows point down only — a rule-1 fitness check):
```
UI (Compose screens, ViewModels)
   ↓ depends only on PORTS
Domain (core/preset/, pure Kotlin, zero Android)
   ↑ implemented by
Adapters (app/…/provider, facades, DataStore, DI)
```

**Domain (`core/preset/`, commonMain, no Android imports):**
- `model/` — `Entity`, `Component` (sealed interface), `LifecycleState`, `Preset`, `Pool`, `Blueprint`, `Profile`, `Tag`, `Enums`, `Outcome`.
- `ecs/` — `EntityDsl`, `Family`, `World` (in-house Fleks-shaped core; `// TODO(ecs-fleks-migration)`).
- `engine/` — `ReconcileEngine` (the System), `ProfileFactory` (the spawn).
- `query/` — `ProfileQuery` (extension selectors).
- `port/` — `Provider`, `ProviderRegistry`, `PoolSource`, `PresetSource`, `ProfileStore`, `InteractionSink`, `ConditionEvaluator`, `LocalizedResources`. **Ports are the only surface UI/adapters may depend on.**

**Adapter (`app/…`):** concrete `Provider`s; facades (`PackageManagerFacade`, `HomeScreenFacade`, `StoreIntentFacade`, `UiPrefsFacade`, `RoleManager` facade) — one ACL per external surface (rule 2); `DataStoreProfileStore`, `BundledPoolSource`, `BundledPresetSource`, `AndroidLocalizedResources`; DI via Hilt `@IntoMap` + `@ComponentKey`.

**UI:** Compose screens + ViewModels + `ProfileBackedFlowRepository` (projects `Profile` → `FlowDescriptor`).

**Feature-seam pattern (the "don't call the engine directly" rule, TASK-69):** a UI feature exposes a **purpose-shaped gateway port** in the domain, and the engine lives behind it as an adapter. Example — Settings:
```
SettingsScreen (Compose)  →  SettingsViewModel  →  SettingsGateway (port: observe(): Flow<SettingsView>, apply(poolRef, params))
                                                        ↑ adapter
                                     EngineSettingsGateway  →  ReconcileEngine (RunMode.Single) + ProfileStore + PresetSource
                                     SettingsPresentationBuilder  →  Profile (+ Preset.settingsMap per I2) → SettingsView
```
The ViewModel depends only on `SettingsGateway`, never on `ReconcileEngine` — so the engine is swappable without touching UI (rule 4 purpose-shaped seam). `SettingsView` is a serialisable descriptor (rows + app-operations); rendering it from JSON is deferred (TASK-133), so today it renders via ordinary Compose reading a ready `SettingsView`.

**Fitness functions (rule 7):**
- Import-guard: `engine/**` must not import concrete `Component.*` subtypes.
- `when`-guard: no `when(component)` on concrete subtypes in `engine/**` or in UI.
- Coverage: every non-structural, non-state `Component` subtype has a registered `Provider`.
- At-most-one-component-per-type per entity (makes `get<T>()` unambiguous).
- Tag-consistency; `paramsOverride` schema; ecs-core LOC budget (≤ 400).
- Roundtrip + backward-compat for every wire shape (rule 5).

## 11. Industry Grounding (why the above is mainstream, not invented)

- **Entity-grouped serialization** is the norm: Bevy `DynamicScene`, Fleks `Snapshot(components, tags)`, Unity entity scenes, Flecs JSON — each serialised entity carries its components + data-less tags. Our Profile mirrors Fleks `Snapshot`.
- **Template→instance** — industry shows two prefab models: **A "template forgotten"** (Bevy bundle "zero runtime significance"; Fleks core has no prefab at all) and **B "instance keeps a live prefab link with inheritance/override"** (Unity prefab; Flecs `(IsA, prefab)`). **We are neither prefab model — we are the korge-fleks `snapshot + AssetStore` pattern**: Fleks-core entities (Model-A style, no template retained) whose *config* is referenced by id from a separate store (Pool+Preset), resolved at runtime, not inlined (I4). korge-fleks is the Fleks-based config framework and our closest stack analogue.
- **Reference-with-overrides over full-inline** is the industry default for storage (Unity stores only the delta; Flecs keeps inherited/presentation data "stored once"; korge-fleks separates runtime snapshot from AssetStore config). Full inline is reserved for portability → our **inline-on-export** exit ramp (§9).
- **Profile + Preset pairing is source-verified against korge-fleks** (Kotlin/Fleks, our closest analogue), not just summarised: components hold a `String` id (`Sprite.name`) into a separate `AssetStore`; the by-name blueprint registry is `EntityFactory.entityConfigs: MutableMap<String, EntityBlueprint>`; `SnapshotSerializerSystem.saveGameState` persists **only** `world.snapshot()` (`Map<Entity, Snapshot>`, components+tags), never the `AssetStore` — config resolved by id at load. Fleks core itself is only `Entity(id, version)` + `Snapshot(components, tags)` with no config concept; the separation is korge-fleks's. Unity independently confirms the intent (`EntityPrefabReference` exists to avoid duplicating a template across instances).
- **Cross-app schema sharing** = a shared component **registry** (Bevy `AppTypeRegistry`) or namespaced **modules** (Flecs) that every app imports; serialised state is meaningful only against it. **That is the role of this file** for our ecosystem.
- Universal invariants we also hold: at-most-one-component-per-type; add/remove = structural change; `get`/`has` by type; tags = zero-data markers; families = the selection layer.

### 11a. Mobile production precedent for the ECS data model

- **Apple GameplayKit** — *first-party* ECS in the iOS/macOS/tvOS SDK since iOS 9 (2015): `GKEntity` ("an entity gets its functionality by being a container for components"), `GKComponent`, `GKComponentSystem`. The platform vendor endorsing the entity-component data model. Strongest single beacon.
- **Unity DOTS ECS** — shipped mobile title *Detonation Racing* (Apple Arcade).
- **Kotlin/Fleks via KorGE** — our exact stack region; confirmed to run natively on Android + iOS.
- **Non-game ECS** — A-Frame (Mozilla, entity-component over HTML/DOM), Meta Horizon Spatial SDK, robotics (Gazebo), AWS IoT Device Shadow (desired/reported/delta ≈ config-vs-runtime + reconcile). *Honest caveat:* pure settings/config ECS is not an established named practice — we borrow the ECS *data-model* pattern, and take the *engine* pattern from reconcile systems (below).

### 11b. Adopted-approach beacon — engine = declarative desired-state reconciliation

Our execution engine is the **Kubernetes controller / Terraform provider** family, not a game loop. Direct doc-backed precedent: K8s controllers are "control loops that … move the current cluster state closer to the desired state" (`spec` desired vs `status` actual); Terraform providers do Read → plan/diff → apply per resource; a published "ECS and K8s" analysis explicitly frames Kubernetes as an ECS whose reconciliation replaces the game loop (and notes Google Prodspec/Annealing is "partially inspired by ECS"). Coverage of our required cases:

| Our requirement | Kubernetes | Terraform | Ours |
|---|---|---|---|
| entity = component bag + tags | object `metadata`+`spec`/`status`; labels=tags | resource + attributes | `Entity(components, tags)` |
| query by tag/type | label selectors | resource addressing | `byTag` / `get<T>()` / `family{}` |
| serialization (versioned) | YAML/JSON + `apiVersion` | state file + schema version | kotlinx entity-grouped, `schemaVersion` |
| config-vs-runtime split | `spec` vs `status` | config vs state file | Preset (config) vs Profile (runtime), I1-I3 |
| check→apply per component | controller per resource type | provider CRUD (Read→Diff→Apply) | `Provider.check/apply` per `Component` |
| engine ≠ game loop | non-terminating reconcile loop | plan/apply run | `ReconcileEngine.run(mode)` |

The *fusion name* is an intersection we cite, not a coined pattern. Established terms to use: engine = **"declarative desired-state reconciliation / control-loop (Kubernetes controller pattern)"**; data = **"ECS-shaped entity-component model"**. Avoid calling the whole thing "ECS + reconcile" as if canonical (and note the AWS-ECS name collision).

### 11c. Rejected (do not re-litigate)

- **Adopting an external ECS engine (Fleks / Bevy / Unity DOTS / EnTT / Ashley / geary …) as a "growth target"** — rejected (research-verified). (a) No ECS engine provides our config layer — it is always an app/framework add-on (korge-fleks bolts it onto Fleks; Bevy scenes are a separate crate with "conflicting representations"; Unity uses Editor baking; EnTT explicitly refuses to own it; Flecs prefabs are runtime inheritance, not a config store). (b) All are game runtimes with a per-frame scheduler irrelevant at ~20–40 entities. (c) Fleks core covers only the runtime half we already have and would leak vendor types (`World`, `@Component`) into the domain — violating rule 1/2. So an "engine swap" would be a partial rewrite, not a drop-in. The **hand-rolled core is permanent-correct**; growth is additive (more providers / component types / tags), the IaC way.
- **Bringing a Rust ECS (Bevy/Flecs) across our Rust FFI bridge** — rejected. FFI is right for a **narrow compute leaf** (crypto: bytes→bytes, no mature Kotlin lib). ECS config is the opposite — a **central domain model** touched by UI/reconcile/serialization; marshalling every entity/component/query across the Kotlin↔Rust boundary defeats ECS cache locality, still leaves the config layer unbuilt, imports an unused scheduler, and leaks Rust vendor types into the domain (rule 1/2). Wrong tool for a central model.
- **External game ECS as a *runtime* target** stays a *theoretical* option only if we ever gain a genuine game loop (thousands of entities × 60fps) — not our trajectory; the config layer would remain ours regardless.

Sources (for audit):
- **Source-verified (read line-by-line)**: Fleks core [`entity.kt`](https://github.com/Quillraven/Fleks/blob/master/src/commonMain/kotlin/com/github/quillraven/fleks/entity.kt), [`world.kt`](https://github.com/Quillraven/Fleks/blob/master/src/commonMain/kotlin/com/github/quillraven/fleks/world.kt) (`Entity(id, version)`, `Snapshot(components, tags)`, `world.entity{}`, `family{all/any/none}`, `snapshot()/loadSnapshot()`); korge-fleks [`Sprite.kt`](https://github.com/korlibs/korge-fleks/blob/main/src/commonMain/kotlin/korlibs/korge/fleks/components/Sprite.kt), [`AssetStore.kt`](https://github.com/korlibs/korge-fleks/blob/main/src/commonMain/kotlin/korlibs/korge/fleks/assets/AssetStore.kt), [`EntityFactory.kt`](https://github.com/korlibs/korge-fleks/blob/main/src/commonMain/kotlin/korlibs/korge/fleks/entity/EntityFactory.kt), [`SnapshotSerializerSystem.kt`](https://github.com/korlibs/korge-fleks/blob/main/src/commonMain/kotlin/korlibs/korge/fleks/systems/SnapshotSerializerSystem.kt).
- **Doc/summary level**: Sander Mertens ECS FAQ; Flecs Prefabs/Relationships/Modules manuals; Bevy cheatbook (Bundles), `docs.rs` Bundle/DynamicScene/TypeRegistry; Unity DOTS baking-prefabs, Prefab Variants / instance overrides, archetypes; EnTT crash course.
- **Beacon / precedent**: Apple GameplayKit guide + `GKEntity`/`GKComponent`/`GKComponentSystem` docs; Unity DOTS + *Detonation Racing* (Electric Square) case; A-Frame ECS docs; Meta Horizon Spatial SDK ECS; AWS IoT Device Shadow docs; Kubernetes [controllers/control-loop](https://kubernetes.io/docs/concepts/architecture/controller/); Terraform [state purpose](https://developer.hashicorp.com/terraform/language/state/purpose) + drift; "ECS and K8s" (fancl20) — direct ECS↔K8s equivalence; "Infrastructure as Data" (Crossplane/ACK). *Note:* AWS "ECS" (Elastic Container Service) is a name collision, unrelated to Entity-Component-System.

Naming note: Fleks core uses `Entity.configure { }` (`EntityUpdateContext`); `configureEntity(world, name, entity)` is korge-fleks's `EntityFactory`. Align our in-house core to whichever layer we mirror per call site.

## 12. How to change this document (sync rule — HARD)

- **Single source of truth.** Any change to the ECS model, a runtime invariant (§9), or the class/app architecture (§10) MUST update this file **in the same commit** as the code/spec change. Refuse to ship a model change that doesn't touch `ecs.md`.
- **Other docs point here, they do not re-describe.** `preset-model.md` is a redirect to this file; glossary / ADRs / specs link to `ecs.md#…` rather than restating the model.
- **Invariants in §9 are decided.** Revising one requires a `decision-supersedes` backlog task (rule 11), not an inline edit.
- **The `ecs` skill is a thin router**, not a copy of this file. It triggers on ECS mentions and sends the agent here. It carries only the trigger, the pointer/section-map, the hard guardrail invariants, and this sync rule — never a second copy of the model (that would drift).

## 13. Related Files

Code: `core/src/commonMain/kotlin/com/launcher/preset/` — `model/` (`Component.kt`, `LifecycleState.kt`, `Pool.kt`, `Preset.kt`, `Profile.kt`, `Enums.kt`), `ecs/` (`EntityDsl`, `Family`, `World`), `engine/` (`ReconcileEngine.kt`, `ProfileFactory.kt`), `query/ProfileQuery.kt`, `port/` (`Provider.kt`, `ProviderRegistry.kt`, …); `core/…/adapters/flow/ProfileBackedFlowRepository.kt`.

Docs / ADRs: [ADR-013 — Canonical ECS](../adr/ADR-013-canonical-ecs.md) (current) · [ADR-012](../adr/ADR-012-tagged-component-model-vs-canonical-ecs.md) (superseded).

Backlog / Specs: TASK-136 (`specs/task-136-ecs-canonical-foundational-model/`); TASK-120 / TASK-127 (superseded by TASK-136); TASK-69 (Settings gateway seam); TASK-130 (preset→profile update, depends on I4); TASK-131 (lenient reader); TASK-70 (cloud/remote unit — open, §9).
