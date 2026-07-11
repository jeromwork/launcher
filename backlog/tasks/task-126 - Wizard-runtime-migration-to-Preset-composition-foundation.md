---
id: TASK-126
title: 'Wizard runtime migration to Preset composition foundation'
status: Draft
assignee: []
created_date: '2026-07-11 07:45'
updated_date: '2026-07-11 07:45'
labels:
  - phase-2
  - refactor
  - preset
  - wizard
  - one-way-door
milestone: m-1
dependencies:
  - TASK-120
priority: high
ordinal: 126000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

TASK-120 положил **новый фундамент** для настройки лаунчера — модель `Component / Pool / Preset / Profile` в пакете `com.launcher.preset.*`. Но реально first-run flow, Settings, BootCheck работают через **старую** систему `com.launcher.api.wizard.*` (WizardEngine + WizardStep + WizardManifest). Два движка живут параллельно.

Эта задача — **переехать production wizard на новый фундамент**, потом удалить старый пакет целиком.

По шагам:
1. Переключить `FirstLaunchActivity` на `PresetBootstrap.bootstrap()` + `ReconcileEngine.run(RunMode.Wizard)`.
2. Мигрировать `WizardScreen` (Compose UI) чтобы работать с `ProfileComponent`/`Component` вместо `WizardStep`.
3. Переписать `WizardEngineImpl` — вся его логика уже дублирована в `ReconcileEngine`; убрать двойник.
4. Мигрировать все существующие `CheckHandler` / `ApplyHandler` пары → `Provider<T : Component>` реализации.
5. Мигрировать `SystemSettingPort` / `PermissionRequestPort` в новые `Component.SystemPermission` subtype + Provider.
6. Мигрировать Settings screens (PendingChecklistViewModel и другие) с `ConfigKind` на `Preset.settingsMap[]`.
7. Мигрировать `WizardCheckpoint` → `Profile.preWizardSnapshot` (уже существует, просто переключить callers).
8. Мигрировать 439+ импортов legacy wizard через codebase.
9. Обновить E2E-тесты (`BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest`).
10. **Удалить** `core/src/commonMain/kotlin/com/launcher/api/wizard/` целиком (~15 файлов).
11. Удалить `Spec015Module` legacy bindings, переработать на `Task120Module` расширение.

## Зачем

Дублирование движка (WizardEngine + ReconcileEngine делают одно и то же — check/apply loop) нарушает CLAUDE.md rule 4 (MVA) и rule 7 (fitness functions — сейчас невозможно enforce «один engine»). Пока legacy `wizard.*` жив:
- Каждый bug fix надо делать в двух местах (WizardEngine и ReconcileEngine).
- Fitness function #2 «engine не switch'ит на subtype» покрывает только новый ReconcileEngine, legacy остаётся дырой.
- `Component / Provider` архитектура (ECS-style: Component = data, Provider = system, Profile = world) не приносит value пока real UI на неё не переехал.
- Cross-artifact tracing спек путается: одни ссылаются на `WizardStep`, другие на `Component`.

Плюс: пользователь на устройстве получает **всё то же самое** (первый запуск лаунчера), но под капотом — один coherent engine, готовый к расширению новыми Component subtypes (MessengerTile из TASK-121, SignInGoogle из draft-1 wizard).

## Что входит технически (для AI-агента)

- **Wire-format сохранение**: legacy `wizard.manifest` / `system-settings.pool` / `ui-customization.pool` JSON'ы (если использовались как persisted config) читаются миграционным writer'ом → конвертируются в `Preset` schemaVersion=2 + `Pool` schemaVersion=1. Backward-compat test обязателен (CLAUDE.md rule 5).
- **Migration mapping**:
  - `WizardStep` (interface) → `Component` subclass + `Provider<T>` реализация.
  - `StepType.UIChoice` → `Component.UIChoice` subtype (новый).
  - `StepType.SystemSetting` → `Component.SystemPermission` subtype (новый).
  - `StepType.TutorialHint` → `Component.TutorialHint` subtype (новый).
  - `WizardManifestBody.steps[]` → `Preset.wizardFlow[]` (с сохранением `order`, `criticality`, `canSkip`).
  - `WizardCheckpoint` → `Profile.preWizardSnapshot` (уже в TASK-120).
  - `WizardState.Running{currentStepIndex, totalSteps}` → derived from `Profile.components.filter { it.status == Pending }`.
  - `PendingStep` → `ProfileComponent` (со `status == Pending`).
  - `CheckHandler`/`ApplyHandler` (spec-015 pattern) → `Provider<T>.check()` / `Provider<T>.apply()`.
  - `SystemSettingPort` → перенести внутрь `SystemPermissionProvider` через facade pattern (rule 2 ACL).
  - `UserPreferencesStore` → остаётся отдельно (не связан с Preset, storage для i18n/theme personal overrides).
  - `WizardCheckpointStore` → удалить, `ProfileStore.setPreWizardSnapshot()` покрывает.
  - `DiagnosticEmitter` → перевести на общий logger (спека 006 telemetry) или удалить если не используется.
- **DI миграция**: `Spec015Module` → слиться с `Task120Module` (или переименовать Task120Module → PresetModule и удалить Spec015Module).
- **Fitness functions добавить**: #11 «нет импортов `com.launcher.api.wizard` в production code» (кроме миграционного writer'а на переходный период).
- **UI миграция**: `WizardScreen` Composable ↔ `ReconcileEngine.run(RunMode.Wizard, sink)`. `InteractionSink` — тонкая обёртка над `WizardViewModel` (StateFlow ProfileComponent → suspend answer).
- **E2E тесты**: `BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest` — переписать на `PresetBootstrap` + `ReconcileEngine` API. Golden JSON под `simple-launcher` регенерировать.
- **439 импортов**: организовать миграцию поэтапно (по субсистемам: сначала first-run, потом Settings, потом BootCheck, потом E2E, потом cleanup).

## Про роли в этой задаче

Задача чисто **техническая** — конечный пользователь (primary user / remote administrator / restricted caregiver) не замечает изменений. First-run flow выглядит и работает точно так же. Никаких новых persona-специфичных сценариев.

## Состояние

- **Foundation готов** — TASK-120 положил Component/Pool/Preset/Profile + ReconcileEngine + Provider ports + bundled JSON + Koin wiring (`task120Module`). Смока на Xiaomi Redmi Note 11 подтвердил что foundation резолвится без ошибок. Скриншот в `verification-evidence/task-120-xiaomi-first-launch.png`.
- **Blocking**: `PresetBootstrap` в Koin висит но никто его не вызывает; legacy wizard всё ещё powers FirstLaunchActivity.
- **Не начато**: migration/refactor work sam.

---

## Готовый промт для `/speckit.specify`

```
Заменить production wizard subsystem (com.launcher.api.wizard.*) на TASK-120 foundation
(com.launcher.preset.*) — full migration + deletion легаси-пакета.

ЧТО СТРОИМ:
End-to-end интеграция ReconcileEngine + Provider<T> + Profile в реальные first-run /
Settings / BootCheck / RemotePush flows. После миграции — production wizard, Settings,
BootCheck, E2E-тесты работают ТОЛЬКО через com.launcher.preset.*. Пакет
com.launcher.api.wizard.* удалён полностью.

ЗАЧЕМ:
Убрать дублирование engine (WizardEngineImpl + ReconcileEngine делают одно и то же).
Активировать ECS-архитектуру (Component=data, Provider=system, Profile=world) под
реальным UI, а не только в изоляции. Разблокировать простое добавление новых Component
subtypes (MessengerTile TASK-121, SignInGoogle draft-1) — сейчас каждый требует правки
в двух местах.

SCOPE ВКЛЮЧАЕТ:
- Миграция FirstLaunchActivity → PresetBootstrap + ReconcileEngine.run(RunMode.Wizard).
- Миграция WizardScreen (Compose) → InteractionSink pattern поверх ReconcileEngine.
- Новые Component subtypes для покрытия legacy StepType вариантов:
  UIChoice / SystemPermission / TutorialHint.
- Миграция всех CheckHandler/ApplyHandler пар (spec-015) → Provider<T> реализации.
- Миграция SystemSettingPort / PermissionRequestPort → внутрь SystemPermissionProvider
  через facade wrapper (rule 2 ACL).
- Миграция Settings screens (PendingChecklistViewModel и др.) с ConfigKind →
  Preset.settingsMap[].
- Wire-format migration writer: legacy wizard.manifest / system-settings.pool JSON →
  Preset + Pool. Backward-compat test обязателен (CLAUDE.md rule 5).
- DI консолидация: Spec015Module + Task120Module → единый PresetModule.
- E2E-тесты (BootBenchmarkE2ETest, BootCriticalMissingE2ETest, FirstLaunchPickerE2ETest,
  XiaomiOemMatrixE2ETest) переписать на новый API. Golden JSON regenerate.
- Fitness function #11 «нет импортов com.launcher.api.wizard в production code»
  (кроме миграционного writer'а на переходный период — TODO с датой removal).
- Удаление core/src/commonMain/kotlin/com/launcher/api/wizard/ целиком после
  того, как все 439 импортов переехали.
- Ручной прогон на Xiaomi Redmi Note 11 (первый запуск, Settings edit, force-reboot
  BootCheck), сравнение UX с pre-migration состоянием.

SCOPE НЕ ВКЛЮЧАЕТ:
- Новые user-facing фичи — задача чисто рефакторинговая. Пользователь не замечает
  изменений.
- MessengerTile (TASK-121) — отдельная задача, использует уже стабилизированный
  Provider port контракт из этой миграции.
- SignInGoogle Component subtype (draft-1) — отдельная задача, добавляется поверх.
- iOS parity — foundation KMP-ready, iOS providers post-MVP.

DEPENDENCIES:
- TASK-120 (Component/Preset/Profile foundational model) — DONE. Foundation готова.
- Нет других upstream tasks.

ACCEPTANCE CRITERIA (проверяемые человеком):
1. На Xiaomi Redmi Note 11: fresh install → первый запуск показывает 3 preset options
   (workspace / launcher / simple-launcher), выбор простого запускает wizard, каждый
   шаг применяется, финальный экран — home с настроенными плитками. UX идентичен
   pre-migration состоянию (compare with verification-evidence/task-120-xiaomi-first-launch.png).
2. На Xiaomi: полный wizard проход простого лаунчера завершается без падений и без
   двойных диалогов permission.
3. На Xiaomi: force-close во время wizard step 2 → reopen → wizard возобновляется
   с step 2 (проверка Profile.preWizardSnapshot persistence).
4. На Xiaomi: complete wizard, force-reboot устройства → BootCheck реapplyит критичные
   компоненты, verify persistence.
5. `git grep "import com.launcher.api.wizard"` (без миграционного writer'а) возвращает
   0 совпадений в production code.
6. Файлы `core/src/commonMain/kotlin/com/launcher/api/wizard/*.kt` удалены (проверить
   git log).
7. `./gradlew :app:testMockBackendDebugUnitTest :core:testMockBackendDebugUnitTest`
   зелёный.
8. `./gradlew :app:connectedMockBackendDebugAndroidTest --tests "*E2E*"` зелёный на
   Xiaomi.

LOCAL TEST PATH:
- Unit: `:core:testMockBackendDebugUnitTest` (ProfileFactory, ReconcileEngine, все
  новые Provider реализации).
- Robolectric: `:app:testMockBackendDebugUnitTest` (BundledAssetsLoadTest,
  PresetBootstrapTest, миграционный writer tests).
- E2E: Xiaomi Redmi Note 11 через `adb`.

CONSTITUTION GATES:
- Article I (Architecture) — port/adapter split preserved.
- Article V (Modularization With Restraint) — консолидация DI модулей, не размножение.
- Article VII §9-13 (Preset composition) — canonical model уже установлена в TASK-120.
- Article XI (Simplicity) + rule 4 (MVA) — удаление дубликата engine, не добавление
  новых абстракций.
- Article XVI Gate 6 (Battery+Performance) — ReconcileEngine cold-start delta ≤ 30 ms
  на Pixel 5 baseline (уже проверено TASK-120).

EFFORT:
Средне-большая — миграция 439 импортов + все E2E тесты + удаление 15 файлов. Основная
сложность — не сам код (foundation готов), а количество call sites и необходимость
верификации на устройстве после каждой фазы.
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- SECTION:AC:BEGIN -->

<!-- AC заполнится через procedure-sync-backlog-ac после /speckit.specify + /speckit.clarify. -->

- [ ] #1 Foundation готова и подключена end-to-end (FirstLaunchActivity → PresetBootstrap).
- [ ] #2 На Xiaomi Redmi Note 11 первый запуск проходит через новый engine без визуальных отличий от pre-migration.
- [ ] #3 `git grep "import com.launcher.api.wizard"` возвращает 0 в production code.
- [ ] #4 Пакет `com.launcher.api.wizard/*` удалён.
- [ ] #5 E2E-тесты (`BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest`) зелёные на Xiaomi.

<!-- SECTION:AC:END -->
