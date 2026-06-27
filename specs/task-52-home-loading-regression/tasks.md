# Tasks: HomeActivity loading regression

**Input**: Design documents from `/specs/task-52-home-loading-regression/`
**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md), checklists/ (12 files, all PASS)

**Tests**: Включены (см. plan.md Test strategy — unit + fitness function + manual smoke gates).
**Organization**: По User Stories (US1/US2/US3) после shared foundation. US1+US2 — оба P1, могут параллельно после foundation. US3 — P2 smoke.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Может выполняться параллельно (разные файлы, без зависимостей).
- **[Story]**: US1 (happy path), US2 (failure recovery), US3 (smoke tiles), SH (shared).
- Включает точные пути файлов.

## Path Conventions

- `core/src/commonMain/kotlin/com/launcher/` — domain + UI logic (KMP-ready)
- `core/src/commonTest/kotlin/com/launcher/` — JVM unit tests
- `core/src/commonMain/composeResources/values{,-ru}/` — i18n strings
- `app/src/main/java/com/launcher/app/` — Android-specific Activity glue
- `app/src/androidTest/` — instrumentation (optional)

---

## Phase 1: Foundation (Shared Infrastructure)

**Purpose**: State machine type + retain mechanism — без этого ни одна US не имплементится.

- [x] **T001** [SH] Создать sealed class `HomeLoadingState` в `core/src/commonMain/kotlin/com/launcher/ui/navigation/HomeLoadingState.kt`. 3 варианта: `data object Loading`, `data class Ready(val activeFlowId: String)`, `data class Error(val reason: String)`. Pure Kotlin, никаких imports кроме stdlib. Trace: FR-001, plan §Architecture.
- [x] **T002** [SH] Добавить `private val _loadingState = MutableStateFlow<HomeLoadingState>(HomeLoadingState.Loading)` + `val loadingState: StateFlow<HomeLoadingState> = _loadingState.asStateFlow()` в `core/src/commonMain/kotlin/com/launcher/ui/navigation/HomeComponent.kt`. Размещение в **существующем** компоненте — retain через Decompose `retainedInstance` уже работает. Trace: FR-001, FR-010, plan §Architecture, Clarification Q5.
- [x] **T003** [SH] [P] Добавить `private val _resetDialogVisible = MutableStateFlow(false)` + публичный `StateFlow` в `HomeComponent.kt` (для confirmation dialog retention через recreate per R4). Trace: FR-006, plan §R4.
- [x] **T004** [SH] Реализовать `private fun launchLoadFlows()` в `HomeComponent.kt`: cancel предыдущий `loadFlowsJob?.cancel()`, set `_loadingState.value = Loading`, запустить `coroutineScope.launch { withTimeout(3000) { … FlowRepository.getFlows() … } }`. Branches: non-empty → `Ready(activeFlowId)`; empty → `Error("flows empty")`; `TimeoutCancellationException` → `Error("timeout 3s")`; other exception → `Error("exception: ${e.message}")`. Все Error ветки делают `logger.warn(...)` с reason (FR-012). Trace: FR-002, FR-003, FR-012, R3.
- [x] **T005** [SH] Вызвать `launchLoadFlows()` из `init` блока `HomeComponent`. Trace: FR-002.
- [x] **T006** [SH] Добавить public `fun retry()` в `HomeComponent.kt` — вызывает `launchLoadFlows()`. Trace: FR-005.
- [x] **T007** [SH] Добавить public `fun showResetConfirmation()` / `fun hideResetConfirmation()` / `fun confirmReset()` в `HomeComponent.kt`. `confirmReset()` вызывает существующий `onResetData` callback. Trace: FR-006.

**Checkpoint**: State machine готов в core. UI ещё «Загрузка…», но логика переходов покрыта unit-тестами.

---

## Phase 2: Test foundation (TDD — пишем тесты до UI)

**Purpose**: Unit-тесты для state machine. Должны падать сейчас (HomeComponent ещё не отдаёт StateFlow в существующем коде, but T002 уже сделан) → проходить после T001-T007.

- [ ] **T010** [P] [SH] Создать `core/src/commonTest/kotlin/com/launcher/ui/navigation/HomeComponentLoadingStateTest.kt`. Setup: fake `FlowRepository`, fake `PresetRepository`, `TestScope` через `runTest { … }`. Trace: plan §Test strategy.
- [ ] **T011** [P] [SH] Test `loading_to_ready_on_non_empty_flows`: fake возвращает 6 flow'ов → state переходит в `Ready(activeFlowId = первый)`. Trace: FR-002, SC-006.
- [ ] **T012** [P] [SH] Test `loading_to_error_on_empty_flows`: fake возвращает `emptyList()` → state переходит в `Error("flows empty")`. Trace: FR-003, SC-006.
- [ ] **T013** [P] [SH] Test `loading_to_error_on_timeout`: fake возвращает `delay(forever)`; `advanceTimeBy(3001)` → state переходит в `Error("timeout 3s")`. Включает fitness assertion: «после 3.5s state НЕ `Loading`». Trace: FR-003, SC-006, plan §Fitness functions.
- [ ] **T014** [P] [SH] Test `loading_to_error_on_exception`: fake throws `IllegalStateException("boom")` → state переходит в `Error("exception: boom")`. Trace: FR-003, SC-006.
- [ ] **T015** [P] [SH] Test `retry_after_error_relaunches`: start в Error → `retry()` → state транзитирует Loading → второй call возвращает данные → Ready. Trace: FR-005, SC-006.
- [ ] **T016** [P] [SH] Test `retry_cancels_previous_pending_job`: первый load в полёте (delay forever), вызов `retry()` → первый job cancel'нут, новый job запущен. Verify через `loadFlowsJob.isCancelled` или флаг в fake. Trace: R3.
- [ ] **T017** [P] [SH] Test `reset_confirmation_state_transitions`: initial `_resetDialogVisible.value == false` → `showResetConfirmation()` → true → `hideResetConfirmation()` → false; `confirmReset()` → вызывает passed callback. Trace: FR-006, R4.

**Checkpoint**: `./gradlew :core:testDebugUnitTest --tests "*HomeComponentLoadingStateTest*"` зелёный (7 тестов).

---

## Phase 3: US1 — Happy path (Priority: P1) 🎯 MVP

**Goal**: Свежеустановленный пользователь видит главный экран с плитками за ≤ 3s.

**Independent Test**: Manual smoke на pixel_5_api_34 + Xiaomi 11T — fresh install → wizard → секундомер ≤ 3s.

- [ ] **T020** [US1] В `core/src/commonMain/kotlin/com/launcher/ui/screens/HomeScreen.kt` заменить текущий `if (flowSlot.child != null)` на `when (state.loadingState)`:
  - `Loading` → существующий текст «Загрузка…» (preserve current behaviour);
  - `Ready` → существующий `FlowScreen(active)`;
  - `Error` → placeholder Box (заполняется в US2 task'ами).
  Trace: FR-004 part 1.
- [ ] **T021** [US1] В `app/src/main/java/com/launcher/app/HomeActivity.kt`: ревизировать `runBlocking { presetRepository.getActivePreset() }` (line 55). Замерить median time через `System.nanoTime()` + 3 cold launch'а. **Если < 16ms** — оставить как есть (низкий ANR risk). **Если ≥ 16ms** — переместить чтение активного preset'а внутрь `HomeComponent.init` через suspend init. Документировать измерение inline-комментарием. Trace: FR-008, R2, plan §Risk R2.
- [ ] **T022** [US1] В `app/src/main/java/com/launcher/app/wizard/WizardActivity.kt` проверить: `presetRepository.setActivePreset(...)` завершается **до** `startActivity(HomeActivity)`. Если сейчас fire-and-forget — обернуть в `lifecycleScope.launch { setActivePreset(); startActivity(...) }`. Trace: FR-007, plan §R1.

**Checkpoint US1**: Свежий запуск → главный экран с 6 плитками за ≤ 3s. Manual smoke (deferred gates) проверит.

---

## Phase 4: US2 — Failure recovery UI (Priority: P1)

**Goal**: При сбое загрузки настроек пользователь видит error UI с Retry + Reset, не вечную «Загрузка…».

**Independent Test**: Unit-тесты для state machine (Phase 2) + Compose UI rendering зрительная проверка через debug build с force-empty FlowRepository через DI override.

- [ ] **T030** [P] [US2] Добавить string keys в `core/src/commonMain/composeResources/values/strings_wizard.xml` (EN base) + `core/src/androidMain/res/values-ru/strings_wizard.xml` (RU) + `core/src/androidMain/res/values/strings_wizard.xml`:
  - `home_loading_error_title` → «Не удалось загрузить настройки» / «Failed to load settings»
  - `home_loading_error_retry` → «Попробовать снова» / «Try again»
  - `home_loading_error_reset` → «Сбросить настройки и пройти заново» / «Reset settings and start over»
  - `home_reset_dialog_message` → «Все настройки будут стёрты. Продолжить?» / «All settings will be erased. Continue?»
  - `home_reset_dialog_confirm` → «Сбросить» / «Reset»
  - `home_reset_dialog_cancel` → «Отмена» / «Cancel»
  Trace: FR-004, FR-006, FR-011.
- [ ] **T031** [P] [US2] Добавить context entries для 6 новых keys в `core/strings-context/CONTEXT.json` (tone: elderly-friendly, formal-but-warm). Trace: plan §Localization checklist open issue, localization.md.
- [ ] **T032** [US2] В `HomeScreen.kt` реализовать `Error` ветку: Column с Text(title) + два Button (Retry, Reset). Tap target ≥ 56dp (senior-safe). `Modifier.wrapContentSize()` для устойчивости к length expansion (RU 30-40% длиннее EN). Retry button onClick → `component.retry()`. Reset button onClick → `component.showResetConfirmation()`. Trace: FR-004 part 2, FR-005, localization-ui.md.
- [ ] **T033** [US2] В `HomeScreen.kt` добавить `AlertDialog` (Compose Material3), visible когда `state.resetDialogVisible == true`. Title/text/buttons через string resources из T030. Confirm → `component.confirmReset()` (которая вызывает existing onResetData callback в HomeActivity). Cancel → `component.hideResetConfirmation()`. Trace: FR-006, R4.
- [ ] **T034** [US2] Verify: technical reason из `Error(reason)` **НЕ показывается** в UI — только title из T030. logcat WARN/ERROR пишется из `launchLoadFlows()` (уже сделано в T004). Trace: FR-012, elderly-friendly.md.

**Checkpoint US2**: Compose UI рендерит 3 state'а. Unit-тесты из Phase 2 зелёные. Force-empty (через debug build flag или DI override) показывает error UI.

---

## Phase 5: US3 — Tile smoke (Priority: P2)

**Goal**: Все 6 плиток `classic-6` tile-set'а тапабельны после первого запуска.

**Independent Test**: Manual smoke на pixel_5_api_34 — fresh install → wizard → тап по каждой из 6 плиток последовательно.

- [ ] **T040** [US3] Verify через grep, что `core/src/androidMain/assets/wizard/tile-sets/classic-6.json` содержит 6 actions: `phone.call`, `messages.open`, `camera.open`, `gallery.open`, `contacts.open`, `settings.open`. Если состав изменился — обновить SC-004 в spec и AC #4 в backlog. Trace: SC-004, Clarification Q1.
- [ ] **T041** [US3] [deferred-local-emulator] Manual smoke на pixel_5_api_34: install fresh APK, пройти wizard, тапнуть каждую из 6 плиток по очереди. Записать результат в PR description: какие плитки открывают экран без крэша, какие фолят (last → TASK-7 territory, не блокер). Trace: SC-004, US3.
- [ ] **T042** [US3] [deferred-physical-device] То же на Xiaomi 11T (owner). Trace: SC-004, SC-001.

---

## Phase 6: Verification & measurement

- [ ] **T050** [deferred-local-emulator] Baseline cold-start measurement на pixel_5_api_34: 3 cold launches main APK через `adb shell am start -W com.launcher.app/.HomeActivity` → median. Затем 3 launch'а post-fix APK — median. Diff ≤ 200ms (SC-007). Записать в PR description. Trace: SC-007, plan §Rollout step 4.
- [ ] **T051** [deferred-local-emulator] Smoke gate на pixel_5_api_34: fresh install → wizard → секундомер до плиток ≤ 3s. Затем kill app + open → главный экран ≤ 1s. Trace: SC-001, SC-002, SC-005.
- [ ] **T052** [deferred-physical-device] То же на Xiaomi 11T (owner): fresh install → wizard → секундомер ≤ 3s; kill + open → ≤ 1s. Trace: SC-001, SC-005.
- [ ] **T053** [US3] [deferred-local-emulator] Verify 7-tap admin gate работает поверх Error state (R6): force-empty FlowRepository → видим Error UI → тапнуть 7 раз быстро по пустой области → admin gate должна сработать (или явно зафиксировать что не работает в этом state'е и не блокирует merge). Trace: plan §R6.
- [ ] **T054** Записать root-cause analysis в `specs/task-52-home-loading-regression/research.md` после fix'а: какой из 4 гипотез (а/б/в/г) подтвердился через logcat trace, почему предыдущие подходы не сработали, что сработало. Trace: SC-008, plan §Open issues.

---

## Phase 7: Cross-cutting cleanup

- [ ] **T060** Запустить `./gradlew :core:testDebugUnitTest --tests "*HomeComponent*"` локально — все 7 тестов зелёные. Trace: SC-006, plan §Local Test Path.
- [ ] **T061** Запустить `./gradlew :app:assembleRealBackendDebug` — successful build, no warnings новые. Trace: plan §Rollout.
- [ ] **T062** Запустить existing checklist-validator-ы поверх finalных артефактов: `requirements-quality`, `meta-minimization`, `failure-recovery` — verify все остались PASS после изменений в spec.md (если в clarify spec обновлялся, повторного pass'а checklist'ов после tasks не требует — но всё равно verify). Trace: plan §Constitution Check.
- [ ] **T063** Вызвать skill `pre-pr-backlog-sync` ДО `gh pr create`: обновит backlog AC под текущее состояние (auto-checklist count'ы, auto-deferred list из этого tasks.md), переведёт TASK-52 → `Verification` (если deferred AC остались `[ ]`) или `Done` (если все физические gates успели пройти). Trace: CLAUDE.md HARD RULE pre-PR sync.

---

## Deferred tasks summary

### `[deferred-local-emulator]` (требуют AVD)

- **T041** — Tile smoke на pixel_5_api_34
- **T050** — Baseline cold-start measurement
- **T051** — End-to-end smoke (≤ 3s cold, ≤ 1s warm)
- **T053** — 7-tap admin gate over Error state

### `[deferred-physical-device]` (требуют Xiaomi 11T)

- **T042** — Tile smoke
- **T052** — End-to-end smoke на физическом устройстве

Owner ([primary user] / [remote administrator]) запускает их вручную через skill `android-emulator` (local) и физически (Xiaomi). AI session помечает соответствующие AC `[ ]` до прохода gate'ов.

---

## Dependencies

```
T001 ─┬─→ T002 ─→ T004 ─→ T005 (init wires launchLoadFlows)
      │           │
      │           ├─→ T006 (retry uses launchLoadFlows)
      │           └─→ T007 (reset confirmation API)
      └─→ T003 (independent _resetDialogVisible)

T002,T004 ─→ T010..T017 (tests for state machine)

T002 ─→ T020 (HomeScreen reads loadingState)
T020 ─→ T032 (Error branch fleshes out)
T030,T031 ─→ T032, T033 (strings needed for UI)
T003,T007 ─→ T033 (reset dialog uses _resetDialogVisible + confirmReset)

T020,T032,T033 ─→ T041, T051, T052 (smoke after UI compile)
T021 ─→ T050 (measurement after potential runBlocking change)

All implementation ─→ T060, T061 (verify)
T060,T061,smoke gates ─→ T063 (pre-PR sync)
```

## Trace summary

- **FR-001** → T001, T002
- **FR-002** → T004, T005, T011
- **FR-003** → T004, T012, T013, T014
- **FR-004** → T020, T032
- **FR-005** → T006, T015, T032
- **FR-005a** → T004 (no auto-retry — direct timeout → Error)
- **FR-006** → T007, T030, T033, T017
- **FR-007** → T022
- **FR-008** → T021
- **FR-009** → T051, T052 (warm start ≤ 1s)
- **FR-010** → T002 (StateFlow в HomeComponent — retain через Decompose)
- **FR-011** → T030, T031
- **FR-012** → T004 (logcat WARN/ERROR), T034 (not in UI)
- **SC-001/002** → T051, T052
- **SC-003** → T032, T012, T013, T014
- **SC-004** → T040, T041, T042
- **SC-005** → T051, T052
- **SC-006** → T010..T017, T060
- **SC-007** → T050
- **SC-008** → T054

## Open issues addressed in tasks

| plan §Open issue | Closing task(s) |
|---|---|
| R2: runBlocking measurement | T021, T050 |
| R3: cancel-previous-on-retry | T004, T016 |
| R4: dialog retention through recreate | T003, T007, T017, T033 |
| R6: 7-tap gate over Error | T053 |
| SC-007: baseline measurement | T050 |
| SC-008: root-cause analysis in research.md | T054 |

---

**Total tasks**: 31 (T001-T063 с gaps в нумерации для phase clarity).
**Parallel-safe** ([P]): T003, T010-T017, T030, T031 — могут идти в одной сессии разными worker'ами/agent'ами без конфликта файлов.
**AI-closeable** (без deferred): T001-T034, T040, T054, T060, T061, T062, T063 — 24 tasks.
**Deferred** ([deferred-*]): T041, T042, T050, T051, T052, T053 — 6 tasks, требуют owner manual gate.
