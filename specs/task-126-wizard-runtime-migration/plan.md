# Implementation Plan: Wizard Runtime Migration to Preset Composition

**Branch**: `task-126-wizard-runtime-migration` | **Date**: 2026-07-11 | **Spec**: [spec.md](spec.md)
**Input**: OpenSpec design.md (D1–D9) + spec.md (22 FR, 4 NFR, 7 User Stories, CL-1..CL-9 resolved)

---

## Summary

Three parallel engine stacks in the codebase (`com.launcher.api.wizard.*` legacy, TASK-65 `com.launcher.api.preset.*`, TASK-120 `com.launcher.preset.*`) collapse into the single TASK-120 `ReconcileEngine`. This is a pure technical refactoring: all 18 FRs target engine consolidation, adapter migration, and legacy deletion. Zero user-visible behavior changes.

---

## Technical Context

**Language/Version**: Kotlin 1.9 / KMP (JVM + Android targets)
**Primary Dependencies**: Koin 3.x DI, Compose UI, DataStore, kotlinx.serialization, WindowInsetsControllerCompat
**Storage**: DataStore (ProfileStore only — no WizardStore per CL-5), bundled JSON assets
**Testing**: JUnit5 + Robolectric (unit), Espresso/Compose test (E2E on Xiaomi)
**Target Platform**: Android API 26–34, primarily Xiaomi Redmi Note 11 (MIUI)
**Project Type**: KMP Android library (`core`) + Android application (`app`)
**Performance Goals**: ReconcileEngine cold-start ≤ 30 ms on Pixel 5 (NFR-001, already verified in TASK-120)
**Constraints**: No production users → no data migration needed (D1, Article XX constitution)
**Scale/Scope**: 411 legacy imports across 87 files; 6 phases; ~75 tasks

---

## Architecture

### Module Map (unchanged — no new Gradle modules)

```
app/                        Android application module
├── di/
│   ├── Task120Module.kt    → renamed/expanded to PresetModule.kt (Phase 6)
│   ├── Spec015Module.kt    → DELETED (Phase 6)
│   └── Task65Module.kt     → DELETED (Phase 6)
├── firstlaunch/
│   └── FirstLaunchActivity.kt  → rewired to PresetBootstrap + ReconcileEngine (Phase 2)
├── boot/
│   ├── BootCheckReceiver.kt    → dispatch-only; enqueue BootCheckWorker (Phase 4, CL-9)
│   └── BootCheckWorker.kt      ← NEW WorkManager worker running the actual reconcile (Phase 4, CL-9)
├── wizard/
│   ├── WizardActivity.kt        → DELETED (Phase 6)
│   └── NoopAdapters.kt          → DELETED (Phase 6)
├── preset/task120/
│   ├── PresetBootstrap.kt       (exists — no change)
│   ├── adapter/                 (exists — DataStore*, Bundled*)
│   ├── facade/                  (exists — HomeScreen*, PackageManager*, UiPrefs*)
│   └── provider/
│       ├── AppTileProvider.kt   (exists)
│       ├── FontSizeProvider.kt  (exists)
│       ├── SosProvider.kt       (exists)
│       ├── ToolbarProvider.kt   (exists)
│       ├── LauncherRoleProvider.kt   ← NEW (Phase 1)
│       ├── ThemeProvider.kt          ← NEW (Phase 1)
│       ├── LanguageProvider.kt       ← NEW (Phase 1)
│       └── StatusBarPolicyProvider.kt ← NEW (Phase 1)
├── preset/task126/
│   └── BundledHintPoolSource.kt      ← NEW (Phase 1) — HintPoolSource adapter (CL-7)
└── settings/
    └── PendingChecklistViewModel.kt  → migrated from ConfigKind (Phase 3)

core/src/commonMain/kotlin/com/launcher/preset/
├── model/
│   ├── Component.kt          → add LauncherRole, Theme, Language, StatusBarPolicy subtypes
│   ├── Pool.kt               → add requires + required fields to ComponentDeclaration
│   ├── Preset.kt             → add hintFlow + wizardPresentation fields; schemaVersion → 2
│   ├── HintFlowEntry.kt      ← NEW (Phase 1)
│   └── ValidationError.kt    ← NEW (Phase 1) — sealed class per FR-019
├── engine/
│   ├── PresetValidator.kt    → returns Result<Preset, ValidationError> (FR-019, no exceptions across boundary)
│   ├── ReconcileEngine.kt    (exists — unchanged)
│   └── ProfileFactory.kt     (exists — unchanged)
└── port/
    ├── InteractionSink.kt    (exists — unchanged)
    ├── ProfileStore.kt       (exists — unchanged)
    ├── Provider.kt           (exists — unchanged)
    └── HintPoolSource.kt     ← NEW (Phase 1) — port per CL-7

core/src/androidMain/kotlin/com/launcher/preset/
└── (new providers land here; existing adapters in app/preset/task120/)

DELETED packages (Phase 6):
  core/commonMain/com/launcher/api/wizard/     ~26 files
  core/commonMain/com/launcher/api/preset/     ~4 files
  core/commonMain/com/launcher/api/profile/
  core/commonMain/com/launcher/api/pools/
  core/commonMain/com/launcher/api/switchstrategy/
  core/androidMain/com/launcher/adapters/wizard/ ~13 files
  core/androidMain/assets/wizard/
  app/wizard/
```

### Port-Adapter Shape (new providers)

All four new Component subtypes follow the existing `Provider<T>` contract — no new ports needed:

```
Domain (commonMain)                Adapter (androidMain / app)
─────────────────────────────────────────────────────────────
Component.LauncherRole             LauncherRoleProvider
  check() → Ok | NeedsApply         uses RoleManager API (API ≥ 29)
  apply() → Ok | Failed              Intent.ACTION_MAIN + CATEGORY_HOME (API 26–28)

Component.Theme                    ThemeProvider
  check() → Ok | NeedsApply         reads DataStore UiPrefs
  apply() → Ok                      writes AppThemeController (new port)

Component.Language                 LanguageProvider
  check() → Ok | NeedsApply         queries AppCompatDelegate.getApplicationLocales()
  apply() → Ok                      calls AppCompatDelegate.setApplicationLocales()

Component.StatusBarPolicy          StatusBarPolicyProvider
  check() → Ok (stateless; always re-apply) 
  apply() → Ok | Failed              WindowInsetsControllerCompat.hide(statusBars())
                                     MIUI fallback: window FLAG_FULLSCREEN
```

`ThemeRef` — write-time sugar only. `ThemeCatalog` (new, androidMain) reads `theme-catalog.json` from assets and expands `ThemeRef(name)` → flat `Theme` fields. Wire format never contains `ThemeRef` (D3).

`HintPoolSource` port (new, `core/commonMain/kotlin/com/launcher/preset/port/HintPoolSource.kt`) + `BundledHintPoolSource` adapter (new, androidMain) read `hint-pool.json` from assets (CL-7 revises D5 loader shape). Additional sources (file import, share intent, marketplace) plug in additively per CLAUDE.md rule 9. `hintFlow` field is UI-layer metadata — ReconcileEngine never touches it (D5).

### Data Flow (Phase 2 — First Launch)

```
FirstLaunchActivity.onCreate()
  → SplashScreen API (CL-1)
  → WizardViewModel.bootstrap()              (retained across recreation via SavedStateHandle, CL-5)
      → PresetBootstrap.bootstrap()           loads preset + pool JSON via HintPoolSource / PresetSource / PoolSource
                                              → PresetValidator returns Result<Preset, ValidationError>  (FR-019)
                                              → Failure → ReconcileState.ValidationFailed → error screen + crash report (FR-020)
      → emit ReconcileState.Loading
  → WizardScreen observes StateFlow<ReconcileState>
      → ReconcileEngine.run(RunMode.Wizard, interactionSink)
          loop wizardFlow: Provider.check()
                            check() == Ok        → step skipped (already Applied in reality)
                            check() == NeedsApply → emit Interactive(componentId)
                                                    WizardScreen shows step UI
                                                    InteractionSink.answer() → Provider.apply() → ProfileStore.save()
                                                    denial:
                                                      required=false → mark Skipped, continue (FR-002)
                                                      required=true  → blocking screen «try preset Y» (FR-002)
      → emit ReconcileState.Done
  → navigate to HomeScreen (kiosk settings applied here — not during wizard)
```

### Wizard progress source of truth (CL-5, supersedes CL-3 WizardStore decision)

**No separate WizardStore / step counter is persisted.** Only one DataStore instance:

| Store | Contents | Persisted? | Server-sync? |
|-------|----------|-----------|-------------|
| `ProfileStore` (existing DataStore) | Applied/Pending/Failed component statuses per Component | Yes | Yes (future) |

On every wizard entry (fresh install, resume after force-close, resume after language change):
- `ReconcileEngine.run(RunMode.Wizard)` walks `wizardFlow` in order.
- For each Component, calls `Provider.check()` against real Android OS state (LauncherRole assigned? locale set? StatusBar policy in effect?).
- `check() == Ok` → step considered done, not shown to user.
- `check() == NeedsApply` → step is emitted as `Interactive(componentId)`.

Rationale: prior CL-3 model (`WizardStore.lastCompletedStepIndex`) could drift when Android OS state changes externally — for example user grants a permission through system Settings, or a system update revokes launcher role. New model queries reality directly. Cost: `check()` per Component on every entry — bounded by wizardFlow length (typically 5–15 Components), negligible latency (< 5 ms per Provider on Pixel 5 baseline).

### Mid-wizard lifecycle handling (CL-5, CL-9)

- **Configuration change (rotation, dark-mode, locale change, font-scale)**: `WizardViewModel` retained across `Activity` recreation via `SavedStateHandle`. `StateFlow<ReconcileState>` re-emitted; `WizardScreen` re-renders current step in new configuration. Target rebuild time: < 200 ms user-perceived (SC-11).
- **Language change (`Configuration.locale`)**: same path — `AppCompatDelegate.setApplicationLocales()` triggers Activity recreation; `WizardViewModel` survives; strings resolve in new locale on next composition. No «apply on next step» deferral.
- **Rotation**: default behavior — interactive rebuild. Preset MAY declare an optional `ScreenOrientationPolicy` Component (future additive item, not in scope) to lock orientation.
- **Force-close / process death**: `Profile` persisted in DataStore reconstructs via `PresetBootstrap`. `ReconcileEngine` re-walks `wizardFlow`; `Provider.check()` determines resumption point from reality, not from a stored counter (CL-5).

### BootCheck dispatch model (CL-9)

```
BOOT_COMPLETED broadcast
  → BootCheckReceiver.onReceive()      (< 100 ms; dispatch only)
      → WorkManager.enqueueUniqueWork("boot-check", BootCheckWorker)
  → onReceive() returns immediately  (well within 10s ANR window)

Later, WorkManager schedules BootCheckWorker:
  → PresetBootstrap.bootstrap()
  → ReconcileEngine.run(RunMode.BootCheck)  (only critical=true providers)
  → ProfileStore.save()
```

Rationale: Android BroadcastReceiver `onReceive()` has ~10s hard limit before ANR. Inline reconcile could exceed on slow-boot devices (Xiaomi cold boot with many apps). WorkManager guarantees execution outside the receiver context.

---

## Data Model

See [data-model.md](data-model.md) for field-level detail.

**Key changes to existing models:**

`Component` (sealed class) gains 4 new subtypes:
- `LauncherRole` — no parameters
- `Theme` — `paletteSeedHex: String`, `typographyScale: TypographyScale`, `shapeStyle: ShapeStyle`, `darkMode: Boolean`
- `Language` — `locale: String` (sentinel `"system"`)
- `StatusBarPolicy` — no parameters

`Pool.ComponentDeclaration` gains:
- `requires: List<String>? = null` — component IDs that must appear earlier in `wizardFlow`
- `required: Boolean = false` — if true, wizard is not complete until Applied

`Preset` gains:
- `hintFlow: List<HintFlowEntry>? = null`
- `wizardPresentation: WizardPresentation? = null` — `{ darkMode: Boolean, typographyScale: TypographyScale }` applied once at wizard start

`Preset.schemaVersion` → 2 (from 1)
`Pool.schemaVersion` → 2 (from 1)

Both upgrades are additive / backward-compatible (v1 readers ignore new fields; v2 readers default missing fields to null/false).

---

## Wire Formats

See [contracts/preset-schema-v2.md](contracts/preset-schema-v2.md) and [contracts/pool-schema-v2.md](contracts/pool-schema-v2.md).

| Format | File | Version bump | Backward-compat |
|--------|------|-------------|----------------|
| Preset JSON | `assets/presets/simple-launcher.json` | v1 → v2 | ✓ new fields nullable/defaulted |
| Pool JSON | `assets/pool.json` | v1 → v2 | ✓ `requires`/`required` optional |
| Hint pool JSON | `assets/hint-pool.json` | new (`schemaVersion: 1`, CL-7) | N/A new file — loaded via `HintPoolSource` port |
| Theme catalog JSON | `assets/theme-catalog.json` | new (v1) | N/A new file |

Legacy wizard assets deleted entirely (D1, zero migration — Article XX constitution):
- `assets/wizard/tile-sets/*`
- `assets/wizard/system-settings/*`

---

## Dependency Impact

No new Gradle dependencies introduced. All required APIs already on the classpath:

| API | Already present | Usage |
|-----|----------------|-------|
| `WindowInsetsControllerCompat` | ✓ (core-ktx) | StatusBarPolicyProvider |
| `AppCompatDelegate.setApplicationLocales()` | ✓ (appcompat) | LanguageProvider |
| `RoleManager` (API ≥ 29) | ✓ (Android SDK) | LauncherRoleProvider |
| `kotlinx.serialization` | ✓ | Preset/Pool schema v2 |
| `DataStore` | ✓ | ProfileStore only — no WizardStore per CL-5 |

Lint rule FF-011 (FR-015): custom lint check in `lint-rules/` module (already exists or create alongside existing lint module). Zero external deps.

---

## Test Strategy

Following CLAUDE.md rule §6 + §7.

### Unit tests (per phase)

| Phase | Test class | What's tested |
|-------|-----------|--------------|
| 1 | `LauncherRoleProviderTest` | check()→Ok when already default; check()→NeedsApply when not; apply() opens dialog once |
| 1 | `ThemeProviderTest` | apply() writes AppThemeController; check() reads current state |
| 1 | `LanguageProviderTest` | apply() calls setApplicationLocales(); sentinel "system" handled |
| 1 | `StatusBarPolicyProviderTest` | apply() calls hide(statusBars()); MIUI fallback path |
| 1 | `PresetValidatorTest` | valid ordering returns `Result.Success`; requires violation returns `Result.Failure(ValidationError.RequiresOrderViolation)`; missing requires ID returns `ValidationError.UnknownComponentId` (FR-019) |
| 1 | `BundledPresetValidationTest` | every JSON under `assets/presets/` returns `Result.Success` at CI time (SC-12, CL-8) |
| 1 | `HintPoolSourceTest` | `BundledHintPoolSource.load()` reads JSON with `schemaVersion: 1`; missing file → empty pool (CL-7) |
| 1 | `ThemeRefExpansionTest` | ThemeRef("dark") expands to known flat fields |
| 1 | `PresetSchemaVersionTest` | v1 reader ignores hintFlow; v2 reader defaults to empty list |
| 2 | `WizardViewModelTest` | state transitions: Loading → Interactive → Done; interactionSink.answer() advances state |
| 2 | `PresetBootstrapIntegrationTest` | Koin graph resolves without UninitializedPropertyAccessException |
| 3 | `PendingChecklistViewModelTest` | loads from Preset.settingsMap[], not ConfigKind |
| 4 | `BootCheckProviderTest` | only critical=true providers invoked; non-critical skipped |
| 4 | `BootCheckWorkerTest` | Receiver dispatch → WorkManager enqueue; Worker runs `ReconcileEngine.run(RunMode.BootCheck)` outside receiver context (CL-9) |
| 2 | `WizardLocaleChangeTest` | Robolectric: locale change mid-wizard → `WizardViewModel` retained via `SavedStateHandle` → step re-renders with new locale strings (SC-11, CL-5) |
| 2 | `WizardResumeCheckTest` | resume after external OS state change (e.g., permission granted through system Settings) → `Provider.check()` returns `Ok` → step skipped (no `WizardStore` counter drift, CL-5) |
| 2 | `WizardDenialUxTest` | required=false denial → step Skipped, wizard proceeds; required=true denial → blocking screen «try preset Y» (CL-6, FR-002) |

### E2E tests (Phase 5, on Xiaomi Redmi Note 11)

- `BootBenchmarkE2ETest` — rewritten on ReconcileEngine API
- `BootCriticalMissingE2ETest` — rewritten on ReconcileEngine API
- `FirstLaunchPickerE2ETest` — rewritten on ReconcileEngine API
- `XiaomiOemMatrixE2ETest` — StatusBarPolicyProvider MIUI path verified

### Fake adapters

- `FakeProfileStore` (already exists in TASK-120 test tree)
- `FakeInteractionSink` — new for Phase 2 tests; answers each Interactive step automatically

### Fitness functions

- **FF-011**: custom lint check `com.launcher.api.wizard` import → build failure (Phase 6)
- **NFR-001 guard**: `ReconcileEnginePerfTest` — cold-start ≤ 30 ms regression (already exists from TASK-120)
- **Phase 6 grep gate**: `git grep "import com.launcher.api.wizard"` + `git grep "import com.launcher.api.preset"` → zero results (CL-4)

---

## Risks

| Risk | Severity | Mitigation |
|------|---------|-----------|
| MIUI `WindowInsetsController` non-standard behavior | Medium | Test `StatusBarPolicyProvider` on Xiaomi in Phase 2 before touching other code; fallback to `FLAG_FULLSCREEN` with `MANUFACTURER` detection inline in Provider |
| 411 legacy imports — missed call sites at deletion | High | FF-011 lint rule (Phase 6) catches compile-time; `git grep` in Phase 6 checklist before deletion |
| Koin DI resolution order (`PresetBootstrap` before `FirstLaunchActivity.onCreate`) | Medium | Integration test in Phase 2 asserting Koin graph resolves without `UninitializedPropertyAccessException` |
| E2E golden JSON incompatible with schemaVersion 2 | Low | Regenerate in Phase 5 commit; include before/after diff in PR description |
| `FirstLaunchActivity` currently does direct-control wizard (not state-driven) | Medium | Phase 2 full rewrite to `WizardViewModel` + `StateFlow`; existing recovery/auth steps must be preserved in new flow (not deleted) |
| `PresetSelectionService` / `PresetSwitchService` naming diverges from ECS | Low | Phase 3 audit; rewrite if diverged — don't carry over TASK-65 logic (CL-4) |

---

## Phased Migration Order (D9)

One PR on `task-126-wizard-runtime-migration` branch, 6 phases:

### Phase 1 — New Component Subtypes + Pool Schema
Target: domain additions only; no UI changes; tests green after each commit.
- `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy` in `core/commonMain`
- `requires`/`required` on `ComponentDeclaration`; `PresetValidator` extended
- `hintFlow` + `HintFlowEntry` in Preset; `HintPoolSource` port (commonMain) + `BundledHintPoolSource` adapter (androidMain) per CL-7
- schemaVersion bumps on Preset + Pool (v1→v2)
- `ThemeRef` + `ThemeCatalog` in androidMain
- All 4 new Providers in `app/preset/task120/provider/`
- Wire new Providers into `task120Module` Koin bindings + CapabilityContract
- **Gate**: `./gradlew :app:testMockBackendDebugUnitTest :core:testMockBackendDebugUnitTest` green

### Phase 2 — FirstLaunch Wiring
Target: `FirstLaunchActivity` + `WizardScreen` run exclusively through ReconcileEngine.
- `WizardViewModel` wrapping `ReconcileEngine` + exposing `InteractionSink`
- `WizardScreen` Composable consuming `StateFlow<ReconcileState>`
- No WizardStore — wizard progress derived from `Provider.check()` results on each cold start (CL-5)
- SplashScreen API integration (CL-1)
- **Preserve** existing `FirstLaunchActivity` auth + recovery steps (they are not wizard steps — they remain as-is before PresetBootstrap runs)
- **Gate**: manual smoke on Xiaomi — UX identical to `verification-evidence/task-120-xiaomi-first-launch.png`

### Phase 3 — Settings Migration
Target: zero `ConfigKind` references in Settings screens.
- `PendingChecklistViewModel` → `Preset.settingsMap[]` + `ProfileStore`
- All Settings screens reading ConfigKind → `ProfileStore` + `Preset`
- `PresetSelectionService` + `PresetSwitchService` audited/rewritten to `com.launcher.preset.*`
- **Gate**: Settings edit round-trip on Xiaomi; `PendingChecklistViewModelTest` green

### Phase 4 — BootCheck Migration
Target: `BootCheckReceiver` uses ReconcileEngine.
- `BootCheckReceiver` → `PresetBootstrap` + `ReconcileEngine.run(RunMode.BootCheck)`
- All `CheckHandler`/`ApplyHandler` pairs from `adapters/wizard/` → `Provider<T>` in `com.launcher.preset.provider.*`
- `WizardCheckpointStore` deleted → callers use `ProfileStore.setPreWizardSnapshot()`
- **Gate**: force-reboot smoke on Xiaomi; `BootCheckProviderTest` green

### Phase 5 — E2E Test Migration
Target: all 4 E2E tests on new API.
- `BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest` rewritten
- `simple-launcher` golden JSON regenerated for schemaVersion 2
- **Gate**: `./gradlew :app:connectedMockBackendDebugAndroidTest --tests "*E2E*"` green on Xiaomi

### Phase 6 — Legacy Deletion + Fitness Function
Target: zero legacy code, FF-011 enforced.
- Delete `com.launcher.api.wizard.*`, `com.launcher.api.preset.*`, `com.launcher.api.profile.*`, `com.launcher.api.pools.*`, `com.launcher.api.switchstrategy.*`
- Delete `com.launcher.adapters.wizard.*`, `app/wizard/`, `assets/wizard/`
- Delete `Spec015Module.kt` + `Task65Module.kt` → merge into renamed `PresetModule.kt`
- Remove `uses-accessibility-service` from `AndroidManifest.xml`
- Add FF-011 lint rule
- **Gate**: `git grep "import com.launcher.api.wizard"` → zero; `git grep "import com.launcher.api.preset"` → zero; `./gradlew lint` FF-011 zero violations; full unit + E2E green

---

## Required Context Review

- [docs/governance/document-map.md](../../docs/governance/document-map.md)
- [openspec/changes/task-126-wizard-runtime-migration/design.md](../../openspec/changes/task-126-wizard-runtime-migration/design.md) — D1–D9 decisions
- [backlog/tasks/task-126 - Wizard runtime migration to Preset composition foundation.md](../../backlog/tasks/task-126%20-%20Wizard%20runtime%20migration%20to%20Preset%20composition%20foundation.md)
- TASK-120 spec (foundation that this task builds on)
- [app/src/main/java/com/launcher/app/di/Task120Module.kt](../../app/src/main/java/com/launcher/app/di/Task120Module.kt) — existing DI wiring
- [app/src/main/java/com/launcher/app/firstlaunch/FirstLaunchActivity.kt](../../app/src/main/java/com/launcher/app/firstlaunch/FirstLaunchActivity.kt) — rewire target

---

## Constitution Check

_Per `procedure-constitution-check` — checked against `.specify/memory/constitution.md` v1.7._

| Article | Gate | Status | Notes |
|---------|------|--------|-------|
| I — Spec-First Delivery | spec.md exists, clarified, no open NEEDS CLARIFICATION | ✅ PASS | CL-1..CL-4 closed |
| II — Product Identity | no new user-visible features; pure refactoring | ✅ PASS | NFR-004 enforces UX identity |
| III §3 — Device restart reliability | BootCheck (Phase 4) + ProfileStore persistence (Phase 1-2) | ✅ PASS | US-3 + US-4 cover it |
| III §4 — Invalid config fails safely | PresetValidator blocks wizard start on bad preset | ✅ PASS | FR-006 + SEQ-3 |
| III §7 — Stability over system changes | LanguageProvider uses AppCompatDelegate (not system locale) | ✅ PASS | FR-004 |
| IV — Layered architecture | domain (core/commonMain) ← ports ← adapters (androidMain/app); no SDK in domain | ✅ PASS | FR-018 explicitly requires ACL for SystemSettingPort/PermissionRequestPort |
| V — Modularization restraint | no new Gradle modules; DI consolidated (not expanded) | ✅ PASS | FR-016 |
| VI — Core owns system integration | BootCheckReceiver stays in androidMain; system callbacks don't leak into domain | ✅ PASS | Phase 4 scope |
| VII §5 — Wire format versioning | schemaVersion bumped to 2 on both Preset + Pool; v1 backward-compat | ✅ PASS | FR-014 |
| VII §10/13 — Config as JSON content | all new Component subtypes are JSON-driven, no hardcoded logic | ✅ PASS | D3/D4/D8 |
| X — No migration writer (Article XX) | D1 explicitly states zero production users; Article XX permits deletion | ✅ PASS | FR-017 |
| XIII §3 — Accessibility | StatusBarPolicy is preset-controlled (clinic keeps bar visible); not mandatory | ✅ PASS | FR-005, US-6 |
| XV §7 — Fitness functions | FF-011 lint rule enforces import ban at compile time | ✅ PASS | FR-015, NFR-003 |

**CONSTITUTION CHECK: PASS (13/13)**

---

## Rollout / Verification

### After each phase commit:
- `./gradlew :app:testMockBackendDebugUnitTest :core:testMockBackendDebugUnitTest` — must be green (NFR-002)

### Phase 2 smoke:
- Full wizard on Xiaomi Redmi Note 11
- Screenshot comparison against `verification-evidence/task-120-xiaomi-first-launch.png` (NFR-004)
- StatusBarPolicyProvider MIUI behavior verified

### Phase 6 final:
- `./gradlew lint` → FF-011 zero violations (NFR-003)
- `git grep "import com.launcher.api.wizard"` → empty
- `git grep "import com.launcher.api.preset"` → empty
- `./gradlew :app:connectedMockBackendDebugAndroidTest --tests "*E2E*"` on Xiaomi → green (SC-8)
- Manual: fresh install → preset picker → wizard → home → settings edit → force-reboot → BootCheck (SC-1..SC-4)

---

## Project Structure

```
specs/task-126-wizard-runtime-migration/
├── spec.md             ← source
├── plan.md             ← this file
├── data-model.md       ← field-level schema changes
├── contracts/
│   ├── preset-schema-v2.md
│   └── pool-schema-v2.md
└── tasks.md            ← generated by speckit-tasks (next step)
```

Source tree impact: no new directories; changes within `core/` (domain + androidMain) and `app/` (di, preset, firstlaunch, settings, wizard→deleted).

---

## Open Issues

Architectural: none. All 6 architectural questions (D1–D9) resolved. All 9 clarifications (CL-1..CL-9) closed. Constitution check passes.

**Plan-time refinement items (non-blocking):**

- **API 35+ / OEM matrix enumeration**: which specific `Android version × OEM × device version` combinations get explicit test paths first? Baseline: Xiaomi Redmi Note 11 (MIUI, Android 13). Matrix rows added as devices join. Decided per phase, not upfront.
- **Crash reporting adapter selection (FR-020)**: Firebase Crashlytics vs Sentry self-hosted vs custom Cloudflare Worker endpoint? Deferred — beta phase can ship with logcat-only fallback + owner-side pull; production adapter chosen additively when a real crash triggers the need.
- **`ScreenOrientationPolicy` Component**: future additive item (out of scope for TASK-126). Triggered when the first preset needs an orientation lock. Aligns with FR-022 configuration-change strategy.

**One implementation detail to verify in Phase 2**: `FirstLaunchActivity` currently runs recovery/auth steps (F-4 Sign-In, F-5b passphrase setup/entry) before routing to `WizardActivity`. In the new model, the auth steps MUST remain in `FirstLaunchActivity` as-is (they are not `ReconcileEngine` components — they are identity orchestration, not preset reconciliation). The new wizard wrapping (`WizardViewModel` + `ReconcileEngine`) replaces only the `WizardActivity` call at the end of `FirstLaunchActivity.proceedToHome()`. This boundary is confirmed by spec assumptions and CL-2 (kiosk settings post-wizard), but the implementer must verify the exact wiring point to avoid accidentally removing the F-4/F-5 steps.

---

## Novice Summary (для владельца)

**Что такое этот план?**

Это подробное техническое описание того, **как именно** будет выполнена миграция. Не «что делаем» (это в spec.md) — а «в каком порядке, какие файлы, какие проверки».

**Что происходит внутри:**

Сейчас в коде три отдельных «движка» делают похожие вещи — по-разному. Это как если бы у вас было три разных пульта для одного телевизора. TASK-126 убирает два старых пульта и оставляет один — уже работающий `ReconcileEngine` из TASK-120.

**Что изменилось после второго clarify pass (2026-07-11):**

- **Не запоминаем прогресс wizard'а в отдельном счётчике.** На каждом запуске wizard заново спрашивает у Android «а это уже сделано?» через `Provider.check()`. Так проще и надёжнее: если бабушка сама что-то поменяла в настройках Android, wizard это увидит.
- **Отказ от разрешения = смена preset'а, а не переустановка.** Если preset требует permission, а user отказал — предлагаем другой preset, где эта permission не нужна.
- **Смена языка в середине wizard'а — сразу видна.** Wizard пересобирается интерактивно (< 200 мс), не ждёт следующего шага.
- **BootCheckReceiver 10 секунд — обход через WorkManager.** Receiver только передаёт задачу WorkManager'у и возвращается; сам reconcile выполняется отдельно, без риска ANR.
- **Ошибка в bundled preset — ловим на CI.** Есть тест, который проверяет каждый JSON в `assets/presets/` перед релизом. В production — если всё же случилось — показываем понятный экран + автоматически шлём обезличенный краш-репорт владельцу.

**Шесть фаз:**

1. **Новые компоненты** — добавляем 4 новых «блока настройки» (выбор лаунчером по умолчанию, тема, язык, статус-бар) в новый движок.
2. **Первый запуск** — переключаем экран первого запуска с старого движка на новый.
3. **Настройки** — переключаем экран «Настройки» на новый движок.
4. **Загрузка при включении** — переключаем проверку при ребуте на новый движок.
5. **Тесты** — переписываем 4 автотеста под новый API; обновляем тестовые JSON-файлы.
6. **Удаление** — удаляем весь старый код (411 импортов в 87 файлах), добавляем защиту от случайного возврата к старому (lint-правило FF-011).

**Что пользователь видит:** ничего. UX идентичен до и после — задача чисто техническая.
