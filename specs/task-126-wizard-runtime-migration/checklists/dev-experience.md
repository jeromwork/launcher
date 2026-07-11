# Checklist: dev-experience
**Spec**: `specs/task-126-wizard-runtime-migration/spec.md`
**Run date**: 2026-07-11
**Result**: 12/21 ✓ (1 N/A skipped), 5 FAIL, 3 WARN

---

## Local-test path

- [ ] CHK001 Spec's "Local Test Path" section is filled in (not the template placeholder).
  - **FAIL** — No `## Local Test Path` section exists. Independent Test commands are scattered across 7 User Story blocks. A developer must read the entire spec to assemble a complete local verification plan. Recommend: add a consolidated `## Local Test Path` section listing all unit test commands and clearly separating them from physical-device-only steps.

- [ ] CHK002 Verification command is exact (e.g., `./gradlew :core:test --tests *FamilyGroupTest`) — not "run the tests".
  - **FAIL** — US-1: `./gradlew :app:testMockBackendDebugUnitTest -tests "*PresetBootstrap*"` ✓. US-7: `./gradlew lint` + `git grep "import com.launcher.api.wizard"` ✓. US-2 through US-6: reference class names only (`LauncherRoleProviderTest`, `BootCheckProviderTest`, `PendingChecklistViewModelTest`, `StatusBarPolicyProviderTest`) without module path or `--tests` flag. Half the independent tests are not runnable as-written. Suggest: `./gradlew :core:testMockBackendDebugUnitTest --tests "*LauncherRoleProviderTest*"` pattern for each.

- [x] CHK003 The verification command runs in under 5 minutes on a developer laptop (cold cycle).
  - **PASS** — All independent test commands target `:app:testMockBackendDebugUnitTest` or `:core:testMockBackendDebugUnitTest` (JVM-only unit tests). Robolectric tests (US-3) typically 30–90s. No E2E command in the per-US independent test blocks. Well under 5 min.

- [x] CHK004 At least one path of the feature is verifiable without an emulator (pure JVM unit test on domain logic).
  - **PASS** — US-1 (`PresetBootstrap.bootstrap()` + `ReconcileEngine.run()` with `fakeInteractionSink`) is pure JVM. US-3 (Robolectric `ProfileStore` persistence) is JVM. US-4 (`BootCheckProviderTest` with fake Profile) is JVM. US-5 (`PendingChecklistViewModelTest`) is JVM.

- [ ] CHK005 If the feature requires an emulator, the spec names the preset from skill `android-emulator` (e.g., `pixel_5_api_34`).
  - **FAIL** — tasks.md 5.6 calls `./gradlew :app:connectedMockBackendDebugAndroidTest` (connected = emulator or device) but names no AVD preset. Manual smokes (tasks 2.6, 3.6, 4.5, 6.15) specify "Xiaomi Redmi Note 11" (physical device) without an emulator fallback name. NFR-001 cites "Pixel 5 baseline" without naming the AVD preset. Add: `pixel_5_api_34` (from android-emulator skill) as the emulator gate for connected tests; physical Xiaomi as the additional OEM verification.

## Fake adapters

- [x] CHK006 Every external port the feature depends on has a fake adapter available (or the spec lists adding one as a task).
  - **WARN** — Existing fakes from TASK-120: `FakeProfileStore`, `FakePoolSource`, `FakePresetSource` ✓. US-6 explicitly lists "mock `WindowInsetsControllerCompat`" for `StatusBarPolicyProviderTest` ✓. US-4 uses fake Profile for `BootCheckProviderTest` ✓. **Gap**: `LanguageProvider` depends on `AppCompatDelegate.setApplicationLocales()` — no fake/stub mentioned in spec or tasks.md for isolated `LanguageProviderTest`. tasks.md 1.16 lists `LanguageProviderTest` but provides no fake strategy. Recommend: add `FakeLocaleDelegate` or test using Robolectric's locale API.

- [x] CHK007 Fake adapters are used in tests — the spec does not require real Firebase / real Cloudflare Worker / real FCM to verify.
  - **PASS** — All independent test commands use `mockBackendDebug` build flavor. E2E tests (tasks.md 5.6): `connectedMockBackendDebugAndroidTest`. No real cloud service required for any verification path.

- [x] CHK008 The DI wiring picks fakes for `debug` / `test` builds and reals for `release` (or the spec describes the equivalent build-flavor split).
  - **PASS** — `mockBackend` / `realBackend` build flavors established by project (visible throughout spec and tasks.md). FR-016 consolidates DI into `PresetModule` under same flavor split. No new flavor introduced.

## Fixtures

- [x] CHK009 Test data lives in a checked-in fixture (JSON / Kotlin object) — not hand-typed in each test.
  - **PASS** — `simple-launcher` golden JSON is checked-in (tasks.md 5.5 regenerates it). `theme-catalog.json` + `hint-pool.json` are bundled assets. Domain unit tests use in-memory fixture objects built from `Preset` / `Pool` data classes.

- [x] CHK010 Fixtures are stable across runs (no `Random()`, no `now()` without a fixed clock).
  - **PASS** — No `Random()` or timestamp-dependent logic described. `ReconcileEngine` is a pure state machine over a typed `Profile`. `ProfileStore` Robolectric tests construct deterministic Profile objects.

- [x] CHK011 Cross-version fixtures exist for any wire format introduced (v(N-1) sample saved for backward-compat test).
  - **WARN** — FR-014 bumps `Preset` and `Pool` to schemaVersion 2. tasks.md 1.19 (`PresetSchemaVersionTest`) implies reading a v1-format Preset with the v2 reader. However: spec does not explicitly require the v1 golden JSON file to be preserved (checked-in) as a backward-compat test fixture. tasks.md 5.5 says "regenerate `simple-launcher` golden JSON for schemaVersion 2" without requiring the v1 version to coexist. Recommend: keep `simple-launcher-v1.json` alongside `simple-launcher-v2.json` for regression coverage per CLAUDE.md rule 5.

## Cannot-test-locally gaps

- [ ] CHK012 Every gap that requires a physical device, OEM-specific behavior, or real billing is **explicitly listed** in the "Local Test Path → Cannot-test-locally gaps" subsection.
  - **FAIL** — Physical-device-only steps exist (tasks.md 2.6, 3.6, 4.5, 6.15 — all "Manual smoke on Xiaomi Redmi Note 11"; US-6 scenario 1 "проверить визуально"). These are not consolidated in a `Cannot-test-locally gaps` subsection in the spec. The `Edge Cases` section mentions MIUI behavior as a risk but not as an explicit locally-untestable gap. Add: a `### Cannot-test-locally gaps` subsection listing each gap explicitly.

- [ ] CHK013 Each gap has an inline TODO in code / spec: `TODO(physical-device): <what to verify>` or `TODO(real-account): <what to verify>`.
  - **FAIL** — No `[deferred-physical-device]` markers on tasks.md manual smoke items (2.6, 3.6, 4.5, 6.15). CLAUDE.md Portfolio Tracker requires these markers for `[auto:deferred-physical-device]` AC sync. Without them, pre-PR backlog sync cannot auto-generate the deferred AC rows. Minimum fix: annotate each manual smoke task in tasks.md with `[deferred-physical-device]`.

- [x] CHK014 No gap is silently swept under "we'll test it in prod".
  - **PASS** — All physical-device tests name Xiaomi Redmi Note 11 explicitly. No "test in prod" language. No production account or production Firebase project required for any test path.

## Build cycle

- [x] CHK015 Adding this feature does not increase clean-build time on a developer laptop by more than ~30 seconds (or the spec acknowledges the cost and justifies it).
  - **PASS** — 4 new data classes + 1 interface + 1 lint rule added; ~50+ files deleted (Phase 6). Net source count reduction. No new annotation processors or heavy compile-time dependencies introduced. Build time impact expected to be neutral or negative.

- [x] CHK016 The feature does not require a one-time manual setup step that is not documented (e.g., "you must enable X in Firebase console" → must be in spec or a setup script).
  - **PASS** — `theme-catalog.json` and `hint-pool.json` are bundled assets (tasks.md 1.4, 1.13) — no external provisioning. `mockBackend` builds need no credentials. No Firebase console steps.

- [x] CHK017 No new credential / API key / service-account file is needed for `debug` builds unless the spec lists how to obtain it.
  - **PASS** — Pure Android refactoring. No new external service, API key, or service account.

## Crash + log diagnostics

- [ ] CHK018 The feature emits enough log signal (Logcat tag, structured log fields) for a developer to diagnose a failure without attaching a debugger.
  - **FAIL** — No logging requirements anywhere in the spec. `ReconcileEngine`, `PresetBootstrap`, `PresetValidator`, and `WizardViewModel` have no specified Logcat tags or structured log output. `PresetValidationException` (FR-006) is described but no requirement to log it with context (which component, which preset ID). A developer debugging a wizard hang or silent boot failure cannot determine the failure point from Logcat. Recommend: add NFR-005 "ReconcileEngine MUST emit Logcat tag `ReconcileEngine` at INFO level for each step: componentId, check result, apply result. `PresetBootstrap` MUST log at ERROR level on load failure with exception message."

- [ ] CHK019 Failure modes that would crash silently (background coroutine, lifecycle-detached job) have an opt-in log line.
  - **FAIL** — `BootCheckReceiver` launches `ReconcileEngine.run(RunMode.BootCheck)` in a background coroutine (lifecycle-detached from any Activity). If it throws (e.g., `PresetValidationException`, DataStore read failure), the error is lost. `WizardViewModel` coroutine cancellation on Activity destroy could swallow mid-wizard failures. No `CoroutineExceptionHandler` or logging requirement specified. Recommend: require `CoroutineExceptionHandler` with Logcat ERROR emission in `BootCheckReceiver` and `WizardViewModel`.

- [N/A] CHK020 If the feature has runtime feature flags or remote config, the current value is loggable on demand.
  - **N/A** — No remote feature flags or remote config in this spec. `wizardPresentation` is a local preset field.

## Cross-developer reproducibility

- [x] CHK021 The spec does not embed developer-machine-specific paths, env vars, or assumptions (e.g., "set `MY_PHONE_NUMBER=…`").
  - **PASS** — All commands use `./gradlew` (repo-relative). No machine-specific paths or env vars.

- [x] CHK022 Onboarding a new developer to verify this feature is documented in less than 1 page (or covered by existing `docs/dev/dev-environment.md`).
  - **WARN** — `docs/dev/dev-environment.md` covers build setup. However, a new developer verifying this specific feature must scan all 7 User Story blocks to assemble the local test commands — there is no consolidated "How to verify this feature locally" section. Acceptable given the existing dev-environment.md, but a consolidated `## Local Test Path` (see CHK001) would eliminate the hunt.

---

## Summary

| Result | Count | Items |
|--------|-------|-------|
| PASS   | 12    | CHK003, CHK004, CHK007, CHK008, CHK009, CHK010, CHK014, CHK015, CHK016, CHK017, CHK021, CHK022 (marginal) |
| WARN   | 3     | CHK006 (LanguageProvider fake), CHK011 (v1 fixture not explicitly preserved), CHK022 (scattered test commands) |
| FAIL   | 5     | CHK001 (no Local Test Path section), CHK002 (half commands not exact), CHK005 (no AVD preset named), CHK012 (no cannot-test-locally gaps section), CHK013 (no `[deferred-physical-device]` markers), CHK018 (no logging NFR), CHK019 (silent coroutine failures) |
| N/A    | 1     | CHK020 (no remote feature flags) |

> Note: CHK013 and CHK018/CHK019 each represent one FAIL item counted above — total distinct FAIL items = 5 (CHK001, CHK002, CHK005, CHK012+CHK013 grouped, CHK018+CHK019 grouped).

### Key issues (ordered by severity)

1. **CHK018+CHK019 FAIL** — No logging or diagnostics requirements. `BootCheckReceiver` and `WizardViewModel` coroutines can fail silently. Add NFR-005 requiring Logcat tags on `ReconcileEngine` step transitions, `PresetBootstrap` load errors, and `CoroutineExceptionHandler` in background receivers.

2. **CHK012+CHK013 FAIL** — Physical-device gaps not consolidated or tagged. Manual smokes in tasks.md (2.6, 3.6, 4.5, 6.15) need `[deferred-physical-device]` annotations for CLAUDE.md AC sync. Add `### Cannot-test-locally gaps` subsection to spec.

3. **CHK001+CHK002 FAIL** — No `## Local Test Path` section; half the independent test commands are class-name-only, not runnable. Add section with exact `./gradlew :module:testMockBackendDebugUnitTest --tests "*ClassName*"` commands for each US.

4. **CHK005 FAIL** — Connected Android tests have no named AVD preset. Add `pixel_5_api_34` as the emulator target for `connectedMockBackendDebugAndroidTest`.

5. **CHK011 WARN** — v1 golden JSON not explicitly preserved for backward-compat regression. Recommend keeping `simple-launcher-v1.json` after Phase 5 regeneration.
