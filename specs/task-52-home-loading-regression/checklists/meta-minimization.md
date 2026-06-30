# Meta-minimization Checklist: HomeActivity loading regression

**Purpose**: Verify no premature abstractions / preemptive layers per Article XI + CLAUDE.md rule 4 (MVA).
**Created**: 2026-06-26
**Feature**: [spec.md](../spec.md)

## Checks

- [x] **No new ports introduced** — fix использует существующие `FlowRepository`, `PresetRepository`, `HomeComponent`. Никаких новых интерфейсов уровня домена.
- [x] **No new adapters** — изменения в существующих `ConfigBackedFlowRepository`, `HomeActivity`, `WizardActivity`. Никаких новых platform-specific классов.
- [x] **No new modules** — fix живёт в `core` + `app`, без выделения нового Gradle-модуля.
- [x] **No `LoadingViewModelFactory` / `StateMachineFactory` / generic state-machine abstraction** — `HomeLoadingState` это inline sealed class на 3 варианта, не generic library.
- [x] **No retry framework** — кнопка Retry это прямой call `FlowRepository.getFlows()`, без `RetryPolicy` / `BackoffStrategy` / circuit-breaker (rejected per Clarification Q3).
- [x] **No new wire format** — `HomeLoadingState` не persist'ится через границу app (rule 5 not triggered).
- [x] **Confirmation dialog inline в HomeScreen** — не extract'ится в generic `ConfirmationDialogFactory`. Один use-case = один inline Composable.
- [x] **Timeout 3s захардкожен** — не вынесен в `LoadingTimeoutConfig` provider. Если потребуется per-device tuning, расширяется в будущей фиче.
- [x] **`HomeResetConfirmationDialog` упомянут в Key Entities — это inline Composable, не отдельный модуль/класс с DI**.
- [x] **Не делается общий «error UI framework» для всех экранов** — fix scope ограничен HomeScreen.
- [x] **Не вводится `OneShotEvent` / `Effect` инфраструктура** для navigation из dialog'а — используется существующий `onResetData` callback.
- [x] **research.md скоуп** (упомянут в SC-008) ограничен logcat trace, не превращается в полный architectural analysis document.
- [x] **No premature i18n abstraction** — RU+EN добавляются через существующий strings_wizard.xml mechanism, без выделения `LocaleAwareErrorMessages`.

## Verdict

✅ **13/13 passed.** Fix остаётся inline-точечным, без эскалации в инфраструктуру.

## Risks for future drift

- Если parallel feature потребует error state для другого экрана (Settings, Wizard step), возникнет соблазн extract'ить `HomeResetConfirmationDialog` → `GenericResetDialog`. На том коммите надо явно решить: extract или duplicate. **Один use-case + один candidate = inline.** Extract только когда появится третий вызов.
- Если timeout 3s будет признан недостаточным на слабых устройствах — соблазн ввести `DeviceTier → Timeout` мапу. Альтернатива: оставить hardcode и **измерить** на realных устройствах через benchmarking task в backlog.
