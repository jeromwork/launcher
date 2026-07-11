---
id: DRAFT-2
title: Wizard runtime migration to Preset composition foundation
status: Draft
assignee: []
created_date: '2026-07-11 07:45'
updated_date: '2026-07-11 13:17'
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

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 mentor — 2026-07-11 explore/paused

Paused for fresh session. **No code touched.** Explore stance до конца — recon + market research + 6 open questions владельцу.

#### Recon: три параллельных мира в repo

1. **Legacy wizard** (`com.launcher.api.wizard.*` 26 files + `ui.wizard.*` ~10 + `adapters.wizard.*` ~13) — 411 imports across 87 files, powers `FirstLaunchActivity` сегодня
2. **TASK-65 preset model** (`com.launcher.api.preset.*` 4 files + `api.profile.*` + `api.switchstrategy.*`) — mid-flight, используется `PresetPickerScreen` + `PresetSelectionService` (последний импортирует ОБА world 1 и world 2)
3. **TASK-120 foundation** (`com.launcher.preset.*`) — parallel, unused, 4 MVP Component subtypes wired через `task120Module`

**Owner directive 2026-07-11**: всё legacy (worlds 1 + 2) выпиливаем без следов. TASK-120 — единственная правда. Wire-format migration writer НЕ нужен (нет пользователей).

#### Key finding: TASK-120 foundation НЕ покрывает interaction-примитивы

MVP Component wave: `AppTile`, `FontSize`, `Sos`, `Toolbar` — outcome state. TASK-120 полностью НЕ адресует:
- **HOME role** — ноль упоминаний в spec
- **Прочие system permissions** (POST_NOTIFICATIONS, CALL_PHONE, AccessibilityService, battery-optimization) — но `FailReason.PermissionDenied` first-class + design intent (CHK013) = Provider-inline preconditions
- **Theme, Language** — были в session 2 middle-granularity list как `Theme(name)` / `Language(...)`, superseded в session 2.5
- **TutorialHint** — ноль упоминаний где-либо в TASK-120
- **hide_status_bar** — UX chrome

Из legacy [android-pool.json](../../core/src/androidMain/assets/wizard/system-settings/android-pool.json) 6 системных настроек нуждаются в mapping'е: `android.role.home`, `POST_NOTIFICATIONS`, `CALL_PHONE`, `accessibility.our-service`, `battery.ignore_optimizations`, `hide_status_bar`.

#### Market research 2026-07-11

**Coach marks / TutorialHint** ([NN/g contextual onboarding](https://www.nngroup.com/articles/onboarding-tutorials/), [Onborda](https://onboardjs.com/blog/5-best-react-onboarding-libraries-in-2025-compared)):
- Contextual just-in-time > upfront tour; one hint at a time; dismissible
- Onborda pattern «named tours над плоским catalog steps» — mirror нашего Pool→Preset
- Per-preset variability подтверждена рынком

**Material 3 Theme** ([Codelab](https://codelabs.developers.google.com/jetpack-compose-theming), [Compose M3](https://developer.android.com/develop/ui/compose/designsystems/material3), [Design tokens practical guide](https://softaai.com/design-tokens-in-material-design-with-jetpack-compose/)):
- Tokens трёхуровневые (reference → system → component), но production apps используют 3 seed'а + tonal palette algorithm
- Coarse `Theme(seed, typography, shape, dark)` совпадает с production practice
- Atomic 25-token подход = overengineering для senior-safe launcher

**Flat vs nested schema** ([Terraform provider design principles](https://developer.hashicorp.com/terraform/plugin/best-practices/hashicorp-provider-design-principles), [module composition](https://developer.hashicorp.com/terraform/language/modules/develop/composition)):
- Schema follows API structure — не изобретать nesting
- Composition через references (наш `poolRef`) — market-approved
- Additive versioning (rule 5) даёт path к nested-расширению без ломки — **NOT one-way door**

#### Package delete list (черновой, sanity-check в следующей сессии)

**Уверенно на удаление:**
- `core/commonMain/com/launcher/api/wizard/` (26 files)
- `core/commonMain/com/launcher/ui/wizard/` (~10 files: WizardEngineImpl, WizardHostScreen, steps/*)
- `core/androidMain/com/launcher/adapters/wizard/` (~13 files: handlers + stores + facades)
- `core/commonMain/com/launcher/api/preset/` (TASK-65 model, 4 files)
- `core/commonMain/com/launcher/api/profile/` + `androidMain/adapters/profile/` (заменяется `preset.port.ProfileStore` + `DataStoreProfileStore`)
- `core/commonMain/com/launcher/api/pools/` + `api/switchstrategy/`
- `app/main/java/com/launcher/app/wizard/` (WizardActivity + NoopAdapters)
- DI: `Spec015Module.kt`, `Task65Module.kt` (сливаются в переименованный `PresetModule`)
- Assets `core/androidMain/assets/wizard/` (tile-sets/*, system-settings/*)
- Тесты под `core/commonTest/**/wizard/**`, `androidUnitTest/**/wizard/**`

**Требует переписать (не удалить):**
- `core/androidMain/adapters/preset/PresetSelectionService|PresetSwitchService|PresetReminderService` — импортируют TASK-65 + wizard.ConfigSource, перевести на TASK-120 API
- Compose UI legacy: `core/commonMain/ui/screens/WizardScreens.kt`, `SettingsScreen.kt`, `ui/navigation/WizardComponents.kt` — переписать под `InteractionSink` pattern
- E2E тесты: `BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest` — на `PresetBootstrap + ReconcileEngine` API

**Требует уточнения scope:**
- `core/commonMain/api/setup/*`, `adapters/setup/CallPhoneCheckAdapter.kt`, `ui/dialog/CallPhoneRationaleScreen.kt` — spec-010 setup-assistant, отдельная параллельная жизнь; проверить в scope миграции или нет
- `core/commonMain/api/config/*` (ConfigDocument, ConfigDocumentWireFormat, ConfigSnapshot) — spec-009 config history, НЕ legacy этого task, оставить

#### Owner input — уже зафиксировано в session 1

- **HOME role = Component** (per-preset user override), не bootstrap — семантика params уточняется в Q1
- **Preset schema НЕ должна жёстко зашивать flat** — сохранить path к future nesting через schemaVersion + optional field additive
- **Theme**: coarse vs atomic — market view дан, ждём choice (Q2)
- **Language** — один Component (Q3 confirm)
- **TutorialHint** — per-preset composition, не полностью вне; возможно свой Pool (Q4)
- **Permissions Provider-inline** — просит объяснить (Q5)
- **hide_status_bar / AccessibilityService** — просит объяснить (Q6)

#### Open questions for next session

**Q1. HOME role Component params.** С чем? `LauncherRole(mode: Prompt|SkipIfSet|Reject)` или без параметров (просто наличие в preset = «Wizard спросит»)?

**Q2. Theme granularity.** (a) coarse `Theme(paletteSeedHex, typographyScale, shapeStyle, darkMode)` 1 subtype 4 params, (b) atomic `PaletteSeed / Typography / Shapes / DarkMode` 4 subtypes, (c) hybrid?

**Q3. Language.** `Language(locale)` — confirm единый subtype один параметр?

**Q4. TutorialHint layout.**
- (a) отдельный `hint-pool.json` (mirror Component/Pool паттерн) + новое поле `hintFlow: List<HintFlowEntry>` в Preset рядом с `wizardFlow/settingsMap/activeComponents` — рекомендация
- (b) новый `Component.TutorialHint(targetId, textKey)` subtype в общем pool — унифицирует, но нарушает FR-025 (TutorialHint не reconcile-able)

**Q5. Provider-inline permissions.** Confirm понимание формулировки:
> «SosProvider знает что для звонка нужен CALL_PHONE. В pool.json НЕТ отдельной записи `call-phone-permission`. Пользователь видит permission-запрос через Wizard только когда Sos-компонент проходит через Wizard и его check() возвращает Failed(PermissionDenied). После grant Sos применяется. Никаких изолированных permission-шагов в preset'е.»

**Q6. hide_status_bar / AccessibilityService.**
- (a) bootstrap `WindowInsetsController` при старте launcher app, AccessibilityService **удаляется целиком** — рекомендация (Play Store risk + лишний permission gone)
- (b) `Component.StatusBarPolicy(hidden: Bool)` — preset решает
- (c) сохранить legacy AccessibilityService — против рекомендации

#### Session 1 склоны (для дальнейшего давления)

- **Theme**: (a) coarse — совпадает с M3 production practice, простое ментальное моделирование, atomic — future additive через wire-format bump
- **TutorialHint**: (a) отдельный hint-pool + `hintFlow` — TutorialHint принципиально не reconcile-able (нельзя apply/check)
- **hide_status_bar**: (a) bootstrap WindowInsetsController — удаляет весь AccessibilityService пласт риска

#### Session 2 entry point

### Session 2 mentor — 2026-07-11 Q1-Q6 resolved

Все 6 вопросов закрыты. Дополнительно выяснены: cross-app TODO, один PR (много коммитов), component dependency validation.

#### Решения session 2

| # | Вопрос | Решение |
|---|---|---|
| Q1 | LauncherRole params | Без параметров. `check()` = SkipIfSet внутри. Reject = не включать в preset |
| Q2 | Theme | Гибрид: `ThemeRef(name)` раскрывается в `PaletteSeedHex + TypographyScale + ShapeStyle + DarkMode` при записи. Wire-format всегда flat |
| Q3 | Language | `Language(locale)` где `"system"` = sentinel (системный locale) |
| Q4 | TutorialHint | Отдельный `hint-pool.json` + `hintFlow: List<HintFlowEntry>` в Preset. Не в основном pool |
| Q5 | Permissions | Provider-inline. Нет отдельных Permission компонентов |
| Q6 | StatusBarPolicy | `Component.StatusBarPolicy` в preset, Provider через `WindowInsetsController`. AccessibilityService удалить |
| new | Component dependencies | `requires: [ComponentRef]` в pool.json. Валидатор при десериализации Preset проверяет порядок |
| new | Cross-app | TODO в коде + TASK-127 Draft (только описание, без spec) |
| new | Один PR | Много коммитов по подсистемам (Phase 1→6), один PR |

#### OpenSpec артефакты (session 2, вспомогательные)

Созданы в `openspec/changes/task-126-wizard-runtime-migration/` — design.md (D1-D9 решения + план 6 фаз) и tasks.md (47 задач). Используй как входной материал для `/speckit.specify`, не как замену speckit pipeline.

Причина возврата на speckit: opsx не даёт sequence диаграмм, MENTOR-DETAIL объяснений и архитектурных чеклистов — критично для владельца-новичка.

### Decision (English, immutable) 🔒

**Choice**: Migrate all production wizard flows to TASK-120 foundation (`com.launcher.preset.*`). Delete legacy `com.launcher.api.wizard.*` and TASK-65 `com.launcher.api.preset.*` entirely. Add 4 new Component subtypes (LauncherRole, Theme, Language, StatusBarPolicy). Permissions are Provider-inline. TutorialHint lives in separate hint-pool. Component ordering validated via `requires` field in pool.json.

**Rationale**: No production users → no migration writer needed. Single engine (ReconcileEngine) eliminates dual-maintenance. ECS architecture (Component/Provider/Profile) delivers value only when real UI is on it. ThemeRef expands to flat fields at write time to avoid wire-format ambiguity. Provider-inline permissions prevent orphan Permission components when parent Component is removed.

**Applies to**: TASK-126 migration scope — `FirstLaunchActivity`, `WizardScreen`, `PendingChecklistViewModel`, `BootCheckReceiver`, all `CheckHandler`/`ApplyHandler` pairs, E2E tests. TASK-121 (MessengerTile) and TASK-127 (cross-app shared Profile) build on top of stabilized Provider port contract from this task.

**Trade-offs accepted**: One large PR (phased commits) instead of multiple PRs. ThemeRef is design-time sugar only — no runtime named-theme lookup. TutorialHint has no reconcile semantics — UI layer owns rendering.

**Exit ramp**: If ReconcileEngine proves inadequate for new Component types → introduce `ReconcileStrategy` port (additive, no rewrite). If cross-app Theme sharing needed → lift `Theme` + `Language` into shared Profile layer (TASK-127, additive move, no wire-format break).

<!-- SECTION:DISCUSSION:END -->

## Acceptance Criteria

<!-- SECTION:AC:BEGIN -->

<!-- AC заполнится через procedure-sync-backlog-ac после /speckit.specify + /speckit.clarify. -->

- [ ] #1 Foundation готова и подключена end-to-end (FirstLaunchActivity → PresetBootstrap).
- [ ] #2 На Xiaomi Redmi Note 11 первый запуск проходит через новый engine без визуальных отличий от pre-migration.
- [ ] #3 `git grep "import com.launcher.api.wizard"` возвращает 0 в production code.
- [ ] #4 Пакет `com.launcher.api.wizard/*` удалён.
- [ ] #5 E2E-тесты (`BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest`) зелёные на Xiaomi.

<!-- SECTION:AC:END -->
