# Preset / Profile / Component Architecture

<!-- AI-TLDR:BEGIN -->

## AI TL;DR

**What we're modelling**: launcher configuration as an ECS-inspired system (adapted, not canonical — see [ADR-012](../adr/ADR-012-tagged-component-model-vs-canonical-ecs.md)). Foundation laid in TASK-120 (Component / Provider / Profile), completed in TASK-127 (Tag / Query / hierarchy / honest status / ECS naming). We do NOT use a runtime ECS framework (Fleks/Ashley/Artemis) — we borrow the *modelling shape* only.

**The one mental model to keep: ECS ≈ a database table.**

| Database | ECS | Here |
|---|---|---|
| table | World | `Profile` |
| row | Entity | `Entity` (one item in `Profile.components`) |
| columns | Components | `Component.AppTile(packageName, labelKey)` |
| `WHERE tag='Tile'` | Query | `byTag(Tag.Tile)` |
| foreign key `parent_id` | `Parent`/`Children` | `Entity.parentId` |
| stored procedure | System | `ReconcileEngine` + `Provider` |

Storage is **flat**. The screen tree is *computed* by queries, never nested in the wire format — same pattern as Bevy / Unity DOTS `Parent` and Android Launcher3's `favorites.container`.

**Core types** (all in `core/src/commonMain/kotlin/com/launcher/preset/`):

- **Pool** — catalog of `Blueprint`s (bundled `pool.json`, source of truth for available components).
- **Blueprint** *(was `ComponentDeclaration`, renamed TASK-127)* — a spawn template: id + embedded `Component` + wizardBehavior + critical + requires.
- **Preset** — shareable JSON template referencing Pool entries (`schemaVersion` + wizardFlow + settingsMap + activeComponents). Shareable per CLAUDE.md rule 9. `ActiveComponentEntry.parentRef` declares a tree edge.
- **Profile** — device-local instance built from `Preset + Pool` by `ProfileFactory`. Source of truth for runtime UI/behaviour state. Persisted, `schemaVersion = 2`.
- **Entity** *(was `ProfileComponent`, renamed TASK-127)* — a row: `id` + `component` + `status` + `parentId` + wizardBehavior + critical.
- **Component** — sealed hierarchy, **11 subtypes**: behavioural (`AppTile`, `Sos`, `FontSize`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`) + structural (`Workspace`, `Flow`, `ToolbarButton`). Every subtype carries a non-empty `tags: Set<Tag>` default (fitness-enforced).
- **Provider** — per-platform effector (`Provider<T : Component>` port). Fallback chain: vendor → platform → NoOp. **Structural subtypes have no Provider by design** — they are screen skeleton, nothing to apply to the OS.
- **ReconcileEngine** — dispatch loop over `Profile.components`. Modes: `Wizard`, `BootCheck`, `Single`, `RemotePush`.
- **Tag** — **13 values**: 8 semantic (Presentation / Appearance / System / Safety / Capabilities / Communication / Accessibility / Emergency) + 5 structural (Tile / Toolbar / Workspace / Flow / ToolbarButton).
- **Query** (`preset/query/ProfileQuery.kt`) — tag selectors (`byTag`, `byAllTags`, `byAnyTag`, `byNotTag`) + hierarchy selectors (`children`, `roots`, `workspace`, `flows`, `tilesOf`, `toolbar`, `toolbarButtons`). Queries are **never persisted** — only tags are, which sidesteps query-language versioning entirely.

**Three orthogonal axes** (do NOT conflate them):

| Axis | Where declared | Answers | Example |
|------|----------------|---------|---------|
| **Lifecycle** | `Preset.wizardFlow` / `settingsMap` / `activeComponents` | WHEN the component is used | AppTile in `wizardFlow` = the user is asked about it during first-run setup |
| **Semantic** | `Component.tags: Set<Tag>` | WHAT the component is about | AppTile with `tags = [Presentation, Tile, Communication]` = a visible messaging tile |
| **Structural** | `Entity.parentId` + Workspace/Flow/ToolbarButton | WHERE on the screen it lives | AppTile with `parentId = "flow-calls"` = a tile on the Calls tab |

The three are independent: the same component can sit in any lifecycle bucket, carry any tag combination, and hang anywhere in the tree.

**Two rules that are easy to get wrong**:

1. **Render gating** — tags say *what* a component is; `status` says whether this device managed to apply it. `homeScreenTiles()` / `tilesOf()` exclude `Failed` and `Skipped`: an elderly user must never face a button that does nothing. `Pending` and `Unverifiable` do render.
2. **`Unverifiable` is honest, not a bug** — Android exposes no read-back for some settings (hiding the status bar is a chain of intents with no query API). The provider returns `Outcome.NeedsUserConfirmation`, the wizard asks the human, and the engine records `Unverifiable` instead of a fictional `Applied`. `BootCheck` skips those entities (re-probing would nag forever); they are re-verified only on an explicit Settings action.

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

Stored as a flat list; `ProfileBackedFlowRepository` projects it onto the existing `FlowDescriptor(id, name, slots)` UI contract — one descriptor per Flow — so no port and no UI changed. A profile with **no** Flow entities (the simple-launcher case) yields one synthetic descriptor holding every tile: the one-level screen is the same code path, not a special case.

**Data flow** (happy path):
```
Pool (assets/pool.json) + Preset (bundled/file/network) → ProfileFactory.build() → Profile
    ↓
ReconcileEngine.run(mode) → dispatches each Entity → Provider.apply() → Outcome
    ↓
Profile updated (status per component), persisted via ProfileStore
    ↓
UI reads Profile via Query selectors (e.g. HomeScreen ← ProfileBackedFlowRepository)
```

**Key decisions** (immutable per rule 11):

- **Sealed Component hierarchy** — compile-time exhaustiveness on `when(component)` (TASK-120).
- **Multiple tags per Component** (`Set<Tag>`, not single enum) — one component may span domains (TASK-127).
- **Hierarchy by reference, storage flat** (`Entity.parentId`) — the tree is computed, never nested (TASK-127; research R-7).
- **Queries are not persisted** — only tags are, so there is no query language to version (TASK-127).
- **Profile is the source of truth**; legacy `ConfigDocument` deprecated (see § Deprecated: ConfigDocument path and `docs/dev/server-roadmap.md` § SRV-CONFIG-DEPRECATION).
- **Provider fallback chain** vendor → platform → NoOp (TASK-120) — every behavioural component always resolves to something callable.
- **`schemaVersion` stays 2** — everything TASK-127 added (tags, parentId, 3 subtypes, Unverifiable) is additive, so no bump and no migration writer while the MVP is unreleased.

**Rejected**:

- ECS runtime frameworks (Fleks/Ashley/Artemis) — overkill for ~dozens of Components; adds a vendor dep with no runtime benefit.
- **Nested containers** for the screen tree (`Flow` holding its tiles) — reads nicer as JSON but breaks cross-cutting queries and tile moves, and fights the table model (research R-7).
- `ConfigDocument` as long-term architecture — MVP hack, being removed.

**Open** (deferred, with owners):

- **Lenient reader** — an older reader currently fails loud on an unknown Tag / Component type / status. Safe while a Profile never leaves the device that wrote it; **mandatory before admin push or preset sharing** — TASK-131.
- **Preset → Profile update** (preset v2 ships, installed profiles must catch up) — TASK-130.
- **Add-flow UX / empty slots** — TASK-134. Industry check: do NOT model an empty slot as an entity; absence is absence (Launcher3, SpringBoard).
- **Composition** (several Components on one Entity) — the latent one-way door in ADR-012; trigger and exit ramp documented there.

**How AI should use this file**:

- Routine questions about the model → **read TL;DR only**, stop here.
- Adding a new `Component` subtype or `Tag` → jump to §4 / §5 checklists.
- Question about `ConfigDocument` legacy → §7.
- Confused about "why does this component have both a lifecycle position and a tag" → the three-axes table above.

<!-- AI-TLDR:END -->

## 2. Two Orthogonal Dimensions

The single most common confusion when reading this codebase: mistaking **lifecycle position** for **semantic tag**, or trying to model one via the other. They are independent axes.

### Object structure

```
Preset {                            // shareable JSON template
    schemaVersion: 3,
    wizardFlow: [                   // ← LIFECYCLE dimension: shown during first-run
        { poolRef: "app-tile-whatsapp", paramsOverride: {...} }
    ],
    settingsMap: [                  // ← LIFECYCLE dimension: shown in Settings screen
        { poolRef: "font-size", paramsOverride: {...} }
    ],
    activeComponents: [             // ← LIFECYCLE dimension: currently applied on device
        { poolRef: "sos-tile", paramsOverride: {...} }
    ]
}

Pool {                              // catalog of Blueprints (assets/pool.json)
    Blueprint {
        id: "app-tile-whatsapp",
        type: "AppTile",
        packageName: "com.whatsapp",
        tags: ["Presentation", "Communication"]      // ← SEMANTIC dimension
    },
    Blueprint {
        id: "sos-tile",
        type: "Sos",
        phoneNumber: "+7...",
        tags: ["Presentation", "Safety", "Emergency"] // ← SEMANTIC dimension, multiple
    }
}

Profile {                           // device-local instance
    components: [
        Entity {
            id: "app-tile-whatsapp",
            component: Component.AppTile(
                packageName = "com.whatsapp",
                tags = setOf(Tag.Presentation, Tag.Communication)  // ← inherited from Pool
            ),
            status: Applied
        }
    ]
}
```

### What each dimension answers

- **Lifecycle** answers *WHEN is this component used*: during wizard? during Settings edit? applied at runtime?
- **Semantics** answers *WHAT is this component about*: presentation? safety? accessibility?

These are truly independent. An `AppTile` can be in `wizardFlow` AND have `tags = [Presentation]`. Or in `settingsMap` AND have `tags = [Appearance, Accessibility]`. Or in `activeComponents` AND carry both.

### Rule of thumb for AI

- Adding a component to `wizardFlow` / `settingsMap` / `activeComponents` — you're declaring **WHEN**.
- Adding tags to `Component` — you're declaring **WHAT**.
- **Never conflate them**; never remove one to model the other. If you feel tempted to introduce a `wizardOnly: true` tag or a `Presentation` bucket in Preset, stop — that's the conflation.

## 3. Data Flow

End-to-end sequence, startup to render:

```
1. App startup
   ↓
2. Pool loaded from assets/pool.json (BundledPoolSource)
   ↓
3. Preset loaded (bundled seed OR file OR share intent OR network — via PresetSource port)
   ↓
4. ProfileFactory.build(preset, pool) → Profile
   (expands each poolRef into full Entity, applies paramsOverride,
    initial status = Pending)
   ↓
5. ReconcileEngine.run(mode) iterates Entities, dispatches via ProviderRegistry
   - Wizard mode: uses InteractionSink for Interactive steps
   - BootCheck mode: only critical components (LauncherRole, permissions)
   - Single mode: one component (Settings edit path)
   - RemotePush mode: applies ChangeItems from admin push
   ↓
6. Provider.apply(component, profile) → Outcome (Applied / Pending / Failed / NotApplicable)
   ↓
7. Profile updated with new status; persisted via ProfileStore
   ↓
8. UI reads Profile via Query (ProfileBackedFlowRepository)
   ↓
9. HomeScreen renders using selectors:
   - Profile.homeScreenTiles()
   - Profile.byTag(Tag.Presentation)
   - Profile.byTag(Tag.Emergency)
```

## 4. Adding a New Component (checklist for AI)

1. Define a new `Component.MyNewType` subtype in `core/preset/model/Component.kt` — **MUST** specify `tags` param (may be empty `emptySet()`, but the field must be present for exhaustiveness).
2. Add `Blueprint` entry to `pool.json` (`assets/pool.json`) — MAY override tags per-declaration.
3. Implement `MyNewTypeProvider : Provider<Component.MyNewType>` in `app/androidMain/provider/` (or the appropriate platform module).
4. Register the Provider in DI via `@IntoMap` + `@ComponentKey(MyNewType::class, Android, null)`.
5. Add a unit test covering `check` and `apply` outcomes (fake InteractionSink where needed).
6. If component affects HomeScreen — verify the appropriate selector exists on `Profile` (add if a new tag is introduced).
7. **No changes needed** in `ReconcileEngine`, `ProviderRegistry`, or `ProfileFactory` (this is the fitness function per rule 4 MVA — if you find yourself editing engine code, the abstraction is broken).

## 5. Adding a New Tag

1. Add enum value to `core/preset/model/Enums.kt` `Tag` — **additive only** per rule 5 (wire-format versioning). Never rename or remove.
2. If commonly queried — add convenience selector to `Profile.kt` (e.g. `fun Profile.byMyNewTag(): List<Entity>`).
3. Update `pool.json` entries that should carry the new tag.
4. Migration: existing Profile v3 files don't need migration (tags field is additive within v3).
5. Document the new tag semantic in the glossary below (§6).

## 6. Tag Glossary

Current tags and their meaning. `Component.tags` in Pool declarations is authoritative — this glossary is guidance for authoring new declarations.

| Tag | Meaning | Example components |
|-----|---------|--------------------|
| `Presentation` | Visible on the home screen | `AppTile`, `Sos`, `Toolbar` |
| `Appearance` | Visual theming | `FontSize`, `Theme` |
| `System` | OS-level settings | `LauncherRole`, `StatusBarPolicy` |
| `Safety` | Emergency / protective | `Sos`, emergency contacts |
| `Capabilities` | Feature-gates for cloud/pairing | `SignInGoogle`, `PairingProvided` |
| `Communication` | Messaging / calling apps and contacts | `AppTile` (WhatsApp, Phone), contact tiles |
| `Accessibility` | Settings addressing disabilities | `FontSize`, `HighContrast`, `Haptic` |
| `Emergency` | Highest-severity actionable | `Sos` in fast-access mode |

## 7. Deprecated: ConfigDocument path

- **ConfigDocument was an MVP hack** for cloud-delivered configuration (spec 008 / 009 era).
- **Being replaced** by Profile-as-source-of-truth. See `docs/dev/server-roadmap.md` § SRV-CONFIG-DEPRECATION for the migration plan.
- **Current status (2026-07-15)**: HomeScreen migrated to `ProfileBackedFlowRepository` (TASK-127). `ConfigDocument` remains in the codebase for the admin-push path (spec 009 F-5c FCM). Removal is deferred to a separate task.
- **Do not add new `ConfigDocument` usage.** If you must touch existing usage, add a `// TODO(config-deprecation)` comment pointing to SRV-CONFIG-DEPRECATION.

## 8. Related Files

Code:

- `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Preset.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/engine/ReconcileEngine.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/port/Provider.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/port/ProviderRegistry.kt`
- `core/src/commonMain/kotlin/com/launcher/preset/port/InteractionSink.kt`
- `core/src/commonMain/kotlin/com/launcher/adapters/config/ProfileBackedFlowRepository.kt` (new in TASK-127)

Docs:

- `docs/dev/server-roadmap.md` § SRV-CONFIG-DEPRECATION
- `docs/architecture/pool-naming.md`

Backlog:

- TASK-120 — foundation (Component / Provider / Profile)
- TASK-127 — ECS Tags + Query extension

Specs:

- `specs/task-120-preset-composition-foundation/`
- `specs/task-127-ecs-foundation/`
