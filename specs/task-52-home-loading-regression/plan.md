# Implementation Plan: HomeActivity loading regression

**Branch**: `task-52-home-loading-regression` | **Date**: 2026-06-26 | **Spec**: [spec.md](./spec.md)
**Backlog Task**: TASK-52
**Sizing**: Tiny bug-fix — plan.md only, no sub-artifacts (`research.md`, `data-model.md`, `quickstart.md`, `contracts/` все N/A — нет новых wire-форматов, нет персистируемых типов, нет нового dev workflow).

## Summary

Фикс блокирующего bug'а: после wizard'а главный экран `HomeActivity` показывает вечную «Загрузка…», не переходит к плиткам. Решение: ввести **state machine** `HomeLoadingState ∈ {Loading, Ready, Error}` в существующем `HomeComponent`, обернуть загрузку flow в `withTimeout(3s)`, добавить error UI с кнопками Retry / Reset (последняя через confirmation dialog). Никаких новых модулей, портов, wire-форматов — точечная правка существующих 4 файлов в `core` + `app`.

## Technical Context

**Language/Version**: Kotlin 2.x (commonMain + androidMain)
**Primary Dependencies**: Decompose (navigation/retain), Compose Multiplatform, Koin (DI), kotlinx.coroutines (`withTimeout`)
**Storage**: N/A — fix не персистирует состояние; existing `PresetRepository` (DataStore) и `ConfigBackedFlowRepository` (file-backed bundled config) используются как есть.
**Testing**: JUnit + kotlinx-coroutines-test (`runTest`, `TestDispatcher.advanceTimeBy`) для unit; Compose UI Test (опционально) для instrumentation; manual smoke для physical / emulator gates.
**Target Platform**: Android 9+ (compileSdk 34, minSdk текущий проекта). Baseline целевые устройства: pixel_5_api_34 emulator + Xiaomi 11T physical (Android 12, MIUI).
**Project Type**: Mobile (Android-only сейчас; код в `core/commonMain` — KMP-ready).
**Performance Goals**: Cold start без regression > 200ms (SC-007). Loading→Ready ≤ 3s (SC-001/002). Loading→Ready на warm start ≤ 1s (SC-005).
**Constraints**: state machine pure-Kotlin (rule 1), без новых external SDK (rule 6 — fake adapter уже есть в виде fake `FlowRepository`).
**Scale/Scope**: ~5 файлов изменены, ~150 строк нового кода в `core`, ~30 строк в `app`.

## Architecture

### Module map (изменения только в существующих файлах)

```
core/src/commonMain/kotlin/com/launcher/
├── ui/
│   ├── navigation/
│   │   └── HomeComponent.kt        ← MODIFIED: добавить HomeLoadingState + StateFlow + withTimeout
│   └── screens/
│       └── HomeScreen.kt           ← MODIFIED: state-machine UI (Loading/Ready/Error) + ResetConfirmationDialog
└── adapters/config/
    └── ConfigBackedFlowRepository.kt ← MAY-MODIFY: гарантия что getFlows() не висит дольше внутреннего разумного таймаута

app/src/main/java/com/launcher/app/
├── HomeActivity.kt                 ← MODIFIED: убрать runBlocking, передать suspend init в HomeComponent
└── wizard/
    └── WizardActivity.kt           ← MAY-MODIFY: гарантированный порядок setActivePreset → finish

core/src/commonMain/composeResources/values/
└── strings_wizard.xml              ← MODIFIED: 4-5 новых string keys (RU base через androidMain strings_wizard.xml, EN base)

core/src/commonTest/kotlin/com/launcher/ui/navigation/
└── HomeComponentLoadingStateTest.kt ← NEW: unit test, 3 transitions через kotlinx-coroutines-test
```

### Data flow

```
WizardActivity (finish) → setActivePreset(slug) → DataStore.write [SUSPEND, await]
                                                        ↓
                                            startActivity(HomeActivity)
                                                        ↓
HomeActivity.onCreate
    ├── создаёт HomeComponent (через retainedInstance Decompose)
    └── HomeComponent.init { state = Loading }
                                                        ↓
        loadFlowsJob = launch { withTimeout(3000) { flows = FlowRepository.getFlows() } }
                                                        ↓
        ┌── success(non-empty) → state = Ready(activeFlowId)
        ├── empty list          → state = Error("flows empty")
        ├── timeout             → state = Error("timeout 3s")
        └── exception           → state = Error("exception: $message")
                                                        ↓
        HomeScreen.compose:
            Loading → text «Загрузка…»
            Ready    → FlowScreen(active)
            Error    → ErrorBlock + кнопки Retry/Reset → ResetConfirmationDialog
                            Retry  → HomeComponent.retry() → state = Loading → relaunch
                            Reset  → confirm → onResetData() → FirstLaunchActivity
                            Cancel → dialog hides, Error visible
```

### Port-adapter shape

Все требуемые ports уже существуют (`FlowRepository`, `PresetRepository`). Fix их не меняет. Fake `FlowRepository` для unit-теста — inline класс в test sources, не отдельный module.

### Why HomeComponent, not HomeActivity, owns the state machine

`HomeComponent` живёт в `commonMain` (KMP-ready), retain'ится Decompose'ом сквозь Activity recreation (FR-010 per Clarification Q5). Размещение state machine в `HomeActivity` потеряло бы при rotation и потребовало бы `savedInstanceState` пляски — что explicit rejected.

## Wire formats

**N/A.** Fix не вводит новых wire-форматов. `HomeLoadingState` — внутренний type, не пересекает app boundary, не персистируется. CLAUDE.md rule §5 не triggered.

## Dependency impact

**Никаких новых Gradle dependencies.** Все используемые библиотеки уже подключены: kotlinx-coroutines (`withTimeout`, `TestDispatcher`), Decompose (`retainedInstance`), Compose Material3 (`AlertDialog` для confirmation). Article XIII не triggered.

## Test strategy

### Unit (preferred — JVM, fast, deterministic)

**`HomeComponentLoadingStateTest`** в `core/src/commonTest/`, использует `kotlinx-coroutines-test` `runTest { … }` и `TestDispatcher.advanceTimeBy(...)`:

| Test name | Scenario | Expected |
|---|---|---|
| `loading_to_ready_on_non_empty_flows` | Fake `FlowRepository.getFlows()` returns 6 plitki | State `Loading` → `Ready(activeFlowId = "phone-flow")` |
| `loading_to_error_on_empty_flows` | Fake returns empty list | State `Loading` → `Error("flows empty")` |
| `loading_to_error_on_timeout` | Fake delays forever; advanceTimeBy(3001) | State `Loading` → `Error("timeout 3s")` |
| `loading_to_error_on_exception` | Fake throws `IllegalStateException` | State `Loading` → `Error("exception: …")` |
| `retry_after_error_relaunches` | Start in `Error`, call `retry()` | Returns to `Loading`, второй call → `Ready` |
| `state_survives_component_lifecycle` | Simulate Activity recreate (Decompose `retainedInstance` ctx) | `Ready` остаётся `Ready`, не reset в `Loading` |

### Fitness functions (rule §7)

Один inline assertion в unit-тесте: «после 3.5s state НЕ `Loading`» — гарантирует что timeout сработал.

### Integration (Android instrumentation, optional)

Если unit-теста недостаточно или хочется убедиться в Compose render, опциональный `HomeActivityLoadingTest` в `app/src/androidTest/`. **Помечается `[deferred-local-emulator]`** — owner-AVD-зависимо.

### Manual smoke

- pixel_5_api_34 emulator: install fresh APK → wizard → secundomer (`[deferred-local-emulator]`).
- Xiaomi 11T: то же самое, plus measure cold start through `adb shell am start -W` (`[deferred-physical-device]`).

## Risks

| Risk | Mitigation |
|---|---|
| **R1**: Root cause выясняется только в runtime (одна из 4 гипотез а/б/в/г из spec не подтверждается). | Plan не привязывает решение к конкретному root cause; state machine с timeout + retry **превосходит** любой из 4 root causes. Diagnostic logcat (FR-012) даст точную причину **после** fix'а, для записи в research.md. |
| **R2**: `runBlocking { presetRepository.getActivePreset() }` в `HomeActivity.onCreate` может быть ANR risk на холодном старте. | Plan допускает либо оставить (если измеренный median < 16ms на pixel_5_api_34) либо заменить на suspend init в HomeComponent. Решение — runtime measurement в начале работы. |
| **R3**: State machine reentrancy (повторный retry пока предыдущий в полёте). | Default behaviour: cancel previous job + start new (latest user action wins). Реализация: `loadFlowsJob?.cancel(); loadFlowsJob = launch { … }`. |
| **R4**: Confirmation dialog state не retain'ится при rotation. | Hold dialog visibility в `HomeComponent` state (отдельный `MutableStateFlow<Boolean>` или extension `HomeLoadingState.Error.showResetConfirm`). Retain через Decompose. |
| **R5**: Слабые устройства < Xiaomi 11T → 3s timeout мало. | Out-of-scope spec'и; если возникнет в будущем — отдельный adjustment task в backlog. |
| **R6**: 7-tap admin gate поверх error UI — недокументированное взаимодействие. | Verify at implementation: existing `sevenTapAdminGate` modifier должен работать поверх любого state. Если ломается — добавить тест. |
| **R7**: `simple-launcher` manifest или `classic-6.json` будут изменены другой фичей до merge → SC-004 рассинхрон. | SC-004 формулировка обновлена (Clarification Q1) — «все плитки текущего bundled tile-set'а». AC обновляется автоматически по факту. |

## Required Context Review

Связанные документы из `docs/` (для будущего AI-агента, который будет имплементить):

- [`docs/governance/document-map.md`](../../docs/governance/document-map.md) — карта документов проекта (если существует).
- [`docs/compliance/permissions-and-resource-budget.md`](../../docs/compliance/permissions-and-resource-budget.md) — **N/A**: fix не добавляет permissions / не меняет resource usage значимо (только logcat write).
- ADRs:
  - **N/A** для этой фичи: state machine inline в HomeComponent — не уровень ADR.
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — Article IV §5 (state survives recreation), Article IX (performance), Article XI (simplicity), Article VIII §7 (elderly-safe).
- `CLAUDE.md` rules 1 (domain isolation), 4 (MVA), 6 (mock-first).
- Memory `reference_compose_ui_test_api_mismatch.md` — для optional UI instrumentation использовать AVD ≤ API 34.
- Memory `reference_testing_environment.md` — physical device matrix.
- Spec [`specs/task-7-simple-launcher-first-run/spec.md`](../task-7-simple-launcher-first-run/spec.md) — родительская фича, мы фиксим её регрессию. Особо: contracts/simple-launcher-manifest.md (если есть) для understanding tile-set формата.

## Rollout / verification

1. **Реализация** в порядке: state machine в HomeComponent → unit-тест → HomeScreen UI states → strings RU/EN → integration в HomeActivity.
2. **Локальная проверка**: `./gradlew :core:testDebugUnitTest --tests "*HomeComponent*"` — все 6 тестов зелёные.
3. **Smoke на pixel_5_api_34** через skill `android-emulator`: fresh install → wizard → secundomer ≤ 3s; затем kill + reopen → ≤ 1s.
4. **Baseline cold-start measurement** (для SC-007): 3 прогона main APK через `adb shell am start -W` до fix, 3 прогона после; median diff ≤ 200ms.
5. **Manual smoke на Xiaomi 11T** (owner): same flow, secundomer.
6. **PR creation** через skill `pre-pr-backlog-sync` — обновит backlog AC, переведёт TASK-52 → `Verification` (если deferred AC остались `[ ]`) или `Done` (если все физические gates успели пройти до merge).

## Constitution Check

*Per Article XVI of `.specify/memory/constitution.md`. 8 gates evaluated against this plan.*

| Gate | Verdict | Justification |
|---|---|---|
| **1. Architecture** | ✅ PASS | Никаких новых модулей. Изменения в существующих файлах `core/ui/*` и `app/`. State machine inline в `HomeComponent` — не создаёт новый layer. |
| **2. Core/System Integration** | ✅ PASS | Fix не вводит system events (BroadcastReceiver, boot, package change). Использует существующий Decompose lifecycle. |
| **3. Configuration** | ✅ N/A | Fix не меняет profiles / schema / wire-format. `HomeLoadingState` — internal type, не пересекает app boundary, не персистируется. CLAUDE.md rule §5 not triggered. |
| **4. Required Context Review** | ✅ PASS | Constitution Articles IV §5, VIII §7, IX, XI, XVI явно процитированы. CLAUDE.md rules 1/4/6 учтены. Связи на TASK-7 spec и memory `reference_compose_ui_test_api_mismatch` указаны. ADR-relevant изменений нет (это bug-fix). |
| **5. Accessibility** | ✅ PASS | Elderly-friendly checklist 12/12 PASS (см. [checklists/elderly-friendly.md](./checklists/elderly-friendly.md)). Tap targets кнопок Retry/Reset наследуют existing senior-safe density (Composable wrapContent, не fixed-width). TalkBack читает кнопки и dialog естественно. Contrast — наследуется от `MaterialTheme.colorScheme.onSurfaceVariant` и `error`. |
| **6. Battery/Performance** | ✅ PASS | Никаких новых background tasks / polling / boot receivers. Cold start regression budget 200ms (SC-007). `withTimeout(3s)` — single coroutine, terminates determinedly. logcat write только при actual failure (FR-012), low frequency. |
| **7. Testing** | ✅ PASS | Unit-тест с 6 scenarios покрывает все transitions (см. Test strategy). Fake `FlowRepository` inline (CLAUDE.md rule §6 satisfied — fake adapter exists alongside real one). Никаких новых ports = никаких новых fake-adapter requirements. |
| **8. Simplicity** | ✅ PASS | Test 1 (что потеряем при inlining): inline state machine = ничего теряем. Test 2 (если зависимость подорожает): мы не зависим от new dependency. `HomeLoadingState` — 3-вариантная sealed class, не generic library. Meta-minimization checklist 13/13 PASS. |

**OVERALL: 7 PASS, 1 N/A, 0 FAIL → plan COMPLETE.**

## Complexity Tracking

Нет нарушений Constitution → секция остаётся пустой.

---

## Open issues to track в tasks.md

Из clarification + checklist passes:

1. **R2**: измерить median `runBlocking { presetRepository.getActivePreset() }` time на pixel_5_api_34 — решение об inline vs suspend init принимается на основе данных.
2. **R3**: реализация cancel-previous-on-retry — добавить tests case.
3. **R4**: confirmation dialog state retention — добавить unit test для recreate during dialog visible.
4. **R6**: проверить 7-tap admin gate работает поверх Error state — добавить smoke step в manual gate.
5. **SC-007**: baseline cold-start measurement — отдельный benchmark task (`[deferred-local-emulator]`).
6. **SC-008**: logcat trace для root cause — записать в research.md как **post-fix artifact** (не блокер для merge, но обязательно для PR description).

Все 6 уйдут в `tasks.md` через `/speckit.tasks`.
