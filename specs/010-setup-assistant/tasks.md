# Task Breakdown: Setup Assistant and Launcher Bootstrap

**Branch**: `010-setup-assistant` | **Date**: 2026-05-19 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)
**Input**: plan.md (8 phases, Constitution Check 8/8 PASS) + 13 checklist findings + plan §11 implementation constraints (C-1..C-10).

---

## Conventions

- **ID** `T0NN` (`T001`..`T116`, **plus T032a** inserted post-analyze for Finding #1 closure — 117 tasks total). Phase boundaries: T001-T012 (Phase 0), T013-T028 (Phase 1), T029-T040 inc. T032a (Phase 2), T041-T053 (Phase 3), T054-T065 (Phase 4), T066-T080 (Phase 5), T081-T093 (Phase 6), T094-T103 (Phase 7), T104-T116 (Phase 8).
- **[P]** parallel-safe (no file conflict, no logical dependency on adjacent tasks).
- **Trace**: `(FR-NNN | US-NN | Plan §X | C-N | CHK-NNN)` — non-optional.
- **Acceptance**: test name OR manual check OR command result.
- **Dependencies**: `requires: TNNN [, TNNN]` if applicable.
- **Constraint refs**: C-1..C-10 from plan.md §11 are **policy constraints** — every task touching that area MUST respect them.

---

## Phase 0 — Preflight (CRITICAL — blocks all subsequent phases)

Goal: closure 3 critical findings из `checklists/_overview.md`. Estimated 3-4 days.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T001** [P] | Add `<uses-permission android:name="android.permission.CALL_PHONE" />` в `app/src/main/AndroidManifest.xml` | FR-012, CHK-permissions-006 | — | `grep "CALL_PHONE" app/src/main/AndroidManifest.xml` returns match |
| **T002** [P] | Add `<uses-feature android:name="android.hardware.telephony" android:required="false" />` в `AndroidManifest.xml` | Plan §5, CHK-permissions-007 | — | `grep "hardware.telephony" AndroidManifest.xml` returns match |
| **T003** [P] | **CRITICAL**: Add `<queries>` block для `tel:` scheme (DIAL + CALL intents) в `AndroidManifest.xml` | FR-012, FR-014, Plan §5, CHK-permissions-008/020 | — | `grep -A 10 "<queries>" AndroidManifest.xml` shows both DIAL and CALL intent declarations с `data android:scheme="tel"` |
| **T004** | Create `core/commonMain/kotlin/com/launcher/api/setup/GmsAvailabilityPort.kt` (interface + `suspend fun status(): GmsStatus`) | FR-042, Plan §3, CHK-domain-001 | — | File compiles; Konsist gate T007 will pass |
| **T005** | Create `core/commonMain/kotlin/com/launcher/api/setup/GmsStatus.kt` sealed (`Available`, `MissingRecoverable(reason, resolutionAvailable)`, `MissingFatal(reason)`) | FR-042..FR-044 | requires: T004 | File compiles; sealed exhaustive `when` test compiles |
| **T006** | Create `core/androidMain/kotlin/com/launcher/adapters/setup/GmsAvailabilityAdapter.kt` wrapping `GoogleApiAvailability.isGooglePlayServicesAvailable()` | FR-042, Plan §10 Phase 0 | requires: T004, T005 | Unit test `GmsAvailabilityAdapterTest` maps success/recoverable/fatal codes correctly |
| **T007** [P] | Konsist gate #1: `api/setup/*.kt` MUST NOT import `android.*` / `androidx.*` / `com.google.android.gms.*` в `core/commonTest/.../KonsistGateSetupIsolation.kt` | C-1 (rule 1), CHK-domain-001 | — | Konsist test passes; introducing `android.*` import in any `api/setup/` file fails build |
| **T008** [P] | Konsist gate #2: `api/gate/*.kt` MUST NOT import `android.*` / `androidx.*` | C-1 (rule 1) | — | Konsist test passes |
| **T009** [P] | Konsist gate #3: `IntentSpec` MUST contain only `String` / primitive fields (lint-style check via Konsist) | Plan §2, CHK-domain-007 | — | Konsist test fails on introducing `Intent`/`Uri`/`ComponentName` field |
| **T010** [P] | Konsist gate #4: `:app/androidMain` Activity classes (under setup/gate/call/paired) MUST have `android:exported="false"` declaration | C-9, CHK-security-009 | — | Konsist test asserts manifest declares exported=false for affected Activities |
| **T011** | Update `docs/compliance/permissions-and-resource-budget.md`: добавить CALL_PHONE row, POST_NOTIFICATIONS row, ROLE_HOME (role не permission), `<queries>` block, `<uses-feature> telephony required=false` | Plan §5, CHK-permissions-022 | requires: T001, T002, T003 | Diff shows 4 new rows / blocks with justification text |
| **T012** | Update `spec.md` FR-042 wording: replace direct `GoogleApiAvailability.isGooglePlayServicesAvailable()` reference с `GmsAvailabilityPort.status()` domain abstraction; preserve behavior contract | FR-042, CHK-domain-015, Plan §10 Phase 0 | requires: T004, T005 | Diff shows port reference в FR-042; spec.md grep `GoogleApiAvailability` empty |

---

## Phase 1 — Core domain types + ports + fakes (~1 week)

Goal: pure-Kotlin domain layer + fake adapters. Mock-first per CLAUDE.md rule 6.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T013** [P] | Create `core/commonMain/api/setup/SetupCheck.kt` interface (id, criticality, surfaces, check, resolveIntent) | FR-017, Plan §3 | — | File compiles; Konsist gate T007 passes |
| **T014** [P] | Create `core/commonMain/api/setup/Criticality.kt` sealed (`Required`, `Recommended`) | FR-017, Plan §3 | — | File compiles |
| **T015** [P] | Create `core/commonMain/api/setup/Surface.kt` sealed (`Settings`, `MainScreen`) **с inline-TODO C-5** documenting anticipated спек 013 consumer | C-5, CHK-meta-001/010 | — | File contains inline TODO matching C-5 text; Konsist gate T007 passes |
| **T016** [P] | Create `core/commonMain/api/setup/CheckStatus.kt` sealed (`Ok`, `NotConfigured(reason: String)`) | FR-017, FR-020b, Plan §3 | — | File compiles; sealed exhaustive `when` test |
| **T017** [P] | Create `core/commonMain/api/setup/IntentSpec.kt` data class (`category: String, action: String, extras: Map<String, String> = emptyMap()`) | Plan §3, CHK-domain-007 | — | File compiles; Konsist gate T009 passes |
| **T018** [P] | Create `core/commonMain/api/gate/Challenge.kt` sealed (`NumericEntry(answer: String)`, `SequenceTap(buttonIds: List<Int>, expectedOrder: List<Int>)`) | FR-023, Plan §3 | — | File compiles; Konsist gate T008 passes |
| **T019** [P] | Create `core/commonMain/api/gate/generateRandomChallenge.kt` **free function** (not class/interface per C-4) | C-4 (D-3), FR-023 | requires: T018 | Function signature `fun generateRandomChallenge(random: Random = Random.Default): Challenge`; uniform random selection between Numeric/Sequence verified in T024 |
| **T020** [P] | Create `core/commonMain/api/action/SlotToActionMapper.kt` **free function** `fun Slot.toAction(contacts: List<Contact>): Action?` | FR-003, Plan §3 | — | File compiles; null returned when `contactId` not found |
| **T021** | Create `core/commonTest/kotlin/.../setup/FakeSetupCheck.kt` (configurable `status: CheckStatus`, `criticality`, `surfaces`) | C-3 (rule 6), Plan §6 | requires: T013-T017 | Used by 3+ tests; serves as test double for SetupCheck consumers |
| **T022** | Create `core/commonTest/kotlin/.../setup/FakeGmsAvailabilityPort.kt` (programmable status) | C-3 (rule 6), Plan §6 | requires: T004, T005 | Used by GmsHardBlockTest (T050) |
| **T023** [P] | Unit test `SlotToActionMapperTest`: все `SlotKind` variants → correct Action; missing contact → null; emoji в contact name preserved | FR-003 | requires: T020 | Test class with ≥ 8 assertions, all green |
| **T024** [P] | Unit test `ChallengeGenerationTest`: uniform distribution Numeric vs Sequence over 1000 iterations; numeric 1000-9999 range; sequence 3-position from 6 buttons | FR-023 | requires: T019 | Statistical test with χ² acceptance; assertions on value ranges |
| **T025** [P] | Unit test `ChallengeFPRateTest` **(SC-007)**: simulated random taps на numeric keypad (10000 trials) + sequence-tap (10000 trials) — assert FP ≤ 1% | SC-007, FR-023 | requires: T019 | FP rate logged + asserted; theoretical bound (0.01% numeric, 0.83% sequence) holds |
| **T026** | Koin module `setupModule.kt` для `mockBackend` flavor: provides `List<FakeSetupCheck>` (5 instances), `FakeGmsAvailabilityPort` | C-3 (D-2), Plan §10 Phase 1 | requires: T021, T022 | DI resolution test passes; List<SetupCheck> injectable |
| **T027** | Koin module `setupModule.kt` для `realBackend` flavor: provides 5 real SetupCheck adapters (stubs at this phase, implementations in Phase 2-5), `GmsAvailabilityAdapter` | C-3, Plan §10 Phase 1 | requires: T006 | DI module compiles; stubs return `NotConfigured` для now |
| **T028** | Contract test base `SetupCheckContractTest` abstract class — каждый real adapter inherits и runs same test set (`check()` non-null, idempotent, no exception) | Plan §6 (CLAUDE.md rule 6) | requires: T013, T016, T021 | Contract test class compiles; T037/T038/T048/T060/T074 will extend it |

---

## Phase 2 — ARCH-016 closure (~1 week, **HIGH PRIORITY** — closes TODO-ARCH-016)

Goal: HomeScreen renders from `/config/current`, не mock. Closes the spec's blocking issue.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T029** | Refactor `core/commonMain/.../ui/screens/HomeScreen.kt`: collect `ConfigEditor.appliedConfig: Flow<ConfigCurrent>` as State; remove `FlowRepository` dependency | FR-001, FR-002, FR-005, FR-006, ARCH-016, Plan §10 Phase 2 | requires: Phase 1 | UI test T039 passes; manual smoke shows раскладка renders from /config not flows_mock |
| **T030** | Wire `SlotToActionMapper` в FlowScreen/TileCard rendering: convert `Slot` → `Action` using `appliedConfig.contacts` | FR-003 | requires: T029, T020 | TileCard renders correct Action для each Slot variant; integration test |
| **T031** | **Delete** `app/src/main/assets/flows_mock_*.json` files (4-5 files based on existing) | FR-004, SC-008 | requires: T029 | Files removed from working tree; `git status` shows deletion |
| **T032** | **Delete** `core/src/androidMain/kotlin/com/launcher/core/flows/MockFlowRepository.kt` (the class file itself) | FR-004 | requires: T031 | File removed; интегральная компиляция требует T032a для consumers |
| **T032a** | **Update consumers of MockFlowRepository** (per analyze-report.md Finding #1): (a) `core/src/androidMain/.../core/LauncherCore.kt:23` — remove `import com.launcher.core.flows.MockFlowRepository`; (b) `LauncherCore.kt:49` — remove default fallback `?: MockFlowRepository(...)`. Replace with required DI parameter sourced from `ConfigEditor` adapter (Spec 8 port). Constructor signature change: `flowRepository: FlowRepository?` → `flowRepository: FlowRepository` (mandatory); (c) **Delete** `core/src/androidUnitTest/kotlin/com/launcher/core/flows/MockFlowRepositoryTest.kt`; (d) Update `core/src/commonMain/kotlin/com/launcher/ui/di/CoreKoinModule.kt:8` docstring — remove «MockFlowRepository» mention from facade-classes list; (e) Verify `core/src/androidMain/.../adapters/config/LegacyMockStorageCleanup.kt` comments still valid (file is no-op marker; comments reference MockFlowRepository historically — update to note spec 010 ARCH-016 closure if needed) | FR-004, CHK-meta-012, analyze Finding #1 | requires: T032, T029 | `./gradlew :core:compileDebugKotlinAndroid` succeeds после T032+T032a; `MockFlowRepositoryTest.kt` removed; LauncherCore.kt has no MockFlowRepository imports; CoreKoinModule.kt docstring updated |
| **T033** [P] | **Grep verification**: `grep -r "flows_mock" .` empty (excluding historical specs); `grep -r "MockFlowRepository" --include="*.kt"` returns ноль matches в коде (only historical spec doc mentions). CI step added to prevent regression | FR-004, CHK-meta-012 | requires: T031, T032, T032a | Grep on `.kt` files returns no matches; spec docs (003/004/005/006/008 historical references) acceptable |
| **T034** | Rewrite Robolectric test `HomeActivityTest` (спек 3) на `FakeRemoteSyncBackend` (спек 7 pattern) — identify exact test file перед start, document path | SC-008, concern #6 | requires: T029 | Test passes against fake backend; old mock-data assertions replaced |
| **T035** | Rewrite Robolectric test `FlowScreenTest` (спек 3/4) на `FakeRemoteSyncBackend` | SC-008, concern #6 | requires: T029 | Test passes |
| **T036** | Rewrite additional 1-3 Robolectric tests affected by `flows_mock` removal (audit all spec 3/4 tests перед start) | SC-008, concern #6 | requires: T029 | All identified tests passing; SC-008 verified (CI green) |
| **T037** [P] | Create `core/androidMain/.../adapters/setup/RoleHomeCheckAdapter.kt` real implementation — **с API 26-28 legacy fallback** (per C-6 inline TODO + `Build.VERSION.SDK_INT >= 29` branching) | FR-018 (RoleHomeCheck Required first), C-6, Plan §10 Phase 2 | requires: T013, T028 | Extends SetupCheckContractTest; manual check on API 26-28 emulator returns sensible status |
| **T038** [P] | Create `core/androidMain/.../adapters/setup/NetworkOnlineCheckAdapter.kt` real implementation (uses `ConnectivityManager`) | FR-018, Plan §10 Phase 2 | requires: T013, T028 | Extends SetupCheckContractTest; airplane mode → NotConfigured |
| **T039** | Compose UI test `HomeScreenArch016Test`: renders from `FakeConfigEditor.appliedConfig`; **offline cold-start** renders last-applied per US-1 #2; **preset fallback** when no paired link (FR-005) | US-1, FR-002, FR-005 | requires: T029 | Test class with 3 named scenarios, all green |
| **T040** | SC-002 cold-start manual smoke + macrobenchmark scaffolding (Pixel 4a class) for Phase 8 final measurement | SC-002, Plan §6 | requires: T029 | Manual smoke shows раскладка appears ≤ 1 sec on emulator; macrobenchmark module skeleton ready |

---

## Phase 3 — Wizard extension + GMS hard-block (~1 week)

Goal: 2 new wizard steps + progress indicator + GMS hard-block before wizard.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T041** [P] | Create `core/commonMain/.../ui/setup/WizardProgressIndicator.kt` Composable — «Шаг N из M» + dots/bar | FR-008a, CHK-elderly-007 | — | Renders correctly for M=3 (Android <13) и M=4 (Android 13+) |
| **T042** | Create `core/commonMain/.../ui/setup/RoleHomeStep.kt` Composable — large explanation + "Сделать главным"/"Позже" buttons (≥ 56dp) | FR-007, US-2 | requires: T041 | Composable test renders both buttons with correct labels |
| **T043** | Create `core/commonMain/.../ui/setup/PostNotificationsStep.kt` Composable — rationale «Чтобы внук видел, что у тебя всё в порядке» + "Разрешить"/"Позже" buttons. **Android < 13 skip path** | FR-008, US-4 | requires: T041 | Composable test; skip-on-API<33 logic verified |
| **T044** | Extend `app/src/main/.../setup/FirstLaunchActivity.kt` navigation: insert RoleHomeStep + PostNotificationsStep (conditional) after preset picker; wire WizardProgressIndicator host | FR-007, FR-008, FR-008a | requires: T042, T043 | Manual walkthrough: completes all 4 (or 3 on <13) steps |
| **T045** | Implement ROLE_HOME legacy fallback for API 26-28 in `RoleHomeStep` — branch on `Build.VERSION.SDK_INT`: ≥29 uses `RoleManager.createRequestRoleIntent(ROLE_HOME)`; 26-28 uses `IntentFilter(Intent.CATEGORY_HOME)` chooser | FR-007, C-6, R2 risk | requires: T042 | Manual test on Android 8.0 emulator (API 26) — chooser opens; on Android 13 — RoleManager dialog opens |
| **T046** | Create `app/src/main/.../setup/GmsHardBlockActivity.kt` — full-screen senior-safe layout: (a) explanation text «Это устройство не поддерживается...»; (b) **clickable URL link FR-043** `https://support.google.com/googleplay/answer/9037938` (≥24sp senior-safe, `Intent.ACTION_VIEW`); (c) «Понятно» button → `finishAffinity()` | FR-042, FR-043 | requires: T004-T006 | Manual test: shows on GMS-less emulator; URL link clickable + opens browser; «Понятно» closes app affinity |
| **T047** | Hook GMS detection at FirstLaunchActivity entry: call `GmsAvailabilityPort.status()` BEFORE wizard; route to `GmsHardBlockActivity` if `MissingFatal`, system dialog if `MissingRecoverable` (FR-044) | FR-042, FR-044 | requires: T006, T046 | Integration test with FakeGmsAvailabilityPort routes correctly per status |
| **T048** [P] | Create `core/androidMain/.../adapters/setup/PostNotificationsCheckAdapter.kt` (Android 13+ only — returns `Ok` automatically on <13) | FR-018 | requires: T013, T028 | Extends SetupCheckContractTest |
| **T049** [P] | Compose UI test `WizardProgressIndicatorTest`: «Шаг 2 из 4» renders; updates after step skip/complete; M=3 when Android < 13 simulated | FR-008a | requires: T041 | Test green |
| **T050** [P] | Compose UI test `GmsHardBlockTest`: fatal → screen visible + Понятно calls `finishAffinity()`; recoverable → system dialog stubbed | FR-042..FR-044 | requires: T046, T022 | Test green using FakeGmsAvailabilityPort |
| **T051** | Manual smoke: wizard walkthrough на Android 8.0 emulator — legacy ROLE_HOME chooser opens correctly | FR-007, T045 | requires: T045 | Smoke log entry в `smoke-checkpoint.md` |
| **T052** | Manual smoke: wizard walkthrough на Android 13+ emulator — POST_NOTIFICATIONS step appears, grant/deny paths work | FR-008, US-4 | requires: T044 | Smoke log entry |
| **T053** | Manual smoke: GMS-less emulator scenario — hard-block screen shown, app closes on Понятно | FR-042 | requires: T046, T047 | Smoke log entry |

---

## Phase 4 — Call confirmation dialog (~1 week)

Goal: custom call confirmation replaces dialer two-tap flow.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T054** | Create `core/commonMain/.../ui/dialog/CallConfirmationDialog.kt` — full-screen senior-safe: photo (or initials placeholder when null), name, formatted number, **CANCEL (filled large left)** + **CALL (outlined right)**, both ≥ 56dp | FR-011, FR-015, CHK-elderly-006 | — | Composable test; tap-target measurement ≥ 56dp |
| **T055** | Create `core/commonMain/.../ui/dialog/CallPhoneRationaleScreen.kt` Composable — first-tap rationale «Чтобы звонок шёл сразу одной кнопкой» + Allow/Skip; FR-013 | FR-013, US-3 | — | Composable test renders both buttons |
| **T056** | Implement WhatsApp variant of CallConfirmationDialog (FR-014): same UI, WhatsApp icon instead of phone icon, deep-link `https://wa.me/<phone>` on CALL | FR-014, US-3 #6 | requires: T054 | Integration test verifies wa.me URI is used |
| **T057** | Create `core/androidMain/.../util/PhoneNumberFormatter.kt` (locale-aware phone formatting; libphonenumber-style via `android.telephony.PhoneNumberUtils.formatNumber()` with locale) | FR-011, CHK-localization-008 | — | Unit test: «+79161234567» (RU) → «+7 (916) 123-45-67»; «+14155550123» (US) → «+1 415-555-0123» |
| **T058** | Extend `:app/androidMain/.../call/PhoneHandler.kt` (спек 5 T541): conditional `Intent(ACTION_CALL, ...)` if `ContextCompat.checkSelfPermission(CALL_PHONE) == GRANTED`, else fallback `Intent(ACTION_DIAL, ...)` | FR-012, A-4 | requires: T001 | Unit test with mocked PackageManager + permission checker — verifies correct Intent action |
| **T059** | Update `specs/005-action-architecture-v2/tasks.md` с cross-reference: T541 PhoneHandler extended by спец 010 FR-012 | A-4, Plan §8 | requires: T058 | Diff in спека 5 tasks.md showing cross-ref note |
| **T060** [P] | Create `core/androidMain/.../adapters/setup/CallPhoneCheckAdapter.kt` real implementation | FR-018 | requires: T013, T028 | Extends SetupCheckContractTest |
| **T061** | TalkBack focus order in CallConfirmationDialog: `Modifier.semantics { traversalIndex = -1f }` on CANCEL — CANCEL is focused first | FR-011, CHK-accessibility-011 | requires: T054 | Manual TalkBack walkthrough verifies CANCEL is first focus on dialog open |
| **T062** [P] | Compose UI test `CallConfirmationDialogTest`: tap targets ≥ 56dp; invalid-number disables CALL + shows «Номер некорректен» (FR-015); photo placeholder fallback to initials; back button returns без side effects (FR-016) | FR-011, FR-015, FR-016 | requires: T054 | Test with 4+ named scenarios |
| **T063** [P] | Integration test verifying `Intent(ACTION_CALL, "tel:+79161234567")` resolves on Android 11+ — confirms `<queries>` declaration T003 effective | T003, FR-012 | requires: T003, T058 | `packageManager.queryIntentActivities()` returns non-empty on emulator |
| **T064** | Manual smoke: 2-tap call SC-003 verification (tap tile → press CALL → ringing) when CALL_PHONE granted; 3-tap when denied (fallback) | SC-003 | requires: T058 | Smoke log entry в `smoke-checkpoint.md` |
| **T065** | Manual TalkBack walkthrough: CANCEL focused first (CHK-accessibility-011), CALL second; full call confirmation read aloud | CHK-accessibility-011, US-3 | requires: T061 | TalkBack walkthrough log в smoke |

---

## Phase 5 — Settings + soft-checks engine (~1 week)

Goal: 5 SetupChecks registered, `!N` + `?M` badges live, exception handling.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T066** | Register 5 SetupCheck implementations via Koin `single<List<SetupCheck>>` (RoleHomeCheck, PostNotificationsCheck, CallPhoneCheck, NetworkOnlineCheck, BatteryOptimizationCheck) — **NO Registry class** per C-3 | FR-018, C-3 (D-2) | requires: T037, T038, T048, T060, T074 | DI test resolves list with 5 elements; no `class SetupCheckRegistry` exists |
| **T067** | Create `core/commonMain/.../ui/setup/SetupChecksBadge.kt` Composable — `[!] N` red + `[?] M` yellow; text labels «критично» / «рекомендуется»; shape-different icons (triangle vs circle); TalkBack `contentDescription` via plurals | FR-019, CHK-accessibility-005 | requires: T068, T069 | Composable test verifies shapes, colors, hidden-when-0 logic, contentDescription |
| **T068** | Create `plurals` resource `setup_badge_required_count_a11y` в `app/src/main/res/values/strings.xml` + `values-ru/strings.xml` — Russian forms `one/few/many/other` per ICU rules | FR-019, CHK-localization-005, C-8 | — | XML validates; `plurals` test loads correct form for N=1, 2, 5, 21 (Russian) |
| **T069** | Create `plurals` resource `setup_badge_recommended_count_a11y` — same pattern для Recommended | FR-019, CHK-localization-005, C-8 | — | XML validates; tests pass |
| **T070** | Define yellow badge color `#D97706` в theme (WCAG 3.1:1 on white) — **NOT** Material Yellow `#FFEB3B` per C-7 | FR-019, CHK-accessibility-005, C-7 | — | Theme file diff shows `#D97706`; Accessibility Scanner CI passes on Settings screen |
| **T071** | Create `core/commonMain/.../ui/setup/WhatNeedsConfiguringScreen.kt` Composable — two sections «Срочно настроить» (Required) + «Можно настроить позже» (Recommended); each item: description + «Настроить» button → `IntentSpec` → real Intent | FR-020 | requires: T013, T067 | Composable test renders both sections; «Настроить» tap triggers correct IntentSpec |
| **T072** | Implement FR-020a execution model: SetupCheck run on app cold-start (warm cache via `LaunchedEffect`) + on Settings screen `Lifecycle.RESUMED` (re-run всех с `surfaces.contains(Settings)`) | FR-020a, Plan §6 | requires: T066 | Integration test simulates cold-start + RESUMED — verifies check() called appropriate times; no background polling started |
| **T073** | Add diagnostic event type `setupCheckException(checkId: String, reason: String)` to diagnostic events catalog (no PII per CHK-security-004) | FR-020b, CHK-security-004 | — | Diagnostic event class compiles; usage в T075 |
| **T074** [P] | Create `core/androidMain/.../adapters/setup/BatteryOptimizationCheckAdapter.kt` real implementation (uses `PowerManager.isIgnoringBatteryOptimizations(packageName)`) — **explicit try-catch для Xiaomi SecurityException** per FR-020b R5 risk | FR-018, FR-020b, R5 | requires: T013, T028 | Extends SetupCheckContractTest; emulated SecurityException → returns NotConfigured |
| **T075** | Implement FR-020b exception handling: wrap each `SetupCheck.check()` invocation в try-catch; на exception → `CheckStatus.NotConfigured(reason = exception.message)` + diagnostic event `setupCheckException(checkId, reason)` | FR-020b | requires: T066, T073 | Unit test with intentionally-throwing FakeSetupCheck — Settings UI doesn't crash; diagnostic event emitted |
| **T076** [P] | Compose UI test `SetupChecksBadgeTest`: `[!] 2 критично` + `[?] 3 рекомендуется` rendering; hidden when count == 0; TalkBack contentDescription pluralizes correctly (1 vs 2 vs 5) | FR-019 | requires: T067, T068, T069 | Test green; plurals correctly loaded for ru locale |
| **T077** [P] | Compose UI test `WhatNeedsConfiguringScreenTest`: Required first then Recommended; each item has «Настроить» button | FR-020 | requires: T071 | Test green |
| **T078** [P] | Test `SetupCheckExceptionHandlingTest`: mock SetupCheck throws → Settings still renders, !N includes failing check | FR-020b | requires: T072, T075 | Test green |
| **T079** | SC-004 manual verification: fresh install on Android 13+ emulator → Settings shows `!N` with `N ≥ 2` (ROLE_HOME + POST_NOTIFICATIONS) | SC-004 | requires: T044, T072 | Smoke log entry with N count |
| **T080** | SC-005 manual verification: after granting all Required → `N == 0` (no `!` badge) | SC-005 | requires: T072 | Smoke log entry |

---

## Phase 6 — Paired devices + local-first revocation (~1 week)

Goal: Settings paired-devices section + **твой local-first FR-032a pattern**.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T081** [P] | Create `core/commonMain/.../store/LocalLinkRevocationStore.kt` interface + DataStore-backed androidMain adapter; methods: `markRevoked(linkId)`, `isRevoked(linkId): Flow<Boolean>`, `clearRevoked(linkId)` | FR-032, FR-025 (in-memory ≠ persistent flag — flag IS allowed per A-13 / FR-032), Plan §3 | — | DataStore file appears at expected location; CRUD tests pass |
| **T082** | Create `core/commonMain/.../ui/paired/PairedDevicesScreen.kt` Composable — **two sections** «Кто помогает мне» (Managed-side links) + «Кому я помогаю» (Admin-side links); empty state per FR-033 | FR-029, FR-033 | requires: spec 7 LinkRegistry consumer | Composable test renders both sections with mock data |
| **T083** | Create `core/commonMain/.../ui/paired/UnlinkConfirmationDialog.kt` — **двухступенчатое** подтверждение per FR-031 Article VIII destructive pattern | FR-031 | requires: T082 | Composable test: «Прекратить помощь от Маши?» + «Маша больше не сможет менять...» + ДА/НЕТ |
| **T084** | Implement FR-032 immediate local revocation logic: on user confirm → `LocalLinkRevocationStore.markRevoked(linkId)` + emit revocation event for ConfigEditor (стоп слушать `/config/{linkId}` push'и) + stop publishing /state | FR-032, US-5 | requires: T081 | Integration test: after markRevoked, ConfigEditor.appliedConfig для этого link не обновляется; UI updates immediately (Маша исчезает) |
| **T085** | Create `:app/androidMain/.../paired/UnlinkCleanupWorker.kt` — WorkManager Worker, `Constraints.NetworkType.CONNECTED`, calls `LinkRegistry.deactivate(linkId)` | FR-032a, Plan §10 Phase 6 | requires: spec 7 LinkRegistry | Worker class compiles; CONNECTED constraint declared |
| **T086** | Wire `UnlinkCleanupWorker` enqueue on local revocation: `WorkManager.enqueueUniqueWork("unlink_<linkId>", ExistingWorkPolicy.KEEP, oneTimeRequest)` | FR-032a | requires: T084, T085 | Integration test: after markRevoked, worker is enqueued (visible в WorkManager.getWorkInfos) |
| **T087** | Implement FR-032a paths (a)/(b)/(c)/(d) в UnlinkCleanupWorker: (a) online success → deactivate + cascade cleanup + queue clear; (b) offline → toast + queued (auto retry via WorkManager); (c) reconnect → check current revoked flag → idempotent deactivate; (d) retry on failure → WorkManager exponential backoff | FR-032a | requires: T085, T086 | 4 integration tests (one per path) all green; UnlinkLocalFirstTest covers all scenarios |
| **T088** | Empty-state FR-033 in PairedDevicesScreen: «Никто пока тобой не помогает — попроси внука отсканировать QR-код» + «Показать QR» button → triggers QR-flow (спек 7 entry) | FR-033 | requires: T082 | Composable test verifies empty state visible when both lists empty; QR-flow trigger verified |
| **T089** | Create `core/commonMain/.../util/DateFormatter.kt` (locale-aware date formatting via `kotlinx.datetime` LocalDate.format) — used for «дата привязки» в PairedDevicesScreen | FR-030, CHK-localization-007 | — | Unit test: en/ru locale renders correctly |
| **T090** [P] | Compose UI test `PairedDevicesScreenTest`: two sections; «Прекратить помощь» button triggers UnlinkConfirmationDialog; empty state | FR-029, FR-031, FR-033 | requires: T082, T083, T088 | Test green |
| **T091** [P] | Integration test `UnlinkLocalFirstTest` — 4 scenarios per FR-032a paths (a/b/c/d): online/offline/reconnect/race | FR-032, FR-032a | requires: T084, T085, T086, T087 | All 4 scenarios green; idempotency confirmed for case (c) |
| **T092** | Test `LocalLinkRevocationStoreTest`: persistence across simulated process restart (close DataStore, reopen, verify flag retained) | FR-032 | requires: T081 | Test green |
| **T093** | Manual smoke: unlink while offline → Маша disappears immediately from UI → toggle WiFi on → verify server-side `/links/{linkId}.revoked = true` within 60 sec | FR-032, FR-032a | requires: T087 | Smoke log entry с Firestore timestamps |

---

## Phase 7 — Challenge gate + 7-tap gesture (~1 week)

Goal: 7-tap → challenge → admin-mode entry.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T094** | Implement 7-tap gesture detector в `core/commonMain/.../ui/screens/HomeScreen.kt` — non-interactive area (excludes tiles), ±48dp delta tolerance, ≤ 5 sec window | FR-021 | requires: T029 | Unit test: 7 taps within constraints fire callback; tap on tile doesn't count; >5sec window resets |
| **T095** | Implement vibration escalation в 7-tap detector: light (`HapticFeedbackConstants.VIRTUAL_KEY`) на taps 1-3, medium на 4-6, success pattern (`LONG_PRESS`) на 7 | FR-021 | requires: T094 | Manual test on emulator с haptic enabled — pattern audible/feelable; Edge case test with HAPTIC_FEEDBACK_ENABLED=0 — 7-tap still works without haptic |
| **T096** | Create `core/commonMain/.../ui/gate/ChallengeGateScreen.kt` host Composable — uses `rememberSaveable` для challenge state per C-1 (survives rotation); contains large CANCEL button (≥ 56dp); accepts pluggable challenge variant | FR-022, FR-026, C-1 | requires: T018, T019 | Composable test: rotation preserves challenge; CANCEL returns to home (FR-022) |
| **T097** | Create `core/commonMain/.../ui/gate/NumericEntryChallenge.kt` — small font (≤ 14sp per FR-026 + A-13) random number display + custom 56dp numeric keypad | FR-023, FR-026, A-13 | requires: T096 | Composable test: number displayed at ≤14sp; keypad buttons ≥ 56dp; correct input passes |
| **T098** | Create `core/commonMain/.../ui/gate/SequenceTapChallenge.kt` — 6 numbered buttons in random visual layout + instruction text «нажмите кнопки X, Y, Z по порядку» via plurals; wrong order → regenerate (FR-024) | FR-023, FR-024 | requires: T096 | Composable test: correct order passes; wrong order regenerates challenge |
| **T099** | FR-027 TalkBack accessibility: challenge text `importantForAccessibility="auto"` — TalkBack reads challenge aloud (accepted edge per US-7 #7) | FR-027, US-7 #7 | requires: T097, T098 | Manual TalkBack test: challenge text read; CANCEL focusable first |
| **T100** | Wire 7-tap detector → ChallengeGateScreen → on success navigate to admin-mode (спек 9 entry) per FR-022 | FR-021, FR-022 | requires: T094, T096 | Integration test: full flow 7-tap → challenge appears → correct answer → admin-mode activates |
| **T101** [P] | Compose UI test `ChallengeGateScreenTest`: gesture detection edge cases; CANCEL returns no side effects; correct answer navigates; wrong answer regenerates new challenge; rotation preserves state | FR-021, FR-022, FR-024 | requires: T094, T096, T097, T098 | Test green; rotation scenario uses StateRestorationTester |
| **T102** [P] | Manual TalkBack walkthrough per US-7 #7: 7-tap → challenge text read → user presses CANCEL → returns to home | US-7 #7, FR-027 | requires: T099 | TalkBack walkthrough log в smoke |
| **T103** | Edge case test: vibration disabled (`HAPTIC_FEEDBACK_ENABLED=0`) — 7-tap still works without haptic feedback | FR-021, Edge case | requires: T095 | Integration test simulates setting off; gesture still detects |

---

## Phase 8 — Verification & ship-readiness (~0.5 week)

Goal: validation pass + handoff.

| ID | Task | Trace | Dependencies | Acceptance |
|----|------|-------|--------------|------------|
| **T104** | Run `/speckit.analyze` full audit (procedure-cross-artifact-trace + re-run all 13 checklists с current state) | Plan §10 Phase 8 | requires: T029-T103 | analyze-report.md generated с findings list |
| **T105** | **Senior-safe walkthrough plan**: prepare 5 elder-user test scenarios (fresh install wizard, tile→call, accidental 7-tap+cancel, TalkBack admin entry); document in `smoke/senior-safe-walkthrough.md` | CHK-elderly-022 | requires: Phase 7 done | Doc exists with 5 scenarios + expected outcomes |
| **T106** | OEM matrix smoke на 3 устройствах: Samsung One UI (CALL flow), Xiaomi MIUI (SetupCheck BatteryOptimization exception path FR-020b), Pixel emulator (baseline) | Plan §10 Phase 8, R5 risk | requires: Phase 5 done | OEM matrix doc в `smoke/oem-matrix.md` with results |
| **T107** | Macrobenchmark SC-002 final pass: HomeScreen cold-start first-frame ≤ 1 sec on Pixel 4a class | SC-002, Plan §6 | requires: T040, T029 | macrobenchmark report shows p95 ≤ 1000ms; saved в `perf-checkpoint.md` |
| **T108** | APK size delta verification SC-009: release build vs спек 9 release ≤ +500 KB | SC-009 | requires: Phase 7 done | `apkdiff` output saved в `perf-checkpoint.md` |
| **T109** | Update `perf-checkpoint.md` с SC-002 + SC-009 results + macrobenchmark methodology | Plan §10 Phase 8 | requires: T107, T108 | File exists с numbers |
| **T110** | Update `smoke-checkpoint.md` со всеми manual smoke entries (T051-T053, T064-T065, T079-T080, T093, T102, T105, T106) | Plan §10 Phase 8 | requires: all Phase 3-7 smoke tasks done | File aggregates all smoke logs |
| **T111** | Close `TODO-ARCH-016` в `docs/dev/project-backlog.md`: status → `✅ DONE 2026-MM-DD`, link to implementation commits | ARCH-016 | requires: T029-T036 | Backlog diff shows DONE marker + commit refs |
| **T112** | Update `docs/product/roadmap.md` спек 010 status: «Не начат» → «Готов» (после ship); add reference to PR | Plan §10 Phase 8 | requires: T111 | Roadmap diff shows updated status |
| **T113** | Re-run all 13 checklists post-implementation per `/speckit.analyze` (verifies actual code matches spec.md/plan.md) | Plan §10 Phase 8 | requires: T104 | All checklist results captured в analyze-report.md |
| **T114** [P] | Cross-artifact trace verification via `procedure-cross-artifact-trace` — every FR has covering task; every contract roundtrip exists; every removed file has deletion task | Plan §10 Phase 8 | requires: T113 | Trace report green; no orphan FR/task |
| **T115** [P] | FR-039 localization audit: grep всех новых Kotlin files (`specs/010-*` touched code) на hardcoded Russian/English string literals в Composables. Hardcoded strings → fix to `stringResource(R.string.…)` reference | FR-039, CHK-localization-001 | requires: T044, T054, T067, T071, T082, T096 | Grep `'[^"]*[А-Яа-я][^"]*'` в .kt files returns ноль matches вне `strings.xml`; same для English literals в UI strings |
| **T116** | Final code review preparation: open PR with `ultrareview` ready label; checklist of all closed CHK findings | Plan §10 Phase 8 | requires: T104-T115 | PR opened on GitHub; description references _overview.md + plan.md Constitution Check |

---

## Cross-references and traceability

### Constraint compliance (plan §11 C-1..C-10)

| Constraint | Enforcing tasks |
|------------|------------------|
| **C-1** rememberSaveable challenge | T096 |
| **C-2** no persistent challenge counter | Konsist gate T008 enforces no DataStore keys for challenge; reviewed in T104 |
| **C-3** List<SetupCheck> not Registry | T026, T066 |
| **C-4** generateRandomChallenge free function | T019 |
| **C-5** Surface.MainScreen documented seam | T015 inline TODO |
| **C-6** ROLE_HOME legacy fallback TODO | T037, T045 |
| **C-7** Yellow `#D97706` (not Material Yellow) | T070 |
| **C-8** Russian plurals 4 forms | T068, T069 |
| **C-9** exported="false" | T010 Konsist gate |
| **C-10** GMS single adapter | T006 |

### FR coverage map

Verified в T114 cross-artifact trace. All 44 FR от спека 10 traced to at least one task (sample):
- FR-001..FR-006 (ARCH-016) → T029, T030, T039
- FR-007, FR-008, FR-008a (wizard) → T042, T043, T041, T044
- FR-011..FR-016 (call) → T054, T055, T056, T058, T062
- FR-017, FR-018, FR-019, FR-020, FR-020a, FR-020b → T013, T066, T067, T071, T072, T073
- FR-021..FR-027 (challenge gate) → T094-T100
- FR-029..FR-033 (paired devices) → T081-T088
- FR-039 (localization) → enforced via FR-039 mandate + T068/T069 plurals
- FR-040 (no wire format change) → confirmed via no contracts/ task
- FR-042..FR-044 (GMS hard-block) → T046, T047

### SC verification

| SC | Verification task |
|----|--------------------|
| SC-001 (push ≤10 sec) | Manual smoke T040 (post-Phase 2 ARCH-016 done) |
| SC-002 (cold-start ≤1 sec) | T040 + T107 macrobenchmark |
| SC-003 (call in 2 taps) | T064 |
| SC-004 (fresh install `!N≥2`) | T079 |
| SC-005 (after grant `N=0`) | T080 |
| SC-007 (challenge FP ≤1%) | T025 unit test |
| SC-008 (mock removal CI green) | T031-T036 |
| SC-009 (APK delta ≤500KB) | T108 |

### External artifacts touched

| External artifact | Touching tasks |
|--------------------|------------------|
| `app/src/main/AndroidManifest.xml` | T001, T002, T003, T044, T046 |
| `docs/compliance/permissions-and-resource-budget.md` | T011 |
| `docs/dev/project-backlog.md` | T111 |
| `docs/product/roadmap.md` | T112 |
| `specs/005-action-architecture-v2/tasks.md` | T059 |
| Спек 5 `PhoneHandler` | T058 |
| Спек 7 `LinkRegistry` | T085 (consumer) |
| Спек 8 `ConfigEditor` | T029, T084 (consumer) |

---

## Что внутри (TL;DR на русском)

Это **117 конкретных задач** (T001..T116 + T032a inserted post-analyze) для реализации спека 010, разложенных по 8 фазам (~7 недель). Каждая задача имеет ID, traceability на FR/SC/Plan/Constraint, dependencies, и acceptance criteria (тест name или manual check).

**Phase 0 (T001-T012) — CRITICAL preflight** — 3 critical fixes из checklists: `<queries>` для tel: в AndroidManifest (без него call broken на Android 11+), GmsAvailabilityPort port introduction (чтобы domain не видел vendor API), Konsist gates × 4 для domain isolation. Также — обновление compliance documents.

**Phase 1 (T013-T028)** — domain types и порты в `commonMain`: SetupCheck, Challenge, SlotToActionMapper, GmsAvailabilityPort, плюс 6 fake adapters для тестов (mock-first per CLAUDE.md rule 6).

**Phase 2 (T029-T040)** — **ARCH-016 closure** (closes TODO-ARCH-016): HomeScreen теперь читает из `/config/current` спека 8, удаление `flows_mock_*.json`, переписать 3-5 Robolectric тестов на FakeRemoteSyncBackend.

**Phase 3 (T041-T053)** — wizard расширение (ROLE_HOME с Android 8/9 fallback, POST_NOTIFICATIONS на 13+, прогресс-индикатор «Шаг N из M») + GMS hard-block screen для устройств без Google Play Services.

**Phase 4 (T054-T065)** — call confirmation dialog (≥56dp кнопки CANCEL/CALL, photo placeholder, WhatsApp variant, PhoneHandler extension, TalkBack focus-first на CANCEL).

**Phase 5 (T066-T080)** — Settings + soft-checks engine: 5 SetupCheck implementations, badges `[!] N` + `[?] M` с text labels + shape-different icons + Russian plurals (4 формы) + FR-020b exception handling.

**Phase 6 (T081-T093)** — paired devices + **твой local-first revocation pattern**: LocalLinkRevocationStore (DataStore-backed), UnlinkCleanupWorker (WorkManager CONNECTED), 4 path scenarios (online/offline/reconnect/race).

**Phase 7 (T094-T103)** — challenge gate: 7-tap detection (любая non-interactive область, ±48dp, ≤5sec), vibration escalation, NumericEntry + SequenceTap variants, rememberSaveable для rotation survival.

**Phase 8 (T104-T115)** — verification + ship: /speckit.analyze audit, senior-safe walkthrough на 5 elder users, OEM matrix, macrobenchmark SC-002, APK delta SC-009, perf/smoke checkpoints, закрытие TODO-ARCH-016 в backlog.

**Constraint policies** (C-1..C-10 из plan.md §11) — обязательные правила, например: rememberSaveable для challenge (не ViewModel), List<SetupCheck> injection (не Registry class), generateRandomChallenge free function (не interface), Yellow #D97706 (не Material Yellow), Russian plurals 4 формы, exported=false для всех новых Activities.

**Следующий шаг:** human review tasks.md → `/speckit.analyze` pre-implementation audit → начало имплементации с Phase 0.
