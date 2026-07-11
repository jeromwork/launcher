## Context

TASK-120 delivered the `com.launcher.preset.*` foundation: `Component / Pool / Preset / Profile` domain model, `ReconcileEngine` check/apply loop, `Provider<T>` port interface, 4 MVP Component subtypes (`AppTile`, `FontSize`, `Sos`, `Toolbar`), bundled JSON seeds, and Koin `task120Module`. Smoke test on Xiaomi Redmi Note 11 confirmed DI resolution without errors.

Three parallel worlds currently live in the codebase:
1. **Legacy wizard** — `com.launcher.api.wizard.*` (26 files) + `ui.wizard.*` + `adapters.wizard.*` + `app/wizard/` — powers `FirstLaunchActivity` today, 411 imports across 87 files
2. **TASK-65 model** — `com.launcher.api.preset.*` + `api.profile.*` + `api.switchstrategy.*` — mid-flight, used by `PresetPickerScreen` + `PresetSelectionService`
3. **TASK-120 foundation** — `com.launcher.preset.*` — wired but unused by real UI

No production users exist yet. Wire-format migration writer is not needed — all legacy JSON is deleted, not migrated.

## Goals / Non-Goals

**Goals:**
- Single engine: `ReconcileEngine` is the only check/apply runtime; legacy `WizardEngineImpl` deleted
- All first-run / Settings / BootCheck flows run exclusively through `com.launcher.preset.*`
- Four new Component subtypes added: `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`
- Component dependency validation via `requires: [ComponentRef]` in pool.json
- TutorialHint as separate `hint-pool.json` + `hintFlow` field in Preset (not reconcile-able)
- Fitness function #11 enforced: zero `com.launcher.api.wizard` imports in production code
- AccessibilityService removed from manifest

**Non-Goals:**
- New user-visible features — UX identical pre/post migration
- MessengerTile (TASK-121), SignInGoogle component — separate tasks using stabilized Provider port
- iOS providers — KMP-ready ports, adapters post-MVP
- Cross-app shared Profile layer (TASK-127 Draft) — additive future change

## Decisions

### D1: No wire-format migration writer

**Choice**: Delete all legacy `wizard/` assets outright. No migration path.

**Rationale**: Zero production users. Migration writer adds complexity and a transitional code path with no beneficiaries. CLAUDE.md rule 4 (MVA) — don't add what isn't needed.

**Alternative considered**: Keep migration writer for safety. Rejected — it delays deletion and creates a dead code path.

### D2: LauncherRole — no parameters

**Choice**: `LauncherRole` is a parameter-less Component. `Provider.check()` internally detects if already set and skips. Preset without `LauncherRole` = don't request (e.g., clinic preset using MDM).

**Rationale**: `SkipIfSet` logic belongs inside the Provider, not the preset schema. `Reject` mode is expressed by omission. Keeps preset schema clean.

**Alternative considered**: `LauncherRole(mode: Prompt|SkipIfSet|Reject)`. Rejected — `Prompt` is the only meaningful value when present; the others collapse to omission.

### D3: Theme — hybrid ThemeRef expanding to flat fields

**Choice**: Wire format stores `PaletteSeedHex + TypographyScale + ShapeStyle + DarkMode` as flat fields. `ThemeRef(name)` is a sugar shorthand that expands to these four fields at write time against a bundled `theme-catalog.json`. Wire format never contains `ThemeRef`.

**Rationale**: Named themes are convenience; flat fields are the canonical representation. Avoids ambiguity when both ThemeRef and individual fields coexist. Future field additions (e.g., `CustomAccentHex`) are additive schemaVersion bumps per CLAUDE.md rule 5.

**Alternative considered**: Store `ThemeRef` in wire format and resolve at runtime. Rejected — theme rename = breaking change for persisted Presets.

### D4: Language — sentinel "system"

**Choice**: `Language(locale: String)` where `"system"` is an explicit sentinel meaning "follow OS locale". Default for new Presets.

**Rationale**: `null` would be ambiguous (unset vs. system-follow). Explicit sentinel carries meaning in wire format per CLAUDE.md rule 5.

### D5: TutorialHint — separate pool, not Component

**Choice**: `hint-pool.json` + `hintFlow: List<HintFlowEntry>` field in Preset alongside `wizardFlow` / `settingsMap` / `activeComponents`. TutorialHint is NOT a `Component` subtype.

**Rationale**: `ReconcileEngine` operates on reconcile-able components (check/apply semantics). TutorialHint has no `check()` / `apply()` — it cannot fail, cannot be pending, cannot be retried. Forcing it into the Component model would pollute `ReconcileEngine` with a special case.

**Alternative considered**: `Component.TutorialHint(targetId, textKey)` in main pool. Rejected — breaks FR-025 semantics and ReconcileEngine invariants.

### D6: Provider-inline permissions

**Choice**: No standalone `Permission` Component subtypes. Each Provider declares its own permission preconditions. `SosProvider.check()` requests `CALL_PHONE` internally. User sees permission dialog in the context of the Component that needs it.

**Rationale**: Decoupling permissions from their owning Component creates fragile sync: if `Sos` is removed from Preset, its orphaned `CallPhonePermission` Component must be removed too. Provider-inline makes this automatic.

**Alternative considered**: `Component.Permission(name)` as explicit steps. Rejected — creates implicit ordering dependency and orphan risk.

### D7: Component dependency validation via requires field

**Choice**: Each component descriptor in pool.json gains an optional `requires: [componentId]` field. A `PresetValidator` runs at deserialization time and throws if `wizardFlow` ordering violates declared requirements.

**Rationale**: Prevents preset authors (human or AI) from creating Presets where, e.g., `AppTile` steps appear before `LauncherRole` is set. Machine-readable constraint, not a convention. Fail-fast at load time, not at runtime mid-wizard.

### D8: StatusBarPolicy — Component, not bootstrap

**Choice**: `StatusBarPolicy` is a `Component` in Preset. `StatusBarPolicyProvider` applies via `WindowInsetsController.hide(statusBars())`. AccessibilityService deleted entirely.

**Rationale**: Preset should control UX chrome. Different presets (e.g., clinic) may want status bar visible. Provider-inline, no manifest permission needed.

**Alternative considered**: Bootstrap always-hide on launcher start. Rejected — inflexible across presets.

### D9: Migration order — one PR, phased commits

**Choice**: All migration in one PR on `task-126-wizard-runtime-migration` branch. Commits organized by subsystem: (1) new Component subtypes + pool schema, (2) FirstLaunch + ReconcileEngine wiring, (3) Settings migration, (4) BootCheck migration, (5) E2E test migration, (6) legacy deletion + fitness function enforcement.

**Rationale**: One PR keeps review coherent. Phased commits allow subsystem-level smoke testing on Xiaomi after each phase without merge complexity.

## Risks / Trade-offs

- **[Risk] MIUI WindowInsetsController behavior** → Mitigation: test `StatusBarPolicyProvider` on Xiaomi Redmi Note 11 in Phase 2 before touching Settings/BootCheck. If MIUI blocks it, fall back to `FLAG_FULLSCREEN` window flag for MIUI-detected builds.
- **[Risk] 411 legacy imports — missed call sites** → Mitigation: fitness function #11 runs as lint check in CI. No compile-time escape hatch — `com.launcher.api.wizard` package deleted means compiler catches any remaining reference.
- **[Risk] Koin DI resolution order** → `PresetBootstrap` depends on `task120Module` being loaded before `FirstLaunchActivity` initializes. Mitigation: add integration test asserting Koin graph resolves without `UninitializedPropertyAccessException` on app cold start.
- **[Risk] E2E golden JSON regeneration** → Existing `simple-launcher` golden JSON references legacy component IDs. Mitigation: regenerate as part of Phase 5 commit, include before/after diff in PR description for reviewer.

## Migration Plan

**Phase 1** — New Component subtypes + pool schema changes
- Add `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy` subtypes to domain
- Add `requires` field to pool descriptor model + `PresetValidator`
- Add `hintFlow` + `hint-pool.json` support
- schemaVersion bump on Preset + Pool formats
- Unit tests for all new subtypes + validator

**Phase 2** — FirstLaunch wiring
- Wire `FirstLaunchActivity` → `PresetBootstrap.bootstrap()` + `ReconcileEngine.run(RunMode.Wizard)`
- Rewrite `WizardScreen` Composable → `InteractionSink` over `ReconcileEngine`
- Smoke: full wizard on Xiaomi Redmi Note 11, compare UX with pre-migration screenshot

**Phase 3** — Settings migration
- Migrate `PendingChecklistViewModel` + Settings screens from `ConfigKind` → `Preset.settingsMap[]`
- Smoke: Settings edit round-trip on Xiaomi

**Phase 4** — BootCheck migration
- Migrate `BootCheckReceiver` + related adapters to `PresetBootstrap` + `ReconcileEngine`
- Smoke: force-reboot BootCheck on Xiaomi

**Phase 5** — E2E test migration
- Rewrite `BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest`
- Regenerate `simple-launcher` golden JSON

**Phase 6** — Legacy deletion + fitness function
- Delete `com.launcher.api.wizard.*`, `com.launcher.api.preset.*`, `com.launcher.api.profile.*`, `com.launcher.api.switchstrategy.*`, `com.launcher.adapters.wizard.*`, `app/wizard/`
- Delete `Spec015Module`, `Task65Module` → merge into `PresetModule`
- Delete `core/androidMain/assets/wizard/` assets
- Remove `uses-accessibility-service` from manifest
- Add fitness function #11 lint rule
- Full green: `./gradlew :app:testMockBackendDebugUnitTest :core:testMockBackendDebugUnitTest`
- Full green: E2E on Xiaomi

**Rollback**: branch `task-126-wizard-runtime-migration` — `main` untouched until PR merge. Rollback = close PR, no production impact.

## Open Questions

*(All 6 architectural questions from Discussion session 1+2 resolved. No open questions.)*
