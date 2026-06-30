---
id: TASK-65
title: Profile Composition Foundation v2
status: In Progress
assignee: []
created_date: '2026-06-28 18:30'
updated_date: '2026-06-30 22:00'
labels:
  - phase-2
  - foundation
  - profiles
  - composition
  - one-way-door
milestone: m-1
dependencies:
  - TASK-7
priority: high
ordinal: 65000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Чисто **архитектурная инфраструктура**. Никаких ролей, никаких сценариев конкретного пользователя. Подготавливает почву для будущих preset'ов (`workspace`, `clinic-patient`, `self-care`) и для cross-app reuse (messenger TASK-27, photo TASK-28) — добавляет три bundled preset'а (`simple-launcher`, `launcher`, `workspace`) через generic composition.

## Что это простыми словами

Превращаем «один лаунчер захардкожен» в «вариант приложения = самосодержащий JSON, который шарится». После TASK-65 приложение умеет **выбирать вариант** один раз при установке через picker (3 карточки: simple-launcher / launcher / workspace), **переключать** через Settings с сохранением истории настроек, и **проверять** на boot'е реальное состояние Android — если что-то критическое не настроено, появляется banner.

**Терминология (важно — изменилась в clarify-фазе, constitution amendment 1.11)**:
- **Preset** — самосодержащий шарящийся JSON (`preset.json`). Имена: `simple-launcher`, `launcher`, `workspace`. Identity = composite `PresetRef(uid, version)`. Не содержит личных данных.
- **Profile** — per-device personal data на этом телефоне. Содержит: applied preset reference + layout + bindings (контакты в плитках) + settings cache (применённые Android-настройки). Хранится `Map<PresetRef, ProfileData>` — история всех preset'ов которые когда-либо активировал. Синхронизируется зашифрованно на сервер (TASK-70 для admin remote management).

**Что происходит по шагам (новая установка):**
1. Пользователь устанавливает APK, открывает.
2. Видит picker с **3 карточками** (simple-launcher / launcher / workspace) — как в Telegram при первом запуске выбираешь язык.
3. Тапает → запускается wizard, шаги собираются из `preset.configs[]` (что preset требует) + проверка реального Android-состояния.
4. Прошёл wizard → ProfileData персистится в `ProfileStore.profiles[ref]`, `activePresetRef` записан.

**При переключении preset'а (Settings → Сменить preset):**
1. Snapshot текущего Profile сохраняется в `Map[oldRef]`.
2. Если для нового preset уже есть ProfileData в Map (был раньше активирован) → restore целиком (bindings, layout, settings — всё на месте).
3. Иначе → CopyOnActivateStrategy создаёт новый ProfileData из preset.abstractProfile + preset.configs.
4. Wizard показывает только diff (что не настроено).
5. После — commit, Activity recreate.

**При boot'е (revised axiom — clarify pass 2)**: callback каждой settings entry проверяет реальное Android-состояние. Если **critical missing** (например HOME role отозван другим приложением между запусками) → HomeActivity показывает сверху banner «приложение работает не как надо — настроить?». Tap → mini-wizard со всеми critical missing. **Non-critical missing** (font size) — silent, видны только в Settings reminders.

## Зачем

Сейчас `simple-launcher` захардкожен (поле `appFamilyId: "simple-launcher"` в wizard.manifest). Конституция Article VII §3 запрещает форки профилей: profiles are **data, not forks**. TASK-65 исправляет нарушение, открывает дорогу для всех будущих preset'ов без переписывания кода + закладывает foundation для cross-app vision (тот же engine переиспользуется messenger / photo app).

## Что входит технически (для AI-агента)

**Wire formats**:
- **`preset.json` schemaVersion=1** (новый): self-contained с composite identity `PresetRef(uid, version)`, slug (display, NOT identity), embedded `configs[]` (UX hints inline) + optional `abstractProfile` (initial layout/bindings, без личных данных).
- **`wizard.manifest` schemaVersion=2** (bump from v1): удаляется поле `appFamilyId` из body. Migration writer `migrateLegacyWizardManifest` (scoped function) ships в том же commit.
- **`ProfileStore` DataStore Preferences** (single key `profile.store.json`, JSON-сериализация со composite Map keys `"uid::version"`). Sync target для TASK-70 (зашифровано через pairing keys).

**Ports + adapters**:
- **`PoolSource`** port + 2 adapter'а: `HardcodedPoolSource` (live) + `JsonAssetPoolSource` (scaffold с TODO + roundtrip test gate). DI swap.
- **`ProfileSwitchStrategy`** port + `CopyOnActivateStrategy` default adapter (port готов для будущих `KindMatchSwitchStrategy` / `SandboxSwitchStrategy`).
- **`ConfigKind.Preset`** — 6-й variant existing enum. **НЕ создаём** дублирующий `RequirementsChecker` port — переиспользуем `WizardEngine.computePending` (per rule 4 MVA).
- **`CheckSpec.UIFont(minScale)`** + `UIFontChecker` handler — extension существующей sealed hierarchy. Используется в `test-preset.json` для proof of generic-ness.

**UI + services**:
- **`PresetPickerScreen`** (Compose) reused first-launch + Settings switch.
- **`PresetBootRouter`** — Activity router (boot path с settings check + classification critical/optional).
- **`HomeBanner`** Compose — critical-missing banner с CTA «Настроить» + dismiss «Позже» (per CLAUDE.md rule 10 in-app indicator, НЕ push).
- **`PresetSelectionService`, `PresetSwitchService`, `PresetReminderService`** — domain services orchestration.
- **3 bundled preset'а** + 1 fixture: `simple-launcher.preset.json`, `launcher.preset.json`, `workspace.preset.json`, `test-preset.json` (androidTest).
- **Settings entry** «Сменить preset» + boot-time + onResume reminders.

**Fitness functions (Detekt — новый `lint-rules/` Gradle module)**:
- **`PresetIdBranchingDetector`** — ловит `if (presetId == "...")`, `when (appFamilyId)` вне whitelisted `core/preset/` packages.
- **`ExtractionReadinessDetector`** — запрещает launcher-specific imports (`com.launcher.app.tiles.*`) в foundation packages — обеспечивает cross-app extraction готовность.
- Pre-commit hook script + `detektFoundation` Gradle alias.

**Тесты (21 task в Phase 6)**:
- Contract roundtrip: `PresetWireFormatRoundtripTest`, `ProfileStoreSerializationTest`, `PresetRefValidationTest`, `PoolSourceRoundtripTest`.
- Backward-compat: `WizardManifestBackwardCompatTest` с pre-TASK-65 fixture.
- Migration: `PreferencesProfileStoreTest` (legacy migration + idempotency).
- Fitness: `EngineGenericityFitnessTest` (UIFont dispatch), `BundledPresetsParseTest` (build-time validation).
- Regression: `SimpleLauncherCompositionRegressionTest` (golden snapshot).
- Edge case: `SettingsCallbackThrowsTest` (Indeterminate per Article VII §15).
- Lint rule tests: `PresetIdBranchingDetectorTest`, `ExtractionReadinessDetectorTest`.
- E2E (instrumentation, pixel_5_api_34): FirstLaunchPicker, PresetSwitch, Migration, SettingsReminders, BootCriticalMissingBanner, BootBenchmark.

**Constitution amendment 1.11** применён к `.specify/memory/constitution.md` — naming inversion preset/profile + `appFamilyId` deprecation + `ConfigKind.preset` 6-й kind.

## Что НЕ входит (явно вынесено в follow-up tasks)

- **Settings UI как view на Profile** (динамическая генерация из preset.configs) → **TASK-69**.
- **Profile sync на server + multi-profile-capable storage** (admin'ский app для нескольких managed primary users) → **TASK-70**.
- **Wizard hidden steps + defaults** (preset author может скрыть шаг и применить дефолт автоматически) → **TASK-71**.
- **Pool browser UI** (opportunistic preset authoring для продвинутых users / AI agents) → **TASK-72**.
- **`KindMatchSwitchStrategy`** (smart binding migration по Slot.kind), **`SandboxSwitchStrategy`** (примерить preset без commit'а) — port готов, adapters — future tasks.
- **Server-fetched / file-imported presets** → additively через новые ConfigSource adapters позже.
- **`CheckSpec.AuthState` / `ApplySpec.RequestSignIn`** — clarify pass решил вынести в TASK-3 / TASK-49 (там реально нужно для Sign-In flow).

## Состояние

**In Progress** (clarify pass 2 завершён 2026-06-30). Spec-kit pipeline complete: specify → clarify (13 clarifications) → scenarios (5 sequences ADR-011) → plan (Constitution Check 8/8 PASS) → tasks (50 tasks T600-T67M, 7 phases) → analyze (READY-WITH-CAVEATS — 3 UI-impl-phase smoke items defer).

Foundation для TASK-66/67/68 + всех будущих preset'ов. Должна быть закрыта до start работ над workspace (TASK-68).

Следующий шаг: `/speckit.implement` (Phase 0 inventory → Phase 6 fitness).

---

## Готовый промт для `/speckit.specify` (historical — pre-clarify model)

> **Historical artifact.** Этот промт описывает initial scope до clarify-фазы (когда terminology была «profile»/`profile.json`/`RequirementsChecker port`). После clarify pass 2 модель эволюционировала к Preset/Profile inversion + PresetRef composite identity + revised boot axiom + reuse WizardEngine.computePending вместо нового RequirementsChecker. Финальный scope — см. выше + spec.md.

```
Реализуй F-?? (TBD): Profile Composition Foundation v2.

ЧТО СТРОИМ:
Generic profile composition runtime: профиль = JSON-пик из каталога pool entries.
First-launch profile picker + profile switch flow через wizard-diff.
Удаление profile-leakage (`appFamilyId`) из wizard manifest format.
Pool naming convention (namespaced immutable IDs, schemaVersion per pool).
Lint rule «no `profileId == ...` in business logic».

ЗАЧЕМ:
Article VII §3 конституции: profiles are data, not forks. Сейчас simple-launcher
захардкожен в манифесте. Чтобы добавить workspace / clinic-patient / self-care
профили без переписывания кода — нужна generic composition foundation.

SCOPE ВКЛЮЧАЕТ:
- Удаление `appFamilyId` из wizard-manifest.json body.
- `Profile` wire format JSON (`profile.json` schemaVersion=1).
- `ConfigSource` port + `BundledConfigSource` adapter.
- `RequirementsChecker` port — generic диспатчер по CheckSpec.kind (android-role, android-permission, ui-font, ...). Расширяемо additively.
- First-launch profile picker UI (Compose).
- Profile switch flow в Settings (RequirementsChecker.check → WizardComposer.build от missing).
- In-app Settings reminders (banner-карточки по missing requirements, re-check на onResume, tap → mini-wizard) — per rule 10 in-app indicator.
- `CheckSpec.AuthState` + `ApplySpec.RequestSignIn` variants для auth-в-wizard.
- Demo `CheckSpec.UIFont` variant для extension-proof теста (используется в test-profile.json).
- Pool naming convention spec (`contracts/pool-naming.md`).
- Lint rule «no profileId branching» + pre-commit fitness function.
- Lint rule «extraction-readiness» — запрет launcher-specific imports в core/profiles/wizard/pools.
- Regression test: simple-launcher идентичен после рефакторинга.
- Fitness test: dummy test-profile.json (с non-Android требованием) доказывает generic-ность engine.

SCOPE НЕ ВКЛЮЧАЕТ:
- Server-fetched profiles (`NetworkProfileSource`) — добавляется additively позже.
- Sharing/import profiles — TASK-35 (Marketplace) в Phase 5.
- Профильно-специфические pool entries (pairing-list — TASK-67, workspace JSON — TASK-68).
- Extraction в sub-repo / shared library — kept в monorepo per rule 4. Trigger: messenger TASK-27 / photo TASK-28.
- Push notifications для missing requirements — per rule 10 используем in-app reminders, не push.

DEPENDENCIES:
- TASK-7 (Simple Launcher Setup Wizard) — Done.

ACCEPTANCE CRITERIA (проверяет пользователь):
- Установил APK с чистого листа → увидел экран выбора профиля → выбрал simple-launcher → визард прошёл идентично TASK-7.
- В Settings нашёл «Сменить профиль» → переключил на dummy `test-profile` → визард показал только недостающие шаги → после визарда профиль активен.
- Существующий simple-launcher пользователь после установки нового APK видит свой профиль автоматически (миграция в первый запуск).
- Lint падает на попытке закоммитить код с `if (profileId == "simple-launcher")` в core/ или app/.
- Документация pool-naming.md написана простым русским.

LOCAL TEST PATH:
- Emulator pixel_5_api_34 — первый запуск + переключение профиля.
- Unit tests с dummy test-profile.json — engine generic-ness.
- Fitness test для lint rule (gradle task).

CONSTITUTION GATES:
- Article VII §3 (profiles = data, not forks) — fitness через lint rule.
- Article VII §13 (no `if (appFamilyId == "x")` branches) — fitness через lint rule.
- Rule 5 (wire format): profile.json schemaVersion=1, pool naming immutable.
- Rule 9 (shareability): ConfigSource adapter pattern с первого коммита.

EFFORT: Medium (~2 weeks).
```
<!-- SECTION:DESCRIPTION:END -->

## Sequences

Sequence diagrams (SEQ-1..SEQ-5) live inline in [`specs/task-65-profile-composition-foundation-v2/spec.md`](../../specs/task-65-profile-composition-foundation-v2/spec.md) under `## Sequences` heading. Format: Mermaid spec-level + plan-level + MENTOR-DETAIL block, per [CLAUDE.md «Sequences in spec.md»](../../CLAUDE.md) + ADR-011.

- **SEQ-1**: First-launch preset picker → simple-launcher apply (US-1).
- **SEQ-2**: Preset switch through Settings — diff-wizard + Profile history preserved (US-2).
- **SEQ-3**: Boot path — main axiom «no checks» (US-7).
- **SEQ-4**: In-app Settings reminders for missing requirements (US-4).
- **SEQ-5**: Silent migration of pre-TASK-65 simple-launcher users (US-3).

Backlog ранее (pre-2026-06-30) содержал sequences inline здесь — перенесены в spec.md по ADR-011 conformance (sequences живут в spec.md, не в backlog-task).

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 [hand] Установил APK с чистого листа → увидел экран выбора **preset'а** → выбрал simple-launcher → визард прошёл идентично TASK-7 (SC-001)
- [ ] #2 [hand] В Settings → 'Сменить preset' → переключил на dummy test-preset → визард показал только недостающие шаги → preset активен. Switch обратно на simple-launcher → прежние bindings восстановились из истории Profile (SC-002)
- [ ] #3 [hand] Существующий simple-launcher пользователь после установки нового APK видит свой preset автоматически — без picker'а, без re-wizard (SC-003)
- [ ] #4 [hand] Detekt `PresetIdBranchingDetector` падает на `if (presetId == "simple-launcher")`, `when (appFamilyId)` в core/ или app/ (вне whitelisted `core/presets/`) — SC-004
- [ ] #5 [hand] Документация `contracts/pool-naming.md` написана простым русским, владелец-новичок может прочитать за <10 минут (SC-006)
- [ ] #6 [hand] Boot приложения после первой настройки НЕ вызывает `WizardEngine.computePending()` — trace доказывает (SC-007)
- [ ] #7 [hand] В Settings → отозвал ROLE_HOME руками → banner-карточка 'не настроено: HOME launcher' → тап → mini-wizard с ровно одним шагом → исправил → banner исчезает (SC-008 + SC-011)
- [ ] #8 [hand] Generic engine: `CheckSpec.UIFont` variant + `test-preset.json` с non-Android требованием → engine корректно диспатчит, строит wizard step, после применения fontScale re-check возвращает missing=[] (SC-009)
- [ ] #9 [hand] Detekt `ExtractionReadinessDetector` падает на `import com.launcher.app.tiles.Tile` в `core/presets/` / `core/wizard/` / `core/pools/` (SC-005)
- [ ] #10 [hand] `PoolSource` swap: DI переключение между `HardcodedPoolSource` и `JsonAssetPoolSource` (когда последний реализован) — приложение работает идентично; roundtrip test гарантирует identical entries (SC-012)
- [ ] #11 [hand] Naming inversion применён: в коде, spec'е, backlog AC используется **Preset** для shareable top-level и **Profile** для per-device personal data. Constitution amendment подготовлен (Article VII §9), требует владелец-approval перед merge
<!-- AC:END -->
