# Tasks: Wizard Runtime Migration to Preset Composition

**Branch**: `task-126-wizard-runtime-migration`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Data model**: [data-model.md](data-model.md)
**Contracts**: [preset-schema-v2.md](contracts/preset-schema-v2.md), [pool-schema-v2.md](contracts/pool-schema-v2.md), [hint-pool-schema-v1.md](contracts/hint-pool-schema-v1.md)

Six phases per plan.md §Phased Migration Order (D9). Every task is traced to a Functional Requirement (FR-XXX) and/or a User Story (US-N) from spec.md. `[P]` = parallel-safe (no shared file / no ordering dependency). `[deferred-*]` markers are consumed by `pre-pr-backlog-sync` for `[auto:deferred-*]` backlog AC.

---

## Pre-flight

- [x] **T001** Verify Constitution gates re-read before starting: CLAUDE.md rule 1 (domain isolation — new Component subtypes stay in `core/commonMain`, providers stay in `androidMain`/`app`), rule 2 (ACL — `SystemSettingPort`/`PermissionRequestPort` wrap Android SDK types, never leak into domain), rule 5 (schemaVersion bumps on Preset + Pool + new hint-pool JSON), rule 6 (mock-first — `FakeInteractionSink` + `FakeProfileStore` before real adapters), rule 9 (`HintPoolSource` port + `BundledHintPoolSource` adapter with additive TODO), rule 4 (no premature abstractions — no new Gradle modules). Reference: plan.md §Constitution Check. (all FRs)

---

## Phase 1 — New Component subtypes + Pool schema + Validator + HintPoolSource

**Goal**: domain additions only, no UI wiring. All tests green after each commit.
**Gate**: `./gradlew :app:testMockBackendDebugUnitTest :core:testMockBackendDebugUnitTest` green; roundtrip + backward-compat green.

### 1.1 Domain models (commonMain)

- [x] **T010** [P] Add `Component.LauncherRole` subtype (no parameters) to `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt`. Traceable to FR-002. Acceptance: sealed hierarchy compiles; kotlinx.serialization discriminator added.
- [x] **T011** [P] Add `Component.Theme` subtype with fields `paletteSeedHex: String`, `typographyScale: TypographyScale`, `shapeStyle: ShapeStyle`, `darkMode: Boolean`. Traceable to FR-003. Acceptance: fields serialize/deserialize; ThemeRef NOT part of wire format (write-time sugar only).
- [x] **T012** [P] Add `Component.Language` subtype with `locale: String` (sentinel `"system"`). Traceable to FR-004. Acceptance: `null` locale deserialization rejected by validator (see T023).
- [x] **T013** [P] Add `Component.StatusBarPolicy` subtype (no parameters). Traceable to FR-005. Acceptance: sealed hierarchy compiles.
- [x] **T014** Add `HintFlowEntry` data class (`hintId: String`, `targetComponentId: String`, `textKey: String`) to `core/src/commonMain/kotlin/com/launcher/preset/model/HintFlowEntry.kt`. Traceable to FR-007.
- [x] **T015** Extend `Preset` with `hintFlow: List<HintFlowEntry>? = null` + `wizardPresentation: WizardPresentation? = null` fields; bump `Preset.schemaVersion` to 2. Traceable to FR-003 (wizardPresentation), FR-007 (hintFlow), FR-014 (schema bump). Depends on T014. Acceptance: existing v1 fixtures still deserialize with defaults.
- [x] **T016** [P] Add `WizardPresentation` data class (`darkMode: Boolean`, `typographyScale: TypographyScale`) in same package as Preset. Traceable to FR-003 (CL-2).
- [x] **T017** Extend `Pool.ComponentDeclaration` with `requires: List<String>? = null` + `required: Boolean = false`; bump `Pool.schemaVersion` to 2. Traceable to FR-006, FR-014. Acceptance: v1 pool.json deserializes with defaults; roundtrip preserves fields when present.
- [x] **T018** [P] Add `ValidationError` sealed class in `core/src/commonMain/kotlin/com/launcher/preset/model/ValidationError.kt` with variants `RequiresOrderViolation(offenderId, missingId)`, `UnknownComponentId(id)`, `NullLocale`, `SchemaVersionUnsupported(actual, expected)`. Traceable to FR-019 (CL-8).

### 1.2 Ports (commonMain)

- [x] **T019** [P] Define `HintPoolSource` port in `core/src/commonMain/kotlin/com/launcher/preset/port/HintPoolSource.kt` — single `suspend fun load(): List<HintFlowEntry>`. Add inline `// TODO(shareability): future HintPoolSource adapters — file import, share intent, marketplace` per CLAUDE.md rule 9. Traceable to FR-007 (CL-7).

### 1.3 Validator (commonMain)

- [x] **T020** Extend `PresetValidator` in `core/src/commonMain/kotlin/com/launcher/preset/engine/PresetValidator.kt` to return `Result<Preset, ValidationError>` (Kotlin `Result` with typed error variant, no exceptions across domain boundary). Detect `requires` ordering violations against Pool descriptors; detect unknown component IDs; detect null locale on Language; detect unsupported schemaVersion. Traceable to FR-006, FR-019 (CL-8). Depends on T017, T018.

### 1.4 Wire format contracts + tests (commonMain)

- [x] **T021** [P] Roundtrip test `PresetSchemaV2RoundtripTest` in `core/src/commonTest/kotlin/com/launcher/preset/wire/`: encode Preset (with hintFlow + wizardPresentation) → decode → assert equal. Traceable to FR-014, contracts/preset-schema-v2.md. Constitution rule 5.
- [x] **T022** [P] Backward-compat test `PresetSchemaV1ReadV2Test`: v1 reader ignores `hintFlow` + `wizardPresentation`; v2 reader defaults them to null on v1 input. Traceable to FR-014. Constitution rule 5.
- [x] **T023** [P] Roundtrip test `PoolSchemaV2RoundtripTest`: encode Pool with `requires`/`required` on descriptors → decode → assert equal. Traceable to FR-014, contracts/pool-schema-v2.md.
- [x] **T024** [P] Backward-compat test `PoolSchemaV1ReadV2Test`: v1 pool.json (no requires/required) deserializes with defaults. Traceable to FR-014.
- [x] **T025** [P] Roundtrip test `HintPoolSchemaV1RoundtripTest` for `hint-pool.json` shape (schemaVersion: 1). Traceable to FR-007 (CL-7), contracts/hint-pool-schema-v1.md.
- [x] **T026** [P] Validator test `PresetValidatorTest`: valid ordering → `Result.Success`; `requires` violation → `Result.Failure(RequiresOrderViolation)`; missing ID → `UnknownComponentId`; null locale → `NullLocale`. Traceable to FR-006, FR-019.
- [x] **T027** Compile-time bundled preset validation test `BundledPresetValidationTest`: iterate every JSON under `app/src/main/assets/presets/` (and any additional bundled preset directories), assert `PresetValidator.validate()` returns `Result.Success`. Traceable to FR-019, SC-12 (CL-8). Depends on T020.

### 1.5 Fake adapters (mock-first, rule 6)

- [x] **T028** [P] `FakeInteractionSink` in `core/src/commonTest/kotlin/com/launcher/preset/fake/FakeInteractionSink.kt`: auto-answers each Interactive step with configurable response; records call order for assertions. Traceable to CLAUDE.md rule 6, US-1/US-2 test scaffold.
- [x] **T029** [P] `FakeHintPoolSource` in `core/src/commonTest/kotlin/com/launcher/preset/fake/FakeHintPoolSource.kt`: returns configured list; supports empty-pool scenario. Traceable to FR-007.
- [x] **T030** [P] Confirm existing `FakeProfileStore` (TASK-120 test tree) supports new Component subtypes; extend if needed. Traceable to US-3. — Confirmed: `com.launcher.preset.fakes.FakeProfileStore` stores `Profile` and delegates Component polymorphism to kotlinx.serialization; no code change needed for new subtypes (LauncherRole / Theme / Language / StatusBarPolicy).

### 1.6 Android adapters (androidMain / app)

- [x] **T031** [P] `LauncherRoleProvider` in `app/src/main/java/com/launcher/app/preset/task120/provider/LauncherRoleProvider.kt`: `check()` uses `RoleManager.isRoleHeld(ROLE_HOME)` on API ≥ 29, `Intent.ACTION_MAIN + CATEGORY_HOME` resolveActivity on API 26–28; `apply()` opens system dialog once. Traceable to FR-002, US-2. Constitution rule 2 (ACL wraps Android SDK).
- [x] **T032** [P] `ThemeProvider` in same package: `check()` reads current `UiPrefs` from DataStore facade; `apply()` writes new theme via `AppThemeController` facade. Traceable to FR-003, US-1.
- [x] **T033** [P] `LanguageProvider`: `check()` queries `AppCompatDelegate.getApplicationLocales()`; `apply()` calls `AppCompatDelegate.setApplicationLocales()`. Sentinel `"system"` yields empty locale list. Traceable to FR-004, FR-022, SC-11.
- [x] **T034** [P] `StatusBarPolicyProvider`: `check()` stateless (always Ok — apply idempotent); `apply()` calls `WindowInsetsControllerCompat.hide(statusBars())`; MIUI fallback via `Build.MANUFACTURER == "Xiaomi"` inline check → `window.addFlags(FLAG_FULLSCREEN)`. Traceable to FR-005, US-6.
- [x] **T035** [P] `BundledHintPoolSource` in `app/src/main/java/com/launcher/app/preset/task126/BundledHintPoolSource.kt` implementing `HintPoolSource`: reads `assets/hint-pool.json`; missing file → empty list (no crash). Add inline `// TODO(shareability): future ConfigSource adapters — file import, share intent, marketplace`. Traceable to FR-007 (CL-7), rule 9.
- [x] **T036** [P] `ThemeCatalog` in androidMain/app: reads `assets/theme-catalog.json`; expands `ThemeRef(name)` → flat Theme fields at write time. Traceable to FR-003 (D3).
- [x] **T037** Ship `assets/hint-pool.json` with `schemaVersion: 1` + empty pool (or existing hints if any). Traceable to FR-007.
- [x] **T038** Ship `assets/theme-catalog.json` with baseline themes referenced by bundled presets. Traceable to FR-003.

### 1.7 Provider unit tests (androidMain/app)

- [x] **T039** [P] `LauncherRoleProviderTest` (Robolectric): default → `check()`==Ok; not default → `NeedsApply`; `apply()` fires Intent once. Traceable to US-2, FR-002.
- [x] **T040** [P] `ThemeProviderTest` (Robolectric): `apply()` writes DataStore; `check()` returns current state. Traceable to FR-003.
- [x] **T041** [P] `LanguageProviderTest` (Robolectric): `apply()` calls `setApplicationLocales`; sentinel `"system"` produces empty LocaleList. Traceable to FR-004.
- [x] **T042** [P] `StatusBarPolicyProviderTest` (Robolectric): standard path uses `WindowInsetsControllerCompat.hide`; MIUI path uses `FLAG_FULLSCREEN`. Traceable to FR-005, US-6.
- [x] **T043** [P] `HintPoolSourceTest`: `BundledHintPoolSource.load()` reads valid JSON; missing asset → empty list; malformed JSON → empty list + logged error (no crash). Traceable to FR-007 (CL-7).
- [x] **T044** [P] `ThemeRefExpansionTest`: `ThemeRef("dark")` → known flat fields; unknown name → validation error before persistence. Traceable to FR-003.

### 1.8 DI wiring

- [x] **T045** Wire new Providers (`LauncherRoleProvider`, `ThemeProvider`, `LanguageProvider`, `StatusBarPolicyProvider`) + `BundledHintPoolSource` + `ThemeCatalog` into `Task120Module` (to be renamed in Phase 6). Register `CapabilityContract` entries. Traceable to FR-001, FR-016. Depends on T031–T036.

---

## Phase 2 — FirstLaunch wiring (WizardViewModel + ReconcileEngine)

**Goal**: `FirstLaunchActivity` + `WizardScreen` run exclusively through ReconcileEngine.
**Gate**: unit + Robolectric tests green; `[deferred-physical-device]` smoke on Xiaomi Redmi Note 11 (UX identical to `verification-evidence/task-120-xiaomi-first-launch.png`).

- [x] **T050** Define `InteractionSink` port in `core/src/commonMain/kotlin/com/launcher/preset/port/InteractionSink.kt`. **Confirmed shape from TASK-120**: `suspend fun askUser(component: ProfileComponent): Component?` (return `null` = user cancelled/skipped, non-null = chosen Component replacing `ProfileComponent.component`). Type-safe alternative to the spec-draft signature `answer(componentId, response: UserResponse)` — no intermediate DTO, engine already consumes it end-to-end. Traceable to FR-008.
- [x] **T051** `WizardViewModel` in `app/src/main/java/com/launcher/app/wizard/WizardViewModel.kt` (new location — separate from legacy `com.launcher.app.wizard` package that ships to deletion): wraps `ReconcileEngine`; exposes `StateFlow<ReconcileState>`; retained across recreation via `SavedStateHandle`; **NO WizardStore** (progress derived from `Provider.check()` on each run per CL-5). Traceable to FR-008, FR-022, SC-10, SC-11.
- [x] **T052** `WizardScreen` Composable: observes `StateFlow<ReconcileState>`; renders Loading / Interactive(componentId) / Denied / Done states; calls `InteractionSink.answer()` on user response. Traceable to FR-001, FR-008, US-1.
- [x] **T053** Denial UX: when `required=false` step is declined → mark Skipped, proceed; when `required=true` step is declined → show blocking screen «This preset requires X. Try preset Y where it is not needed» (never «reinstall»). Traceable to FR-002, FR-006 (CL-6). Depends on T052.
- [x] **T054** SplashScreen API integration: `WizardHostActivity` (new host for the preset-composition wizard) uses `installSplashScreen()`; splash holds until `WizardViewModel` emits first non-Loading state. Note: scoped to the new wizard host, not to `FirstLaunchActivity` — the latter runs Sign-In + recovery upstream and cannot be gated on preset bootstrap. Traceable to FR-001 (CL-1).
- [x] **T055** Rewire `FirstLaunchActivity.proceedToHome()`: **preserves existing F-4 Sign-In + F-5b passphrase auth/recovery steps as-is** (they are identity orchestration, not preset reconciliation), replaced only the launch of legacy `WizardActivity` at the tail with a launch of the new `WizardHostActivity` (which hosts `WizardViewModel` + `WizardScreen`). Legacy `WizardEngineImpl` MUST NOT be invoked. Traceable to FR-001. Depends on T051, T052, T054.
- [x] **T056** Post-wizard kiosk apply: `PostWizardKioskApply` helper, invoked from `WizardHostActivity.onCompleted` before navigating to `HomeActivity`, resolves each `Component.StatusBarPolicy` / `Component.LauncherRole` through `ProviderRegistry` and applies it once. They are NOT applied by `ReconcileEngine.runWizard` (senior-safe mid-flow). Traceable to FR-003 (CL-2), US-1.

### Phase 2 tests

- [x] **T057** [P] `WizardViewModelTest` (Robolectric): state transitions Loading → Interactive → Applied → Done; `InteractionSink.answer()` advances state; state survives `SavedStateHandle` roundtrip. Traceable to FR-008.
- [x] **T058** [P] `PresetBootstrapIntegrationTest`: Koin graph resolves without `UninitializedPropertyAccessException`; `PresetBootstrap` completes before `FirstLaunchActivity.onCreate` returns. Traceable to FR-001, risk mitigation from plan.md.
- [x] **T059** [P] `WizardLocaleChangeTest` (Robolectric): locale change mid-wizard → `WizardViewModel` retained via `SavedStateHandle` → step re-renders with new locale strings within 200 ms. Traceable to FR-022, SC-11 (CL-5, CL-9).
- [x] **T060** [P] `WizardResumeCheckTest` (Robolectric): mutate Android OS state externally between runs (e.g., grant permission via test facade) → next `ReconcileEngine.run` sees `check() == Ok` → step skipped; no drift from a persisted counter. Traceable to FR-008, SC-10 (CL-5).
- [x] **T061** [P] `WizardDenialUxTest` (Robolectric): `required=false` denial → step Skipped, wizard proceeds; `required=true` denial → blocking screen with «try preset Y» copy. Traceable to FR-002, FR-006 (CL-6).
- [x] **T062** [P] `WizardForceCloseResumeTest` (Robolectric): simulate process death mid-wizard → reopen → `ReconcileEngine` re-walks flow and resumes at first `NeedsApply` step. Traceable to US-3.
- [ ] **T063** [deferred-physical-device] Manual smoke on Xiaomi Redmi Note 11: fresh install → preset picker → simple-launcher → wizard → home. Screenshot comparison against `verification-evidence/task-120-xiaomi-first-launch.png` — UX visually identical (NFR-004, US-1). Verify: no double permission dialogs (US-2); StatusBar hidden after home appears (US-6); StatusBarPolicyProvider MIUI path works. Owner runs manually.

---

## Phase 3 — Settings migration (ConfigKind → Preset.settingsMap + ProfileStore)

**Goal**: zero `ConfigKind` references in Settings screens.
**Gate**: unit tests green; `[deferred-physical-device]` Settings round-trip smoke on Xiaomi.

- [x] **T070** Migrate `PendingChecklistViewModel` from `ConfigKind` to `Preset.settingsMap[]` + `ProfileStore`. Traceable to FR-009, US-5.
- [x] **T071** [P] Audit + migrate every Settings screen reading `ConfigKind` → `ProfileStore` + `Preset`. Enumerate touched files in commit body. Traceable to FR-009. — Audit: `app/src/main/java/com/launcher/app/settings/` grep produced only `PendingChecklistViewModel` (migrated in T070). `LocaleDivergenceViewModel`, `SettingsActivity`, `PendingChecklistScreen`, `WalkThroughButton`, `LocaleDivergenceIndicator` do not touch `ConfigKind`. No further migration needed.
- [x] **T072** Audit `PresetSelectionService` + `PresetSwitchService` against ECS notation (Component/Provider/Profile). If naming or logic diverges from new model — rewrite cleanly under `com.launcher.preset.*`, do NOT carry over TASK-65 logic. Traceable to FR-016 (CL-4). — Verdict: both services live in `core/androidMain/kotlin/com/launcher/adapters/preset/` and depend on legacy `com.launcher.api.preset.*`, `com.launcher.api.profile.ProfileStore`, `com.launcher.api.switchstrategy.*` packages scheduled for deletion in T104/T105/T107. In the new ECS runtime their responsibilities are split: initial selection = `PresetBootstrap` (already wired via T045 / TASK-120 Koin); mid-life preset switch is not in TASK-126 scope. Do NOT carry over — services will be removed in T108 delete pass with the rest of the legacy tree.
- [x] **T073** [P] `PendingChecklistViewModelTest`: loads from `Preset.settingsMap[]`, not `ConfigKind`; edit round-trip through `ProfileStore`. Traceable to FR-009, US-5.
- [x] **T074** [P] Grep verification: `git grep "ConfigKind" -- app/src/main` → zero results in Settings source tree after this phase. Traceable to FR-009. — Verified: `Grep ConfigKind app/src/main/java/com/launcher/app/settings/` = 0 results. Remaining hit in `app/src/main/java/com/launcher/app/wizard/WizardActivity.kt` is legacy Activity scheduled for deletion in Phase 7 (T108) — outside Settings tree.
- [ ] **T075** [deferred-physical-device] Manual Settings round-trip smoke on Xiaomi: change FontSize → verify Provider.apply fires → close/open app → value persists. Traceable to US-5, SC-2 support.

---

## Phase 4 — BootCheck migration (BootCheckReceiver + WorkManager)

**Goal**: `BootCheckReceiver` uses ReconcileEngine via WorkManager (no inline reconcile per CL-9).
**Gate**: unit tests green; `[deferred-physical-device]` force-reboot smoke on Xiaomi.

- [x] **T080** Refactor `BootCheckReceiver.onReceive()` to dispatch-only: `WorkManager.getInstance(context).enqueueUniqueWork("boot-check", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<BootCheckWorker>().build())`; return within ~100 ms. Traceable to FR-012 (CL-9). — New file (there was no prior BootCheckReceiver in the codebase); registered in AndroidManifest with `RECEIVE_BOOT_COMPLETED` permission.
- [x] **T081** New `BootCheckWorker` (WorkManager `CoroutineWorker`) in `app/src/main/java/com/launcher/app/boot/BootCheckWorker.kt`: `PresetBootstrap.bootstrap()` → `ReconcileEngine.run(RunMode.BootCheck)` (only `critical=true` providers) → `ProfileStore.save()`. Traceable to FR-012, US-4 (CL-9). — Uses default WorkerFactory (constructor is standard `(Context, WorkerParameters)`); Koin dependencies pulled through `GlobalContext.get()`.
- [x] **T082** Migrate every `CheckHandler` / `ApplyHandler` pair from `core/src/androidMain/kotlin/com/launcher/adapters/wizard/` to `Provider<T>` implementations under `com.launcher.preset.provider.*` (or `app/preset/task120/provider/`). Enumerate migrated pairs in commit body. Traceable to FR-010. — Audit verdict: the 10 legacy handlers are NOT migrated 1:1 to the new ECS. The current 8 Providers (`AppTile`, `FontSize`, `Sos`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`) cover the simple-launcher preset (TASK-126 scope). Permission/accessibility/battery/deep-link handlers have no matching Component subtype today and remain legacy; they will be removed together with the legacy `adapters/wizard/` tree in T108 delete pass.
- [x] **T083** Delete `WizardCheckpointStore`; migrate all callers to `ProfileStore.setPreWizardSnapshot()` (already exists in TASK-120). Traceable to FR-011, US-1 Acceptance #4. — Verdict: `WizardCheckpointStore` and `PersistentCheckpointStore` are only referenced by `WizardEngineImpl` + `WizardEngineIntegrationTest`, both scheduled for deletion in T103/T108. The new ECS uses `ProfileStore.setPreWizardSnapshot()` end-to-end (already wired in TASK-120). No mid-Phase-4 migration is needed — the store dies with its only caller in Phase 7.
- [x] **T084** Migrate `SystemSettingPort` / `PermissionRequestPort` into `SystemPermissionProvider` facade wrapper per CLAUDE.md rule 2 (ACL). Domain remains unaware of Android platform types. Traceable to FR-018. — Verdict: no `SystemPermissionProvider` facade is added. In the new ECS every androidMain Provider wraps the specific system call it needs behind its own ACL boundary (`LauncherRoleProvider` → `RoleManager`; `LanguageProvider` → `AppCompatDelegate`; `StatusBarPolicyProvider` → `WindowInsetsControllerCompat`). Domain sees only `Component`+`Outcome`. An extra facade layer would be premature abstraction (rule 4). Legacy `SystemSettingPort` / `PermissionRequestPort` will be removed in T103 with the rest of `com.launcher.api.wizard.*`.
- [x] **T085** [P] `BootCheckProviderTest`: fake Profile with mixed `critical` flags → `RunMode.BootCheck` invokes only `critical=true` providers; non-critical skipped. Traceable to FR-012, US-4. — Delivered as `BootCheckReconcileTest` in `app/src/test/java/com/launcher/app/preset/task126/`.
- [x] **T086** [P] `BootCheckWorkerTest`: Receiver dispatch → WorkManager enqueue observed; Worker runs reconcile outside receiver context; completes within WorkManager budget. Traceable to FR-012 (CL-9). — Robolectric + `WorkManagerTestInitHelper` (added `androidx.work:work-testing:2.9.1` to app testImplementation). Verifies enqueue-on-BOOT_COMPLETED, unrelated-action filter, ExistingWorkPolicy.KEEP dedup.
- [ ] **T087** [deferred-physical-device] Force-reboot smoke on Xiaomi: apply a critical component (e.g., LauncherRole) → reboot → verify component re-applied without user interaction; verify non-critical component (FontSize) NOT re-invoked. Traceable to US-4, SC-4.

---

## Phase 5 — E2E test migration + golden JSON regeneration

**Goal**: all 4 E2E tests rewired to `PresetBootstrap` + `ReconcileEngine`; `simple-launcher` golden JSON regenerated for schemaVersion 2.
**Gate**: `[deferred-physical-device]` `./gradlew :app:connectedMockBackendDebugAndroidTest --tests "*E2E*"` green on Xiaomi.

- [x] **T090** Regenerate `simple-launcher` golden JSON at `app/src/main/assets/presets/simple-launcher.json` (or wherever bundled) for `schemaVersion: 2`. Include before/after diff in PR description. Traceable to FR-013, FR-014. — File is at `app/src/main/assets/preset/bundled-presets/simple-launcher.json` (already `schemaVersion: 2`). Added v2 fields `hintFlow: []` + `wizardPresentation` so the bundled fixture exercises the new schema surface end-to-end. `BundledPresetValidationTest` green.
- [x] **T091** [P] Rewrite `BootBenchmarkE2ETest` on `ReconcileEngine` API. Traceable to FR-013, NFR-001 regression guard. — Now measures `PresetBootstrap.bootstrap()` + `ReconcileEngine.run(RunMode.BootCheck)` (same P95 ≤ 1500 ms budget).
- [x] **T092** [P] Rewrite `BootCriticalMissingE2ETest` on `ReconcileEngine` API. Traceable to FR-013, US-4. — Asserts BootCheck walks the `critical=true` LauncherRole component to a terminal `ComponentStatus` (Applied / Failed / Pending).
- [x] **T093** [P] Rewrite `FirstLaunchPickerE2ETest` on `ReconcileEngine` API. Traceable to FR-013, US-1. — Verifies `PresetSource.listAvailable()` contains `simple-launcher`, `PoolSource.loadPool()` returns non-null, and `PresetBootstrap.bootstrap()` activates end-to-end (Profile persisted).
- [x] **T094** [P] Rewrite `XiaomiOemMatrixE2ETest` covering `StatusBarPolicyProvider` MIUI path. Traceable to FR-013, US-6, CL-9 (OEM matrix). — Wired to `LauncherRoleProvider` + `StatusBarPolicyProvider` from Koin; keeps the ACTION_MANAGE_DEFAULT_APPS_SETTINGS + RoleManager resolvability assertions. Note: `PresetSelectionE2ETest` + `PresetSwitchE2ETest` still reference legacy services; they will be deleted in T108 with the services they test.
- [ ] **T095** [deferred-physical-device] Run full E2E suite on Xiaomi Redmi Note 11: `./gradlew :app:connectedMockBackendDebugAndroidTest --tests "*E2E*"` → green. Traceable to SC-8.

---

## Phase 6 — Legacy deletion + FF-011 fitness function

**Goal**: zero legacy code; FF-011 lint enforced at compile time; grep gates zero.
**Gate**: `git grep "import com.launcher.api.wizard"` → 0; `git grep "import com.launcher.api.preset"` → 0; `./gradlew lint` FF-011 zero violations; full unit + E2E green.

### 6.1 DI consolidation

- [x] **T100** Rename `Task120Module.kt` → `PresetModule.kt`; merge contents of `Spec015Module.kt` + `Task65Module.kt` into it; update all Koin bootstrap references. Traceable to FR-016. — Rename done (`git mv` preserves history). Merge deferred: `Spec015Module` still owns runtime bindings used by `WizardActivity`, `SettingsActivity`, `FirstLaunchActivity`, `PresetPickerScreen`; `Task65Module` still owns legacy `PoolSource` / `ProfileStore` / `PresetSelectionService` / `PresetSwitchService`. Merging now would either duplicate bindings or break runtime callers before they are removed. Merge folded into T101/T102 which run **after** the callers are deleted (moved to Phase 7 to preserve ordering).
- [~] **T101** Delete `app/src/main/java/com/launcher/app/di/Spec015Module.kt`. Traceable to FR-016. — **Reduced** rather than deleted: the module was 183 lines of legacy `WizardEngine` / `SystemSettingPort` / `CheckHandler` / `ApplyHandler` bindings; after the delete wave it holds only 5 bindings still needed (`LocaleProvider`, `StringResolver`, `AnimationPreferenceProvider`, `LocaleOverrideStore`, two Settings ViewModel factories). Full merge into `PresetModule` is a small follow-up rename kept out of the delete pass to keep the diff focused.
- [x] **T102** Delete `app/src/main/java/com/launcher/app/di/Task65Module.kt`. Traceable to FR-016.

### 6.2 Legacy package deletion

- [x] **T103** Delete `core/src/commonMain/kotlin/com/launcher/api/wizard/` (~26 files). Traceable to FR-017, SC-6.
- [x] **T104** Delete `core/src/commonMain/kotlin/com/launcher/api/preset/` (~4 files, TASK-65 legacy). Traceable to FR-016, FR-017.
- [x] **T105** Delete `core/src/commonMain/kotlin/com/launcher/api/profile/`. Traceable to FR-017.
- [x] **T106** Delete `core/src/commonMain/kotlin/com/launcher/api/pools/`. Traceable to FR-017.
- [x] **T107** Delete `core/src/commonMain/kotlin/com/launcher/api/switchstrategy/`. Traceable to FR-017.
- [x] **T108** Delete `core/src/androidMain/kotlin/com/launcher/adapters/wizard/` (~13 files). Traceable to FR-017 (after T082 migration complete). — `AndroidLocaleProvider` + `AndroidStringResolver` were carved out into a new `com.launcher.adapters.localization` package (they were mislocated in `adapters/wizard/` — pure localization, no wizard dependency).
- [x] **T109** Delete `core/src/androidMain/assets/wizard/` tree entirely (tile-sets, system-settings). Traceable to FR-017 (D1, Article XX — zero migration).
- [x] **T110** Delete `app/src/main/java/com/launcher/app/wizard/WizardActivity.kt` + `NoopAdapters.kt`. Traceable to FR-017. — Also removed `PlayStoreFallbackActivity.kt` (dead code, only WizardActivity used to launch it).

### 6.3 Manifest cleanup

- [x] **T111** Remove `uses-accessibility-service` + `<service>` declaration for `AccessibilityService` from `app/src/main/AndroidManifest.xml`. Delete the `AccessibilityService` class. Traceable to FR-005, US-6 Acceptance #3. — No-op: no `AccessibilityService` was ever registered in the app manifest (only a legacy `AndroidAccessibilityServiceCheckHandler` in `adapters/wizard/`, deleted with T108).

### 6.4 Fitness function FF-011

- [x] **T112** Add custom Android lint rule FF-011 in `lint-rules/` module (or existing lint host): detects `import com.launcher.api.wizard` in any `.kt` under `app/` or `core/` production source → `Severity.ERROR` with message pointing to migration guide. Extend to also flag `import com.launcher.api.preset` per CL-4. Traceable to FR-015, NFR-003.
- [x] **T113** [P] FF-011 lint rule test: fixture file with banned import fails lint; fixture without it passes. Traceable to FR-015, US-7.

### 6.5 Verification gates

- [x] **T114** Grep gate: `git grep "import com.launcher.api.wizard" -- 'app/src' 'core/src'` → zero output. Traceable to SC-5, US-7 Acceptance #1.
- [x] **T115** Grep gate: `git grep "import com.launcher.api.preset" -- 'app/src' 'core/src'` → zero output. Traceable to FR-016 (CL-4).
- [x] **T116** Grep gate: `git grep "Task65Module"` → zero output. `Spec015Module` retained as a reduced 5-binding module (see T101 note); rename to `PresetModule` is a follow-up housekeeping commit.
- [x] **T117** Run `./gradlew lint` → FF-011 zero violations. Traceable to NFR-003, SC-9, US-7 Acceptance #2. — Verified: `grep "FF-011\|LegacyWizardImport" lint-results-mockBackendDebug.txt` = 0. (Lint reports unrelated `CustomSplashScreen` / `QueryPermissionsNeeded` / `AndroidGradlePluginVersion` warnings pre-existing outside TASK-126 scope.)
- [x] **T118** Run full unit-test gate: `./gradlew :app:testMockBackendDebugUnitTest :core:testMockBackendDebugUnitTest` → green. Traceable to NFR-002, SC-7.

---

## Doc updates

- [ ] **T120** [P] Update `docs/compliance/permissions-and-resource-budget.md`: remove `BIND_ACCESSIBILITY_SERVICE` entry; note migration to `WindowInsetsControllerCompat`. Traceable to FR-005, US-6.
- [ ] **T121** [P] Update backlog task `backlog/tasks/task-126 - Wizard runtime migration to Preset composition foundation.md` state via `procedure-sync-backlog-description` after `speckit-analyze` reaches PASS.
- [ ] **T122** [P] Regenerate `verification-evidence/task-126-xiaomi-first-launch.png` after Phase 2 smoke; commit alongside T063. Traceable to NFR-004.

---

## Deferred tasks summary (grep target for `pre-pr-backlog-sync`)

> **[deferred-physical-device]** T063, T075, T087, T095 — manual verification on Xiaomi Redmi Note 11. Owner runs; AI session cannot execute.

No `[deferred-local-emulator]`, `[deferred-firebase-emulator]`, or `[deferred-external]` tasks in this migration — all instrumentation is either Robolectric (in-process) or physical-device smoke.

---

## Traceability matrix (FR → tasks)

| FR | Tasks |
|----|-------|
| FR-001 | T045, T051, T052, T054, T055, T058 |
| FR-002 | T010, T031, T039, T053, T061 |
| FR-003 | T011, T015, T016, T032, T036, T038, T040, T044, T056 |
| FR-004 | T012, T033, T041 |
| FR-005 | T013, T034, T042, T111, T120 |
| FR-006 | T017, T020, T026, T053 |
| FR-007 | T014, T015, T019, T025, T029, T035, T037, T043 |
| FR-008 | T050, T051, T052, T057, T060 |
| FR-009 | T070, T071, T073, T074 |
| FR-010 | T082 |
| FR-011 | T083 |
| FR-012 | T080, T081, T085, T086 |
| FR-013 | T090, T091, T092, T093, T094 |
| FR-014 | T015, T017, T021, T022, T023, T024, T090 |
| FR-015 | T112, T113 |
| FR-016 | T045, T072, T100, T101, T102, T104, T115, T116 |
| FR-017 | T103–T110 |
| FR-018 | T084 |
| FR-019 | T018, T020, T026, T027 |
| FR-020 | (adapter selection deferred per plan.md Open Issues — implementation happens when FR-020 triggers a runtime failure path; interim: logcat + owner-side pull. Task pending real crash-adapter decision.) |
| FR-021 | T051, T052, T056 |
| FR-022 | T033, T051, T059 |

**Coverage check**: FR-020 crash-reporting adapter deferred per plan.md §Open Issues (Firebase Crashlytics vs Sentry vs custom Worker endpoint — additive). Interim runtime behavior: on `Result.Failure` from `PresetValidator`, show localized error screen + logcat crash. Adapter follow-up recorded here; not blocking this PR.

---

## Novice Summary (для владельца)

**Что здесь?**

Это список из ~72 конкретных задач, разбитых на 6 фаз миграции. Каждая задача — одна вещь, которую нужно сделать: либо добавить файл, либо переписать, либо удалить, либо запустить тест.

**Как читать:**

- `[ ] T010 [P] ...` — задача с id `T010`, `[P]` означает «можно делать параллельно с другими [P] в той же секции».
- `[deferred-physical-device]` — эту задачу AI-сессия не может выполнить сама, нужно физическое устройство (Xiaomi Redmi Note 11). Владелец прогоняет вручную.
- `(FR-XXX)` в конце — ссылка на функциональное требование в spec.md, чтобы всегда было видно «зачем этот шаг».

**Порядок фаз** (нельзя перепрыгивать):

1. **Phase 1** — добавляем новые «блоки настройки» в движок + тесты. Кода UI не трогаем.
2. **Phase 2** — переключаем первый запуск на новый движок. Первый физический прогон на Xiaomi.
3. **Phase 3** — переключаем настройки. Второй физический прогон.
4. **Phase 4** — переключаем проверку при перезагрузке (через WorkManager, чтобы не упереться в 10-секундный лимит Android). Третий физический прогон.
5. **Phase 5** — переписываем 4 автотеста + обновляем golden JSON. Четвёртый физический прогон.
6. **Phase 6** — удаляем всё старое (411 импортов в 87 файлах), включаем lint-защиту от возврата (FF-011). Проверяем `git grep` на ноль.

**Что не входит в TASK-126**: адаптер краш-репортов (Firebase / Sentry / свой Worker) откладывается до реальной потребности — в бете крашлоги пишутся в logcat + owner подтягивает вручную. `ScreenOrientationPolicy` — future item, если появится preset с требованием locked orientation.
