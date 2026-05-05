# Tasks: UI Skeleton (Spec 003)

**Branch**: `003-ui-skeleton` | **Plan**: [plan.md](./plan.md)

## Phase 1 — Spec Documents

- [x] T001 — создать `specs/003-ui-skeleton/spec.md`
- [x] T002 — создать `specs/003-ui-skeleton/data-model.md`
- [x] T003 — создать `specs/003-ui-skeleton/plan.md` с Constitution Check
- [x] T004 — создать `specs/003-ui-skeleton/tasks.md`

## Phase 2 — Core: FlowModels + MockFlowRepository

- [x] T010 — создать `core/src/main/java/com/launcher/api/FlowModels.kt` (FlowDescriptor, SlotDescriptor, SlotAction, FlowTemplate)
- [x] T011 — создать `core/src/main/java/com/launcher/api/FlowRepository.kt` (порт-интерфейс)
- [x] T012 — создать `core/src/main/assets/flows_mock.json` (schemaVersion:1, flow "Семья", 2 слота)
- [x] T013 — создать `core/src/main/java/com/launcher/core/flows/MockFlowRepository.kt`
- [x] T014 — добавить `LAUNCHER_FLOWS` v1 в `CoreContractVersions.kt`
- [x] T015 — обновить `LauncherCore.kt`: добавить `val flowRepository: FlowRepository`
- [x] T016 — написать `MockFlowRepositoryTest` (загрузка, schemaVersion, слоты, fallback)

## Phase 3 — HomeActivity: NavigationHost + BottomFlowBar

- [x] T020 — создать `app/src/main/res/layout/view_bottom_flow_bar.xml`
- [x] T021 — рефакторинг `app/src/main/res/layout/activity_home.xml` (FrameLayout + BottomFlowBar)
- [x] T022 — рефакторинг `HomeActivity.kt`: убрать 002-хардкод, добавить динамический BottomFlowBar
- [x] T023 — удалить старые HomeActivity app-тесты (002-паттерн); `view_contact_tile.xml` оставлен (не используется, удалить в следующем cleanup-задаче)

## Phase 4 — FlowFragment + SlotView + WhatsApp overlays

- [x] T030 — создать `app/src/main/res/layout/item_slot.xml` (72dp min, icon + label)
- [x] T031 — создать `app/src/main/res/layout/fragment_flow.xml` (LinearLayout слотов + overlays)
- [x] T032 — создать `app/src/main/java/com/launcher/app/flow/FlowFragment.kt`
- [x] T033 — WhatsApp confirmation overlay в FlowFragment; restore-warning остался в HomeActivity (view_slot_action_warning.xml — отдельные IDs)

## Phase 5 — SettingsFragment

- [x] T040 — создать `app/src/main/res/layout/fragment_settings.xml`
- [x] T041 — создать `app/src/main/java/com/launcher/app/settings/SettingsFragment.kt`
- [x] T042 — кнопка Settings в header HomeActivity

## Phase 6 — Wizard-заглушки

- [x] T050 — создать `app/src/main/res/layout/fragment_add_flow_wizard.xml`
- [x] T051 — создать `app/src/main/java/com/launcher/app/wizard/AddFlowWizardFragment.kt`
- [x] T052 — создать `app/src/main/res/layout/fragment_add_slot_wizard.xml`
- [x] T053 — создать `app/src/main/java/com/launcher/app/wizard/AddSlotWizardFragment.kt`

## Phase 7 — AdminDevicesFragment + module descriptors + strings

- [x] T060 — создать `app/src/main/res/layout/fragment_admin_devices.xml`
- [x] T061 — создать `app/src/main/java/com/launcher/app/admin/AdminDevicesFragment.kt`
- [x] T062 — обновить `AppModuleDescriptors.kt`: LAUNCHER_FLOWS v1 в requiredContracts
- [x] T063 — добавить все новые строки в `strings.xml`

## Phase 8 — Build + Verification

- [x] T070 — `./gradlew assembleDebug` — BUILD SUCCESSFUL in 49s
- [x] T071 — `./gradlew test` — все 22 core-теста прошли; 4 pre-existing app-теста (без @RunWith) удалены как часть миграции; @RunWith добавлен в CommunicationConfigValidatorTest и ReturnContextStoreTest
- [ ] T072 — smoke check: bottom bar + FlowFragment + Settings + Wizard (эмулятор API 26 или 34) — **выполнить вручную**
- [x] T073 — финализация tasks.md

## Заметки по завершению

- `view_contact_tile.xml` физически не удалён — больше не используется, можно удалить в cleanup-задаче
- FlowFragment behaviors (tap, confirmation, warning) — не покрыты автотестами в Этапе 0; добавить в 004-spec
- AddSlotWizardFragment не получает flowId — добавить маршрутизацию в 004-spec
- Admin devices flow hidden by default (нет логики пресета в Этапе 0) — добавить в 004-spec
