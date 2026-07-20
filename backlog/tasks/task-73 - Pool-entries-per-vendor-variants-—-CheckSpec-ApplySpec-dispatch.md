---
id: TASK-73
title: Pool entries per-vendor variants — CheckSpec/ApplySpec dispatch
status: Paused
assignee: []
created_date: '2026-07-01 04:15'
updated_date: '2026-07-20 07:15'
labels:
  - phase-3
  - area-preset
  - area-oem
  - foundation-followup
milestone: m-2
dependencies:
  - TASK-65
references:
  - specs/task-65-profile-composition-foundation-v2/
  - specs/task-73-pool-entries-per-vendor-variants/
priority: high
ordinal: 73000
---

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас в приложении есть класс `LauncherRoleProvider` — он умеет проверять и включать «быть launcher'ом по умолчанию» (Android HOME role). Работает он одним способом, рассчитанным на чистый Pixel: через стандартный системный диалог Android.

**Проблема:** на других производителях (Xiaomi, Huawei, Samsung) этот стандартный путь либо не работает, либо ведёт не туда:

- На **Xiaomi MIUI** — системный диалог выбора роли часто не применяется даже после тапа «Да»; нужен свой путь через настройки MIUI.
- На **Huawei без Google-сервисов** (EMUI/HarmonyOS) — стандартная проверка может упасть с ошибкой вместо честного «не настроено».
- На **Samsung One UI** — есть свои особенности (Knox), но для MVP хватает честного fallback.

**Что должно быть:**
1. Тот же самый `LauncherRoleProvider` пробует сначала vendor-специфичный путь (если для текущего производителя есть запись в файле-рецепте) → если нет, старый generic-путь (ничего не меняется для Pixel) → если и это не сработало, честно возвращает «не получилось» с понятным текстом инструкции.
2. Vendor-специфичные пути и текст ошибки лежат **в отдельном файле** (`vendor-recipes.json`), не в коде — новый рецепт для уже известного производителя добавляется без пересборки приложения.

## Зачем

- **UX real-world**: сейчас настройка «сделать launcher по умолчанию» реально работает только на устройствах, похожих на Pixel. Xiaomi/Huawei/Samsung — большинство рынка целевой аудитории — получают broken UX без обратной связи.
- **Community-authored presets** (в будущем): автор preset'а не должен думать про OEM-матрицу — это забота инфраструктуры, не автора.

## Что входит технически (для AI-агента)

**Важно**: технический раздел ниже (и «Готовый промт» ниже него) описывают **исходный, устаревший план** от 2026-07-01, написанный в терминологии TASK-65 (`CheckSpec`/`ApplySpec`/`Pool`/`ConfigSource`), которая была полностью заменена каноническим ECS (TASK-136, [ADR-013](../../docs/adr/ADR-013-canonical-ecs.md), 2026-07-18). Актуальная техническая модель — в `specs/task-73-pool-entries-per-vendor-variants/plan.md` (см. «Корректировки модели» ниже). Кратко, что реально строится:

- Переиспользуется **уже существующий** `enum Vendor` (не новый `VendorProfile`) и **уже существующий** `Provider<Component.LauncherRole>` (не `CheckSpec`/`ApplySpec`).
- Новые порты: `VendorDetector` (определяет `Vendor` по `Build.MANUFACTURER`, с alias-таблицей Redmi/POCO→Xiaomi) и `VendorRecipeSource` (читает `vendor-recipes.json`, по образцу уже существующих `PoolSource`/`PresetSource`).
- Vendor-логика — **внутри** `LauncherRoleProvider`, не через отдельный (уже существующий, но выключенный) вендорский диспетчер `ProviderRegistry.HandlerKey.vendor` — тот потребовал бы нового релиза на каждый vendor-override.
- Минимальное покрытие v1 — только `Component.LauncherRole` (3 vendor-override: Xiaomi/Huawei/Samsung); `POST_NOTIFICATIONS` выпал из скоупа — permissions не смоделированы как `Component` в текущей ECS.
- CI: Firebase Test Lab job на 3 устройства по PR-метке `oem-matrix-required`.

## Корректировки модели в процессе

- **2026-07-19 (grounding-коррекция при `/speckit.specify`+`/speckit.plan`)**: исходное описание задачи (2026-07-01) писалось до канонизации ECS (TASK-136, тот же день 2026-07-18) и использовало терминологию `CheckSpec`/`ApplySpec`/`PoolEntry`/`ConfigSource`, которой в текущем коде не существует. При написании spec.md обнаружено, что реальная архитектура — `Component`/`Provider`/`ProviderRegistry`/`Vendor` — и что часть желаемой инфраструктуры (3-tier vendor fallback в `ProviderRegistry.HandlerKey`) **уже существует в коде**, но не задействована (`runtimeVendor = null` в DI). Решение: vendor-recipe данные живут внутри `LauncherRoleProvider` (не через существующий, но выключенный vendor-tier — тот потребовал бы нового релиза на каждый override, что противоречит цели «без пересборки APK»). Обоснование — прямая цитата `docs/architecture/ecs.md` §4 п.7: «никаких правок в ReconcileEngine/ProviderRegistry/ProfileFactory». См. `specs/task-73-pool-entries-per-vendor-variants/research.md` (R1) для полного сравнения альтернатив.
- **2026-07-19 (вторая коррекция, при написании plan.md)**: изначальный план показывать `AlertDialog` из `Provider` — архитектурно неверен (`Provider` — адаптер, не UI-слой, не может сам рисовать диалоги). Заменено на переиспользование уже существующего канала `Outcome.Failed(FailReason.InternalError(messageKey))` → `LifecycleState.Failed` → `ApplyResult.Failed` (тот же путь, что TASK-69 уже построила для Settings-экрана). См. research.md (R2).
- **Минимальное покрытие сузилось** с `android.role.home` + `android.permission.POST_NOTIFICATIONS` до **только `LauncherRole`** — для permissions нет соответствующего `Component` в текущей ECS; добавление нового `Component`-подтипа — отдельная, не бюджетированная в этой задаче работа.

## Состояние

- **`/speckit.analyze` verdict: READY** (2026-07-19). Полный speckit-цикл пройден: specify → clarify (4 вопроса) → grounding-коррекция (дважды) → plan (Constitution Check 8/8) → tasks (34 задачи, 8 фаз) → analyze (9 чек-листов чистые). Ветка `task-73-pool-vendor-variants` запушена.
- **Реализация завершена (2026-07-19), Phase 1-6 из 8**: 30 из 34 задач сделаны (`T073-001..031`) — типы + порты, wire-format тесты, `AndroidVendorDetector` + `BundledVendorRecipeSource` + fakes, `LauncherRoleProvider` vendor-aware dispatch (Xiaomi/Samsung explicit-intent + Huawei action-intent + Huawei-без-GMS skip-generic-branch), Koin DI wiring, манифест `<queries>` + `ManifestQueriesCoverageTest`, i18n fallback-строки (EN+RU) + fitness-тесты, structured diagnostic log (FR-012). Все JVM unit-тесты зелёные (`:core:test`, `:app:testDebugUnitTest`), эмуляторный смок на `Medium_Phone_API_36.1` подтвердил: generic-path (Vendor.GenericAndroid, override отсутствует) не сломан — приложение стало HOME role holder'ом как раньше.
- **Физическая verification вынесена в TASK-137** (2026-07-19, решение владельца): Firebase Test Lab CI (`T073-032`, `[deferred-external]`) и реальные Xiaomi/Huawei/Samsung устройства (`T073-034`, `[deferred-physical-device]`) → TASK-137 (`VERIFY OEM-matrix`). У TASK-73 своих открытых гейтов больше нет — все AC зелёные (`[hand]` ×3 + emulator-smoke) или делегированы (`[N/A] → TASK-137`).
- **Статус**: остаётся `In Progress` до merge PR (по модели проекта терминальный статус требует merge в `main`); при merge переходит **сразу в Done** (Verification не нужен — своих физических гейтов не осталось, они на TASK-137).

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [hand] Dispatch: при наличии vendor-override для текущего Vendor и резолвящемся intent `apply()` запускает vendor-специфичный intent, не generic ROLE-путь (unit `LauncherRoleProviderTest.apply_vendorOverridePresent_andResolves_launchesVendorIntent_notGenericPath` + DI-override `FakeVendorDetector`/`FakeVendorRecipeSource`). Реальный MIUI-экран на железе — TASK-137 #1.
- [x] #2 [hand] `check()`/`apply()` не бросают исключений ни при одном `Vendor`; Huawei-без-GMS → `apply()` возвращает честный `Outcome.Failed(FailReason.InternalError(fallbackTextKey))` и пропускает generic RoleManager-путь (unit `check_returnsWellFormedOutcome_neverThrows_acrossEveryVendor`, `apply_huaweiWithoutGms_skipsGenericPath_noActivityStarted`). Реальное EMUI-без-GMS поведение на железе — TASK-137 #2.
- [x] #3 [hand] Новый vendor-override для уже известного `Vendor` подхватывается правкой `vendor-recipes.json` через `VendorRecipeSource` без изменения Kotlin-кода и нового APK (`VendorRecipeNoRebuildDemonstrationTest`).
- [x] #4 [auto:deferred-local-emulator] Emulator smoke (T073-033): generic-path не сломан vendor-aware расширением. Verified 2026-07-19 на AVD `Medium_Phone_API_36.1` (API 36.1 — канонический AVD проекта; `pixel_5_api_34` из текста задачи на машине отсутствует), онбординг → «Make it home» → приложение стало HOME role holder'ом (`dumpsys role` holders=`com.launcher.app.mock`), commit `b7968af`.
- [N/A] #5 [auto:deferred-external] Firebase Test Lab OEM-matrix CI (T073-032) — делегировано в **TASK-137 #4** (нужен GCP-проект + биллинг).
- [N/A] #6 [auto:deferred-physical-device] Реальные Xiaomi/Huawei/Samsung устройства (T073-034) — делегировано в **TASK-137 #1-#3** (через device rotation владельца).
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Paused 2026-07-20: owner switched to TASK-138 (wire-format conversion). Partial work: specs/task-73-pool-entries-per-vendor-variants/ — full speckit set present (spec.md, plan.md, tasks.md, data-model.md, research.md, analyze-report.md, contracts/), committed on main. No feature branch, working tree clean, nothing stashed. Resume = branch off + /speckit.implement against that tasks.md.
<!-- SECTION:NOTES:END -->

---

## Готовый промт для `/speckit.specify` (historical, kept for archival)

**Не использовать как источник правды** — написан 2026-07-01 в терминологии TASK-65 (`CheckSpec`/`ApplySpec`/`ConfigSource`), полностью заменённой TASK-136 canonical ECS. Реальный scope — в «Корректировки модели» выше и в `specs/task-73-pool-entries-per-vendor-variants/`. Оставлен для истории — показывает, как задача формулировалась изначально vs что получилось после grounding-коррекции.

```
Разработать TASK-73: Pool entries per-vendor variants — CheckSpec/ApplySpec dispatch.

ЧТО СТРОИМ
Расширить CheckSpec/ApplySpec sealed hierarchy vendor-aware variants и добавить
VendorProfile-driven dispatch в CheckHandler/ApplyHandler. Vendor-специфичные
overrides shipping через отдельный wire format (vendor-recipes.json) поверх
существующего ConfigSource port.

ЗАЧЕМ
После TASK-65 pool entries работают на устройствах, похожих на Pixel. Xiaomi
MIUI, Huawei без GMS, Samsung One UI, Oppo/Vivo/OnePlus — большинство senior
target audience — получают broken UX (settings screen не открывается или
открывается не туда, permission dialog silent-deny, RoleManager throws).
Community-authored presets в будущем marketplace должны быть vendor-blind;
OEM matrix — забота инфраструктуры.

SCOPE ВКЛЮЧАЕТ
- VendorProfile sealed value в domain (Pixel / Xiaomi / Huawei / Samsung /
  Oppo / Vivo / OnePlus / Honor / Generic).
- VendorProfileProvider port + Android adapter через Build.MANUFACTURER +
  GmsAvailabilityPort.
- CheckSpec.<Variant>(perVendor: Map<VendorProfile, CheckSpec>? = null).
- ApplySpec.<Variant>(perVendor: Map<VendorProfile, ApplySpec>? = null, fallbackTextKey: String? = null).
- CheckHandler / ApplyHandler dispatch: try vendor override → try default → for
  ApplyHandler additionally fall through to structured AlertDialog with
  localized instruction text.
- ConfigKind.VendorRecipes + BundledConfigSource assets/vendor-recipes/ path.
- Recipe wire format: schemaVersion + Map<poolEntryId, Map<vendorId, VendorOverride>>.
- Firebase Test Lab CI job on PR label `oem-matrix-required` running instrumentation
  tests on Pixel 8 + Samsung Galaxy S24 + Xiaomi Redmi.
- Minimum recipe coverage for TASK-65 pool entries: android.role.home (3 vendors),
  android.permission.POST_NOTIFICATIONS (3 vendors), ui.font.large (single —
  Android API stable).

SCOPE НЕ ВКЛЮЧАЕТ
- Автоматическое определение при runtime какие recipe качать (сначала bundle все,
  оптимизация — отдельная задача).
- Vendor override для сторонних SDK (только для системных Android surfaces).
- Physical device shelf procurement (owner decision).

DEPENDENCIES
- TASK-65 (foundation for CheckSpec/ApplySpec + ConfigSource extensibility).
- TASK-49 (GmsAvailabilityPort — используется для GMS-aware vendor detection).

ACCEPTANCE CRITERIA
- [ ] На Xiaomi Redmi (MIUI) tap на «Настроить HOME launcher» открывает
      Settings → Приложения → По умолчанию → Домашний экран (не generic ROLE dialog).
- [ ] На Huawei без GMS (emulator / Firebase Test Lab с EMUI image) CheckSpec.AndroidRole
      возвращает NotApplied вместо throw; ApplySpec показывает AlertDialog с текстом
      «Откройте Настройки → Приложения → По умолчанию».
- [ ] `vendor-recipes.json` может добавить override для нового vendor без изменения
      Kotlin кода.
- [ ] Firebase Test Lab CI job на 3 устройствах проходит для TASK-65 pool entries
      после добавления recipes.
- [ ] Roundtrip test для vendor-recipes wire format (write → read → equals).

LOCAL TEST PATH
- Unit test: VendorProfileProvider dispatches правильно given Build.MANUFACTURER stub.
- Unit test: CheckHandler pipeline picks vendor override когда есть, default когда нет.
- Instrumentation test (Xiaomi лично): full flow ROLE_HOME + POST_NOTIFICATIONS с
  MIUI-specific recipes.
- Firebase Test Lab job: 3 OEM smoke.

CONSTITUTION GATES
- Article VII §16 (domain isolation) — VendorProfile в domain, `Build.MANUFACTURER`
  чтение в androidMain adapter.
- Article XI (Minimum Viable Architecture) — если только 1 vendor override нужен,
  всё равно architecture есть под будущие; но не строить полный marketplace UI сейчас.
- CLAUDE.md rule 9 (shareability) — vendor-recipes.json ships как portable artifact.

EFFORT
S/M (1-2 недели) — 20-30 tasks; wire format + dispatch + 3 vendor overrides + CI.
```
