# Tasks: UI Stack Migration (Spec 004)

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Branch**: `004-ui-stack-migration`

Каждая задача завершается коммитом + push (Article XVIII §3).

## Phase 1 — Foundation

- [ ] **T401** — Подключить стек в Gradle: KMP plugin, Compose Multiplatform plugin, Material 3, Decompose, Koin, SQLDelight, multiplatform-settings, Coil 3, kotlinx-coroutines, kotlinx-serialization. Обновить `gradle/libs.versions.toml` и `core/build.gradle.kts`, `app/build.gradle.kts`. Проверить sanity-build обоих таргетов (Android `:app:assembleDebug`, KMP `:core:compileKotlinIosArm64` — оба должны пройти, даже если iosMain пустой).

- [ ] **T402** — Превратить `:core` в KMP-модуль. Перенести существующие pure Kotlin файлы (api/, core/, contracts/) из `core/src/main/java/` в `core/src/commonMain/kotlin/`. Завести source-sets `androidMain`, `iosMain`, `commonTest`, `androidUnitTest`. Перенести Android-touching классы (AppIndex, SystemEventBridge, WhatsAppLaunchabilityResolver, ReturnContextStore, DataStorePresetRepository) в `androidMain` через `expect`/`actual` контракты. Создать iosMain stubs (`throw NotImplementedError("iOS support deferred")` для каждого actual). Все 22 теста spec 003 должны быть зелёные после переноса.

- [ ] **T403** — Создать `docs/dev/design-system.md`: Material 3 seed-color, типография (15 ролей с senior-safe override ≥18sp body), shapes (small=8dp, medium=16dp, large=24dp), elevation (3 уровня), правила tap-target ≥56dp, контраст ≥4.5:1, иконки primary-actions с подписями, density-варианты для пресетов (workspace / launcher / simple-launcher). Реализовать `LauncherTheme.kt` в `:core/commonMain/ui/theme/` по этим правилам. Smoke-test: Compose preview в Android Studio показывает три preset-варианта без ошибок.

## Phase 2 — DI и Navigation foundation

- [ ] **T404** — Завести Koin-модули в `:core/commonMain/di/`: `coreModule` (LauncherCore, ProfileEngine, ActionDispatcher, EventRouter), `repositoryModule` (FlowRepository → MockFlowRepository, PresetRepository → MultiplatformSettingsPresetRepository). В `:core/androidMain/di/` — `androidPlatformModule` с actual'ами. В `:core/iosMain/di/` — пустой `iosPlatformModule`. В `:app` — `LauncherApplication` стартует Koin с `androidContext()` и списком модулей. Все existing классы получают зависимости через constructor — Koin их регистрирует, API классов не меняется.

- [ ] **T405** — Создать `:core/commonMain/navigation/RootComponent.kt` (Decompose) с sealed `Config` (FirstLaunch, Home, FlowDetail(flowId), Settings, AddFlowWizard, AddSlotWizard(flowId), AdminDevices). `StackNavigation<Config>` с initial=`FirstLaunch` если preset не выбран, иначе `Home`. Логика выбора начальной конфигурации — через injected `PresetRepository`. Тесты `RootComponentTest` (commonTest): начальная Config, push/pop, replaceCurrent.

## Phase 3 — Pilot screen + gate decision

- [ ] **T406** — Pilot: `FirstLaunchScreen` Composable в `:core/commonMain/ui/screens/`. Использует Material 3 + senior-safe override, три `PresetCard` (workspace / launcher / simple-launcher). Тап → injected callback → `PresetRepository.savePreset(...)` → `RootComponent.navigation.replaceCurrent(Home)`. `:app/FirstLaunchActivity.kt` превращается в `ComponentActivity { setContent { LauncherTheme { FirstLaunchScreen(...) } } }`. Удалить XML-layout и Fragment-обвязку этого экрана. Compose UI test через `createComposeRule` для трёх preset-выборов. **Gate-decision**: Smoke-run на одном эмуляторе (используя skill `.claude/skills/android-emulator/SKILL.md`); измерить cold start, проверить TalkBack-проход. Если результат неприемлем — STOP, поднять с автором, возможный rollback.

## Phase 4 — Migration of remaining 003 screens

- [ ] **T407** — `HomeScreen` + `BottomFlowBar` Composable. `HomeScreen` рендерит `Scaffold` + `BottomFlowBar` (LazyRow с Card-вкладками + «+») + content slot, который заполняется через Decompose `Children` API из ChildStack. `:app/HomeActivity.kt` превращается в `ComponentActivity` wrapper, который держит `RootComponent` и рендерит `Children`. HOME-intent в манифесте — без изменений.

- [ ] **T408** — `FlowScreen` + `TileCard` + `ConfirmationOverlay` + `WarningOverlay` Composable. `LazyVerticalGrid` слотов из `flowRepository.loadFlows(flowId)`. Тап на тайл → bottom sheet с confirmation. Логика handoff остаётся прежней (`ActionDispatcher.dispatch(...)`). Перенос accessibility-семантики из spec 003.

- [ ] **T409** — `SettingsScreen` + `AddFlowWizardScreen` + `AddSlotWizardScreen` + `AdminDevicesScreen` Composable. Используют общие компоненты `ListSection`, `WizardStep`, `AppButton`. Сохраняется функциональность spec 003: язык-placeholder, preset switch, toggle + QR-placeholder (`AlertDialog`), сброс данных, мульти-step wizards, empty state + FAB для AdminDevices. Compose UI tests для каждого.

## Phase 5 — Cleanup and tests

- [ ] **T410** — Удалить весь legacy: `app/src/main/res/layout/*` (кроме splash если нужен), все Fragment-классы в `:app/src/main/java/com/launcher/app/{flow,settings,wizard,admin,communication}/`, ViewBinding-обвязка, `FragmentManager` навигация. Старые Robolectric Activity-тесты — удалить (новые Compose UI tests их заменили). Sanity-build + полный test-run обеих таргетов. Никаких dead imports.

- [ ] **T411** — Перенести оставшиеся тесты в новые source-sets: pure Kotlin тесты → `commonTest`; Android-specific тесты, требующие Robolectric только для контекста (не Activity), → `androidUnitTest`. Compose UI tests для всех экранов через `createComposeRule`. Все тесты зелёные.

## Phase 6 — Performance verification и закрытие T072

- [ ] **T412** — Performance-checkpoint: измерить cold start `HomeActivity` и `FirstLaunchActivity` через `am start -W` или Macrobenchmark; проверить frame budget (`JankStats` или GPU profiling) на скролле Workspace grid. Сравнить с ADR-005 targets (≤ 600 мс / ≤ 700 мс / 0 dropped frames). Записать результаты в `specs/004-ui-stack-migration/perf-checkpoint.md`. Если фейл — Phase 6.5 оптимизация (lazy init, baseline profile, Compose stable lambdas).

- [ ] **T413** — T072 smoke-check (закрытие spec 003) на двух эмуляторах через skill `.claude/skills/android-emulator/SKILL.md`: full-path FirstLaunch → preset workspace → Home → Flow → tile → confirmation → handoff (mock) → return → Settings → preset switch → simple-launcher → recreate → проверка `!`-индикатора (если применимо к этому spec'у). Параллельно на втором эмуляторе — пресет simple-launcher, тот же путь. Документировать результаты в `specs/003-ui-skeleton/tasks.md` (T072 ✓) и в change-log: «UI migrated to Compose Multiplatform per ADR-005 / spec 004».

- [ ] **T414** — Финальная сверка spec 004 deliverables: все user stories US-401..US-404 покрыты, все required gates plan.md PASS, документы обновлены (specs/003-ui-skeleton/tasks.md, specs/003-ui-skeleton/plan.md change-log, docs/dev/design-system.md полный, perf-checkpoint.md заполнен). Открытие PR `004-ui-stack-migration → main`.
