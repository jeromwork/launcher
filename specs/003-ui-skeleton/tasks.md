# Tasks: UI Skeleton (Spec 003)

**Branch**: `003-ui-skeleton` | **Plan**: [plan.md](./plan.md)

## Phase 1 — Spec Documents

- [x] T001 — создать `specs/003-ui-skeleton/spec.md`
- [x] T002 — создать `specs/003-ui-skeleton/data-model.md`
- [x] T003 — создать `specs/003-ui-skeleton/plan.md` с Constitution Check
- [x] T004 — создать `specs/003-ui-skeleton/tasks.md`

## Phase 2 — Core: FlowModels + MockFlowRepository

- [ ] T010 — создать `core/src/main/java/com/launcher/api/FlowModels.kt` (FlowDescriptor, SlotDescriptor, SlotAction, FlowTemplate)
- [ ] T011 — создать `core/src/main/java/com/launcher/api/FlowRepository.kt` (порт-интерфейс)
- [ ] T012 — создать `core/src/main/assets/flows_mock.json` (schemaVersion:1, flow "Семья", 2 слота)
- [ ] T013 — создать `core/src/main/java/com/launcher/core/flows/MockFlowRepository.kt`
- [ ] T014 — добавить `LAUNCHER_FLOWS` v1 в `CoreContractVersions.kt`
- [ ] T015 — обновить `LauncherCore.kt`: добавить `val flowRepository: FlowRepository`
- [ ] T016 — написать `MockFlowRepositoryTest` (загрузка, schemaVersion, слоты, fallback)

## Phase 3 — HomeActivity: NavigationHost + BottomFlowBar

- [ ] T020 — создать `app/src/main/res/layout/view_bottom_flow_bar.xml`
- [ ] T021 — рефакторинг `app/src/main/res/layout/activity_home.xml` (FrameLayout + BottomFlowBar)
- [ ] T022 — рефакторинг `HomeActivity.kt`: убрать 002-хардкод, добавить динамический BottomFlowBar
- [ ] T023 — убрать `view_contact_tile.xml` (заменяется item_slot.xml)

## Phase 4 — FlowFragment + SlotView + WhatsApp overlays

- [ ] T030 — создать `app/src/main/res/layout/item_slot.xml` (72dp min, icon + label)
- [ ] T031 — создать `app/src/main/res/layout/fragment_flow.xml` (GridLayout/LinearLayout слотов)
- [ ] T032 — создать `app/src/main/java/com/launcher/app/flow/FlowFragment.kt`
- [ ] T033 — перенести WhatsApp confirmation/warning overlay из HomeActivity в FlowFragment

## Phase 5 — SettingsFragment

- [ ] T040 — создать `app/src/main/res/layout/fragment_settings.xml`
- [ ] T041 — создать `app/src/main/java/com/launcher/app/settings/SettingsFragment.kt`
- [ ] T042 — добавить точку входа в Settings из HomeActivity (кнопка/иконка)

## Phase 6 — Wizard-заглушки

- [ ] T050 — создать `app/src/main/res/layout/fragment_add_flow_wizard.xml`
- [ ] T051 — создать `app/src/main/java/com/launcher/app/wizard/AddFlowWizardFragment.kt`
- [ ] T052 — создать `app/src/main/res/layout/fragment_add_slot_wizard.xml`
- [ ] T053 — создать `app/src/main/java/com/launcher/app/wizard/AddSlotWizardFragment.kt`

## Phase 7 — AdminDevicesFragment + module descriptors + strings

- [ ] T060 — создать `app/src/main/res/layout/fragment_admin_devices.xml`
- [ ] T061 — создать `app/src/main/java/com/launcher/app/admin/AdminDevicesFragment.kt`
- [ ] T062 — обновить `AppModuleDescriptors.kt`: LAUNCHER_FLOWS v1 в requiredContracts
- [ ] T063 — добавить все новые строки в `strings.xml`

## Phase 8 — Build + Verification

- [ ] T070 — `./gradlew assembleDebug` — clean build
- [ ] T071 — `./gradlew test` — unit tests pass
- [ ] T072 — smoke check: bottom bar + FlowFragment + Settings + Wizard (эмулятор API 26 или 34)
- [ ] T073 — закрыть финальные задачи в этом файле
