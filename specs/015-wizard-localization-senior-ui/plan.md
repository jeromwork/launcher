# Implementation Plan: F-3 Wizard Module + Localization + Senior UI Kit

**Branch**: `015-wizard-localization-senior-ui` | **Date**: 2026-06-16 (REVISED 2026-06-17 post pre-flight) | **Spec**: [spec.md](spec.md)
**Input**: 77 FR / 20 SC / 23 OUT / 19 A из spec.md после `/speckit.specify` + `/speckit.clarify` (38 C-resolutions including pre-flight reality check 2026-06-17) + 22 checklists.

> **⚠ REVISED 2026-06-17 post pre-flight**: половина plan.md (модульная структура, library choices) переписана после обнаружения, что F-3 должна работать **внутри существующего `:core` KMP-модуля** (per ADR-005 + spec 007 flavors). Подробности — Clarifications C-7, C-8, C-15, C-33..C-38 в spec.md.

---

## 1. Summary

F-3 — **первый шаг Phase 1**, foundation для всей Phase 1+ работы. Добавляет **пакеты в существующий `:core` KMP-модуль** (`com.launcher.api.wizard`, `com.launcher.api.localization`, `com.launcher.ui.senior`, `com.launcher.ui.wizard`), на которых строятся Simple Launcher S-1, Admin App S-2, и будущие ecosystem apps в Phase 4.

**Технический подход** (per ADR-005 + existing project stack):
- **Compose Multiplatform** (`commonMain`) для всего — wizard engine + UI primitives + step Composables. iOS support автоматически (3 iOS targets уже в `core/build.gradle.kts`).
- **Decompose** для wizard step navigation (per ADR-005 Amendment 2026-05-07a — уже в проекте).
- **Koin** для DI (per ADR-005 Amendment — уже в проекте).
- **Compose Multiplatform Resources** для строк (`compose.components.resources` — уже подключено).
- **DataStore Preferences** для simple persistence (уже в `core:androidMain`).
- **Konsist** для fitness function `ui.* → api.wizard.*` directional guard (уже в `libs.versions.toml`, в `core:androidUnitTest`).
- **5 JSON wire formats** (`wizard.manifest`, `screen.layout`, `tile.set`, `system-settings.pool`, `ui-customization.pool`) с общим 6-полевым header'ом.
- Wizard работает **локально** — без identity, без cloud (per A-10 + decision 2026-06-15-deferred-cloud).
- Compatible с обоими flavors (`realBackend` + `mockBackend`) per spec 007.

**Pre-implementation gate** (sharply reduced): library spike отменён (per C-38) — все choices зафиксированы существующим проектом. T001 = 30-минутная verification что весь стек работает в пустом scaffold.

---

## 2. Technical Context *(REVISED 2026-06-17 post pre-flight)*

**Language/Version**: Kotlin **2.0.21** (per `libs.versions.toml`), Compose Multiplatform **1.7.3**, AGP 8.7.3, JVM 17.

**Primary Dependencies** (все уже в `core/build.gradle.kts` — F-3 не добавляет новых):
- `org.jetbrains.compose.components:components-resources` — Compose Multiplatform Resources (string tables, plurals, locales, RTL)
- `io.insert-koin:koin-core` + `koin-android` — DI per ADR-005
- `com.arkivanov.decompose:decompose` + `decompose-extensions-compose` — navigation per ADR-005
- `org.jetbrains.kotlinx:kotlinx-serialization-json` — JSON wire formats (already present)
- `androidx.datastore:datastore-preferences` (1.1+) — persistent stores (already present)
- `com.lemonappdev:konsist` — architecture lint, JVM-only via `androidUnitTest` (already in `libs.versions.toml`)
- `kotlinx-datetime` — для Clock (нужно проверить наличие; вероятно уже в проекте)

**Storage**: **DataStore Preferences** для `WizardCheckpoint`, `DismissedHints`, `UserPreferences` (simple key-value с schemaVersion). Future migration в SQLDelight если структура усложнится (consistent с spec 008 `LocalConfigStore` pattern).

**Testing**:
- `commonTest` — JUnit (`kotlin-test`) для domain unit tests (WizardEngine, ports, parsers).
- `androidUnitTest` — Konsist (arch tests) + Robolectric (Compose UI tests via `androidx.compose.ui.test.junit4` — уже подключено) + **Roborazzi** (screenshot tests для SC-006/006a/007 — new dep, per C-37).
- Optional Android Macrobenchmark для SC-001a cold-start budget (new dep).

**Target Platform**: Android **minSdk 26 / targetSdk 35** (per `libs.versions.toml`) + iOS (iosX64, iosArm64, iosSimulatorArm64 — уже включены в core).

**Project Type**: KMP+CMP cross-platform launcher domain.

**Performance Goals**: `WizardEngine` first-run cold-start ≤ 300ms на Pixel 5 API 34 (SC-001a), HomeActivity не регрессирует (SC-011), APK delta ≤ +1.5 MB (SC-010).

**Constraints**: Local-only (нет network в F-3); compatible с обоими flavors `realBackend`+`mockBackend` per spec 007; respects existing two-backend pattern.

**Scale/Scope**: 4 new packages в `:core`, 77 FRs, 5 wire formats, 11 локалей, 3 step types (consolidated).

---

## 3. Constitution Check

См. секцию 12 ниже (после design). Per Article XVI: gate runs **after** design captures architecture, не до.

---

## 4. Project Structure

### Documentation (this feature)

```text
specs/015-wizard-localization-senior-ui/
├── spec.md                  # ✓ done (827 lines, 77 FR)
├── plan.md                  # THIS FILE
├── research.md              # Spike A/B methodology + library comparison
├── data-model.md            # Key types (WizardEngine, ConfigSource, etc.)
├── quickstart.md            # Dev setup (KMP, Compose Resources, Konsist, translation skill)
├── contracts/
│   └── wire-formats.md      # 5 JSON schemas consolidated
├── checklists/              # ✓ 22 files from speckit-clarify (18 violations fixed)
└── tasks.md                 # NOT in plan.md scope — generated by /speckit.tasks
```

### Source Code (внутри существующего `:core` модуля) *(REVISED 2026-06-17)*

```text
core/src/commonMain/kotlin/com/launcher/
├── api/wizard/                              [NEW package]
│   ├── WizardEngine.kt                      # port: run, diffPending, currentState
│   ├── WizardStep.kt                        # interface
│   ├── WizardOutcome.kt                     # sealed
│   ├── WizardState.kt                       # sealed
│   ├── PendingStep.kt + Criticality.kt
│   ├── ConfigSource.kt + ConfigSourceResult.kt + ConfigKind.kt
│   ├── SystemSettingPort.kt + SettingStatus.kt + ApplyResult.kt + SettingMechanism.kt
│   ├── UserPreferencesStore.kt + UserPreferences.kt + ThemeChoice.kt + AttestationRecord.kt
│   ├── WizardCheckpointStore.kt + WizardCheckpoint.kt
│   ├── DismissedHintsStore.kt
│   ├── DiagnosticEmitter.kt + DiagnosticEvent.kt
│   ├── Clock.kt                             # wraps kotlinx-datetime
│   ├── AnimationPreferenceProvider.kt
│   ├── PermissionRequestPort.kt + PermissionResult.kt
│   └── data/                                [sub-package]
│       ├── WizardManifest.kt + StepEntry.kt
│       ├── ScreenLayout.kt + ToolbarSpec.kt + TabSpec.kt
│       ├── TileSet.kt + TileSpec.kt + GridPosition.kt
│       ├── SystemSettingsPool.kt + SystemSettingEntry.kt + DetectionStrategy.kt
│       └── UICustomizationPool.kt + UIOptionEntry.kt + Choice.kt + ChoicesFromRef.kt
│
├── api/localization/                        [NEW package]
│   ├── StringResolver.kt                    # port: resolve, currentLocaleTag (BCP-47)
│   ├── LocaleProvider.kt
│   └── RtlHelper.kt
│
├── ui/senior/                               [NEW package — Compose Multiplatform]
│   ├── primitives/
│   │   ├── SeniorButton.kt                  # ≥56dp, ≥18sp, wrapContent, autoMirrored
│   │   ├── SeniorIconButton.kt
│   │   ├── SeniorTextField.kt
│   │   ├── SeniorBodyText.kt + SeniorTitleText.kt
│   ├── theme/
│   │   └── SeniorWarmTheme.kt               # Material 3 wrapper, ≥7:1 contrast
│   ├── util/
│   │   ├── FontScaleAware.kt + SeniorContentDescription.kt
│   ├── progress/
│   │   ├── WizardProgressIndicator.kt       # FR-008c visual «Шаг N из M»
│   │   └── LiveRegionAnnouncement.kt        # FR-008b TalkBack
│   └── overlay/
│       └── TutorialHintOverlay.kt
│
└── ui/wizard/                               [NEW package — Decompose host]
    ├── WizardComponent.kt                   # Decompose ComponentContext + Stack<Configuration>
    ├── WizardHostScreen.kt                  # Composable host (renders current step)
    ├── steps/
    │   ├── UIChoiceStep.kt                  # consolidates LanguageStep/ThemeStep/etc.
    │   ├── SystemSettingStep.kt
    │   └── TutorialHintStep.kt
    └── managers/
        ├── WizardEngineImpl.kt
        └── TutorialHintManager.kt

core/src/commonMain/composeResources/         [Compose Multiplatform Resources]
├── files/wizard/                            # bundled JSON
│   ├── system-settings/android-pool.json    # FR-053a — 6 entries
│   ├── ui-customization/ui-pool.json        # FR-014a — 6 entries
│   ├── wizard-manifests/                    # test fixtures
│   ├── screen-layouts/
│   └── tile-sets/
├── values/strings.xml                       # base = EN (per C-6)
├── values-ru/strings.xml                    # explicit per FR-031a
├── values-es|zh|ar|hi|pt|de|fr|ja|kk-rLatn/strings.xml
└── values/plurals.xml                       # FR-031e plurals

core/strings-context/CONTEXT.json            # FR-031b per-key context (dev-time)
core/GLOSSARY.md                             # FR-031c canonical terminology
core/scripts/translate-strings.sh            # FR-031a translation skill helper

core/src/commonTest/kotlin/com/launcher/
├── fakes/                                   # FakeConfigSource, InMemoryCheckpointStore, etc.
├── api/wizard/                              # WizardEngineTest, DiffPendingTest, AutoOrderTest, ResumeTest
├── data/                                    # Roundtrip + ForwardCompat + HardFail per schema
└── api/localization/                        # StringResolverFallbackTest, RtlHelperTest

core/src/androidMain/kotlin/com/launcher/
├── adapters/wizard/                         [NEW package — Android-specific]
│   ├── PersistentCheckpointStore.kt         # DataStore Preferences
│   ├── PersistentDismissedHintsStore.kt     # DataStore Preferences
│   ├── PersistentUserPreferencesStore.kt    # DataStore Preferences
│   ├── BundledConfigSource.kt               # Compose Resources reader
│   ├── AndroidSystemSettingAdapter.kt       # FR-055 — mechanism dispatcher
│   ├── AndroidLocaleProvider.kt             # Resources.configuration.locales → BCP-47
│   ├── AndroidAnimationPreferenceProvider.kt  # Settings.Global.ANIMATOR_DURATION_SCALE
│   ├── AndroidPermissionRequestPort.kt      # ActivityResultLauncher wrapper
│   └── SystemClock.kt                       # actual для expect declaration в commonMain
│
└── di/wizard/                               [NEW package]
    └── WizardKoinModule.kt                  # Koin module wiring all bindings

core/src/iosMain/kotlin/com/launcher/
└── adapters/wizard/                         [NEW package — iOS-specific impl]
    ├── IosCheckpointStore.kt                # NSUserDefaults wrapper
    ├── IosLocaleProvider.kt                 # NSLocale → BCP-47
    └── (other iosMain impls — partial, may stub)

core/src/androidUnitTest/kotlin/com/launcher/arch/
└── WizardArchitectureTest.kt                # Konsist — FR-038, FR-038a

app/src/main/kotlin/com/launcher/app/
├── MainActivity.kt                          # extended to route wizard vs home
├── di/
│   └── AppKoinModule.kt                     # extends WizardKoinModule
├── PlayStoreFallbackActivity.kt             # FR-016 hard-fail fallback (Q-6 (b))
└── (no new wizard-specific code — host lives in :core)
```

**Structure Decision** *(REVISED 2026-06-17)*: F-3 — это **package additions** в существующий `:core` KMP-модуль, **не новые модули**. Consistent с existing convention (`api/setup` от spec 010, `api/action` от spec 005, `adapters/*` от spec 008+011). CMP UI в `commonMain` — iOS support автоматически. Koin для DI, Decompose для navigation — уже выбраны per ADR-005.

---

## 5. Architecture (data flow)

### High-level flow: first-run wizard

```
User launches app
    ↓
WizardActivity (in :app/)
    ↓
WizardEngine.run(manifest) [core/wizard, commonMain]
    ↓ (loads via)
BundledConfigSource → ConfigSource port [читает android-pool.json, ui-pool.json, wizard.manifest]
    ↓ (iterates steps)
For each StepEntry:
    UIChoiceStep(optionId) → reads ui-pool entry, renders Compose UI
    SystemSettingStep(settingId) → reads system-settings.pool entry, opens deep-link, awaits return
    TutorialHintStep(hintId) → shows overlay
    ↓ (after each step)
WizardCheckpointStore.save(checkpoint with schemaVersion=1)
    ↓ (after last step)
WizardOutcome.Completed(initialConfig, userPreferences)
    ↓
UserPreferencesStore.save(prefs)  [theme, fontScale, language, attestedSettings]
wizardCompleted(appFamilyId) = true
    ↓
HomeActivity launches
```

### Port-adapter shape (CLAUDE.md rule 2)

| Port (commonMain) | Real adapter (androidMain or :app) | Fake adapter (commonTest) |
|---|---|---|
| `ConfigSource` | `BundledConfigSource` (:app) | `FakeConfigSource` |
| `WizardCheckpointStore` | `PersistentCheckpointStore` (DataStore) | `InMemoryCheckpointStore` |
| `DismissedHintsStore` | `PersistentDismissedHintsStore` (DataStore) | `InMemoryDismissedHintsStore` |
| `UserPreferencesStore` | `PersistentUserPreferencesStore` (DataStore) | `InMemoryUserPreferencesStore` |
| `SystemSettingPort` | `AndroidSystemSettingAdapter` | `FakeSystemSettingAdapter` |
| `LocaleProvider` | `AndroidLocaleProvider` (Resources.configuration) | `FakeLocaleProvider` |
| `StringResolver` | `AndroidStringResolverAdapter` (moko binding) | (in-memory via FakeLocaleProvider) |
| `Clock` | `SystemClock` (kotlinx.datetime.Clock.System) | `FakeClock` |
| `AnimationPreferenceProvider` | `AndroidAnimationPreferenceProvider` (Settings.Global) | `FakeAnimationPreferenceProvider` |
| `DiagnosticEmitter` | (none in F-3 — provided in S-1+) | `RecordingDiagnosticEmitter` |
| `PermissionRequestPort` | `AndroidPermissionRequestPort` (ActivityResultLauncher) | `FakePermissionRequestPort` |

### Package dependency graph (FR-038 + FR-038a Konsist enforced, REVISED 2026-06-17)

```
app/*                          → com.launcher.api.wizard.*, .ui.senior.*, .ui.wizard.*       ✓
com.launcher.api.wizard.*      → com.launcher.api.localization.*                              ✓
com.launcher.api.wizard.*      → com.launcher.ui.* (senior OR wizard)                         ✗ FORBIDDEN
com.launcher.ui.senior.*       → com.launcher.api.* (wizard OR localization)                  ✗ FORBIDDEN
com.launcher.ui.wizard.*       → com.launcher.api.wizard.*, .api.localization.*, .ui.senior.* ✓ (host slot)
com.launcher.* (any)           → com.launcher.app.* (or higher layers)                        ✗ FORBIDDEN
```

Konsist test class — `core/src/androidUnitTest/kotlin/com/launcher/arch/WizardArchitectureTest.kt`. JVM-only per existing pattern (spec 005 §8).

---

## 6. Data Model

См. [data-model.md](data-model.md) — все ключевые типы с полями и связями.

---

## 7. Wire Formats

См. [contracts/wire-formats.md](contracts/wire-formats.md) — все 5 JSON schemas + 3 persistent formats консолидированы в одном документе.

---

## 8. Dependency Impact

### Existing dependencies F-3 reuses (НЕ добавляются заново) *(REVISED 2026-06-17)*

| Dependency | Status | Use в F-3 |
|---|---|---|
| `compose.components.resources` | ✅ Уже в `core:commonMain` | String resolution + bundled JSON loading (per C-8 + C-38) |
| `io.insert-koin:koin-core` + `koin-android` | ✅ Уже в core | DI module wiring (per C-33) |
| `com.arkivanov.decompose:decompose` + `-extensions-compose` | ✅ Уже в core | Wizard step navigation (per C-34) |
| `kotlinx-serialization-json` | ✅ Уже в core | JSON wire format parsing |
| `androidx.datastore:datastore-preferences` | ✅ Уже в `core:androidMain` | Persistent stores (per C-35) |
| `compose.material3` | ✅ Уже в `core:commonMain` | Foundation для SeniorWarmTheme (per FR-035) |
| `com.lemonappdev:konsist` | ✅ Уже в `libs.versions.toml` + `core:androidUnitTest` | Architecture lint (per C-15 + C-38) |

### Новые dependencies F-3 добавляет

| Dependency | Version | Reason | Justified |
|---|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-datetime` | latest stable | `Clock` port + `AttestationRecord.attestedAt: Instant` | ✓ Standard kotlinx, KMP-friendly |
| `io.github.takahirom.roborazzi:roborazzi` | latest stable | Compose screenshot tests (SC-006, SC-006a, SC-007) per C-37 | ✓ Test-only, JVM/Robolectric-backed |

**Verify before T001**: проверить, что `kotlinx-datetime` ещё не подключён в проекте (если уже — отбросить из списка).

### APK size impact

Estimate: ~200-400 KB (только bundled JSONs ~50 KB + new Composables ~150-300 KB; library deps все уже в APK).
**Budget**: SC-010 ≤ +1.5 MB — **comfortable margin**.

### Build time impact

Estimate: **+15-30 seconds** на clean build (только новые .kt files; KMP toolchain уже set up; Compose уже compiled). Significantly lower than initial estimate в A-19 (+1-2 min was wrong assumption — не нужно setup'ить moko-resources + Konsist отдельно).

---

## 9. Test Strategy

### Layer 1 — Domain unit tests (JVM, no emulator)

- WizardEngine state machine: traversal (SC-001), resume (SC-005), diffPending (SC-002a), autoOrder (SC-002b).
- ConfigSource: roundtrip для каждой из 5 schemas (SC-002).
- StringResolver: fallback chain (requested → EN → key).
- UserPreferencesStore: roundtrip persistent format (with schemaVersion).
- SystemSettingPort: status / applyOrPrompt с FakeSystemSettingAdapter.

**Coverage target**: 80% line coverage в `core/wizard/` + `core/localization/` commonMain (excluding generated code).

### Layer 2 — Fitness functions (CI gates)

- **`./gradlew :core:wizard:check`** — runs all unit tests + Konsist arch tests.
- **`./gradlew :core:localization:checkTranslations`** — fails если key missing в любом из 10 не-base языков (FR-031, SC-003).
- **`./gradlew checkLauncherAgnosticImports`** — Konsist FR-038/038a (core/* → app/* forbidden).
- **`./gradlew :core:localization:checkContextEntries`** — fails если новый key в `base/strings.xml` без записи в `CONTEXT.json` (FR-031b).

### Layer 3 — Instrumented tests (эмулятор)

- **Wizard E2E**: process death resume (SC-005), locale change (SC-005a) — Pixel 5 API 34.
- **Compose screenshot tests**: senior primitives на fontScale=2.0 (SC-006), на EN/DE/AR (SC-006a), на ar-SA RTL (SC-007).
- **Cold-start macrobenchmark** (SC-001a): wizard первый шаг до first frame ≤ 300ms.

### Layer 4 — Translation pipeline (dev workflow)

- **`procedure-translate-spec-strings` skill**: запускается в конце `speckit-tasks` через Claude API.
- Verification: synthetic batch of 5 new keys → все 10 не-base языков получают переводы (SC-003a).

### Cannot-test-locally gaps (per project policy)

- TalkBack озвучивание — `TODO(physical-device)` в первом S-1 alpha.
- OEM-specific (Samsung, Xiaomi, Huawei) — `TODO(physical-device)`.
- iOS UI rendering — отложено per C-7.

---

## 10. Pre-implementation verification (REVISED 2026-06-17)

**Library spike CANCELLED** per C-38 — все choices фиксированы существующим проектом (Compose Resources, Konsist, Koin, Decompose). См. [research.md](research.md) для исторического контекста.

**Replaced by single verification task T001** (~30 min):
1. Add empty package `com.launcher.api.wizard` в `core/src/commonMain`.
2. Add minimal Konsist test в `core/src/androidUnitTest/kotlin/com/launcher/arch/SmokeArchitectureTest.kt` — verify Konsist runs `./gradlew :core:testRealBackendUnitTest` (или equivalent).
3. Add one string в `core/src/commonMain/composeResources/values/strings.xml` + `values-ru/strings.xml`, verify resolution works.
4. Add Koin module placeholder, verify wiring.

If verification passes → start Phase 1 immediately. If any component fails — pause + fresh clarify.

---

## 11. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| **moko-resources несовместим с Kotlin 2.0+** | Medium | Spike Day 1; fallback Compose Resources (per C-8 alternative) |
| **Konsist слабые error messages при complex rules** | Low | Spike Day 2; fallback ArchUnit-kotlin |
| **Compose for Multiplatform iOS глюки заставят rewrite UI** | Out of scope F-3 | OUT-019 — iOS отложен до consumer materialization |
| **DataStore Multiplatform support не stable** | Low | DataStore Android-only через port pattern (FR-006, FR-048) — KMP-friendly fallback (Russhwolf Settings) если понадобится |
| **Bundled JSON parsing OOM на low-end Android** | Very Low | JSONs малы (<10 KB каждый), Kotlin Serialization streaming. APK size ≤+1.5 MB (SC-010) |
| **Wizard process death loses uncommitted answer** | Acceptable | FR-003a: `rememberSaveable` для in-progress, commit на «Далее» (per Q-1 (b)) |
| **OEM AccessibilityService restrictions (Samsung KNOX)** | Low | OEM Matrix `// TODO(physical-device)` — F-3 поставляет port + AndroidAdapter, OEM-specific quirks — S-1+ |
| **Claude API translation качество AR/HI/ZH/JA/KK низкое** | Medium | OUT-005a — human review откладывается до Phase 4. AI ship'ает «как есть» |
| **Build time +1-2 min раздражает разработчика** | Acceptable | A-19 explicit acknowledgement; trade-off за JVM testability gain |

---

## 12. Constitution Check

Executed via `procedure-constitution-check` 2026-06-16.

| Gate | Verdict | Justification |
|---|---|---|
| **1. Architecture** | ✅ PASS | Три KMP-модуля (`core/wizard/`, `core/localization/`, `core/ui-senior/`) каждый justified per Article V §3. Three premature abstractions удалены в meta-minimization phase. |
| **2. Core/System Integration** | ✅ PASS | Android system events wrapped через ports (`LocaleProvider`, `AnimationPreferenceProvider`, `SystemSettingPort`); zero raw Android types в commonMain domain. |
| **3. Configuration** | ✅ PASS | 5 JSON schemas + 3 persistent stores с explicit `schemaVersion`; forward-compat + hard-fail policy (per Q-6 (b)); roundtrip + forward-compat + hard-fail tests per FR-017 + SC-002; migration policy в FR-045. |
| **4. Required Context Review** | ✅ PASS | §13 links: CLAUDE.md, constitution.md, glossary.md, decisions, ADR-004, roadmap, спеки 005/007/008/010, memory files. Permission budget — N/A delta (no new manifest permissions в F-3). |
| **5. Accessibility** | ✅ PASS | Tap target ≥56dp (FR-034), body ≥18sp / title ≥24sp, contrast ≥7:1 (FR-035, AAA), TalkBack live regions (FR-008b), visual progress (FR-008c), reduce-motion (FR-036a). SC-006, SC-006a, SC-007. |
| **6. Battery/Performance** | ✅ PASS | Нет background tasks, нет polling, cold-start budget SC-001a (≤300ms), no regression SC-011, APK delta SC-010 (≤+1.5 MB), build time A-19 acknowledged. |
| **7. Testing** | ✅ PASS | 4-layer strategy (§9): JVM unit + fitness functions + Compose screenshot + macrobenchmark. Каждый port имеет fake + real adapter (table в §5). |
| **8. Simplicity** | ✅ PASS | Each abstraction имеет ≥2 impls + consumers (Test 1). Vendor swap costs documented (Test 2). No speculative abstractions. |

**OVERALL: 8 PASS / 0 FAIL / 0 N/A — Constitution Check COMPLETE. No remediation required.**

---

## 13. Required Context Review

Per Article XII §7 — все relevant context-документы reviewed и применены:

| Документ | Что взято в F-3 |
|---|---|
| [`CLAUDE.md`](../../CLAUDE.md) | Rules 1, 2, 4, 5, 6, 7, 9 explicit applied (см. A-5 reusability discipline, A-8 localization keys, FR-038 fitness function) |
| [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) | Articles V (Modularization), VII (Profile-Driven), VIII (Senior-Safe), IX (Performance), XI (Anti-bloat), XIV (Security) explicit applied |
| [`docs/product/glossary.md`](../../docs/product/glossary.md) | **Mandatory read** — terminology contract (app-family / layout-grid / 4-5 JSON schemas + 6-field header) |
| [`docs/product/decisions/2026-06-15-deferred-cloud/`](../../docs/product/decisions/2026-06-15-deferred-cloud/) | F-3 wizard local-only (A-10) |
| [`docs/dev/adrs/ADR-004-localization-and-global-readiness.md`](../../docs/dev/adrs/) | Localization базовые принципы; F-3 overrides base language EN (per C-6 + A-15b) |
| [`docs/adr/ADR-005-ui-stack-compose-multiplatform.md`](../../docs/adr/ADR-005-ui-stack-compose-multiplatform.md) | **CRITICAL** — фиксирует CMP + Material 3 как обязательный UI стек + Amendment 2026-05-07a (Koin DI + Decompose navigation). F-3 строго следует. |
| [Спека 011 (encrypted media)](../011-contacts-and-e2e-encrypted-media/spec.md) | Lazysodium crypto + CBOR в `core:androidMain` — F-3 ortogonal но awareness |
| [`docs/product/roadmap.md`](../../docs/product/roadmap.md) §Шаг 1 F-3 | Order shift 2026-06-15 v2 — F-3 first |
| [Спека 005](../005-action-architecture-v2/spec.md) | actionType — opaque string reference (capability registry future) |
| [Спека 007](../007-pairing-and-firebase-channel/spec.md) | PairingStep НЕ в F-3 (S-2 territory) |
| [Спека 008](../008-bidirectional-config-sync/spec.md) | F-3 НЕ trogает ConfigDocument format; future migration via FR-051 inline TODO |
| [Спека 010](../010-setup-assistant/spec.md) | Hard-fail pattern (FR-016 пар FR-042), `!N` badge cross-spec dependency |
| Memory `feedback_exit_ramps_as_todos.md` | Inline TODOs у FR-021, FR-051, FR-057, android-pool.json |
| Memory `reference_testing_environment.md` | Эмуляторы OK для CI gates; реальные устройства — `TODO(physical-device)` |
| Memory `feedback_critical_mentor_stance.md` | Mentor sessions проведены 2026-06-16 для всех architectural one-way doors |

---

## 14. Rollout / verification

### Phase 0 — Pre-implementation (2 days)

- Library spike A/B (per research.md).
- Decision logged в research.md.
- Update build.gradle.kts с финальным choices.

### Phase 1 — Module skeletons (3-4 days)

- Create 3 modules (`core/wizard/`, `core/localization/`, `core/ui-senior/`) с пустыми ports.
- Konsist fitness function wired in `./gradlew check`.
- All Fake adapters in commonTest.
- CI baseline.

### Phase 2 — Core implementation (1.5-2 weeks)

- `WizardEngine` + all step types + JSON schemas + Persistent* adapters.
- `StringResolver` + 11 locale files (initial AI translation pass).
- Senior UI primitives + theme.
- Bundled `android-pool.json` + `ui-pool.json`.

### Phase 3 — Tests + polish (3-5 days)

- All unit tests pass (Layer 1).
- All fitness functions pass (Layer 2).
- Compose screenshot tests pass (Layer 3).
- Cold-start budget verified.
- Translation skill creates `procedure-translate-spec-strings` + GLOSSARY.md.

### Verification gates

- ✅ All FRs traceable to code (`speckit-analyze`).
- ✅ All SCs measured (`perf-checkpoint.md` artifact).
- ✅ Constitution Check PASS.
- ✅ 22 checklists re-run clean (or warnings explicitly accepted).
- ✅ APK delta within SC-010 budget.
- ✅ Smoke E2E test on Pixel 5 API 34 emulator: install → run wizard → reach HomeActivity.

**Estimated total effort**: **3-4 weeks** (per spec Effort estimate) + 2 days pre-plan spike. Within Phase 1 timeline.

---

## 15. Complexity Tracking

> Filled only if Constitution Check has violations.

**Pending Constitution Check execution.** Expected: no violations (foundation spec aligned с rules through Group A meta-minimization fixes — 3 premature abstractions already removed: WizardStepRegistry, MigrationRegistry skeleton, ResourceReader port).

---

## Краткое содержание простым русским языком *(per skill `procedure-add-novice-summary`)*

Этот документ — **план реализации** F-3 (которая сама про wizard + локализацию + UI-конструктор для пожилых).

**Что внутри плана** простыми словами:

1. **Какие модули создадим**: три новых «папки» в коде — `core/wizard/` (движок wizard'а), `core/localization/` (переводы на 11 языков), `core/ui-senior/` (большие кнопки и тёплая тема). Первые две — Kotlin Multiplatform (готовы к iOS), третья — Android-only Compose (iOS будет отдельно).

2. **Какие готовые библиотеки берём**: moko-resources для переводов, Konsist для проверки архитектурных правил, DataStore для хранения настроек. Все — пробуем сначала 2 дня на тестовом проекте (это «spike»), если что-то не работает — берём резервный вариант.

3. **Как тестируем**: четыре уровня тестов. Бизнес-логика тестируется без эмулятора (быстро). UI тестируется через скриншоты в EN/DE/AR на эмуляторе. Translation pipeline проверяется в конце каждой спеки автоматически. Реальные пожилые пользователи тестируют только при S-1 alpha (TalkBack, разные телефоны Samsung/Xiaomi).

4. **Какие риски**: библиотеки могут не подойти (митигация: spike + fallback), OEM производители могут блокировать accessibility (митигация: на S-1 проверяем на реальных устройствах), AI переводы для арабского/хинди могут быть слабые (митигация: native review откладывается на Phase 4).

5. **Сколько времени**: 3-4 недели реализации + 2 дня предварительной проверки библиотек.

**Что НЕ входит в план**: iOS, TV, Sign-In, cloud sync, pairing с другим устройством, обработка тапа на плитку — это всё в следующих спеках (S-1, S-2, F-4 и т.д.).

**Следующий шаг**: `/speckit.tasks` — превратить план в список задач для разработчика. Перед этим — 2 дня spike (проверка библиотек).
