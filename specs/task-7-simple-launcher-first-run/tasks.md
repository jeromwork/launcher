# Tasks: TASK-7 — Simple Launcher Profile (S-1)

**Input**: Design documents from `/specs/task-7-simple-launcher-first-run/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: included per spec.md FR-009 / FR-010 / FR-012 / FR-020 (roundtrip + backward-compat + fitness functions explicit).

**Organization**: tasks grouped by **plan.md phase** rather than user story — wire format work, engine extensions, and handler registry must precede UI work; cross-cutting changes touch multiple US. Each task includes a `[USn/multi]` trace tag and FR/SC references.

## Format: `[ID] [P?] [USn] Description (trace)`

- **[P]**: parallel-safe (different files, no dependencies on prior task in this phase).
- **[USn]**: which user story this task primarily serves; `[multi]` if cross-cutting.
- **Trace**: `(FR-NNN, SC-NNN, Plan Phase N)` non-optional.

---

## Required-task gates (verification before execution)

Per `speckit-tasks` Step 3:

- [x] **Roundtrip tests** for every contract in `contracts/`: T007 (pool v2), T044 (production assets).
- [x] **Backward-compat tests** for every contract: T008 (v1 → v2 reader).
- [x] **Fake adapters** for every new port: T018 (FakeCheckHandler / FakeApplyHandler).
- [x] **Fitness functions** for module / boundary / domain isolation: T009 (per-test fitness), T059 (Task7ArchitectureTest aggregated).
- [x] **Deletion + grep verification**: no files deleted in TASK-7 (modifications only); N/A.
- [x] **Doc updates**: T067 (project-backlog.md), no permissions doc changes (no new permissions).
- [x] **UI smoke / screenshot**: T060 [deferred-local-emulator].
- [x] **Perf-checkpoint**: T061 (E2E test measures cold-start + home-render times per SC-001/SC-002).

---

## Phase 0: Wire format v2 + roundtrip / backward-compat tests

**Goal**: Establish pool schema v2 with declarative `CheckSpec` / `ApplySpec` types. Backward-compat read for v1 entries.

**Independent test**: `./gradlew :core:test --tests *CheckSpec* --tests *ApplySpec* --tests *Pool*` all green.

- [x] **T001** [P] [multi] Add `CheckSpec` sealed class in `core/src/commonMain/kotlin/com/launcher/api/wizard/data/CheckSpec.kt` with `@OptIn(ExperimentalSerializationApi::class) @JsonClassDiscriminator("kind") @Serializable` annotations; 5 variants: AndroidRole, AndroidPermission, AndroidSpecialPermission, AndroidAccessibilityService, AndroidPackageHome (data-model.md §1.1). (FR-007, Plan Phase 0)
- [x] **T002** [P] [multi] Add `ApplySpec` sealed class in `core/src/commonMain/kotlin/com/launcher/api/wizard/data/ApplySpec.kt`; 4 variants: StandardPermissionRequest, AndroidRoleRequest, SettingsDeepLink, InAppOnly (data-model.md §1.2). (FR-008, Plan Phase 0)
- [x] **T003** [multi] Extend `SystemSettingEntry` in `core/src/commonMain/kotlin/com/launcher/api/wizard/data/SystemSettingsPool.kt` with optional `check: CheckSpec? = null` and `apply: ApplySpec? = null` fields. Verify existing v1 fields untouched (contracts/system-settings-pool-v2.md §3.2). Requires: T001, T002. (FR-005, FR-006, Plan Phase 0)
- [x] **T004** [multi] Implement schemaVersion 2 deserializer path in pool parser; entries with v1 shape (no `check`/`apply`) deserialize with `null` for those fields (graceful, contracts/system-settings-pool-v2.md §3, §6). Requires: T003. (FR-005, FR-010 v1 fallback, Plan Phase 0)
- [x] **T005** [P] [multi] `CheckSpecRoundtripTest` in `core/src/commonTest/kotlin/com/launcher/api/wizard/CheckSpecRoundtripTest.kt`: for each of 5 variants, serialize → deserialize → assertEquals (contracts/system-settings-pool-v2.md §7). Requires: T001. (FR-009, SC-008, Plan Phase 0)
- [x] **T006** [P] [multi] `ApplySpecRoundtripTest` analogous for 4 ApplySpec variants. Requires: T002. (FR-009, SC-008, Plan Phase 0)
- [x] **T007** [P] [multi] `PoolSchemaV2RoundtripTest` in `core/src/commonTest/kotlin/com/launcher/api/wizard/PoolSchemaV2RoundtripTest.kt`: full v2 pool document with all 6 entries containing `check`/`apply` blocks; serialize → deserialize → assertEquals (contracts/system-settings-pool-v2.md §7). Requires: T003. (FR-009, SC-008, Plan Phase 0)
- [x] **T008** [P] [multi] `PoolSchemaBackwardCompatTest` in `core/src/commonTest/kotlin/com/launcher/api/wizard/PoolSchemaBackwardCompatTest.kt`: fixture v1 pool (no `check`/`apply` blocks) read via v2 reader → assert `check == null && apply == null`; assert v1 fields preserved (contracts/system-settings-pool-v2.md §7). Requires: T004. (FR-005, SC-009, Plan Phase 0)
- [x] **T009** [P] [multi] Initial Konsist fitness in `core/src/androidUnitTest/kotlin/com/launcher/architecture/Task7ArchitectureTest.kt` (T7-004 only at this phase — will grow with phases): assert `CheckSpec.kt` and `ApplySpec.kt` do NOT import `android.*` types. Requires: T001, T002. (FR-012, FR-024, FR-030, Plan Phase 0)

**Checkpoint Phase 0**: pool v2 wire format ready; backward-compat verified; sealed types isolated from Android.

---

## Phase 1: Engine `computePending` (state-of-device check)

**Goal**: Replace linear traversal in `WizardEngineImpl.run()` with config-check master pattern per Article VII §14.

**Independent test**: `ComputePendingTest` scenarios green with `FakeSystemSettingPort`.

- [x] **T010** [multi/US2] Add `suspend fun computePending(manifest: WizardManifest): List<StepEntry>` to `WizardEngine` interface in `core/src/commonMain/kotlin/com/launcher/api/wizard/WizardEngine.kt`. (FR-013, Plan Phase 1)
- [x] **T011** [P] [US2] Add `hasValueFor(refId: String): Boolean` helper to `UserPreferences` data class in `core/src/commonMain/kotlin/com/launcher/api/wizard/UserPreferences.kt`. Hardcoded keys for theme/fontScale/language; inline TODO for future generalization (data-model.md §2.3). (FR-013, Plan Phase 1)
- [x] **T012** [US2] Implement `WizardEngineImpl.computePending(manifest)` in `core/src/commonMain/kotlin/com/launcher/ui/wizard/WizardEngineImpl.kt`: iterate `orderedSteps(manifest)`, for each StepEntry dispatch by stepType: SystemSetting → `systemSettingPort.status(refId) != Applied`, UIChoice → `!prefs.hasValueFor(refId)`, TutorialHint → `!dismissedHintsStore.isDismissed(refId)`, Custom → always include. Requires: T010, T011. (FR-013, Plan Phase 1)
- [x] **T013** [US2] Modify `WizardEngineImpl.run(manifest)` to call `computePending(manifest)` as pre-flight: if pending empty → return Completed immediately; else traverse only pending. Requires: T012. (FR-014, Plan Phase 1)
- [x] **T014** [P] [multi] Deprecate `WizardEngine.diffPending(savedCompletedManifest, currentManifest)`: add `@Deprecated("Replaced by computePending() — see Article VII §14")` annotation; keep impl for backward compat. (FR-016, Plan Phase 1)
- [x] **T015** [US2] `ComputePendingTest` in `core/src/commonTest/kotlin/com/launcher/api/wizard/ComputePendingTest.kt`: 4 scenarios with FakeSystemSettingPort — (A) nothing applied → pending = all steps; (B) ROLE_HOME applied → pending excludes that step; (C) all applied → pending = []; (D) Indeterminate → step included (graceful). Requires: T012, T013. (FR-013, SC-003, SC-012, Plan Phase 1)

**Checkpoint Phase 1**: engine.run() routes through computePending; state-of-device skipping applied steps verified.

---

## Phase 2: CheckHandler / ApplyHandler ports + handler registry + cache

**Goal**: Replace hardcoded `when (settingId)` dispatch in `AndroidSystemSettingAdapter` with handler registry keyed on CheckSpec/ApplySpec variant class. Add TTL cache.

**Independent test**: handler unit tests + `CacheInvalidationTest` + `AndroidSystemSettingAdapterTest` green.

### Domain ports + fakes

- [x] **T016** [P] [multi] Add `CheckHandler` port: `interface CheckHandler { suspend fun check(spec: CheckSpec): SettingStatus }` in `core/src/commonMain/kotlin/com/launcher/api/wizard/handlers/CheckHandler.kt` (data-model.md §1.3). (FR-009, Plan Phase 2)
- [x] **T017** [P] [multi] Add `ApplyHandler` port: `interface ApplyHandler { suspend fun apply(spec: ApplySpec): ApplyResult }` in `core/src/commonMain/kotlin/com/launcher/api/wizard/handlers/ApplyHandler.kt` (data-model.md §1.4). (FR-009, Plan Phase 2)
- [x] **T018** [P] [multi] `FakeCheckHandler` and `FakeApplyHandler` in `core/src/commonTest/kotlin/com/launcher/fake/wizard/`. Constructed with `Map<CheckSpec, SettingStatus>` / `Map<ApplySpec, ApplyResult>` for symbol replay. Required by CLAUDE.md rule §6 mock-first. Requires: T016, T017. (FR-009, Plan Phase 2)

### Cache infrastructure

- [x] **T019** [P] [multi] `SettingStatusCache` class in `core/src/androidMain/kotlin/com/launcher/adapters/wizard/SettingStatusCache.kt` per data-model.md §4.1: `Map<settingId, Pair<SettingStatus, Instant>>` with 30s TTL, `get/put/invalidate/invalidateAll` methods, injected Clock. (FR-021, Plan Phase 2)
- [x] **T020** [P] [multi] `CacheInvalidatingLifecycleObserver` in `core/src/androidMain/kotlin/com/launcher/adapters/wizard/CacheInvalidatingLifecycleObserver.kt` per data-model.md §4.2: `DefaultLifecycleObserver` calling `cache.invalidateAll()` on `onResume`. (FR-022, Plan Phase 2)
- [x] **T033** [multi] `CacheInvalidationTest` in `core/src/androidUnitTest/kotlin/com/launcher/adapters/wizard/CacheInvalidationTest.kt`: FakeClock + observe `get` returns cached value within TTL, returns null after TTL; lifecycle RESUMED invalidates whole cache. Requires: T019, T020. (FR-021, FR-022, Plan Phase 2)

### Android handler implementations (each parallel-safe; different file)

- [x] **T021** [P] [multi] `AndroidRoleCheckHandler` in `core/src/androidMain/kotlin/com/launcher/adapters/wizard/handlers/AndroidRoleCheckHandler.kt`: `RoleManager.isRoleHeld(spec.role)` on API 29+; `PackageManager.resolveActivity` fallback on API 26-28 (data-model.md §1.3). Requires: T016. (FR-009, Plan Phase 2)
- [x] **T022** [P] [multi] `AndroidPermissionCheckHandler` wrapping `PermissionRequestPort.isGranted(spec.permission)`. Requires: T016. (FR-009, Plan Phase 2)
- [x] **T023** [P] [multi] `AndroidSpecialPermissionCheckHandler`: dispatch by `spec.variant`; `ignore_battery_optimizations` → `PowerManager.isIgnoringBatteryOptimizations()`; unknown variants → `Indeterminate`. Requires: T016. (FR-009, Plan Phase 2)
- [x] **T024** [P] [multi] `AndroidAccessibilityServiceCheckHandler`: returns `Indeterminate` (SelfAttest path, OEM-unreliable programmatic detection). Requires: T016. (FR-009, Plan Phase 2)
- [x] **T025** [P] [multi] `AndroidPackageHomeCheckHandler`: `PackageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME))` + compare with `spec.packageName ?: context.packageName`. Requires: T016. (FR-009, Plan Phase 2)
- [x] **T026** [P] [multi] `AndroidStandardPermissionApplyHandler` wrapping `PermissionRequestPort.request(spec.permission)` → map to ApplyResult. Requires: T017. (FR-009, Plan Phase 2)
- [x] **T027** [P] [multi] `AndroidRoleApplyHandler`: `RoleManager.createRequestRoleIntent(spec.role)` + `context.startActivity()`. Returns `PromptShown`. Requires: T017. (FR-009, Plan Phase 2)
- [x] **T028** [P] [multi] `AndroidSettingsDeepLinkApplyHandler`: builds `Intent(spec.action)`, optionally `Uri.parse("package:${packageName}")` if `spec.packageScoped`. Requires: T017. (FR-009, Plan Phase 2)
- [x] **T029** [P] [multi] `AndroidInAppOnlyApplyHandler`: returns `PromptShown` (no-op apply). Requires: T017. (FR-009, Plan Phase 2)
- [x] **T032** [P] [multi] Per-handler unit tests in `core/src/androidUnitTest/kotlin/com/launcher/adapters/wizard/handlers/`: one test file per handler (T021..T029 = 9 handler tests). Use Robolectric / mock context where needed. Requires: corresponding handler tasks. (FR-009, Plan Phase 2) — *Trade-off: реализовано как единый `Task7HandlersTest.kt` с 10 `@Test`-методами вместо 9 отдельных файлов (контекст-экономия), coverage эквивалентен.*

### Adapter modification + DI wiring

- [x] **T030** [multi] Modify `AndroidSystemSettingAdapter` in `core/src/androidMain/kotlin/com/launcher/adapters/wizard/AndroidSystemSettingAdapter.kt`: inject `Map<KClass<out CheckSpec>, CheckHandler>` and `Map<KClass<out ApplySpec>, ApplyHandler>` registries + `SettingStatusCache`; in `status()`: check cache → if miss, lookup entry, if `entry.check != null` dispatch to handler matching spec class, else legacy `mechanism + settingId` path (v1 fallback); cache result; in `applyOrPrompt()`: if `entry.apply != null` dispatch to handler, invalidate cache for that settingId, else legacy path. Inline TODO at adapter site: `// TODO(multiplatform): IosSystemSettingAdapter — TASK-26 / TASK-29 — both ship as new adapters without changing engine, ports, or commonMain CheckSpec sealed class`. Requires: T019, T021-T029. (FR-010, FR-031, Plan Phase 2)
- [x] **T031** [multi] Koin DI wiring in `core/src/androidMain/kotlin/com/launcher/di/coreAndroidModule.kt` (or existing equivalent): register handler maps `Map<KClass<out CheckSpec>, CheckHandler>` and `Map<KClass<out ApplySpec>, ApplyHandler>` per data-model.md §1.5; bind `SettingStatusCache`; register `CacheInvalidatingLifecycleObserver` factory. Requires: T030. (FR-009, Plan Phase 2) — *wired in `app/src/main/java/com/launcher/app/di/Spec015Module.kt` (existing wizard module, not a new `coreAndroidModule.kt`)*

**Checkpoint Phase 2**: handler-based dispatch live in adapter; legacy v1 fallback retained; cache + invalidation working.

---

## Phase 3: Locale persistence + Settings indicator

**Goal**: Article III §7 stability enforcement — app-level locale override persists against system locale change.

**Independent test**: locale persistence integration test green; manual emulator smoke (deferred).

- [x] **T034** [US3] Modify `WizardActivity` in `app/src/main/java/com/launcher/app/wizard/WizardActivity.kt`: after `engine.run(manifest)` completes successfully, read `userPreferencesStore.current().languageOverride`; if non-null call `AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale.forLanguageTag(override)))`. (FR-017, Plan Phase 3)
- [x] **T035** [US3] Modify `LauncherApplication.onCreate()` in `app/src/main/java/com/launcher/app/LauncherApplication.kt`: read `userPreferencesStore.current().languageOverride` synchronously (via `runBlocking` consistent with existing project pattern); if non-null call `AppCompatDelegate.setApplicationLocales(...)`. (FR-018, Plan Phase 3)
- [x] **T036** [P] [US3] `LocaleDivergenceIndicator` composable in `app/src/main/java/com/launcher/app/settings/LocaleDivergenceIndicator.kt`: senior-safe styled Compose component; receives `LocaleDivergenceState`; renders "Язык приложения: X (системный Android: Y)" via StringResolver; hidden when `!state.diverges`. (FR-017a, Plan Phase 3)
- [x] **T037** [P] [US3] `LocaleDivergenceViewModel` in `app/src/main/java/com/launcher/app/settings/LocaleDivergenceViewModel.kt`: compares `userPreferencesStore.current().languageOverride` vs `localeProvider.systemLocaleTag()` (data-model.md §6.2). (FR-017a, Plan Phase 3)
- [x] **T039** [P] [multi] Extend `Task7ArchitectureTest.kt` with T7-005 Konsist fitness: `AppCompatDelegate` called only from `app/` or `core/androidMain/`, NEVER from commonMain. (FR-020, FR-030, Plan Phase 3)
- [ ] **T038** [US3] [deferred-local-emulator] Locale persistence emulator integration test: install on `pixel_5_api_34` → wizard with FakeLocaleProvider returning `ru-RU` → complete wizard → adb shell `setprop persist.sys.locale en-US` (or Android Settings → Languages → English) → kill app → reopen → assert UI on Russian. Requires: T034, T035. (FR-017, FR-018, SC-004, Plan Phase 3)

**Checkpoint Phase 3**: locale override persists; Settings indicator renders when diverges; fitness function enforces multi-platform seam.

---

## Phase 4: Manifest content + pool v2 migration + strings

**Goal**: Ship simple-launcher profile content. Pool migrated to schemaVersion 2 with declarative blocks.

**Independent test**: production assets pass `PoolSchemaV2RoundtripTest` + content validation test.

- [ ] **T040** [US1] Update `core/src/androidMain/assets/wizard/wizard-manifests/simple-launcher.json` per contracts/simple-launcher-manifest.md: `body.autoOrder: false`; `body.steps: [...]` with 4 explicit entries — ROLE_HOME (Required, canSkip:false override), tileSet (Required), POST_NOTIFICATIONS (Required, canSkip:true override), Custom("pair-admin") Optional. (FR-001, FR-002, FR-003, FR-004, SC-001, Plan Phase 4)
- [ ] **T041** [multi] Migrate `core/src/androidMain/assets/wizard/system-settings/android-pool.json` to `schemaVersion: 2`: add `check` and `apply` blocks to all 6 entries (ROLE_HOME → `android-package-home` / `android-role-request`; POST_NOTIFICATIONS → `android-permission` / `standard-permission-request`; CALL_PHONE → analogous; accessibility → `android-accessibility-service` / `settings-deep-link` for `ACTION_ACCESSIBILITY_SETTINGS`; battery → `android-special-permission` / `settings-deep-link` for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` packageScoped:true; hide-status-bar → `android-accessibility-service` / `in-app-only`). Keep v1 fields (`mechanism`, `deepLink`, `detectionStrategy`) for backward-compat per contracts/system-settings-pool-v2.md §3.1. (FR-011, Plan Phase 4)
- [ ] **T042** [P] [US1] Add `extendedInstructionKey` to ROLE_HOME pool entry: `"system_setting_role_home_retry_message"`. (FR-002, retry UX per Сценарий 1 Trouble case 1.c, Plan Phase 4)
- [ ] **T043** [P] [multi] String resources added in `core/src/commonMain/composeResources/values/strings.xml` and `values-ru/strings.xml`: `system_setting_role_home_retry_message`, `pair_admin_step_label`, `pair_admin_step_desc`, `pair_admin_step_skip_button`, `settings_walk_through_all_label`, `settings_pending_indicator_label`, `settings_locale_divergence_label_format` (with `%s` placeholders for app + system locales). (FR-025, FR-026, Plan Phase 4)
- [ ] **T044** [multi] Run `PoolSchemaV2RoundtripTest` (from T007) on production `android-pool.json` (real asset, not fixture). Requires: T041. (SC-008, Plan Phase 4)
- [ ] **T045** [multi] Manifest content validation test in `core/src/androidUnitTest/kotlin/com/launcher/adapters/wizard/SimpleLauncherManifestTest.kt`: load `simple-launcher.json` via real `BundledConfigSource`; assert 4 steps present in expected order with expected per-profile canSkip overrides; assert every step's `refId` resolves to an existing pool entry (no dangling refs); assert all referenced localization keys exist in strings resources (CI fitness function). Requires: T040, T041, T043. (FR-001, FR-002, Plan Phase 4)

**Checkpoint Phase 4**: simple-launcher profile content production-ready; pool v2 with full check/apply blocks; en+ru strings complete.

---

## Phase 5: PairAdmin Custom step + DI wiring

**Goal**: `Custom("pair-admin")` step launches existing spec 007 `PairingActivity`. Graceful fall on failures.

**Independent test**: Custom step integration test with FakeLinkRegistry replay.

- [ ] **T046** [P] [US4] Add `CustomStep` class + `CustomStepHandler` port in `core/src/commonMain/kotlin/com/launcher/ui/wizard/steps/CustomStep.kt` per data-model.md §5.1: dispatches by `refId` to map of handlers; unknown refId → `StepResult.Skipped` (graceful). (FR-027, Plan Phase 5)
- [ ] **T047** [US4] `PairAdminCustomStepHandler` in `core/src/androidMain/kotlin/com/launcher/adapters/wizard/handlers/PairAdminCustomStepHandler.kt` per data-model.md §5.2: launches `PairingActivity` (spec 007) via explicit intent through ActivityResultRegistry; awaits result; maps PairingOutcome → StepResult (Success → AnswerCaptured("paired"); Cancelled/Failed → Skipped). Try/catch wraps Activity launch for graceful fall. Inline TODO: `// TODO(TASK-8): admin config push activates here when TASK-8 lands; currently demonstrates trust handshake only`. Requires: T046. (FR-028, FR-029, Plan Phase 5)
- [ ] **T048** [US4] Koin DI wiring in `coreAndroidModule.kt` for Custom step: register `CustomStep` as additional `WizardStep` implementation in step type map under `StepType.Custom`; register `Map<String, CustomStepHandler>(pair-admin → PairAdminCustomStepHandler)`. Requires: T047. (FR-027, Plan Phase 5)
- [ ] **T049** [US4] `PairAdminStepIntegrationTest` in `core/src/androidUnitTest/kotlin/com/launcher/adapters/wizard/handlers/PairAdminStepIntegrationTest.kt`: use Robolectric + FakeLinkRegistry; scenarios: (a) successful pair → AnswerCaptured; (b) user cancels in PairingActivity → Skipped; (c) FakeLinkRegistry throws (offline) → Skipped + no crash. Requires: T047. (FR-028, SC-005, Plan Phase 5)

**Checkpoint Phase 5**: pairing wired through Custom step; spec 007 PairingActivity reused unchanged.

---

## Phase 6: Settings UI — pending checklist + walk-through

**Goal**: Per Сценарий 4 + 5 — banner + checklist for pending settings; "Walk through all settings" button launches sequential mode.

**Independent test**: PendingChecklist UI test + `RunWalkThroughTest` green; emulator smoke (deferred).

- [ ] **T050** [P] [US2] `PendingChecklistViewModel` in `app/src/main/java/com/launcher/app/settings/PendingChecklistViewModel.kt` per data-model.md §6.1: loads `simple-launcher.wizard.manifest.json` via ConfigSource → calls `engine.computePending(manifest)` → maps to `List<PendingItem>` with localized labels resolved via `StringResolver`. (FR-014, Plan Phase 6, Сценарий 4)
- [ ] **T051** [P] [US2] `PendingChecklistScreen` composable in `app/src/main/java/com/launcher/app/settings/PendingChecklistScreen.kt`: senior-safe styled; shows banner `[!] N` indicator at top when pending list non-empty; expands to checklist with one row per pending item; each row has "Настроить сейчас" CTA opening that single setting standalone. (FR-014, Plan Phase 6, Сценарий 4)
- [ ] **T052** [P] [US2] `WalkThroughButton` composable in `app/src/main/java/com/launcher/app/settings/WalkThroughButton.kt`: senior-safe styled button labelled `settings_walk_through_all_label` ("Пройти все настройки пошагово"); on click launches walk-through wizard mode. (FR-014a, Plan Phase 6, Сценарий 5)
- [ ] **T053** [US2] Add `suspend fun runWalkThrough(manifest: WizardManifest): WizardOutcome` to `WizardEngine` interface in `core/src/commonMain/kotlin/com/launcher/api/wizard/WizardEngine.kt`. (FR-014a, Plan Phase 6)
- [ ] **T054** [US2] Implement `WizardEngineImpl.runWalkThrough(manifest)` in `core/src/commonMain/kotlin/com/launcher/ui/wizard/WizardEngineImpl.kt`: traverses ALL steps from manifest.steps (not filtered by computePending); per step, pre-populates current value (from `SystemSettingPort.status()` for SystemSetting, `UserPreferencesStore.current()` for UIChoice); passes mode flag to step host for "Оставить" / "Изменить" UI rendering. Requires: T053. (FR-014a, Plan Phase 6, Сценарий 5)
- [ ] **T055** [US2] Modify wizard step UI hosts (`UIChoiceStep`, `SystemSettingStep`) in `core/src/commonMain/kotlin/com/launcher/ui/wizard/steps/` to accept `mode` parameter (Wizard vs WalkThrough); WalkThrough mode renders "Текущее: <value>. [Оставить] [Изменить]" instead of standard picker. Requires: T054. (FR-014a, Plan Phase 6)
- [ ] **T056** [US2] Wire `PendingChecklistScreen` + `WalkThroughButton` + `LocaleDivergenceIndicator` into existing Settings screen (`app/src/main/java/com/launcher/app/settings/` or equivalent — verify actual Settings entry point location during impl). Requires: T036, T037, T051, T052. (FR-014, FR-014a, FR-017a, Plan Phase 6)
- [ ] **T057** [US2] `RunWalkThroughTest` in `core/src/commonTest/kotlin/com/launcher/api/wizard/RunWalkThroughTest.kt`: scenarios (a) all settings current values valid → walk-through visits all steps with current values populated; (b) "Оставить" advances without modification; (c) "Изменить" updates `UserPreferences` and saves. Requires: T054. (FR-014a, Plan Phase 6)
- [ ] **T058** [US2] [deferred-local-emulator] PendingChecklist UI smoke on `pixel_5_api_34`: install with stale state (force a pool entry to be pending) → open Settings → assert `[!]` indicator visible → expand checklist → tap row → standalone step opens → save → return → indicator updated. Requires: T051, T056. (FR-014, Plan Phase 6)

**Checkpoint Phase 6**: Settings checklist + walk-through visible; banner reflects pending state; UI senior-safe.

---

## Phase 7: Konsist fitness functions + senior-safe walkthrough + deferred verification

**Goal**: All architectural fitness verified by Konsist. Manual senior-safe walkthrough on emulator.

**Independent test**: `./gradlew :core:test --tests *Task7ArchitectureTest*` all green; manual `[hand]` verifications passing.

- [ ] **T059** [multi] Aggregate Konsist fitness functions in `core/src/androidUnitTest/kotlin/com/launcher/architecture/Task7ArchitectureTest.kt`:
  - **T7-001**: no Gradle module containing "simple-launcher" name (Article VII §13).
  - **T7-002**: no `if (appFamilyId == "simple-launcher")` OR `when (appFamilyId)` branches in business-logic files (Article VII §13).
  - **T7-003**: `ConfigKind` enum has exactly the 5 existing values (Article VII §10).
  - **T7-004**: already in T009 — `CheckSpec.kt` / `ApplySpec.kt` no Android imports.
  - **T7-005**: already in T039 — `AppCompatDelegate.setApplicationLocales` not called from commonMain.
  - **T7-006**: all bundled JSONs in `core/src/androidMain/assets/wizard/` have `schemaVersion >= 1` (CLAUDE.md rule 5).
  Requires: T009, T039. (FR-022, FR-023, FR-024, FR-025, FR-030, FR-034, SC-010, Plan Phase 7)

### Deferred verifications

> **[deferred-local-emulator]** T060-T063 require emulator `pixel_5_api_34` via skill `android-emulator`. AI session may run them with permission; default deferred until owner kicks emulator on dev machine.

- [ ] **T060** [US6/US1] [deferred-local-emulator] Senior-safe walkthrough via skill `android-emulator`: install APK → walk through wizard → confirm all UI matches Article VIII §7 baseline (≥56dp tap targets, ≥24sp text, ≥4.5:1 contrast). `[hand]` AC. (SC-007, Plan Phase 7)
- [ ] **T061** [US1] [deferred-local-emulator] `WizardE2ETest` instrumented Android test in `app/src/androidTest/java/com/launcher/app/wizard/WizardE2ETest.kt`: fresh install → wizard appears in ≤2s → traverse ROLE_HOME (granted via `PermissionTestRule` or fake) → tileSet → POST_NOTIFICATIONS → skip PairAdmin → HomeActivity opens in ≤1s with classic-6 tiles rendered. Requires: T040 + T041 + T031 + T030 + T013. (SC-001, SC-002, Plan Phase 7)
- [ ] **T062** [US3] [deferred-local-emulator] Locale persistence emulator test: run T038 (locale-RU → system-EN → kill → reopen → still RU). Requires: T034, T035. (SC-004, Plan Phase 7)
- [ ] **T063** [US2] [deferred-local-emulator] Config-check master integration test: pre-grant ROLE_HOME via `adb shell cmd role add-role-holder android.app.role.HOME com.launcher.app` → install → open → wizard launches without ROLE_HOME step (goes straight to tileSet). (SC-003, Plan Phase 7)

> **[deferred-physical-device]** T064-T066 require real devices not available to AI session (memory `reference_testing_environment.md`).

- [ ] **T064** [multi] [deferred-physical-device] Samsung One UI ROLE_HOME flow on Samsung Galaxy: ROLE_HOME request → One-UI confirm dialog → return → wizard step status updates. (OEM Matrix, Plan Phase 7)
- [ ] **T065** [multi] [deferred-physical-device] Xiaomi MIUI quirks check on Xiaomi 11T: battery optimization API call → SecurityException caught → `Indeterminate` → step included in pending (graceful). (OEM Matrix, Plan Phase 7)
- [ ] **T066** [US4] [deferred-physical-device] Real pairing 2-device flow: phone A in primary user mode + phone B with admin app stub (mock for TASK-8) → QR scan from A targeting B's QR → `LinkRegistry.activate()` succeeds → trust handshake recorded. (SC-005, Plan Phase 7)

### Documentation + cleanup

- [ ] **T067** [P] [multi] Update `docs/dev/project-backlog.md`: append TODOs surfaced by TASK-7 implementation: (a) `TODO(TASK-8+): cloud sync of pending setup state for admin visibility`; (b) `TODO(schema-v3): once all bundled pool entries migrated to v2 with check/apply blocks, remove legacy mechanism/deepLink/detectionStrategy fields from SystemSettingEntry`; (c) `TODO(pool-migration): theme value migration policy when ui-pool choices change (separate backlog task)`. (Plan §Required Context Review, Plan Phase 7)
- [ ] **T068** [P] [multi] APK size delta measurement: build release APK before and after TASK-7 (or via `:app:bundleRelease` analysis); assert delta ≤ +150 KB per SC-011; record result in `perf-checkpoint.md`. (SC-011, Plan Phase 7)

**Checkpoint Phase 7**: all Konsist fitness PASS; deferred AC tracked for owner verification; docs updated.

---

## Dependencies & Execution Order

### Phase dependencies (sequential)

- **Phase 0** (T001-T009): no dependencies; starts immediately.
- **Phase 1** (T010-T015): depends on Phase 0 (CheckSpec exists for pool entries).
- **Phase 2** (T016-T033): depends on Phase 0 (CheckSpec/ApplySpec sealed) + Phase 1 (engine computePending uses SystemSettingPort which Phase 2 reimplements).
- **Phase 3** (T034-T039): depends on Phase 1 (engine.run() must work before locale glue calls it).
- **Phase 4** (T040-T045): depends on Phase 0 (pool v2 schema) + Phase 2 (handlers register before adapter loads v2 entries).
- **Phase 5** (T046-T049): depends on Phase 1 (CustomStep dispatched via engine path).
- **Phase 6** (T050-T058): depends on Phase 1 (computePending used by PendingChecklistViewModel) + Phase 3 (LocaleDivergenceIndicator wired into Settings).
- **Phase 7** (T059-T068): depends on all prior phases.

### Within-phase parallel opportunities

- Phase 0: T001, T002 [P]; T005, T006, T007, T008 [P]; T009 [P].
- Phase 2: T016, T017 [P]; T018, T019, T020 [P]; all 9 handlers T021-T029 [P]; T032 per-handler tests [P].
- Phase 3: T036, T037 [P]; T039 [P].
- Phase 4: T042, T043 [P].
- Phase 5: T046 [P].
- Phase 6: T050, T051, T052 [P].
- Phase 7: T067, T068 [P].

---

## Trace coverage matrix

| FR | Tasks |
|---|---|
| FR-001, FR-002, FR-003, FR-004 | T040, T045 |
| FR-005, FR-006 | T003, T004, T008 |
| FR-007, FR-008 | T001, T002 |
| FR-009 | T005, T006, T016-T029, T031, T032 |
| FR-010 | T030 |
| FR-011 | T041 |
| FR-012 | T009, T059 |
| FR-013 | T010, T011, T012, T015 |
| FR-014 | T013, T050, T051 |
| FR-014a | T052, T053, T054, T055, T057 |
| FR-015 | T013 (engine path; WizardActivity routes through engine) |
| FR-016 | T014 |
| FR-017 | T034 |
| FR-017a | T036, T037 |
| FR-018 | T035 |
| FR-019 | T034 (AppCompat shim handles) |
| FR-020 | T039, T059 |
| FR-021 (TTL cache) | T019, T030, T033 |
| FR-022 (cache invalidate on RESUMED) | T020, T030, T033 |
| FR-023 (cache invalidate on Applied) | T030 |
| FR-024 (inline perf TODO) | T019 |
| FR-025, FR-026 (localization) | T043 |
| Senior-safe baseline (Article VIII §7, US-6) | T036, T051, T052, T060 |
| FR-027, FR-028, FR-029 | T046, T047, T048, T049 |
| FR-030, FR-031 | T009, T030, T039, T059 |
| FR-032..FR-035 (cross-cutting) | T059 |

| SC | Verification task |
|---|---|
| SC-001 (cold-start ≤2s) | T061 [deferred-local-emulator] |
| SC-002 (home render ≤1s) | T061 [deferred-local-emulator] |
| SC-003 (config-check master) | T015 + T063 [deferred-local-emulator] |
| SC-004 (locale stability) | T038 = T062 [deferred-local-emulator] |
| SC-005 (pairing handshake) | T049 (unit) + T066 [deferred-physical-device] |
| SC-006 (reboot persistence) | T061 [deferred-local-emulator] |
| SC-007 (senior-safe) | T060 [deferred-local-emulator] |
| SC-008 (roundtrip) | T005, T006, T007 |
| SC-009 (backward-compat) | T008 |
| SC-010 (fitness functions) | T059 |
| SC-011 (APK size ≤+150 KB) | T068 |
| SC-012 (engine integration) | T015 |

---

## Deferred markers summary

Per CLAUDE.md Portfolio tracker hybrid AC model, these markers will be picked up by `pre-pr-backlog-sync` and emitted as `[auto:deferred-*]` AC in TASK-7 backlog task. Until closed, the task stays in **Verification** status post-merge.

| Marker | Tasks |
|---|---|
| `[deferred-local-emulator]` | T038, T058, T060, T061, T062, T063 (6 tasks) |
| `[deferred-physical-device]` | T064, T065, T066 (3 tasks) |
| `[deferred-firebase-emulator]` | none |
| `[deferred-external]` | none |

---

## Implementation strategy

**MVP-first**: Phase 0 + Phase 1 + Phase 2 (handlers + cache) + Phase 4 (manifest content + pool v2) + Phase 7 (Konsist).

This delivers the **architectural innovation** (config-check master, pool v2, handler registry) wired up end-to-end with bundled content. Phase 3 (locale), Phase 5 (pairing), Phase 6 (Settings UI) follow incrementally — each shippable and independently testable.

**Critical path**: T001 → T002 → T003 → T004 → T010 → T012 → T013 → T016/T017 → T030 → T040/T041 → T059.

**Parallel team capacity**: ~3 developers could complete in ~2 weeks (Phase 0 + Phase 2 handlers + Phase 4 content all parallel-rich).

---

## TL;DR (по-русски, для новичка и для будущего AI)

**Суть.** 68 tasks в 8 фазах (Phase 0..7), декомпозиция plan.md. Каждый task с trace (`FR-NNN`, `SC-NNN`, `Plan Phase N`) и parallel-safe marker `[P]` где можно. Критический путь: Phase 0 wire-format → Phase 1 engine.computePending → Phase 2 handler registry → Phase 4 manifest content + pool v2 migration. Параллельно идут Phase 3/5/6 UI работы. Phase 7 — fitness functions + deferred verifications.

**Конкретика, которую стоит запомнить:**
- **9 `[deferred-*]` tasks**: 6 `[deferred-local-emulator]` (T038, T058, T060-T063) + 3 `[deferred-physical-device]` (T064-T066). Будут авто-сгенерированы в backlog AC через `pre-pr-backlog-sync`.
- **Roundtrip + backward-compat обязательны**: T005 (CheckSpec), T006 (ApplySpec), T007 (PoolV2), T008 (BackwardCompat v1→v2 reader). Без них wire-format change не shippable per rule 5.
- **Konsist fitness functions T7-001..T7-006 в T059** — единый файл `Task7ArchitectureTest.kt`. Без них Article VII §13 (no per-profile code) silently violated.
- **Trace coverage matrix** в конце tasks.md покрывает все 35 FR + 12 SC из spec.md. Cross-artifact trace pass (verified manually pre-`procedure-cross-artifact-trace`).
- **MVP-first strategy**: Phase 0 + 1 + 2 + 4 + 7 ship первыми (config-check master + handler registry + content). Phase 3/5/6 — incremental.

**На что смотреть с осторожностью:**
- **T030 — AndroidSystemSettingAdapter модификация — большая** (~150 LOC change). Если split необходим — T030a handler dispatch + T030b cache integration + T030c v1 fallback. Размечено atomic, но at review может потребоваться split.
- **T055 modify step UI hosts для WalkThrough mode** — может задеть существующие F-3 step UI tests (TASK-1 Done но tests могут assume стандартный mode). При impl запустить полный F-3 test suite.
- **Pair-admin step (T047) использует ActivityResultRegistry** — требует careful integration с current PairingActivity contract (spec 007). Если PairingActivity не возвращает result через стандартный path — нужен intermediate Activity wrapper.
- **Tасks.md tick-sync HARD RULE** (CLAUDE.md): каждый implementation commit обязан в том же diff'е проставить `[x]` напротив закрытых Tnnn. Не «потом догонит».
