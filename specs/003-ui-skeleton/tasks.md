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
- [x] T072 — smoke check на двух эмуляторах (Medium_Phone_API_36.1 + клон `_2`): закрыто в spec 004 / T413 после миграции UI на Compose Multiplatform. Verified paths: FirstLaunch → preset → Home → tile → Confirmation → Cancel/Confirm → handoff/Warning fallback ("WhatsApp недоступен") → Settings → preset switch → Simple launcher. Both presets render. Two known issues opened for spec 005: (a) auto-return на Home после смены preset в Settings не срабатывает; (b) density override для simple-launcher визуально неотличим от workspace.
- [x] T073 — финализация tasks.md

## Заметки по завершению

- `view_contact_tile.xml` физически не удалён — больше не используется, можно удалить в cleanup-задаче
- FlowFragment behaviors (tap, confirmation, warning) — не покрыты автотестами в Этапе 0; добавить в 004-spec
- AddSlotWizardFragment не получает flowId — добавить маршрутизацию в 004-spec
- Admin devices flow hidden by default (нет логики пресета в Этапе 0) — добавить в 004-spec

---

## Phase 9 — First-launch preset picker (расширение)

**Цель:** при первом запуске пользователь выбирает один из трёх пресетов (workspace / launcher / simple-launcher). Выбор сохраняется в DataStore. На повторных запусках picker не показывается. Mock-конфигурация (флоу/слоты) грузится в зависимости от выбранного пресета. Для тестирования двух эмуляторов с разными пресетами добавляется debug-only intent extra и скрипт сброса.

### Доменные модели

- [ ] T080 — добавить `FlowPreset` enum в `core/src/main/java/com/launcher/api/PresetModels.kt`:
  - `WORKSPACE("workspace")` — обычная плотность, всегда видимая bottom-nav
  - `LAUNCHER("launcher")` — крупнее, опционально default-launcher role
  - `SIMPLE_LAUNCHER("simple-launcher")` — максимально крупно, скрытие bottom-nav при одном flow
  - Поля: `slug`, `titleResKey`, `descriptionResKey`, `iconResKey` (resKey — строки, чтобы домен не тащил R)
- [ ] T081 — добавить порт `PresetRepository` в `core/src/main/java/com/launcher/api/PresetRepository.kt`:
  - `suspend fun getActivePreset(): FlowPreset?` — null если ещё не выбран
  - `suspend fun setActivePreset(preset: FlowPreset)`
  - `fun observeActivePreset(): Flow<FlowPreset?>`
- [ ] T082 — добавить `LAUNCHER_PRESETS` v1 в `CoreContractVersions.kt`
- [ ] T083 — обновить `LauncherCore.kt`: добавить `val presetRepository: PresetRepository`

### Mock-данные

- [ ] T084 — переименовать `core/src/main/assets/flows_mock.json` в `flows_mock_simple-launcher.json` (текущая раскладка «Семья» с 2 слотами Аня/Олег — это simple-launcher)
- [ ] T085 — создать `core/src/main/assets/flows_mock_workspace.json`:
  - Flow «Контакты» с 4-6 слотами (placeholder контакты)
  - Flow «Приложения» с 4-6 OpenApp слотами (placeholder packages)
  - schemaVersion: 1
- [ ] T086 — создать `core/src/main/assets/flows_mock_launcher.json`:
  - Flow «Главное» с 4 слотами (звонок / сообщение / браузер / контакты)
  - schemaVersion: 1
- [ ] T087 — расширить `MockFlowRepository`: принимает `PresetRepository`, читает соответствующий JSON по активному пресету; контракт-тест на все три пресета

### Adapter — DataStore-реализация PresetRepository

- [ ] T088 — добавить зависимость `androidx.datastore:datastore-preferences` в `app/build.gradle.kts`
- [ ] T089 — создать `app/src/main/java/com/launcher/app/preset/DataStorePresetRepository.kt`: реализация `PresetRepository` через Preferences DataStore (ключ `active_preset`)
- [ ] T090 — DI-проводка в `LauncherApplication`: ручная конструктор-инъекция `DataStorePresetRepository` → `LauncherCore`
- [ ] T091 — fake `InMemoryPresetRepository` в `core/src/test/` для контракт-теста

### UI — FirstLaunchActivity

- [ ] T092 — создать `app/src/main/res/layout/activity_first_launch.xml`:
  - 3 крупные карточки 120dp+ высотой с иконкой, заголовком и 2-строчным описанием
  - Заголовок экрана «Как вы хотите использовать приложение?»
- [ ] T093 — создать `app/src/main/res/layout/item_preset_card.xml` (переиспользуемая карточка)
- [ ] T094 — создать `app/src/main/java/com/launcher/app/firstlaunch/FirstLaunchActivity.kt`:
  - Читает `presetRepository.getActivePreset()`. Если != null → finish() и переход в HomeActivity
  - Тап карточки → `setActivePreset(...)` → переход в HomeActivity
- [ ] T095 — обновить `AndroidManifest.xml`: `FirstLaunchActivity` становится LAUNCHER intent-filter; `HomeActivity` теряет LAUNCHER (но остаётся MAIN)
- [ ] T096 — добавить иконки-плейсхолдеры в `app/src/main/res/drawable/`:
  - `ic_preset_workspace.xml` — Material `dashboard`
  - `ic_preset_launcher.xml` — Material `home`
  - `ic_preset_simple_launcher.xml` — Material `favorite`
- [ ] T097 — добавить строки в `strings.xml` (ru-RU + en-US):
  - `preset_workspace_title` = «Workspace»
  - `preset_workspace_description` = «Контакты, приложения и ярлыки под рукой»
  - `preset_launcher_title` = «Лаунчер»
  - `preset_launcher_description` = «Заменяет главный экран. Простой и быстрый»
  - `preset_simple_launcher_title` = «Лаунчер для пожилого»
  - `preset_simple_launcher_description` = «Крупные кнопки, защита от случайных тапов»
  - `first_launch_title` = «Как вы хотите использовать приложение?»

### Visual differentiation между пресетами

- [ ] T098 — добавить density-стили в `app/src/main/res/values/styles.xml`:
  - `Theme.Launcher.Workspace` — обычная плотность, slot 72dp, label 16sp
  - `Theme.Launcher.Launcher` — увеличенная, slot 96dp, label 20sp
  - `Theme.Launcher.SimpleLauncher` — крупная, slot 120dp+, label 24sp+, скрытие bottom-nav при одном flow
- [ ] T099 — `HomeActivity` применяет тему по активному пресету через `setTheme()` до `setContentView()`
- [ ] T100 — `BottomFlowBar` визуально отличается между пресетами (размер табов, наличие label под иконкой)

### Settings — переключение пресета

- [ ] T101 — в `SettingsFragment` добавить пункт «Активный пресет: <name>» с кнопкой «Сменить пресет» → диалог с теми же 3 карточками → запись в `presetRepository`
- [ ] T102 — добавить кнопку «Сбросить настройки» (уже есть placeholder) — при нажатии очищает DataStore и перезапускает в `FirstLaunchActivity`

### Debug-инструменты для тестирования

- [ ] T103 — в `FirstLaunchActivity.onCreate()` под `if (BuildConfig.DEBUG)`: чтение intent extra `--es preset <slug>` → если задан и валиден → авто-выбор пресета без показа UI
- [ ] T104 — создать скрипт `scripts/reset-and-launch.ps1`:
  - Параметры: `-Serial <emu>`, `-Preset <slug>`
  - `adb -s $Serial shell pm clear com.launcher.app`
  - `adb -s $Serial shell am start -n com.launcher.app/com.launcher.app.firstlaunch.FirstLaunchActivity --es preset $Preset`
- [ ] T105 — создать скрипт `scripts/test-two-presets.ps1`: одной командой сбрасывает оба эмулятора и запускает с разными пресетами (`emulator-5554` → simple-launcher, `emulator-5556` → workspace)

### Тесты

- [ ] T106 — `MockFlowRepositoryPresetTest`: проверка что для каждого из трёх пресетов читается правильный JSON
- [ ] T107 — `DataStorePresetRepositoryTest`: write → read → observe; reset → null
- [ ] T108 — обновить smoke-check T072: запустить `test-two-presets.ps1`, убедиться визуально что оба эмулятора показывают разный UI

### Документация

- [ ] T109 — обновить `specs/003-ui-skeleton/spec.md`:
  - Убрать «First-launch preset picker» из Out of Scope
  - Добавить US-306 про выбор пресета
  - Зафиксировать три пресета и их UX-параметры
- [ ] T110 — обновить `specs/003-ui-skeleton/data-model.md`: модели `FlowPreset`, `PresetRepository`, ключ DataStore
- [ ] T111 — добавить заметку в `docs/product/roadmap.md` что spec 003 расширен Phase 9

### Финал

- [ ] T112 — `./gradlew assembleDebug && ./gradlew test` — всё зелёное
- [ ] T113 — закоммитить логическими блоками:
  1. core: PresetModels + PresetRepository + contract version
  2. core: split mock JSONs by preset + repository preset-aware
  3. app: DataStorePresetRepository + DI wiring
  4. app: FirstLaunchActivity + manifest + drawables + strings
  5. app: density styles + HomeActivity theming
  6. app: settings preset switcher + reset
  7. debug: intent extras + scripts
  8. docs: spec/data-model/roadmap updates
- [ ] T114 — push в ветку `003-ui-skeleton`, обновить PR
- [ ] T115 — ручной smoke-check на двух эмуляторах через `test-two-presets.ps1`
