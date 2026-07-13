---
id: TASK-126
title: Wizard runtime migration to Preset composition foundation
status: Done
assignee: []
created_date: '2026-07-11 07:45'
updated_date: '2026-07-13 06:36'
labels:
  - phase-2
  - refactor
  - preset
  - wizard
  - one-way-door
milestone: m-1
dependencies:
  - TASK-120
references:
  - specs/task-126-wizard-runtime-migration/
priority: high
ordinal: 126000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

**Артефакты**: [spec.md](../../specs/task-126-wizard-runtime-migration/spec.md) · [plan.md](../../specs/task-126-wizard-runtime-migration/plan.md) · [tasks.md](../../specs/task-126-wizard-runtime-migration/tasks.md) · contracts: [preset v2](../../specs/task-126-wizard-runtime-migration/contracts/preset-schema-v2.md), [pool v2](../../specs/task-126-wizard-runtime-migration/contracts/pool-schema-v2.md), [hint-pool v1](../../specs/task-126-wizard-runtime-migration/contracts/hint-pool-schema-v1.md)

## Что это простыми словами

TASK-120 положил **новый фундамент** для настройки лаунчера — модель `Component / Pool / Preset / Profile` в пакете `com.launcher.preset.*` + `ReconcileEngine`. Но first-run flow, Settings, BootCheck реально работают через **старую** систему `com.launcher.api.wizard.*`. Плюс есть **третий** параллельный мир — TASK-65 `com.launcher.api.preset.*`. Три движка живут параллельно.

Эта задача — **переехать production wizard, Settings и BootCheck на TASK-120 фундамент**, потом удалить оба старых мира целиком. Пользователь на устройстве видит ровно то же самое до и после (задача чисто техническая, refactoring без изменения UX).

По шагам (6 фаз, один PR, много commit'ов):
1. **Phase 1 — новые компоненты**: добавить 4 Component subtypes (`LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`), их Provider'ы, `HintPoolSource` port + bundled adapter, extend Pool descriptors (`requires`, `required`), `PresetValidator` возвращает `Result<Preset, ValidationError>` (не throw), bumpsы schemaVersion (Preset v1→v2, Pool v1→v2, hint-pool v1 new).
2. **Phase 2 — первый запуск**: переключить `FirstLaunchActivity` на `PresetBootstrap` + `ReconcileEngine.run(RunMode.Wizard)`. Новый `WizardViewModel` + `WizardScreen` через `StateFlow<ReconcileState>` + `InteractionSink`. SplashScreen пока bootstrap идёт. Denial UX: `required=false` → skip, `required=true` → предложить другой preset (никакой «переустановите app»).
3. **Phase 3 — Settings**: `PendingChecklistViewModel` и все Settings screens мигрируют с `ConfigKind` на `Preset.settingsMap[]` + `ProfileStore`. Аудит `PresetSelectionService` / `PresetSwitchService` — переписать чисто под ECS-нотацию, не тащить TASK-65 legacy.
4. **Phase 4 — BootCheck**: `BootCheckReceiver.onReceive()` только диспетчит в `BootCheckWorker` (WorkManager) и возвращается за ~100 мс — обход 10-секундного ANR-лимита. Все `CheckHandler`/`ApplyHandler` пары мигрируют в `Provider<T>`. `WizardCheckpointStore` удаляется — `ProfileStore.setPreWizardSnapshot()` покрывает.
5. **Phase 5 — E2E-тесты**: 4 теста (`BootBenchmarkE2ETest`, `BootCriticalMissingE2ETest`, `FirstLaunchPickerE2ETest`, `XiaomiOemMatrixE2ETest`) переписать на новый API. Golden JSON `simple-launcher` регенерируется под schemaVersion 2.
6. **Phase 6 — удаление legacy**: удалить `com.launcher.api.wizard/*` (~26 файлов), `com.launcher.api.preset/*` (~4 файла TASK-65), `api.profile/*`, `api.pools/*`, `api.switchstrategy/*`, `adapters.wizard/*` (~13 файлов), `assets/wizard/`, `WizardActivity`. DI-модули `Spec015Module` + `Task65Module` сливаются в переименованный `PresetModule`. Из manifest'а удаляется `uses-accessibility-service` + сам класс `AccessibilityService`. Добавляется lint-правило FF-011: любой `import com.launcher.api.wizard` → build failure. `git grep "import com.launcher.api.wizard"` и `git grep "import com.launcher.api.preset"` → 0 в production.

## Зачем

Три параллельных мира engine (legacy wizard + TASK-65 preset + TASK-120 foundation) нарушают CLAUDE.md rule 4 (MVA) и rule 7 (fitness functions — невозможно enforce «один engine»). Пока legacy живёт:
- Каждый bug fix надо делать в 2–3 местах.
- ECS-архитектура (Component = data, Provider = system, Profile = world) не приносит value пока real UI не на ней.
- Cross-artifact tracing путается: одни ссылаются на `WizardStep`, другие на `Component`.
- `AccessibilityService` в manifest'е — риск при публикации в Play Store, плюс лишнее разрешение.

Плюс: разблокируется добавление новых Component subtypes (MessengerTile из TASK-121, SignInGoogle из draft-1) — сейчас каждый требует правки в трёх местах.

## Что входит технически (для AI-агента)

**Новые модели (`core/commonMain`)**:
- `Component.LauncherRole` (no parameters), `Component.Theme` (paletteSeedHex, typographyScale, shapeStyle, darkMode), `Component.Language` (locale, sentinel `"system"`), `Component.StatusBarPolicy` (no parameters).
- `HintFlowEntry` (hintId, targetComponentId, textKey) + `Preset.hintFlow: List<HintFlowEntry>?`.
- `Preset.wizardPresentation: { darkMode, typographyScale }` — тема wizard'а отдельно от главного фасада (CL-2).
- `Pool.ComponentDeclaration.requires: List<String>?` + `required: Boolean` (default false).
- `ValidationError` sealed class (`RequiresOrderViolation`, `UnknownComponentId`, `NullLocale`, `SchemaVersionUnsupported`).

**Ports (`core/commonMain`)**:
- `HintPoolSource` — `suspend fun load(): List<HintFlowEntry>` + inline TODO(shareability) для будущих ConfigSource adapters (file import, share intent, marketplace) per CLAUDE.md rule 9.
- `InteractionSink` — `suspend fun answer(componentId, response)`.

**Adapters (`androidMain` / `app`)**:
- `LauncherRoleProvider` (RoleManager on API ≥ 29, Intent.ACTION_MAIN + CATEGORY_HOME для API 26–28).
- `ThemeProvider`, `LanguageProvider` (AppCompatDelegate.setApplicationLocales), `StatusBarPolicyProvider` (WindowInsetsControllerCompat + MIUI fallback FLAG_FULLSCREEN inline).
- `BundledHintPoolSource` (assets/hint-pool.json, missing → пустой список без crash).
- `ThemeCatalog` (assets/theme-catalog.json, expand `ThemeRef(name)` → flat fields at write time; wire format никогда не содержит ThemeRef).
- `BootCheckWorker` (WorkManager CoroutineWorker; reconcile выполняется вне receiver context).

**Wizard state model (CL-5, ключевая ревизия)**:
- **Нет отдельного `WizardStore` / `lastCompletedStepIndex`**. Прогресс derived на каждом запуске через `Provider.check()` для каждого Component в `wizardFlow`. Источник правды = Android OS state + `Profile`. Исключает drift когда user granted permission через system Settings.
- `WizardViewModel` retained через `SavedStateHandle` — переживает rotation / dark-mode / language change. Target rebuild < 200 ms.
- Language change mid-wizard → интерактивная пересборка UI, не «apply on next step».

**Validation & failure (CL-8)**:
- `PresetValidator.validate() → Result<Preset, ValidationError>` — никаких exception через domain boundary (rule 1).
- Compile-time gate: `BundledPresetValidationTest` итерирует все JSON под `assets/presets/` в CI.
- Production runtime failure → user-facing error screen («Не удалось загрузить настройки. Попробуйте переустановить приложение.») + auto-anonymized crash report владельцу (payload opaque, no PII, aligns rule 13). Adapter selection (Firebase Crashlytics / Sentry / custom Worker endpoint) deferred.

**Denial UX (CL-6)**:
- `required=false` step declined → mark Skipped, wizard proceeds, no re-prompt.
- `required=true` step declined → blocking screen «Этот preset требует X. Попробуй preset Y, где это необязательно» (не «переустановите app»).
- Design guideline: preset authors предпочитают `required=false` где возможно.

**Wire-format migration**:
- **Нет migration writer'а** — no production users (D1, Article XX). Legacy `assets/wizard/` удаляется outright.
- Preset v1→v2, Pool v1→v2 — additive/backward-compat (v1 readers ignore new fields; v2 readers default missing).
- hint-pool.json new (schemaVersion 1).

**DI консолидация**: `Task120Module` → переименовать в `PresetModule`, слить туда `Spec015Module` + `Task65Module`, удалить оригиналы. Phase 6 grep gates: `git grep "Spec015Module\|Task65Module"` → 0.

**Fitness function FF-011**: custom Android lint rule → import `com.launcher.api.wizard` / `com.launcher.api.preset` в `app/` или `core/` production source → build failure с сообщением. Тест на fixture.

**Manifest cleanup**: удалить `uses-accessibility-service`, `<service>` declaration + сам класс `AccessibilityService`. Обновить `docs/compliance/permissions-and-resource-budget.md`.

**E2E migration**: 4 теста + regeneration golden JSON `simple-launcher` (before/after diff в PR description).

**Legacy scope** (measurement): **411 импортов legacy wizard в 87 файлах** (проверено на recon session 1; исходное «439» — устаревшее). Плюс TASK-65 preset (4 файла) + профили + pools + switchstrategy. Всего к удалению ~50+ файлов production кода.

## Про роли в этой задаче

Задача чисто **техническая** — конечный пользователь (primary user / remote administrator / restricted caregiver) не замечает изменений. Никаких новых persona-специфичных сценариев. UX идентичен pre-migration screenshot'у.

## Корректировки модели в процессе

**2026-07-11 — Session 1 (mentor recon + market research)**:
- Изначальная «439 импортов» уточнена до **411 импортов в 87 файлах**.
- Три параллельных мира вместо двух: legacy wizard + TASK-65 preset + TASK-120 foundation. Все три схлопываются в TASK-120.
- Wire-format migration writer **отменён** — нет production пользователей (D1).

**2026-07-11 — Session 2 (Q1–Q6 resolved, opsx design.md D1–D9)**:
- LauncherRole без параметров, ThemeRef → flat expansion at write time, Language(locale) с `"system"` sentinel, TutorialHint через отдельный `hint-pool.json` (не Component subtype!), permissions Provider-inline, StatusBarPolicy через WindowInsetsController + AccessibilityService удаляется, `requires` field на pool descriptors.

**2026-07-11 — First clarify pass (CL-1..CL-4)**:
- SplashScreen API пока PresetBootstrap инициализируется (CL-1).
- Wizard/Settings как обычное Android-приложение; kiosk настройки применяются только post-wizard (CL-2). `Preset.wizardPresentation` — отдельное поле.
- Аудит `PresetSelectionService`/`PresetSwitchService` в Phase 3 (CL-4), grep gate в Phase 6.

**2026-07-11 — Second clarify pass (CL-5..CL-9, крупная ревизия)**:
- **CL-5 REVISES CL-3**: `WizardStore` / `lastCompletedStepIndex` **удалены**. Прогресс derived через `Provider.check()` на каждом запуске. Wizard/Settings могут визуально отличаться от главного фасада (FR-021). Language change → интерактивная пересборка (< 200 ms).
- **CL-6**: denial UX — `required=false` skip, `required=true` — сменить preset, не переустанавливать app.
- **CL-7**: `HintPoolLoader` → `HintPoolSource` port + `BundledHintPoolSource` adapter (rule 9); hint-pool.json gains `schemaVersion: 1` (rule 5).
- **CL-8**: `PresetValidator` → `Result<Preset, ValidationError>` (не throw); compile-time gate `BundledPresetValidationTest`; production runtime failure → error screen + anonymized crash report (adapter deferred).
- **CL-9**: `BootCheckReceiver` → dispatch only, `BootCheckWorker` (WorkManager) выполняет reconcile — обход 10-секундного ANR-лимита. Interactive updates preferred при configuration/locale change.

**2026-07-11 — speckit-analyze (READY-WITH-CAVEATS)**:
- 22/22 FR имеют traceability (FR-020 crash-adapter honestly deferred).
- 93 tasks в 6 фазах; 4 `[deferred-physical-device]` gate (T063, T075, T087, T095).
- Constitution 13/13 PASS.
- **Обнаружены 4 blocking drift'а до старта Phase 2** (не Phase 1): plan.md строки 243/316/327 всё ещё упоминают `WizardStore` / `HintPoolLoader` (устарело после CL-5/CL-7); spec.md SEQ-3 всё ещё показывает `PresetValidationException` throw (устарело после CL-8). Требуется clean-up перед Phase 2.
- Чеклисты (17 файлов, 66 stale упоминаний устаревших терминов) — писались до CL-5..CL-9, часть FAIL'ов больше не валидны. Non-blocking; рекомендуется перезапустить `state-management`, `failure-recovery`, `wire-format`, `preset-readiness` при следующем touch'е.

## Состояние

- **Foundation готов** — TASK-120 (Component/Pool/Preset/Profile + ReconcileEngine + Provider ports + bundled JSON + Koin wiring). Xiaomi Redmi Note 11 smoke пройден (verification-evidence/task-120-xiaomi-first-launch.png).
- **Speckit cycle завершён**: `specify` → `clarify` (два pass'а, CL-1..CL-9) → `scenarios` → `plan` → `tasks` (93 задачи) → `analyze` (READY-WITH-CAVEATS).
- **Готово к implementation Phase 1** (T010..T045 — доменные модели, ports, adapters, fake'и). До старта Phase 2 (T050..T063) закрыть 4 blocking drift'а из analyze-report.
- **Не начато**: implementation работы.

---

## Готовый промт для `/speckit.specify` (historical, archived 2026-07-11)

> **Note**: этот промт использовался как starting point для speckit-specify. После двух clarify pass'ов (CL-1..CL-9) финальный scope расширился (denial UX, WizardStore removed, BootCheckWorker, Result-based validation, HintPoolSource port, crash reporting). Оставлен для archival continuity — актуальные требования смотри в spec.md.

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
