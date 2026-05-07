# Implementation Plan: UI Skeleton (Spec 003)

**Branch**: `003-ui-skeleton` | **Date**: 2026-05-05 | **Spec**: [spec.md](./spec.md)

## Summary

Реализовать UI-каркас без backend/sync:
- `HomeActivity` превращается в NavigationHost с кастомным `BottomFlowBar`
- Новые API-типы: `FlowDescriptor`, `SlotDescriptor`, `SlotAction`, `FlowRepository` (порт)
- `MockFlowRepository` читает `flows_mock.json` (schemaVersion = 1) на IO-потоке
- `FlowFragment` показывает GridLayout слотов; WhatsApp overlays переезжают сюда из HomeActivity
- Wizard-заглушки для AddFlow и AddSlot (никакого реального сохранения)
- `SettingsFragment`: язык-placeholder, пресет, toggle + QR-placeholder, сброс
- `AdminDevicesFragment`: empty-state + «+» (только в admin-пресете)
- View + XML; никакого Compose; никаких новых Gradle-модулей; никаких новых runtime-разрешений

## Technical Context

- Kotlin 2.0.21, JDK 17, AGP 8.7.3
- Зависимости: существующие (AppCompat, Core KTX, Lifecycle, Coroutines) — новых нет
- Storage: JSON в assets (mock); никакой БД / cloud в этом этапе
- Testing: JUnit4 + MockK + Robolectric; MockFlowRepositoryTest минимум
- Target: minSdk 26, targetSdk 35
- Performance: `loadFlows()` — Dispatchers.IO; BottomFlowBar обновляется в Main; холодный старт некритичен
- Constraints: View/XML, нет новых permissions, нет polling, нет background services

## Constitution Check

### Architecture Gate — PASS
Слой UI (app) расширяется новыми Fragment-ами и xml-layouts.  
Слой Core (core) получает новый порт `FlowRepository` + реализацию `MockFlowRepository`.  
Никаких новых Gradle-модулей (Article V).  
Границы явны: HomeActivity не знает о SlotAction-деталях, только диспатчит ActionRequest через ActionDispatcher.

### Core/System Integration Gate — PASS
Никаких новых OS listeners/broadcasts.  
`FlowRepository` — pure-domain port, никакого system API.  
Все broadcasts по-прежнему принадлежат `SystemEventBridge`.

### Configuration Gate — PASS
`flows_mock.json` имеет `schemaVersion: 1`.  
Fallback: если JSON невалиден — `MockFlowRepository` возвращает пустой список (не крашит).  
Миграция будущих версий: добавление полей ОК, переименование требует migration-doc (Article VII).

### Required Context Review Gate — PASS
Reviewed:
- `docs/governance/document-map.md` — навигация по документам
- `docs/product/context-decisions-and-open-questions.md` — решения Этапа 0
- `docs/adr/ADR-001-cross-platform-strategy.md` — Android-first, parity gate ниже
- `docs/adr/ADR-004-localization.md` — все строки через strings.xml
- `specs/001-launcher-core-foundation/plan.md` — контрактная модель, CoreContractVersions
- `specs/002-whatsapp-tile-return/plan.md` — что мигрируем

Not directly impacted: ADR-002 (entitlement), ADR-003 (monetization), compliance docs — в Этапе 0 нет платного контента и новых разрешений.

### Accessibility Gate — PASS
- Все SlotView: min 72×72dp tap target, contentDescription = label
- BottomFlowBar кнопки: min 48dp высота, contentDescription
- Confirmation и warning overlays: крупный шрифт, высокий контраст (существующие layouts)
- SettingsFragment: Toggle имеет labelFor / contentDescription

### Battery/Performance Gate — PASS
- Никакого polling
- `loadFlows()` вызывается один раз при старте (Dispatchers.IO), результат кешируется в StateFlow
- Никакого BOOT_COMPLETED; никаких новых background services

### Testing Gate — PASS
- `MockFlowRepositoryTest`: загрузка JSON, schema version, корректные слоты, fallback при невалидном JSON
- Существующие Core-тесты не ломаются (ActionDispatcher, ReturnContextStore, etc.)
- Manual E2E: bottom bar, slot tap, WhatsApp handoff, settings nav, wizard stub

### Simplicity Gate — PASS
- `FlowRepository` — один интерфейс, одна реализация (Mock). Никакой speculative abstraction.
- Wizard-заглушки — простые Fragment-ы, никаких state machines.
- BottomFlowBar — кастомный View (HorizontalScrollView + LinearLayout), никакого ViewPager2.

### Platform Parity Gate (ADR-001) — DOCUMENTED
- Android: реализовано в Этапе 0
- iPhone: UI-парадигма (flows, slots, wizards) семантически переносима; platform-specific binding deferred
- Лакуна: WhatsApp deep-link (whatsapp://) — Android only; iPhone деферирован

### Resource Budget
| Dimension | Impact |
|-----------|--------|
| Permissions | Нет новых |
| Battery | Минимальный (один IO-read assets при старте) |
| Memory | +FlowDescriptor list в памяти (< 1 KB для mock) |
| Storage | flows_mock.json в assets (~500 bytes) |
| Network | Нет |

## Project Structure

```
specs/003-ui-skeleton/
├── spec.md
├── plan.md          ← этот файл
├── data-model.md
└── tasks.md

core/src/main/java/com/launcher/
├── api/
│   ├── FlowModels.kt        (FlowDescriptor, SlotDescriptor, SlotAction, FlowTemplate)
│   └── FlowRepository.kt    (port interface)
├── core/
│   └── flows/
│       └── MockFlowRepository.kt
└── contracts/
    └── CoreContractVersions.kt  (добавить LAUNCHER_FLOWS v1)

core/src/main/assets/
└── flows_mock.json

core/src/main/java/com/launcher/core/
└── LauncherCore.kt           (добавить flowRepository)

app/src/main/java/com/launcher/app/
├── HomeActivity.kt           (рефакторинг)
├── AppModuleDescriptors.kt   (добавить LAUNCHER_FLOWS)
├── flow/
│   └── FlowFragment.kt
├── settings/
│   └── SettingsFragment.kt
├── admin/
│   └── AdminDevicesFragment.kt
└── wizard/
    ├── AddFlowWizardFragment.kt
    └── AddSlotWizardFragment.kt

app/src/main/res/layout/
├── activity_home.xml         (рефакторинг)
├── view_bottom_flow_bar.xml  (новый)
├── fragment_flow.xml         (новый)
├── item_slot.xml             (новый)
├── fragment_settings.xml     (новый)
├── fragment_admin_devices.xml (новый)
├── fragment_add_flow_wizard.xml (новый)
└── fragment_add_slot_wizard.xml (новый)

app/src/main/res/values/
└── strings.xml               (новые строки)
```

## Phase 1 — Spec Documents
Output: spec.md, plan.md, data-model.md, tasks.md  
Commit: `docs: add spec 003 ui-skeleton`

## Phase 2 — Core: FlowModels + MockFlowRepository
Deliverables:
- `FlowModels.kt`, `FlowRepository.kt`
- `MockFlowRepository.kt` + `flows_mock.json`
- `CoreContractVersions.kt` ← LAUNCHER_FLOWS v1
- `LauncherCore.kt` ← `val flowRepository: FlowRepository`
- `MockFlowRepositoryTest`

Commit: `feat(core): add flow/slot domain models and mock repository`

## Phase 3 — HomeActivity: NavigationHost + BottomFlowBar
Deliverables:
- Убрать хардкод 002 (contact tile) из `HomeActivity`
- `activity_home.xml`: FrameLayout (контент) + кастомный `BottomFlowBar` снизу
- `view_bottom_flow_bar.xml`: HorizontalScrollView > LinearLayout (кнопки флоу + «+»)
- `HomeActivity` динамически создаёт кнопки из `flowRepository.loadFlows()`
- При тапе → заменяет контент-фрагмент на `FlowFragment(flowId)`

Commit: `feat(app): refactor HomeActivity to navigation host with bottom-flow-bar`

## Phase 4 — FlowFragment + SlotView + WhatsApp overlays
Deliverables:
- `FlowFragment.kt`: принимает flowId, читает слоты из FlowRepository, строит GridLayout
- `fragment_flow.xml`, `item_slot.xml`
- WhatsApp confirmation и warning views переезжают из HomeActivity layout в FlowFragment
- Тап на слот → диспатчит `ActionRequest.WhatsAppHandoff` через LauncherCore.actionDispatcher

Commit: `feat(app): add FlowFragment with slot grid and whatsapp overlays`

## Phase 5 — SettingsFragment
Deliverables:
- `SettingsFragment.kt`: язык (TextView + placeholder), пресет (название), toggle + кнопка «Показать QR» (AlertDialog-placeholder), «Сбросить данные» (confirmation)
- `fragment_settings.xml`
- Кнопка Settings в HomeActivity (иконка в title bar или отдельная кнопка в BottomFlowBar)

Commit: `feat(app): add settings fragment`

## Phase 6 — Wizard-заглушки
Deliverables:
- `AddFlowWizardFragment.kt`: список шаблонов (RecyclerView), «Добавить» → закрывает wizard
- `AddSlotWizardFragment.kt`: выбор типа (RadioGroup: Позвонить / Видеозвонок / Открыть приложение), «Далее» → шаг 2-placeholder → «Готово» → закрывает
- `fragment_add_flow_wizard.xml`, `fragment_add_slot_wizard.xml`

Commit: `feat(app): add placeholder add-flow and add-slot wizard fragments`

## Phase 7 — AdminDevicesFragment + module descriptors + strings
Deliverables:
- `AdminDevicesFragment.kt`: empty state «Нет сопряжённых устройств» + FAB «+» (placeholder)
- `fragment_admin_devices.xml`
- `AppModuleDescriptors.kt` ← LAUNCHER_FLOWS v1 в requiredContracts
- `strings.xml` ← все новые строки

Commit: `chore(app): admin devices fragment, module descriptors, strings`

## Phase 8 — Build verification + tasks.md checkpoint
- `./gradlew assembleDebug` — clean build
- `./gradlew test` — unit tests pass
- Manual smoke on emulator API 26 и API 34
- Обновить tasks.md (✓ финальные задачи)

Commit: `chore(specs): mark 003-ui-skeleton phases complete`
