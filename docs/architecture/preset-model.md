# Preset / Profile / Component Architecture

<!-- AI-TLDR:BEGIN -->

## AI TL;DR

**What we're modelling**: launcher configuration as an ECS-inspired system (Entity-Component-System pattern, adapted). Foundation laid in TASK-120 (Component / Provider / Profile), extended in TASK-127 (Tag / Query for UI selectors). We do NOT use a runtime ECS framework (Fleks/Ashley/Artemis) — we borrow the *modelling shape* only.

**Core types** (all live in `core/src/commonMain/kotlin/com/launcher/preset/`):

- **Pool** — catalog of `ComponentDeclaration`s (bundled `pool.json`, source of truth for available components).
- **Preset** — shareable JSON template referencing Pool entries (`schemaVersion` + wizardFlow + settingsMap + activeComponents). Sharable per CLAUDE.md rule 9.
- **Profile** — device-local instance built from `Preset + Pool` by `ProfileFactory`. Source of truth for runtime UI/behavior state. Persisted.
- **Component** — sealed hierarchy (`AppTile`, `Sos`, `Toolbar`, `FontSize`, `LauncherRole`, …). Each carries `tags: Set<Tag>`.
- **Provider** — per-platform effector (`Provider<T : Component>` port). `apply(component, profile) → Outcome`. Fallback chain: vendor → platform → NoOp.
- **ReconcileEngine** — dispatch loop over `Profile.components` calling `ProviderRegistry.resolve(component).apply(...)`. Modes: `Wizard`, `BootCheck`, `Single`, `RemotePush`.
- **Tag** — semantic marker enum (Presentation / Appearance / System / Safety / Capabilities / Communication / Accessibility / Emergency).
- **Query** — predicate-based selector over `Profile.components` (e.g. `Profile.byTag(Tag.Presentation)`).

**Two orthogonal dimensions of categorization** (do NOT conflate them):

| Dimension | Where declared | Answers | Example |
|-----------|----------------|---------|---------|
| **Preset lifecycle** | `Preset.wizardFlow` / `settingsMap` / `activeComponents` | WHEN the component is used | AppTile listed in `wizardFlow` = user is asked about it during first-run setup |
| **Component semantics** | `Component.tags: Set<Tag>` | WHAT the component is about | AppTile with `tags = [Presentation, Communication]` = a visible messaging tile |

Same component can appear in any lifecycle bucket AND carry any tag combination — they're independent.

**Data flow** (happy path):
```
Pool (assets/pool.json) + Preset (bundled/file/network) → ProfileFactory.build() → Profile
    ↓
ReconcileEngine.run(mode) → dispatches each ProfileComponent → Provider.apply() → Outcome
    ↓
Profile updated (status per component), persisted via ProfileStore
    ↓
UI reads Profile via Query selectors (e.g. HomeScreen ← ProfileBackedFlowRepository)
```

**Key decisions** (immutable per rule 11):

- **Sealed Component hierarchy** — compile-time exhaustiveness on `when(component)` (TASK-120).
- **Multiple tags per Component** (`Set<Tag>`, not single enum) — one component may span domains (TASK-127).
- **Profile is the source of truth**; legacy `ConfigDocument` deprecated (see § Deprecated: ConfigDocument path and `docs/dev/server-roadmap.md` § SRV-CONFIG-DEPRECATION).
- **Provider fallback chain** vendor → platform → NoOp (TASK-120) — every component always resolves to something callable.

**Rejected**:

- ECS runtime frameworks (Fleks/Ashley/Artemis) — overkill for ~dozens of Components; adds vendor dep with no runtime benefit.
- `ConfigDocument` as long-term architecture — MVP hack, being removed.

**Open** (deferred):

- Toolbar buttons as separate Entities — deferred until independent per-button configuration is actually required.
- Admin-push Profile serialization — deferred, tracked in `docs/dev/server-roadmap.md`.

**How AI should use this file**:

- Routine questions about the model → **read TL;DR only**, stop here.
- Adding a new `Component` subtype or `Tag` → jump to §4 / §5 checklists.
- Question about `ConfigDocument` legacy → §7.
- Confused about "why does this component have both a lifecycle position and a tag" → §2 (Two Orthogonal Dimensions).

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

Pool {                              // catalog of ComponentDeclarations (assets/pool.json)
    ComponentDeclaration {
        id: "app-tile-whatsapp",
        type: "AppTile",
        packageName: "com.whatsapp",
        tags: ["Presentation", "Communication"]      // ← SEMANTIC dimension
    },
    ComponentDeclaration {
        id: "sos-tile",
        type: "Sos",
        phoneNumber: "+7...",
        tags: ["Presentation", "Safety", "Emergency"] // ← SEMANTIC dimension, multiple
    }
}

Profile {                           // device-local instance
    components: [
        ProfileComponent {
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
   (expands each poolRef into full ProfileComponent, applies paramsOverride,
    initial status = Pending)
   ↓
5. ReconcileEngine.run(mode) iterates ProfileComponents, dispatches via ProviderRegistry
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
2. Add `ComponentDeclaration` entry to `pool.json` (`assets/pool.json`) — MAY override tags per-declaration.
3. Implement `MyNewTypeProvider : Provider<Component.MyNewType>` in `app/androidMain/provider/` (or the appropriate platform module).
4. Register the Provider in DI via `@IntoMap` + `@ComponentKey(MyNewType::class, Android, null)`.
5. Add a unit test covering `check` and `apply` outcomes (fake InteractionSink where needed).
6. If component affects HomeScreen — verify the appropriate selector exists on `Profile` (add if a new tag is introduced).
7. **No changes needed** in `ReconcileEngine`, `ProviderRegistry`, or `ProfileFactory` (this is the fitness function per rule 4 MVA — if you find yourself editing engine code, the abstraction is broken).

## 5. Adding a New Tag

1. Add enum value to `core/preset/model/Enums.kt` `Tag` — **additive only** per rule 5 (wire-format versioning). Never rename or remove.
2. If commonly queried — add convenience selector to `Profile.kt` (e.g. `fun Profile.byMyNewTag(): List<ProfileComponent>`).
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
