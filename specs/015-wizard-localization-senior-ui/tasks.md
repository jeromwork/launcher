# Tasks: F-3 Wizard Module + Localization + Senior UI Kit

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-06-16
**Prerequisites**: spec.md (77 FR, 20 SC, 32 C-resolutions), plan.md (Constitution Check 8/8 PASS), research.md, data-model.md, contracts/wire-formats.md, quickstart.md.

**Format**: `[ID] [P?] [Phase] Description (FR/SC/§Plan refs)`

- **[P]**: Parallel-safe (different files, no dependencies).
- **Phase**: 0 spike, 1 foundation, 2 wire formats, 3 wizard, 4 localization, 5 ui-senior, 6 integration.

Total: **124 tasks** across 7 phases. Estimated **3-4 недели** + 2 дня spike (per spec Effort estimate).

---

## Phase 0 — Library Spike (BLOCKER, 2 days)

**Goal**: Validate moko-resources + Konsist choices через A/B before implementation. Per C-30 + research.md.

- [ ] **T001** Execute Day 1 spike — moko-resources vs Compose Multiplatform Resources. Create `spike-strings/` minimal KMP project per [research.md §Day 1](research.md). Score both variants on mandatory + weighted criteria. (Plan §10, research.md)
- [ ] **T002** Execute Day 2 spike — Konsist vs ArchUnit-kotlin. Reuse `spike-strings/`, add fake modules per [research.md §Day 2](research.md). Score. (Plan §10, research.md)
- [ ] **T003** Document spike results: write `research-day1-strings.md` + `research-day2-lint.md`. Update [spec.md C-8 + C-15](spec.md#clarifications) с final library choices. Update [plan.md §2 + §8](plan.md). (research.md)

**Checkpoint**: Library choices fixed. If oба candidates fail в любой категории — pause + fresh clarify session.

---

## Phase 1 — Foundation (Module skeletons, DI, CI gates, 3-4 days)

**Goal**: Three empty modules build green; CI fitness functions active. Closes US-5 (lint guard).

- [ ] **T004** [P] Create `core/wizard/` KMP module: `build.gradle.kts` с `kotlin { jvm(); androidTarget() }`, `commonMain + androidMain + commonTest` source sets. (Plan §4, FR-001)
- [ ] **T005** [P] Create `core/localization/` KMP module аналогично. (FR-026)
- [ ] **T006** [P] Create `core/ui-senior/` Android library module (`com.android.library`, не KMP per C-7). (FR-033)
- [ ] **T007** Update root `settings.gradle.kts`: include `:core:wizard`, `:core:localization`, `:core:ui-senior`. (Plan §4)
- [ ] **T008** Add moko-resources Gradle plugin to `core/localization/build.gradle.kts` (per T003 choice). (FR-026)
- [ ] **T009** Add Konsist test dependency to all three core modules (per T003 choice). (FR-038)
- [ ] **T010** Write Konsist arch test `CoreToAppImportGuardTest` в `core/wizard/src/commonTest`: fails если any file in `core/wizard/` импортирует `com.eastclinic.app.*`. (FR-038, US-5)
- [ ] **T011** Write Konsist directional rule: `core/wizard/` → `core/localization/` OK; `core/wizard/` → `core/ui-senior/` forbidden; `core/ui-senior/` → other core forbidden. (FR-038a)
- [ ] **T012** Add Gradle task `checkLauncherAgnosticImports` aggregating Konsist tests from all three modules; wire в `./gradlew check`. (FR-041)
- [ ] **T013** Add Konsist failure-message customization: include path + class + rationale + suggested fix. (FR-039)
- [ ] **T014** Verify CI baseline: `./gradlew check` passes green с empty modules + arch tests active. Smoke test «forbidden import пишет понятную ошибку и fails build» — verifies SC-004.

**Checkpoint**: 3 modules exist, build green, Konsist fitness function blocks `core/* → app/*` imports. Foundation ready.

---

## Phase 2 — Wire formats & ports (parallel-friendly, 4-5 days)

**Goal**: 5 JSON schemas + 3 persistent formats parsable с roundtrip + forward-compat + hard-fail tests. Closes US-6.

### Domain data types (commonMain)

- [ ] **T015** [P] [US-6] Define `WizardManifest` data class + `StepEntry` + sealed `StepType` в `core/wizard/.../data/WizardManifest.kt`. (FR-012, contracts/wire-formats.md §Schema 1)
- [ ] **T016** [P] [US-6] Define `ScreenLayout` data class + `ToolbarSpec` + `TabSpec`. (FR-013)
- [ ] **T017** [P] [US-6] Define `TileSet` + `TileSpec` + `GridPosition`. (FR-014)
- [ ] **T018** [P] [US-6] Define `SystemSettingsPool` + `SystemSettingEntry` + sealed `SettingMechanism` + enum `DetectionStrategy`. (FR-052, FR-053)
- [ ] **T019** [P] [US-6] Define `UICustomizationPool` + `UIOptionEntry` + sealed `UIOptionKind` + `Choice` + `ChoicesFromRef`. (FR-014a)
- [ ] **T020** [P] Define persistent format types: `WizardCheckpoint` (с schemaVersion), `UserPreferences` (с schemaVersion + `AttestationRecord`), `DismissedHintsState`. (FR-003, FR-047, contracts/wire-formats.md)

### Ports (commonMain interfaces)

- [ ] **T021** [P] Define `ConfigSource` port + sealed `ConfigSourceResult` + `ConfigKind` enum. (FR-019)
- [ ] **T022** [P] Define `WizardCheckpointStore` port. (FR-006)
- [ ] **T023** [P] Define `DismissedHintsStore` port. (FR-024)
- [ ] **T024** [P] Define `UserPreferencesStore` port. (FR-047)
- [ ] **T025** [P] Define `SystemSettingPort` + sealed `SettingStatus` + sealed `ApplyResult`. (FR-054)
- [ ] **T026** [P] Define `LocaleProvider` port (returns BCP-47 String). (FR-028, A-16)
- [ ] **T027** [P] Define `Clock` port (wraps kotlinx-datetime). (A-18)
- [ ] **T028** [P] Define `AnimationPreferenceProvider` port. (FR-036a)
- [ ] **T029** [P] Define `DiagnosticEmitter` port + sealed `DiagnosticEvent`. (A-17)
- [ ] **T030** [P] Define `PermissionRequestPort` + sealed `PermissionResult`. (Plan §5)

### Fake adapters (commonTest)

- [ ] **T031** [P] Implement `FakeConfigSource` (in-memory Map<ConfigKind, Map<id, ConfigDocument>>). (FR-022)
- [ ] **T032** [P] Implement `InMemoryCheckpointStore`. (FR-006)
- [ ] **T033** [P] Implement `InMemoryDismissedHintsStore`. (FR-024)
- [ ] **T034** [P] Implement `InMemoryUserPreferencesStore`. (FR-048)
- [ ] **T035** [P] Implement `FakeSystemSettingAdapter`. (FR-056)
- [ ] **T036** [P] Implement `FakeLocaleProvider`. (Local Test Path)
- [ ] **T037** [P] Implement `FakeClock`. (A-18)
- [ ] **T038** [P] Implement `FakeAnimationPreferenceProvider`. (FR-036a)
- [ ] **T039** [P] Implement `RecordingDiagnosticEmitter`. (A-17)
- [ ] **T040** [P] Implement `FakePermissionRequestPort`. (Plan §5)

### Roundtrip + compat tests

- [ ] **T041** [P] [US-6] Roundtrip test для `wizard.manifest`: fixture → serialize → deserialize → assertEquals. Test fixture `test-app-family.json`. (FR-017, SC-002)
- [ ] **T042** [P] [US-6] Roundtrip test для `screen.layout`. Fixture `test-3x4.json`. (FR-017)
- [ ] **T043** [P] [US-6] Roundtrip test для `tile.set`. Fixture `test-classic-6.json`. (FR-017)
- [ ] **T044** [P] [US-6] Roundtrip test для `system-settings.pool`. Fixture `test-pool.json`. (FR-017)
- [ ] **T045** [P] [US-6] Roundtrip test для `ui-customization.pool`. Fixture `test-ui-pool.json`. (FR-017)
- [ ] **T046** [P] [US-6] Roundtrip test для `WizardCheckpoint` persistent format. (FR-017)
- [ ] **T047** [P] [US-6] Roundtrip test для `UserPreferences` persistent format. (FR-017)
- [ ] **T048** [P] [US-6] Forward-compat test: fixture `tile-set-with-future-fields.json` (unknown additive поле в body) → parses успешно, unknown field dropped. (FR-015, SC-008)
- [ ] **T049** [P] [US-6] Hard-fail test: fixture `tile-set-future-version.json` (schemaVersion=999) → reader возвращает `IncompatibleVersion(999, 1)`. (FR-016, SC-009)
- [ ] **T050** [P] [US-6] Hard-fail UI fallback test: при `IncompatibleVersion` UI показывает Play Store fallback screen (per Q-6 (b)), app не закрывается. (FR-016, instrumented test)

**Checkpoint**: 5 schemas serializable, 3 persistent stores ready, 10 fakes available. US-6 closed.

---

## Phase 3 — Wizard engine & steps (1 week)

**Goal**: WizardEngine state machine + 3 step types работают. Closes US-1 (main flow), US-2 (resume), US-7 (hints).

- [ ] **T051** Implement `WizardEngine` interface + impl `WizardEngineImpl` в commonMain. State machine: `run(manifest) → traverse steps → checkpoint after each → return WizardOutcome`. (FR-002, FR-003)
- [ ] **T052** [US-2] Implement checkpoint resume logic: load existing checkpoint at `run()` start; resume from `currentStepIndex`; restore `answers`. (FR-004, SC-005)
- [ ] **T053** [US-2] Implement in-progress answer preservation via `rememberSaveable` pattern (documented для Compose host integration). (FR-003a, SC-005a)
- [ ] **T054** Implement `wizardCompleted(appFamilyId)` flag persistence via `UserPreferencesStore` extension. (FR-005)
- [ ] **T055** [US-3 cross-cut] Implement `WizardEngine.diffPending(savedManifest, currentManifest)` для delta wizard. (FR-014b, SC-002a)
- [ ] **T056** Implement `autoOrder` support: если `wizard.manifest.body.autoOrder = true`, engine ignores explicit `steps[]`, генерирует Required-first order из обоих pools. (FR-014c, SC-002b)
- [ ] **T057** [US-1] Implement `UIChoiceStep`: reads `UIOptionEntry` from ui-pool; renders Compose UI (simple-choice via radio, pick-from-bundled via list); writes result to `UserPreferencesStore` или ConfigDocument depending on option. (FR-008)
- [ ] **T058** [US-1] Implement `SystemSettingStep`: reads `SystemSettingEntry`; calls `SystemSettingPort.applyOrPrompt`; awaits return; calls `.status()` to verify. (FR-008)
- [ ] **T059** Implement `SystemSettingStep` denial flow: rationale screen + retry/skip; permanent denial → Settings app deep-link. (FR-008a)
- [ ] **T060** [Accessibility] Implement state-change announcements via `LiveRegionAnnouncement` (FR-008b): «Шаг N из M», step success, completion. Strings via `StringResolver`.
- [ ] **T061** [US-7] Implement `TutorialHintStep` + `TutorialHintManager`: показывает overlay, ждёт «Понял», persists dismissed flag. (FR-023, FR-024, FR-025)
- [ ] **T061a** [P] [US-7] Test `TutorialHintManagerTest`: show hint → user dismisses → assert dismissed flag persisted; re-call show с same hintId → assert overlay не появляется (`isDismissed=true`); different hintId → overlay появляется. Closes US-7 acceptance criteria. (FR-024, FR-025, US-7)
- [ ] **T062** Implement System Back behaviour: на step N>0 → previous step (preserve answers); на step 0 → no-op + toast «Чтобы выйти, закройте приложение». (FR-008d, per Q-1 (b) + EF-2)
- [ ] **T063** Implement Save+Continue dialog on Back step 0: «Сохранить прогресс? [Да/Нет]» → если Да → finishAffinity, checkpoint preserved. (Q-1 (b))

### Engine tests (JVM unit, commonTest)

- [ ] **T064** [P] `WizardEngineTraversalTest`: 5-step fixture → completed → outcome contains all answers. (SC-001)
- [ ] **T065** [P] `WizardEngineResumeTest`: simulate process death after step 3 → reload → resumes from step 3. (SC-005)
- [ ] **T066** [P] `WizardEngineDiffPendingTest`: v1 manifest 5 steps + v2 manifest 6 steps (1 new Required + 1 new Optional) → diff returns both с correct criticality. (SC-002a)
- [ ] **T067** [P] `WizardEngineAutoOrderTest`: manifest с autoOrder=true → Required entries first, Optional after. (SC-002b)

**Checkpoint**: Wizard runs end-to-end on fakes. US-1, US-2, US-7 closed.

---

## Phase 4 — Localization (3-4 days)

**Goal**: 11-locale support, CI fitness function, translation pipeline ready. Closes US-3.

- [ ] **T068** [P] Implement `StringResolver` port impl в commonMain (delegates к moko `MR.strings`); fallback chain: requested → EN → key literal. (FR-027, FR-029, US-3)
- [ ] **T069** [P] Implement `RtlHelper.layoutDirectionFor(localeTag: String): LayoutDirection` (RTL для AR/HI). (FR-032)
- [ ] **T070** [P] Create base `core/localization/src/commonMain/resources/MR/base/strings.xml` с initial set keys (wizard nav, system settings labels, UI options). (FR-030)
- [ ] **T071** [P] Create 10 locale stubs (`MR/ru/`, `MR/es/`, `MR/zh/`, `MR/ar/`, `MR/hi/`, `MR/pt/`, `MR/de/`, `MR/fr/`, `MR/ja/`, `MR/kk-rLatn/`) с RU + EN заполнены manually, остальные автогенерируются через translation skill (T080). (C-6, C-9, A-15a, A-15b)
- [ ] **T072** Implement plural support для count-dependent strings (FR-031e); add `wizard_step_n_of_m` plural в base + RU + 9 generated. (FR-031e)
- [ ] **T073** Write `CheckTranslationsTest` (Konsist or custom): fails если key missing в any of 10 non-base locales. (FR-031, SC-003)
- [ ] **T074** Write `CheckContextEntriesTest`: fails если new key в `base/strings.xml` не имеет entry в `CONTEXT.json`. (FR-031b)
- [ ] **T075** [US-3] `StringResolverFallbackTest`: ja-JP locale, key absent в ja → resolves from EN; absent в EN → returns key literal. (FR-029, SC-005a related)
- [ ] **T076** Create `core/localization/strings-context/CONTEXT.json` с schemaVersion=1 + initial entries для bundled keys. (FR-031b)
- [ ] **T077** Create `core/localization/GLOSSARY.md` с canonical терминами (Tile, Wizard, Admin, Managed, Senior, ...) + tone guidelines per language. (FR-031c)
- [ ] **T078** Create skill `.claude/skills/procedure-translate-spec-strings/SKILL.md`: workflow «in end of speckit-tasks» → diff base strings → read CONTEXT → Claude API → write `<lang>/strings.xml` → git stage. (FR-031a, C-10)
- [ ] **T079** Translation skill: implement Claude API call wrapper (`scripts/translate-strings.sh` или Kotlin script). Reads `ANTHROPIC_API_KEY`. (FR-031a)
- [ ] **T080** Run translation skill on initial set (T070-T072): generate переводы для 9 не-base языков (ES/ZH/AR/HI/PT/DE/FR/JA/KK). Verify FR-031 fitness function passes. (SC-003a)
- [ ] **T081** Translation memory verification: re-run skill — existing translations НЕ regenerated unless base changed. (FR-031d)

**Checkpoint**: 11 locales complete. `checkTranslations` green. Translation skill operational. US-3 closed.

---

## Phase 5 — Senior UI primitives (3-4 days)

**Goal**: `core/ui-senior/` Compose primitives + theme + accessibility. Closes US-4.

- [ ] **T082** [P] [US-4] Implement `SeniorButton` Composable: ≥56dp height, ≥18sp text, `wrapContentWidth + wrapContentHeight`, autoMirrored icons, 16dp spacing. (FR-034)
- [ ] **T083** [P] [US-4] Implement `SeniorIconButton`. (FR-034)
- [ ] **T084** [P] [US-4] Implement `SeniorTextField`. (FR-034)
- [ ] **T085** [P] [US-4] Implement `SeniorBodyText` (≥18sp, line-height 1.5×). (FR-034)
- [ ] **T086** [P] [US-4] Implement `SeniorTitleText` (≥24sp). (FR-034)
- [ ] **T087** Implement `SeniorWarmTheme.Light` + `SeniorWarmTheme.Dark`. Warm-contrast palette с WCAG AAA ≥7:1 contrast. (FR-035)
- [ ] **T088** [P] Implement `rememberFontScaleAware()` Composable utility reading system fontScale + UserPreferences override. (FR-036)
- [ ] **T089** [P] Implement `SeniorContentDescription` helper (enforces non-empty cd or explicit clearAndSetSemantics). (FR-036, ACC-3)
- [ ] **T090** [Accessibility] Implement `LiveRegionAnnouncement` Composable для FR-008b state announcements. (FR-008b, ACC-1)
- [ ] **T091** [Accessibility] Implement `WizardProgressIndicator` Composable: «Шаг N из M» + dots, ≥18sp. (FR-008c, EF-1)
- [ ] **T092** [Accessibility] Implement `core/ui-senior/util/AnimationDuration.kt` wrapper respecting `AnimationPreferenceProvider` (0 = no animation). (FR-036a, ACC-2)
- [ ] **T093** [Accessibility] Implement `TutorialHintOverlay` Composable: anchor-positioned overlay + «Понял» button. (FR-023)
- [ ] **T094** Write Compose preview screenshot tests via Roborazzi/Paparazzi: каждый primitive на fontScale=1.0 + 2.0. (SC-006)
- [ ] **T095** Write length-expansion screenshot tests: каждый primitive в EN + DE + AR. Verify no clipped text, no overlap. (SC-006a, LU-1)
- [ ] **T096** Write RTL screenshot test: ar-SA locale wizard step render — кнопка «Назад» справа, выравнивание по правому краю. (SC-007)

**Checkpoint**: All primitives accessible (TalkBack + RTL + max fontScale + reduce-motion verified). US-4 closed.

---

## Phase 6 — Android adapters + app integration (3-5 days)

**Goal**: Real adapters in :app/androidMain, BundledConfigSource, app wiring, smoke tests.

### Real adapter implementations (androidMain)

- [ ] **T097** Implement `PersistentCheckpointStore` (DataStore Android). Key namespace `wizard.checkpoint.<manifestId>`. (FR-006)
- [ ] **T098** Implement `PersistentDismissedHintsStore` (DataStore). (FR-024)
- [ ] **T099** Implement `PersistentUserPreferencesStore` (DataStore с serialization для UserPreferences). (FR-048)
- [ ] **T100** Implement `AndroidSystemSettingAdapter`: reads `android-pool.json` via ConfigSource; dispatches per `mechanism` (StandardPermission via ActivityResultLauncher, AccessibilityService via `Settings.ACTION_ACCESSIBILITY_SETTINGS`, etc.). (FR-055)
- [ ] **T101** Implement `AndroidLocaleProvider` (reads `Resources.configuration.locales[0]`, converts to BCP-47 String). (FR-028, A-16)
- [ ] **T102** Implement `AndroidAnimationPreferenceProvider` (reads `Settings.Global.ANIMATOR_DURATION_SCALE`). (FR-036a)
- [ ] **T103** Implement `AndroidPermissionRequestPort` (ActivityResultLauncher wrapper). (Plan §5)
- [ ] **T104** Implement `BundledConfigSource` в :app: reads JSON files from moko-resources `MR.files.*`; returns `ConfigSourceResult`. Inline TODO про future FileConfigSource/NetworkConfigSource/MarketplaceConfigSource. (FR-020, FR-021)

### Bundled JSON resources

- [ ] **T105** Create `core/wizard/src/commonMain/resources/MR/files/system-settings/android-pool.json` с 6 entries per FR-053a (ROLE_HOME, POST_NOTIFICATIONS, CALL_PHONE, accessibility service, battery, hide status bar). Inline TODO про MIUI/EMUI future entries. (FR-053a, FR-053b)
- [ ] **T106** Create `core/wizard/src/commonMain/resources/MR/files/ui-customization/ui-pool.json` с 6 entries per FR-014a (language, theme, fontScale, grid, screenLayout, tileSet). (FR-014a)

### App wiring

- [ ] **T107** Create `WizardActivity` в :app: host для WizardEngine flow. Compose-based. Wires DI module. (Plan §4)
- [ ] **T108** Create `PlayStoreFallbackActivity` в :app: shown on `ConfigSourceResult.IncompatibleVersion`. «Обновите приложение» + Play Store deep-link. (FR-016, EF-3)
- [ ] **T109** Create `AppWizardModule` (DI binding): production wires Persistent*Store, AndroidSystemSettingAdapter, BundledConfigSource. Steps Map<StepType, WizardStep> с 3 entries (UIChoice, SystemSetting, TutorialHint). (FR-009, Plan §4)
- [ ] **T110** WizardEngine routing on app cold start: check `wizardCompleted(appFamilyId)` → if false, route to WizardActivity; else route to HomeActivity (existing). (FR-005)

### READMEs (extraction discipline)

- [ ] **T111** [P] Create `core/wizard/README.md` с EXTRACT CANDIDATE marker per FR-042. Включить server-roadmap TODO про NetworkConfigSource (FR-046).
- [ ] **T112** [P] Create `core/localization/README.md` с EXTRACT CANDIDATE marker + Translation pipeline setup section (ANTHROPIC_API_KEY env var per quickstart.md §4). (FR-042, FR-031a)
- [ ] **T113** [P] Create `core/ui-senior/README.md` с EXTRACT CANDIDATE marker + iOS UI primitives future TODO. (FR-042, A-12)

### Cross-spec doc updates

- [ ] **T114** [P] Update `docs/dev/server-roadmap.md`: добавить (a) NetworkConfigSource trigger (FR-046), (b) UserPreferences cloud sync via spec 008 после F-4 (FR-051).
- [ ] **T115** [P] Update `docs/dev/project-backlog.md`: закрыть `TODO-FUTURE-SPEC-006` (onboarding-and-tutorials поглощено F-3); добавить «UserPreferences ContentProvider для care family ecosystem» entry per FR-051 второй TODO.
- [ ] **T116** [P] Update `docs/product/roadmap.md` §Шаг 1 F-3: статус Draft → InProgress.
- [ ] **T117** [P] Update `docs/dev/adrs/ADR-004-localization-and-global-readiness.md` (если existing): override base language = EN per C-6 + A-15b. Если ADR-004 не existing — создать.
- [ ] **T118** [P] Update `docs/dev/capability-registry-pending.md`: добавить F-3 capabilities (wizard.start, wizard.skipToStep, localization.resolve, tileSet.list, systemSettings.applyOrPrompt) per CR-1.

### Integration tests + perf

- [ ] **T119** [Smoke] Write `WizardE2ETest` (androidTest): fresh install on Pixel 5 API 34 → wizard runs все шаги → reach HomeActivity. (SC-005 covered, US-1)
- [ ] **T120** [Smoke] Write `WizardLocaleChangeTest`: process running wizard, change system locale via `LocaleList.setDefault()` + recreate Activity → wizard re-renders в новом языке < 500ms. (SC-005a, US-3)
- [ ] **T121** [Perf] Write Android Macrobenchmark `WizardColdStartBenchmark`: measure first-run cold start от Application.onCreate до first wizard step frame. Target ≤300ms на Pixel 5 API 34. (SC-001a)
- [ ] **T122** Create `specs/015-wizard-localization-senior-ui/perf-checkpoint.md` с measured numbers from T121. (Plan §14, PF-2)
- [ ] **T123** Run `android-emulator` skill smoke: `pixel_5_api_34`, install fresh APK, run wizard end-to-end, take screenshot, verify HomeActivity reachable. (per Local Test Path)

**Checkpoint**: App runs end-to-end on эмулятор. All SCs verified. Ready for `/speckit.analyze`.

---

## Dependencies & execution order

### Phase order (sequential between phases)

```
Phase 0 (T001-T003) — spike (2 days)
  ↓
Phase 1 (T004-T014) — module skeletons (3-4 days)
  ↓
Phase 2 (T015-T050) — wire formats + ports + fakes + roundtrip tests (4-5 days, many [P])
  ↓
Phase 3 (T051-T067) — wizard engine + steps (1 week)
  ↓
Phase 4 (T068-T081) + Phase 5 (T082-T096) — parallel (localization + ui-senior, 3-4 days each)
  ↓
Phase 6 (T097-T123) — adapters + app + smoke (3-5 days)
```

### Within Phase 2 (high parallelism)

- T015-T020 (data types) all [P] — different files.
- T021-T030 (ports) all [P] — different files.
- T031-T040 (fakes) all [P] — different files.
- T041-T050 (tests) all [P] — different test files.
- **All 36 Phase 2 tasks can run в parallel** by разные devs (after T014 checkpoint).

### Within Phase 6

- Real adapters T097-T104 [P] across each.
- READMEs T111-T113 [P].
- Cross-spec doc updates T114-T118 [P].
- T119-T123 sequential (require previous phases complete).

### Critical path

T001-T014 (Phase 0+1, ~5-6 дней) → T051-T067 (Phase 3 engine, ~1 неделя) → T119-T123 (smoke + perf, ~3 дня) = **~3 недели minimum** with full parallelism.

---

## Required-task gate verification

Per CLAUDE.md rules + spec gates:

- ✅ Every wire format has roundtrip test (T041-T047) + forward-compat (T048) + hard-fail (T049). [Rule 5]
- ✅ Every new port has fake adapter (T031-T040) + real adapter (T097-T104). [Rule 6]
- ✅ Every new module has Konsist lint task (T010-T013). [Rule 7]
- ✅ Doc updates planned (T114-T118 для server-roadmap, backlog, roadmap, ADR-004, capability-registry-pending).
- ✅ Screenshot tests planned (T094-T096 для primitives, length expansion, RTL).
- ✅ Perf checkpoint planned (T121-T122).
- ✅ Smoke checkpoint planned (T123).
- ✅ Translation pipeline skill planned (T078-T079) + initial run (T080).

---

## Cross-artifact trace (FR coverage)

| FR | Covered by tasks |
|---|---|
| FR-001..006 (WizardEngine + checkpoint) | T004, T020, T051-T054 |
| FR-007..010 (WizardStep + types) | T015, T057-T061, T109 |
| FR-008a..d (denial, announcements, progress, Back) | T059, T060, T062, T063, T090, T091 |
| FR-011..018 (4 schemas + read policy) | T015-T019, T041-T050 |
| FR-014a..c (ui-pool + delta + autoOrder) | T019, T055, T056, T106 |
| FR-019..022 (ConfigSource) | T021, T031, T104 |
| FR-023..025 (TutorialHintManager) | T061, T093 |
| FR-026..032 (localization core) | T005, T068-T081 |
| FR-033..037 (senior UI) | T006, T082-T093 |
| FR-038..041 (Konsist fitness function) | T009-T014 |
| FR-042 (READMEs) | T111-T113 |
| FR-043 (rule 1 compliance) | T004-T006 (KMP setup), T010-T013 (enforcement) |
| FR-044 (literal strings ban) | T041 (roundtrip assertion check) |
| FR-045 (migration policy) | T020 (схема documented), no migration code в F-3 |
| FR-046 (server-roadmap entry) | T114 |
| FR-047..051 (UserPreferences + migration TODO) | T020, T024, T034, T099, T112 |
| FR-052..060 (Part K system settings) | T018, T025, T035, T100, T105 |

**Coverage**: 77/77 FRs covered. **All 20 SCs** have at least one verification task.

---

## Translation skill trigger (per memory `feedback_speckit_scenarios_proactive.md` + C-10)

**В конце этого `/speckit.tasks` orchestrator run**: invoke skill `procedure-translate-spec-strings`. Skill diff'нет `core/localization/src/commonMain/resources/MR/base/strings.xml` за период работы над F-3, найдёт новые ключи, и сгенерирует переводы.

В случае F-3 spec'и: skill **не находит** новых strings (мы их добавим во время implementation, не в spec phase). Skill завершается no-op'ом или с сообщением «no new keys to translate».

---

## Краткое содержание простым русским языком *(per skill `procedure-add-novice-summary`)*

Этот документ — **полный список задач** для разработчиков, чтобы построить F-3.

**Всего 123 задачи** в 7 фазах. Сначала идут зависимости, потом то, что от них зависит. Многие задачи помечены **[P]** — это значит «можно делать параллельно с другими [P] задачами», поэтому 2-3 разработчика могут работать одновременно.

**Какие фазы**:

0. **Spike** (2 дня) — проверить, что 2 библиотеки реально работают. Если нет — выбрать альтернативу.
1. **Foundation** (3-4 дня) — создать 3 пустые папки модулей + lint правило, которое будет ломать сборку при ошибках.
2. **Wire formats** (4-5 дней) — описать 5 JSON форматов в коде + тесты что они правильно читаются/пишутся.
3. **Wizard engine** (1 неделя) — главный движок мастера настройки + 3 типа шагов.
4. **Локализация** (3-4 дня) — переводы на 11 языков + автоматический переводчик через Claude API.
5. **Senior UI** (3-4 дня) — большие кнопки, тёплая тема, поддержка большого шрифта.
6. **App интеграция** (3-5 дней) — собрать всё вместе, реальные Android адаптеры (DataStore, AccessibilityService и т.д.), smoke-тесты на эмуляторе.

**Главные принципы**:
- Каждая задача — максимум 1 день, максимум 5 файлов. Большие задачи разбиты.
- Каждая задача ссылается на FR (требование из спеки) и SC (критерий успеха). Можно проверить «эта работа закрывает то-то».
- Сначала пишем «фейковые» версии (для быстрых тестов без эмулятора), потом настоящие (для production).
- Каждый JSON формат имеет минимум 3 теста: что читается/пишется, что новые поля игнорируются, что несовместимая версия не ломает приложение.

**Что в конце**: smoke-тест на реальном эмуляторе — установили APK, запустили wizard, проверили что дошли до главного экрана. Если зелёное — готово к `/speckit.analyze` (финальный аудит) → внедряем код в репозиторий.

**Общее время**: примерно 3-4 недели для одного разработчика. С 2-3 разработчиками — быстрее за счёт параллельных задач.
