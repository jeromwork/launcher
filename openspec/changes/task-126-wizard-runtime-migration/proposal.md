## Why

Two parallel wizard engines exist in production: legacy `com.launcher.api.wizard.*` (powers `FirstLaunchActivity` today) and TASK-65 mid-flight `com.launcher.api.preset.*` — both doing the same check/apply loop that TASK-120 `ReconcileEngine` already implements correctly. Every bug fix requires changes in two places, and the ECS architecture (Component/Provider/Profile) from TASK-120 delivers no value while real UI still runs on the legacy stack.

## What Changes

- **BREAKING** Delete `com.launcher.api.wizard.*` (~26 files) — legacy WizardEngine, WizardStep, WizardManifest, WizardCheckpoint
- **BREAKING** Delete `com.launcher.api.preset.*` + `api.profile.*` + `api.switchstrategy.*` (TASK-65 mid-flight model, ~12 files)
- **BREAKING** Delete `com.launcher.adapters.wizard.*` + `app/wizard/` DI modules — all legacy handlers, stores, facades
- **BREAKING** Delete `core/androidMain/assets/wizard/` bundled JSON (tile-sets/*, system-settings/*) — replaced by TASK-120 bundled seeds
- Add `Component` subtypes: `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`
- Add `requires: [ComponentRef]` field to pool.json component descriptors — machine-readable ordering constraints
- Add `hintFlow: List<HintFlowEntry>` field to Preset + separate `hint-pool.json` for TutorialHint (not reconcile-able)
- Migrate `FirstLaunchActivity` → `PresetBootstrap` + `ReconcileEngine.run(RunMode.Wizard)`
- Migrate `WizardScreen` Composable → `InteractionSink` pattern over `ReconcileEngine`
- Migrate Settings screens (`PendingChecklistViewModel` etc.) from `ConfigKind` → `Preset.settingsMap[]`
- Migrate all `CheckHandler`/`ApplyHandler` pairs → `Provider<T>` implementations
- Consolidate DI: `Spec015Module` + `Task65Module` → single `PresetModule`
- Migrate E2E tests (`BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest`) to new API
- Add fitness function #11: no imports of `com.launcher.api.wizard` in production code
- Remove `AccessibilityService` from manifest — `StatusBarPolicy` uses `WindowInsetsController` instead

## Capabilities

### New Capabilities

- `preset-runtime`: End-to-end integration of TASK-120 ReconcileEngine + Provider<T> + Profile into first-run / Settings / BootCheck flows. Covers new Component subtypes (LauncherRole, Theme, Language, StatusBarPolicy), component dependency validation, TutorialHint hint-pool, and InteractionSink UI pattern.

### Modified Capabilities

*(No existing openspec/specs/ entries — this is the first real change in the workspace.)*

## Impact

- **Deleted packages**: `com.launcher.api.wizard.*`, `com.launcher.api.preset.*`, `com.launcher.api.profile.*`, `com.launcher.api.switchstrategy.*`, `com.launcher.adapters.wizard.*`, `com.launcher.app.wizard.*`
- **Migrated entry points**: `FirstLaunchActivity`, `WizardScreen`, `PendingChecklistViewModel`, `BootCheckReceiver`
- **Deleted assets**: `core/androidMain/assets/wizard/` tree
- **DI consolidation**: `Spec015Module` + `Task65Module` → `PresetModule`
- **Manifest**: `uses-accessibility-service` declaration removed
- **Wire format**: `Preset` JSON gains `hintFlow` field (schemaVersion bump); pool.json component descriptors gain optional `requires` field — both backward-compatible additive changes per CLAUDE.md rule 5
- **Cross-app TODO**: `Theme` + `Language` are candidates for a future shared cross-app Profile layer (TASK-127 Draft). No action in this change — additive move when second app appears.
