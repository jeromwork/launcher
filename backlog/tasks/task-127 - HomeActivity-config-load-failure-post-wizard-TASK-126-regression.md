---
id: TASK-127
title: 'ECS foundation: entities, tags, query, hierarchy + HomeScreen rewire'
status: Done
assignee: []
created_date: '2026-07-13'
updated_date: '2026-07-16 23:10'
labels:
  - phase-2
  - home-screen
  - bug
  - regression
  - architecture
  - ecs
milestone: m-1
dependencies:
  - TASK-126
  - TASK-120
references:
  - verification-evidence/task-128-xiaomi-fresh-07.png
  - verification-evidence/task-128-xiaomi-blocker-logcat.txt
  - specs/task-127-ecs-foundation/
priority: high
ordinal: 127000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

> **Версии описания.** Создан 2026-07-13 как узкая bug-fix задача «bridge Profile → ConfigDocument через LauncherPresentationBuilder». Пересобран 2026-07-15 после mentor-сессии владельца: изначальный подход отброшен, новый scope — расширение TASK-120 через Tags + Query. Синхронизирован 2026-07-16 после deep pre-implement audit (артефакты приведены к реальному коду; формулировка «ECS-native» заменена на «tagged-component model, ECS-inspired» per ADR-012).

## Что это простыми словами

**Регрессия**: после прохождения wizard'а на реальном устройстве Xiaomi Redmi Note 11 (adb id `17f33878`) `HomeActivity` показывает Error UI «Не удалось загрузить настройки» вместо плиток. TASK-126 (Wizard runtime migration) сломал end-to-end путь «install → wizard → usable home screen».

**Root cause**: между TASK-120 фундаментом (`Profile` / `Provider` / `ReconcileEngine`) и HomeScreen-стеком (`ConfigDocument` / `ConfigBackedFlowRepository`) образовался архитектурный gap. Никто не соединил их правильно: wizard пишет `Profile`, HomeScreen читает `ConfigDocument` — а между ними нет моста.

**Что решаем (mentor-сессия 2026-07-15)**:
1. Изначальный план был построить bridge — новый порт `LauncherPresentationBuilder`, который конвертирует `Profile` → `ConfigDocument`.
2. Владелец задал вопрос: «А не следует ли делать это в ECS-нотации, раз TASK-120 явно к ней тяготеет?».
3. Discovery: TASK-120 сессия 2 явно упоминает ECS-inspiration (sealed `Component` hierarchy = entities с компонентами). Но недостают два стандартных ECS-паттерна — **category tags** (multiple тегов на entity) и **query API** (`world.query<Predicate>()`).
4. **Новое решение**: не строить bridge на плохую архитектуру, а **расширить TASK-120 в ECS-native направлении**. Добавить `Component.tags: Set<Tag>`, добавить `Profile.query { predicate }`, переключить HomeScreen на чтение `Profile` напрямую через query. `ConfigDocument` остаётся в кодовой базе для будущего admin push, но HomeScreen path его больше не использует.

**Как это работает пошагово (после fix)**:
1. Wizard заполняет `Profile` через существующий TASK-120 `Provider` API.
2. `Profile` реконсилится через `ReconcileEngine`, применяется как активный.
3. Новый `ProfileBackedFlowRepository` (реализация существующего `FlowRepository` порта) читает `Profile` напрямую, вызывает `profile.homeScreenTiles()` (= теги `{Presentation, Tile}` минус нерабочие компоненты), проецирует результат в список плиток.
4. `HomeActivity` рендерит плитки — Error UI больше не показывается.

## Зачем

- **Разблокировать физическое тестирование** на Xiaomi Redmi Note 11 (сейчас Error UI блокирует TASK-128 verification bucket #1 и #2).
- **Устранить архитектурный дубликат**: `Profile` + `ConfigDocument` как два параллельных источника правды — long-term maintenance boom.
- **Заложить tagged-component нотацию** для будущих UI-фич (кнопки тулбара, экраны, hint-overlays, safety indicators): tags дают ортогональную категоризацию без forced single-category выбора.
- **Стандартный паттерн**: метки + селекторы — well-understood индустрией (Kubernetes label selectors — ближайший аналог; ECS-семейство Bevy/Flecs/Unity DOTS — источник вдохновения, см. ADR-012). Future maintainers сразу узнают.

## Что входит технически (для AI-агента)

> **Актуальная техническая правда — в спеке** (`specs/task-127-ecs-foundation/`: spec.md FR-001..FR-010, data-model.md, contracts/profile-v2.md, tasks.md — синхронизированы с реальным кодом аудитом 2026-07-16). Ниже — обновлённая сводка.

### Phase 1 — Tag enum + Component.tags

- `Tag` enum — **10 значений** (`Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar`) добавляется в существующий `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt` (`@Serializable`, pure Kotlin).
- **Component.tags: Set<Tag>** — abstract val + constructor-default на **всех 8** subtypes (`AppTile`, `FontSize`, `Sos`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`). `LauncherRole`/`StatusBarPolicy`: object → data class (wire-совместимо).
- **`schemaVersion` остаётся 2** (значение из кода TASK-120; `tags` — аддитивное поле, bump не нужен). **Никакого migration writer** (Q6).
- **Roundtrip тест** `ProfileSchemaV2RoundtripTest` + missing-tags case + fail-loud pins (незнакомый Tag/type → `SerializationException`).

### Phase 2 — Profile.query + selectors

- **Новый файл** `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` — extension-функции: `query(predicate)`, `byTag`, `byAllTags`, `byAnyTag`, `byNotTag`, `homeScreenTiles()` (= `{Presentation, Tile}` минус `Failed`/`Skipped` — render gating), `toolbar()` (query-уровень, HomeComponent пока не вызывает).
- **Unit-тесты**: single-tag query, AND/OR/NOT, empty result, tag-not-present, render gating.

### Phase 3 — ProfileBackedFlowRepository

- **Новый adapter** `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt`, реализует **все 4 метода** существующего `FlowRepository` порта (`api/FlowRepository.kt`, сигнатура не меняется): `loadFlows()` — путь регрессии (ждёт первый non-null Profile; 3s timeout у HomeComponent), `observeFlows()` — hot path (`filterNotNull`), `availableTemplates` — существующий каталог, `addFlow` — throws (parity).
- **DI rewire**: `core/src/android{Mock,Real}Backend/kotlin/com/launcher/di/BackendInit.kt` — заменить binding `ConfigBackedFlowRepository` → `ProfileBackedFlowRepository`.
- **`HomeComponentLoadingStateTest`** (`core/src/commonTest/kotlin/com/launcher/ui/navigation/`) расширить: Profile с одним `AppTile` (default tags) → `HomeLoadingState.Ready`.

### Phase 4 — Wizard string localization (side-fix)

- `core/composeResources/values/strings_wizard.xml`: добавить недостающие ключи, найденные grep'ом по TASK-126 wizard code:
  - `wizard_step_of`
  - `wizard_component_font_size`
  - `wizard_component_sos`
  - `wizard_confirm`
  - (+ любые другие всплывающие при grep'е `wizard_*` в UI слое)

### Phase 5 — Documentation

- **Новый** `docs/architecture/preset-model.md` с `<!-- AI-TLDR:BEGIN ... AI-TLDR:END -->` блоком (~60 строк). Объясняет две ортогональные дименсии:
  - **Lifecycle dimension** (Preset three-field structure: `wizardFlow` / `settingsMap` / `activeComponents`) — как presets применяются.
  - **Semantic dimension** (`Component.tags`) — по каким категориям компоненты группируются для UI-запросов.
- **Comment headers** в `Preset.kt` + `Component.kt`: doc-комментарий с ссылкой на `docs/architecture/preset-model.md`.
- **`docs/dev/server-roadmap.md`**: новая запись **SRV-CONFIG-DEPRECATION** — план удаления `ConfigDocument` из HomeScreen path (сейчас dead-code-like, останется для admin push scenarios в будущем).
- **Inline TODOs** в местах где `ConfigDocument` ещё используется: `// TODO(config-deprecation): SRV-CONFIG-DEPRECATION — remove after admin push migrated to Profile push.`

### Затронутые файлы (реальные пути, sync 2026-07-16)

- `core/src/commonMain/kotlin/com/launcher/preset/model/Enums.kt` — добавить `Tag` enum
- `core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt` — `tags: Set<Tag>` + defaults на 8 subtypes; object → data class ×2
- `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` (новый)
- `core/src/commonMain/kotlin/com/launcher/preset/model/Preset.kt` — только comment header про две дименсии
- `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt` (новый)
- `core/src/commonMain/kotlin/com/launcher/adapters/config/ConfigBackedFlowRepository.kt` — TODO(config-deprecation)
- `core/src/android{Mock,Real}Backend/kotlin/com/launcher/di/BackendInit.kt` — rewire `FlowRepository` binding
- `core/src/commonMain/composeResources/values/strings_wizard.xml` — дописать недостающие ключи (файл существует)
- `docs/architecture/preset-model.md` (есть)
- `docs/dev/server-roadmap.md` — SRV-CONFIG-DEPRECATION запись
- `core/src/commonTest/...` — новые тесты (wire, query, fitness, adapter) + расширение `HomeComponentLoadingStateTest`

## Про роли в этой задаче

Не применяется — TASK-127 внутренний архитектурный fix. Роли `primary user` / `remote administrator` / `restricted caregiver` не отличаются в этом контексте.

## Состояние

**In Progress**. Изначальный подход (`LauncherPresentationBuilder` bridge Profile → ConfigDocument) **отброшен** после mentor-сессии 2026-07-15 как построение моста на плохую архитектуру. Новый подход — **расширение TASK-120 через tagged-component model** (Tags + Query, label-selector стиль per ADR-012).

**2026-07-16 — deep pre-implement audit (4 независимых аудитора: карта модели, слои, индустриальные ECS, портируемость)**. Вывод: **архитектура здоровая, артефакты расходились с реальным кодом**. Найдено 9 расхождений, все исправлены в спеке (детали — `specs/task-127-ecs-foundation/analyze-report.md` § Deep Pre-Implement Audit #2):

- 3 критических: (а) data-model/контракт описывали несуществующие формы `Component` (в т.ч. `Sos(targetPhone)` — PII в shareable-артефакте) и пропускали 2 подтипа; (б) `schemaVersion` — артефакты «1», код и TASK-120 Decision «2» (решено: **остаётся 2**, `tags` аддитивно); (в) план чинил только `observeFlows()`, а ошибка на экране рождается в `loadFlows()`.
- 3 серьёзных: молчаливое изменение порта (`observeToolbar()` при заявлении «сигнатура не меняется» — убрано, рендер тулбара → out of scope); ложное обещание forward-compat по тегам (переписано честно + fail-loud тесты + R-8 с жёстким триггером); выдуманные пути файлов (перенаправлены на реальные).
- 1 дизайн-дыра: не было capability/status gating — плитки со статусом `Failed`/`Skipped` рендерились бы мёртвыми кнопками. Добавлено правило render gating.

Подтверждено здоровым: чистота `commonMain`, порты с fake+real адаптерами, обезличенность Profile-артефакта, разделение preset-поля vs инвариант (rule 11), загрузка bundled-пресетов через адаптеры (rule 9), решение не персистить запросы.

Артефакты синхронизированы с кодом, вердикт **READY FOR IMPLEMENTATION** переиздан честно. Далее — `/speckit.implement`.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [x] #1 [hand] Fresh install + wizard на Xiaomi Redmi Note 11 (adb id `17f33878`) → HomeActivity показывает плитки, не Error UI. **ПРОВЕРЕНО 2026-07-16**: экран показывает «WhatsApp» + вкладку «Главная»; в logcat нет `HomeLoadingState error` / `flows empty` / `timeout 3s` / `FATAL`.
- [x] #2 [hand] Wizard runtime строки локализованы (нет raw `wizard_*` ключей в UI). **ПРОВЕРЕНО 2026-07-16**: «Шаг 1 из 7», «Размер шрифта», «Готово», «Пропустить». Ключи добавлены в `res/values` + `res/values-ru` (резолвер читает их, а не composeResources — это и было причиной сырых ключей).
- [x] #3 [hand] `Component.tags: Set<Tag>` добавлен (13 тегов), constructor-defaults покрывают все 11 subtypes. Roundtrip иерархической фикстуры (schemaVersion 2) зелёный. `ComponentTagsFitnessTest` подтверждает non-empty defaults.
- [x] #4 [hand] `Profile.query` + селекторы по тегам объявлены. Unit-тесты: один тег, AND/OR/NOT, empty result, tag-not-present, render gating (`Failed`/`Skipped` не попадают в плитки; `Pending`/`Unverifiable` попадают).
- [x] #5 [hand] `ProfileBackedFlowRepository` реализован (все 4 метода порта, включая `loadFlows()` — путь регрессии), DI wire в mockBackend + realBackend. `HomeComponentLoadingStateTest` расширен сценарием `postManifestWizardReconcile_profileSeeded_homeReady` (+ ещё 3). Existing config-based сценарии зелёные.
- [x] #6 [hand] `docs/architecture/preset-model.md` обновлён (AI-TLDR: три оси + модель «ECS ≈ таблица БД»). `Preset.kt` + `Component.kt` содержат doc-комментарии. `server-roadmap.md` содержит SRV-CONFIG-DEPRECATION.
- [x] #7 [hand] **Иерархия работает**: `Entity.parentId` + `Workspace`/`Flow`/`ToolbarButton`; `flows()` по порядку, `tilesOf(flowId)` изолирует плитки, `toolbarButtons()` по порядку; сироты не роняют запрос. Одноуровневый профиль работает тем же кодом. Unit-тесты зелёные + иерархия подтверждена на устройстве (вкладка «Главная» = Flow-сущность).
- [x] #8 [hand] **Переключение вкладок на устройстве**: **ПРОВЕРЕНО 2026-07-16**. Выбран пресет «Лаунчер» → logcat `bootstrap outcome=Activated(presetId=launcher)` → экран показывает **две вкладки** («Главная», «Приложения»). Тап по «Приложения»: было «Главная: пока пусто» → стало «WhatsApp», Activity та же (`5aa8b8d`, без перезапуска). Заодно подтверждена изоляция вкладок — WhatsApp лежит в `flow-apps` и на «Главной» не показывался. Попутно закрыт блокер: `PresetBootstrap` игнорировал выбор пикера (всегда `defaultPresetId`) — теперь читает `PresetRepository.getActivePreset()`, куда пикер уже писал выбор; fallback на default остался для «ещё не выбирали».
- [x] #9 [hand] **`Unverifiable` статус честен**: `NeedsUserConfirmation` → `Unverifiable`, а не `Applied`; `BootCheck` его не перепроверяет; `RunMode.Single` перепроверяет. `ReconcileEngineUnverifiableTest` зелёный.
- [x] #10 [hand] **Валидация иерархии**: `DanglingParentRef`, `CircularParentRef` (включая самоссылку), `DanglingTargetRef` — типизированные ошибки. `ProfileFactoryHierarchyValidationTest` зелёный; `BundledPresetHierarchyTest` проверяет реальные bundled-ассеты.
- [x] #11 [hand] **ECS-нейминг**: `ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint` (93 usages / 24 файла); сборка и все тесты зелёные; формат хранения не изменился.
- [x] #12 [auto:deferred-physical-device] Physical device verification (T127-035). **Закрыто 2026-07-16** на Xiaomi Redmi Note 11, adb `17f33878`, commit `d1dc8c2`: fresh install (`pm clear`) → пикер → мастер → `HomeActivity` показывает плитки («WhatsApp», вкладка «Главная»), Error UI нет, в logcat нет `HomeLoadingState error` / `flows empty` / `timeout 3s` / `FATAL`. Строки локализованы («Шаг 1 из 7», «Размер шрифта», «Готово»). Пресет «Лаунчер» → `bootstrap outcome=Activated(presetId=launcher)` → две вкладки, тап по «Приложения» переключает содержимое без перезапуска Activity.
- [N/A] #13 [auto:deferred-local-emulator] Emulator smoke (T127-034). **Не требуется**: проверено на физическом Xiaomi (AC #12) — более сильный гейт, эмулятор ничего не добавляет. Помечено SUPERSEDED в tasks.md T127-034.
<!-- AC:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Session 1 (2026-07-15, mentor-сессия владельца) — краткая сводка

- **Стартовая позиция**: TASK-127 был bug-fix'ом с узким scope'ом — новый порт `LauncherPresentationBuilder`, конвертирующий `Profile.activeComponents` → `ConfigDocument`, чтобы `ConfigBackedFlowRepository` мог читать HomeScreen-контент.
- **Владелец спросил**: «TASK-120 задумывался ECS-inspired (Component как sealed hierarchy). Не выглядит ли `LauncherPresentationBuilder` как bridge на плохую архитектуру вместо расширения хорошей?».
- **Discovery**: TASK-120 Session 2 notes упоминают ECS-inspiration явно. Но не хватает двух канонических паттернов — **category tags** (multiple tags per entity, ортогональная категоризация) и **query API** (`world.query<Predicate>()` для проекций).
- **Альтернативы обсуждены**:
  - **A**. Bridge через `LauncherPresentationBuilder` (изначальный план). Дёшево, но фиксирует two-sources-of-truth (Profile + ConfigDocument).
  - **B**. Убить `ConfigDocument` полностью, HomeScreen читает Profile через ad-hoc getters. Быстро, но не ECS-native, приведёт к N ad-hoc getters по мере роста UI-фич.
  - **C**. Расширить TASK-120 через Tag+Query, HomeScreen читает Profile через query API. ConfigDocument остаётся для admin push (отдельная задача deprecation). **Выбрано.**
- **Adjacent concerns**:
  - schemaVersion bump v2→v3 — one-way door для wire-format (rule 5). Migration writer обязателен.
  - Preset three-field structure (`wizardFlow` / `settingsMap` / `activeComponents`) сохраняется — это lifecycle dimension, ортогональна semantic tags. Нужен doc, иначе future confusion.
  - `ConfigDocument` deprecation — отдельная задача (SRV-CONFIG-DEPRECATION в server-roadmap), не в scope TASK-127.

### Decision (English) 🔒

**Choice**: Extend TASK-120 Component model with **tagged-component patterns (label-selector style; ECS-inspired, NOT canonical ECS — per ADR-012)**. Add `Tag` enum (10 values: `Presentation, Appearance, System, Safety, Capabilities, Communication, Accessibility, Emergency, Tile, Toolbar`) and `Component.tags: Set<Tag>` with constructor-defaults on **all 8 real subtypes** (`LauncherRole`/`StatusBarPolicy` convert object → data class, wire-compatible). Add `Profile.query(predicate)` extension API with convenience selectors (`byTag`, `byAllTags`, `byAnyTag`, `byNotTag`, `homeScreenTiles()`, `toolbar()`); `homeScreenTiles()` excludes `ComponentStatus.Failed/Skipped` (render gating). Rewire HomeScreen from `ConfigBackedFlowRepository` to a new `ProfileBackedFlowRepository` implementing **all four methods of the unchanged `FlowRepository` port** — `loadFlows()` (the actual regression path: awaits first non-null Profile) and `observeFlows()` (hot path, `filterNotNull`); `availableTemplates`/`addFlow` keep parity semantics. Toolbar *rendering* on HomeScreen is out of scope (the `toolbar()` selector ships at query-API level only). **Wire format: `schemaVersion` stays 2** — `tags` is an additive optional field; **no migration writer** (pre-release, no consumer — Clarification Q6). `ConfigDocument` stays in codebase for future admin push scenarios but is no longer used by HomeScreen path. Preset three-field structure (`wizardFlow` / `settingsMap` / `activeComponents`) preserved — lifecycle dimension, orthogonal to `Component.tags` semantic dimension.

**Rationale**:
1. TASK-120 was already tag-friendly per Session 2 notes; adding Tag+Query completes the pattern rather than introducing a foreign paradigm. Honest framing (ADR-012): this is a label-selector system (Kubernetes `matchLabels` analog), not game-engine ECS.
2. Multiple tags per component allow orthogonal categorization without forcing single-category choice (SOS is `Safety` + `Emergency` + `Presentation` + `Tile` at once).
3. Profile-as-source-of-truth eliminates ConfigDocument duplication in the HomeScreen path — one source, one migration story. The fix targets `loadFlows()` — the one-shot path with the 3s timeout where the TASK-52 Error actually fired.
4. Queries are NOT persisted (only tags are in the wire format) — sidesteps query-language versioning entirely.
5. Additive to TASK-120, not superseding — sealed hierarchy unchanged (plus two object→data class conversions that keep wire shape). TASK-120 Decision block remains valid, including Profile `schemaVersion=2`.

**Applies to**: `core/src/commonMain/kotlin/com/launcher/preset/model/{Enums,Component,Preset}.kt`, `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` (new), `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt` (new), `core/src/android{Mock,Real}Backend/kotlin/com/launcher/di/BackendInit.kt` (DI rewire), `core/src/commonMain/composeResources/values/strings_wizard.xml` (extend existing), `docs/architecture/preset-model.md`, `docs/dev/server-roadmap.md` (SRV-CONFIG-DEPRECATION entry). Contract: `specs/task-127-ecs-foundation/contracts/profile-v2.md`.

**Trade-offs accepted**:
1. No migration writer pre-release (rule 4 MVA); first post-release breaking change = mandatory `ProfileMigrationV2toV3` + bump (one-way door per rule 5).
2. Honest forward-compat limitation: an older reader fails loud (`SerializationException`) on an unknown Tag value or Component type — kotlinx.serialization has no per-element enum leniency. Acceptable while Profile is same-device/same-binary; **hard trigger**: lenient `Set<Tag>` serializer + unknown-type policy MUST ship before admin push (spec-009) or preset sharing (rule 9). Pinned by contract tests.
3. Two orthogonal dimensions (lifecycle Preset fields + semantic Component tags) may confuse readers — mitigated by `docs/architecture/preset-model.md` + inline doc-comments.
4. `ConfigDocument` code stays temporarily, dead-code-like for HomeScreen path; SRV-CONFIG-DEPRECATION queued in server-roadmap.
5. Tags enumerated as closed enum — adding a tag requires a code change, and third-party preset authors cannot mint tags. Accepted for MVP; exit = namespaced string tags (lenient-serializer change, not a format break — ADR-012).
6. Render-gating default (hide `Failed`/`Skipped` tiles) is a product default; if user segments later need "render with tap-time fallback", it becomes a preset field per rule 11.

**Exit ramp**: If Tag+Query proves insufficient at scale, keep the Tag data model and replace linear query with indexed lookup — additive, no wire-format break. If the Preset three-field structure conflicts with Tag semantics later, add a fourth Preset field or promote tags to Preset level — both additive. If the tagged-component direction turns out wrong entirely, revert `FlowRepository` binding to `ConfigBackedFlowRepository`, keep Tag+Query as pure metadata (unused by HomeScreen path). Estimated cost of any exit ~2-3 days. Latent one-way door (single Component per entity blocks composition) documented in ADR-012 with 8–16-week canonical-ECS migration estimate and a concrete trigger.

Revision note (2026-07-15): initial scope replaced with ECS Tags+Query per owner mentor session.
Revision note (2026-07-16 a): Decision synced with final artifacts after deep pre-implement audit #2 (four independent auditors) — schemaVersion stays 2 (was: bump to 3 + migration writer, dropped per Q6 + audit); 10-value Tag enum (was 8); selectors renamed to final API; real file paths; all-four-port-methods surface incl. `loadFlows()` regression path; render gating added; honest forward-compat limitation recorded; framing corrected to tagged-component/label-selector per ADR-012.

**Revision note (2026-07-16 b) — SCOPE EXPANDED to full ECS foundation** (Clarifications Q7-Q10, owner decision after end-to-end model review). Everything added is wire-format-affecting → free pre-release, migration writer post-release (rule 5); owner's meta-rule «defer only what later becomes appending, not rewriting» applied:

- **Hierarchy (Q7)**: `Entity.parentId: String?` — flat storage, tree computed by queries. Enables the target screen: `Workspace → N × Flow → tiles` + `Toolbar → N × ToolbarButton` (each button switches a flow). Pattern per Bevy/Unity DOTS `Parent` + Android Launcher3 `favorites.container` (research R-7). New query selectors: `children`, `roots`, `workspace`, `flows`, `tilesOf`, `toolbar`, `toolbarButtons`.
- **Three structural subtypes (Q7)**: `Workspace(layoutKey)`, `Flow(titleKey, layoutKey, order)`, `ToolbarButton(targetFlowId, labelKey, order)`. `layoutKey` moves onto `Flow` (each tab owns its grid); `Profile.layoutKey` kept as legacy fallback. `Tag` enum grows to **13** (+ `Workspace`, `Flow`, `ToolbarButton`); tags-defaults on **11** subtypes.
- **`ComponentStatus.Unverifiable` + `Outcome.NeedsUserConfirmation` (Q8)**: honest state for settings Android cannot read back (status-bar hiding = intent chain, no query API). Provider says «ask the human» → wizard shows «open settings, turn on X, come back, tap "I did it"» → status `Unverifiable`, never a lying `Applied`. `BootCheck` skips these (no infinite re-nagging); re-verification only via explicit Settings action.
- **ECS rename (Q9)**: `ProfileComponent` → **`Entity`**, `ComponentDeclaration` → **`Blueprint`** (93 usages / ~25 files, dedicated first commit). `Component`/`Tag`/`Profile`/`Pool` kept. Wire format untouched.
- **Hierarchy validation (Q10/FR-016)**: `DanglingParentRef`, `CircularParentRef`, `DanglingTargetRef` — typed errors at profile assembly.
- **Profile ↔ Preset (Q10)**: profile references the preset (`basedOnPreset` + `presetVersion`); both stored and shipped together for admin push.
- **Unchanged by the expansion**: `schemaVersion` stays **2** (all additions are additive), no migration writer, `FlowRepository` port signature, and the UI — `BottomFlowBar` + `HomeComponent.selectFlow` already exist (TASK-52), and `FlowDescriptor(id, name, slots)` is already hierarchical, so the projection needs no port/UI change.

Artifacts rebuilt accordingly; spec folder renamed to `specs/task-127-ecs-foundation/`; tasks 23 → **35 across 9 phases**; Constitution Check re-run: 7 PASS / 1 N/A / 0 FAIL.

<!-- SECTION:DISCUSSION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->

**Done 2026-07-16.** 35/35 задач, **11/11 AC зелёные**, всё проверено на физическом Xiaomi Redmi Note 11 (`17f33878`).

**Что чинили**: после мастера настройки главный экран показывал «Не удалось загрузить настройки». Причина — архитектурный разрыв: мастер писал `Profile` (модель TASK-120), а экран читал `ConfigDocument` (старую модель), которую никто не заполнял.

**Что сделали вместо моста**: достроили ECS-фундамент, пока формат хранения не у пользователей (решение владельца, Q7-Q10):
- **Иерархия**: `Entity.parentId` + типы `Workspace` / `Flow` / `ToolbarButton`. Хранение плоское, дерево вычисляется запросами (как Bevy / Unity DOTS / Android Launcher3).
- **Честный статус `Unverifiable`**: Android не даёт проверить, скрыта ли системная шторка — теперь модель не врёт «применено», а спрашивает человека; проверка при старте такие настройки не дёргает.
- **ECS-нейминг**: `ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint` (93 правки).
- **Query API** (теги + иерархия), render gating (мёртвые плитки не показываем), валидация иерархии (3 типизированные ошибки).
- **Новый адаптер** `ProfileBackedFlowRepository` — все 4 метода порта; `loadFlows()` и был путём регрессии.
- `schemaVersion` остался **2** — всё аддитивно, мигратор не нужен.

**Проверено на устройстве**:
- Свежая установка → мастер → экран с плитками, без Error UI (SC-001).
- Строки читаемы: «Шаг 1 из 7», «Размер шрифта», «Готово» (SC-002).
- Пресет «Лаунчер» → две вкладки; тап по «Приложения» переключает содержимое без перезапуска Activity (SC-010).

**4 бага, найденных прогоном на устройстве** (тесты их не ловили, ушли бы в релиз):
1. Мастер показывал сырые ключи — резолвер читает `res/values`, а ключи лежали только в composeResources.
2. «Шаг 1 из 7» — plural, а звался метод для обычных строк.
3. Экран показывал `pool.tile.whatsapp.label` — профиль намеренно хранит ключи (иначе пресет привязан к языку), переводить надо на границе → через порт `StringResolver`.
4. **`PresetBootstrap` игнорировал выбор пикера** — всегда активировал `simple-launcher`. Пикер писал выбор в `PresetRepository`, но bootstrap его не читал. Исправлено, покрыто тестами.

**Отложено (Draft-задачи)**: TASK-130 (обновление пресет→профиль), **TASK-131 (снисходительный читатель — обязателен до обмена пресетами между устройствами)**, TASK-132 (валидация пресета при создании), TASK-133 (настраиваемый вид Wizard/Settings), TASK-134 (add-flow UX).

**Известное ограничение**: старая версия приложения упадёт при чтении профиля с незнакомым тегом/типом. Безопасно, пока профиль не покидает устройство; зафиксировано тестами; снимается TASK-131.

<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done

`In Progress → Verification/Done` через `pre-pr-backlog-sync` после fix + verification на Xiaomi Redmi Note 11 (тот же adb id `17f33878`, свежая установка). AC #3-#6 verifiable via unit/Robolectric — не требуют physical device. AC #1-#2 требуют физический smoke test.

## Resume note (2026-07-16, обновлено после аудита)

Возобновлена после закрытия TASK-122 (F-CRYPTO Foundation → Done, PR #50 merged) и merge ECS scope (branch `origin/task-127-launcher-presentation-builder` merged 2026-07-16). Owner переключился на этот blocker — блокирует merge TASK-126 и TASK-128 verification bucket.

**Speckit stage**: полный цикл пройден дважды. 2026-07-16 (а) **deep pre-implement audit** (4 независимых аудитора) — артефакты приведены в соответствие с реальным кодом (9 findings). 2026-07-16 (б) **scope expansion** после разбора модели с владельцем — задача расширена с «теги + запросы» до полного ECS-фундамента; spec/research правлены руками, plan/data-model/contract/tasks пересобраны, Constitution Check перепрогнан (7 PASS / 1 N/A / 0 FAIL). **Следующий шаг — `/speckit.implement`** по `specs/task-127-ecs-foundation/tasks.md` (**35 задач, 9 фаз**).

**Что важно знать реализатору**:
- **Фаза 1 — переименование отдельным коммитом** (T127-002/003): `ProfileComponent` → `Entity`, `ComponentDeclaration` → `Blueprint` (93 usages). Сначала оно, потом смысловые изменения — иначе ревью нечитаемо.
- `schemaVersion` **не трогать** — остаётся 2; всё новое (`tags`, `parentId`, 3 типа, статус `Unverifiable`) аддитивно.
- Главный фикс регрессии — **`loadFlows()`** в новом адаптере, не только `observeFlows()`.
- **UI не трогать**: `BottomFlowBar` + `HomeComponent.selectFlow` уже существуют (TASK-52), `FlowDescriptor(id, name, slots)` уже иерархичен — проекция ложится на существующий порт без изменений.
- Пути файлов в tasks.md проверены против ветки; DI — в `core/src/android{Mock,Real}Backend/.../BackendInit.kt`, не в `app/`.
- Открытый вопрос (T127-023): `ProfileStore` байндится в `app` `PresetModule` — проверить видимость из core backend-модуля.
- Открытый вопрос (T127-026): как `ProfileFactory` проставляет `parentId` — новое опциональное поле в записи пресета или соглашение по blueprint'у. Выбрать аддитивный вариант, задокументировать в PR.

**Отложено в Draft-задачи**: TASK-130 (обновление пресет→профиль), TASK-131 (снисходительный читатель — жёсткий триггер до cross-device обмена), TASK-132 (валидация пресета при создании), TASK-133 (настраиваемый вид Wizard/Settings через JSON), TASK-134 (add-flow UX / пустые слоты).
