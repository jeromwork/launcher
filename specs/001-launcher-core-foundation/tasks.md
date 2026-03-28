---
description: "Task list for Launcher Core Foundation (001)"
---

# Tasks: Launcher Core Foundation

**Input**: Design documents from `c:\work\launcher\specs\001-launcher-core-foundation\`  
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Обязательны по `.specify/memory/constitution.md` Article X и [plan.md](./plan.md) §Testing Gate и **§ Test matrix (FR-028)** — JVM/Robolectric в `:core`; instrumented — см. отложенную строку в матрице плана.

**Organization**: Фазы по приоритету user stories из [spec.md](./spec.md) (US1 P1 → US2 P2 → US3 P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллелить (разные файлы, нет зависимости от незавершённой задачи)
- **[US1]/[US2]/[US3]**: метка user story
- Пути от корня репозитория `c:\work\launcher\`

## Path Conventions

- Модули: `c:\work\launcher\app\`, `c:\work\launcher\core\`
- Тесты unit: `c:\work\launcher\core\src\test\java\`
- Тесты Android: `c:\work\launcher\app\src\androidTest\java\` (по мере необходимости)

---

## Phase 1: Setup (инфраструктура)

**Purpose**: Gradle multi-module skeleton, версии из [research.md](./research.md) §1 и [plan.md](./plan.md) Technical Context.

- [X] T001 Создать `c:\work\launcher\settings.gradle.kts` с `include(":app", ":core")` и комментарием-шаблоном для будущих `:feature-*`
- [X] T002 Создать корневой `c:\work\launcher\build.gradle.kts` (или convention через plugins) с совместимостью AGP 8.7+ и Kotlin 2.0.x
- [X] T003 Создать `c:\work\launcher\gradle\libs.versions.toml` с версиями: compileSdk 35, minSdk 26, targetSdk 35, AndroidX AppCompat/Lifecycle/Activity, coroutines (без Compose BOM)
- [X] T004 [P] Добавить `c:\work\launcher\gradle.properties` (AndroidX, JVM 17)
- [X] T005 [P] Добавить `c:\work\launcher\.gitignore` для Android/Gradle/IDE
- [X] T006 Сгенерировать Gradle Wrapper в корне `c:\work\launcher\` (gradlew, gradlew.bat, `gradle/wrapper/*`)
- [X] T007 Создать `c:\work\launcher\core\build.gradle.kts` как Android Library с зависимостями тестов: JUnit, MockK, Robolectric (по [plan.md](./plan.md))
- [X] T008 Создать `c:\work\launcher\app\build.gradle.kts` с `implementation(project(":core"))` и зависимостями UI (AppCompat, View/XML)

**Checkpoint**: `./gradlew :app:assembleDebug` синхруется без ошибок (после заполнения исходников в Phase 2).

---

## Phase 2: Foundational (блокирует все user stories)

**Purpose**: Опубликованные контракты в коде, сборка `LauncherCore`, без полноценного UI.

**⚠️ CRITICAL**: До завершения этой фазы задачи US1–US3 не начинать.

- [X] T009 [P] Добавить sealed-модель проектных событий по [contracts/project-events.md](./contracts/project-events.md) в `c:\work\launcher\core\src\main\java\com\launcher\api\ProjectEvent.kt`
- [X] T010 [P] Добавить модели профиля/снимка по [data-model.md](./data-model.md) в `c:\work\launcher\core\src\main\java\com\launcher\api\ProfileModels.kt`
- [X] T011 [P] Добавить `CatalogEntry` / `CatalogSnapshot` в `c:\work\launcher\core\src\main\java\com\launcher\api\CatalogModels.kt`
- [X] T012 [P] Добавить `ActionRequest` / `DispatchResult` в `c:\work\launcher\core\src\main\java\com\launcher\api\ActionModels.kt`
- [X] T013 [P] Добавить `ModuleDescriptor` в `c:\work\launcher\core\src\main\java\com\launcher\api\ModuleDescriptor.kt`
- [X] T014 Реализовать `EventRouter` (Flow/коллектор; debounce серии `PackageSetChanged` **≤ 200 ms** по [contracts/project-events.md](./contracts/project-events.md), без polling) в `c:\work\launcher\core\src\main\java\com\launcher\core\events\EventRouter.kt`
- [X] T015 Добавить `c:\work\launcher\core\src\main\assets\default_profile.json` с `schemaVersion` и флагами модулей по [contracts/profile-bootstrap.md](./contracts/profile-bootstrap.md)
- [X] T016 Реализовать `ProfileEngine` (парсинг, валидация, safe fallback на default) в `c:\work\launcher\core\src\main\java\com\launcher\core\profile\ProfileEngine.kt`
- [X] T017 Реализовать порядок разрешения конфликтов из [data-model.md](./data-model.md) в `c:\work\launcher\core\src\main\java\com\launcher\core\profile\CompositionResolver.kt`
- [X] T018 Реализовать `ModuleRegistry` по [contracts/module-registration.md](./contracts/module-registration.md) в `c:\work\launcher\core\src\main\java\com\launcher\core\modules\ModuleRegistry.kt`
- [X] T019 Реализовать `AppIndex` (запрос установленных приложений через `PackageManager`, фоновый refresh, снимок для UI) в `c:\work\launcher\core\src\main\java\com\launcher\core\catalog\AppIndex.kt`
- [X] T020 Реализовать `ActionDispatcher` по [contracts/actions.md](./contracts/actions.md) в `c:\work\launcher\core\src\main\java\com\launcher\core\actions\ActionDispatcher.kt`
- [X] T021 Реализовать `SystemEventBridge` (регистрация package broadcasts → нормализация → `EventRouter`) в `c:\work\launcher\core\src\main\java\com\launcher\core\bridge\SystemEventBridge.kt`
- [X] T022 Собрать фасад `LauncherCore` (все сервисы, явный lifecycle start/stop для bridge) в `c:\work\launcher\core\src\main\java\com\launcher\core\LauncherCore.kt`
- [X] T023 JVM (Robolectric): smoke-тест — `LauncherCore` создаётся, `start()` / `stop()` не бросают; без Activity в `c:\work\launcher\core\src\test\java\com\launcher\core\LauncherCoreTest.kt`

**Checkpoint**: выполнена задача **T023** (`LauncherCore` подтверждён тестом без Activity).

---

## Phase 3: User Story 1 — Стабильный дом для пользователя (Priority: P1)

**Goal**: После перезапуска и при битом/частичном профиле — предсказуемый home shell без краша; деградация опциональных возможностей.

**Independent Test**: Ручной прогон: cold start, смена `default_profile.json` на невалидный JSON → приложение показывает fallback; отзыв разрешения на список пакетов (если применимо) → каталог в задокументированном degraded-состоянии. Авто: unit-тесты ProfileEngine/CompositionResolver.

### Tests for User Story 1

- [X] T024 [P] [US1] Unit: fallback профиля при невалидном JSON в `c:\work\launcher\core\src\test\java\com\launcher\core\profile\ProfileEngineTest.kt`
- [X] T025 [P] [US1] Unit: порядок precedence CompositionResolver в `c:\work\launcher\core\src\test\java\com\launcher\core\profile\CompositionResolverTest.kt`

### Implementation for User Story 1

- [X] T026 [US1] Создать `c:\work\launcher\app\src\main\java\com\launcher\app\LauncherApplication.kt` — инициализация `LauncherCore`, регистрация/старт `SystemEventBridge` в жизненном цикле процесса
- [X] T027 [US1] Создать `c:\work\launcher\app\src\main\java\com\launcher\app\HomeActivity.kt` — тонкий shell: подписка на снимки каталога/профиля, без бизнес-логики каталога
- [X] T028 [US1] Создать `c:\work\launcher\app\src\main\res\layout\activity_home.xml` — крупные таргеты, `contentDescription`, контрастный базовый layout (View/XML); ручная сверка с [plan.md](./plan.md) § Test matrix (строка Accessibility)
- [X] T029 [US1] Настроить `c:\work\launcher\app\src\main\AndroidManifest.xml` — `application`, `HomeActivity`, `MAIN`/`LAUNCHER`; элемент `<queries>` или политика видимости пакетов согласно targetSdk 35 и [spec.md](./spec.md) FR-032

**Checkpoint**: Эмуляторы **API 26** и **API 35** (как **targetSdk** в Gradle): приложение стартует, home не пустой/не падает при испорченном профиле (подмена assets или тестовый flavor — по усмотрению).

---

## Phase 4: User Story 2 — Расширение без переписывания базы (Priority: P2)

**Goal**: Регистрация модулей через `ModuleRegistry`, профиль включает/выключает модуль; документированный путь для будущего `:feature-*`.

**Independent Test**: Ревью: список `ModuleDescriptor` в Application + отсутствие raw receivers вне `core`; прочитать [EXTENSION_GUIDE.md](./EXTENSION_GUIDE.md).

### Tests for User Story 2

- [X] T030 [P] [US2] Unit: `ModuleRegistry` отключает модуль при несовместимом `requiredContracts` в `c:\work\launcher\core\src\test\java\com\launcher\core\modules\ModuleRegistryTest.kt`

### Implementation for User Story 2

- [X] T031 [US2] Подключить в `LauncherApplication.kt` список дескрипторов (пока пустой или один no-op тестовый модуль-заглушка в том же модуле `app`, не нарушая запрет feature receivers)
- [X] T032 [US2] Написать `c:\work\launcher\specs\001-launcher-core-foundation\EXTENSION_GUIDE.md` — шаги по образцу [spec.md](./spec.md) FR-026 и [contracts/module-registration.md](./contracts/module-registration.md)

**Checkpoint**: Новый разработчик может пройти EXTENSION_GUIDE без чтения исходников Core целиком.

---

## Phase 5: User Story 3 — Аудит platform integration (Priority: P3)

**Goal**: Единая точка входа OS-событий; документация таблицы слушателей для ревью.

**Independent Test**: Открыть `PLATFORM_EVENTS.md` и KDoc у `SystemEventBridge` — видны source, thread, frequency, fallback.

### Tests for User Story 3

- [ ] T033 [P] [US3] Unit: `EventRouter` дедуп/порядок для серии `PackageSetChanged` в `c:\work\launcher\core\src\test\java\com\launcher\core\events\EventRouterTest.kt`

### Implementation for User Story 3

- [ ] T034 [US3] Добавить `c:\work\launcher\specs\001-launcher-core-foundation\PLATFORM_EVENTS.md` — таблица MVP listeners (**источник правды** [research.md](./research.md) §6; изменения сначала в research, затем здесь)
- [ ] T035 [US3] Дополнить KDoc у `c:\work\launcher\core\src\main\java\com\launcher\core\bridge\SystemEventBridge.kt` и `c:\work\launcher\core\src\main\java\com\launcher\core\events\EventRouter.kt` ссылкой на `PLATFORM_EVENTS.md`

**Checkpoint**: Ревьюер находит владельца intake/normalize/route без чтения feature-кода.

---

## Phase 6: Polish & cross-cutting

**Purpose**: Каталог под моки, CI-команда, синхронизация quickstart.

- [ ] T036 [P] Robolectric/JVM: `AppIndex` с подставным `PackageManager` в `c:\work\launcher\core\src\test\java\com\launcher\core\catalog\AppIndexTest.kt`
- [ ] T037 [P] Unit: `ActionDispatcher` возвращает `BlockedByPolicy` при невалидном запросе в `c:\work\launcher\core\src\test\java\com\launcher\core\actions\ActionDispatcherTest.kt`
- [ ] T038 Выполнить `c:\work\launcher\gradlew.bat :core:testDebugUnitTest` и устранить падения
- [ ] T039 Сверить фактические тесты и ручные проверки с таблицей [plan.md](./plan.md) § **Test matrix (FR-028)**; при расхождении обновить план или добавить тесты
- [ ] T040 Проверить соответствие `c:\work\launcher\specs\001-launcher-core-foundation\quickstart.md` фактическим путям и командам после сборки
- [ ] T041 Пройти grep: отсутствие `registerReceiver` в `c:\work\launcher\app\src\` (кроме сгенерированного/оправданного) — только `core` владеет bridge
- [ ] T042 *(отложено, не блокирует MVP)* Instrumented: сценарий `AppIndex` при ограниченной видимости пакетов — в `c:\work\launcher\app\src\androidTest\java\` когда Robolectric недостаточен ([plan.md](./plan.md) матрица, строка AppIndex)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** → **Phase 2** → **Phase 3 (US1)** → **Phase 4 (US2)** → **Phase 5 (US3)** → **Phase 6**
- US2 и US3 теоретически параллелятся после US1, но **US3** опирается на стабильный `EventRouter`/`SystemEventBridge` из Phase 2–3; на практике линейный порядок выше безопаснее.

### User Story Dependencies

- **US1** зависит от Phase 2.
- **US2** зависит от US1 (нужен рабочий bootstrap Application).
- **US3** зависит от Phase 2–3 (bridge и router в прод-коде).

### Parallel Opportunities

- T009–T013, T024–T025, T030, T033, T036–T037 — разные файлы, можно распределять (T023 smoke — после T022, не параллелить с ним).

---

## Parallel Example: US1 tests

```bash
# Параллельно разработчикам (после Phase 2, включая T023):
Task: ProfileEngineTest.kt   # T024
Task: CompositionResolverTest.kt   # T025
```

---

## Implementation Strategy

### MVP (только US1)

1. Phase 1 → Phase 2 (включая **T023** smoke `LauncherCore`) → Phase 3 (US1) с тестами **T024–T025**.
2. Остановка и валидация на эмуляторах **API 26** и **API 35** (см. чекпоинт US1).

### Полный инкремент фичи 001

1. Довести US2 и US3.
2. Phase 6 — AppIndex/ActionDispatcher тесты, сверка с матрицей плана (**T039**), grep (**T041**); **T042** по необходимости.

---

## Summary

| Метрика | Значение |
|--------|----------|
| Всего задач | T001–T042 (**42**, T042 опционально) |
| Phase 1 | T001–T008 |
| Phase 2 (включая smoke T023) | T009–T023 |
| US1 | T024–T029 |
| US2 | T030–T032 |
| US3 | T033–T035 |
| Polish | T036–T042 |
| Параллельные маркеры [P] | см. § Parallel Opportunities |

**Suggested next command**: `/speckit.analyze` или `/speckit.implement`.
