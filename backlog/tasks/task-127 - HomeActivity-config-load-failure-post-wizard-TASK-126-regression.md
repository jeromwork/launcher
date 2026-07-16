---
id: TASK-127
title: 'HomeActivity config load: ECS Tags foundation + Query pattern'
status: In Progress
assignee: []
created_date: '2026-07-13'
updated_date: '2026-07-16 21:30'
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
  - specs/task-127-ecs-tags-and-query/
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

> **Актуальная техническая правда — в спеке** (`specs/task-127-ecs-tags-and-query/`: spec.md FR-001..FR-010, data-model.md, contracts/profile-v2.md, tasks.md — синхронизированы с реальным кодом аудитом 2026-07-16). Ниже — обновлённая сводка.

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

**2026-07-16 — deep pre-implement audit (4 независимых аудитора: карта модели, слои, индустриальные ECS, портируемость)**. Вывод: **архитектура здоровая, артефакты расходились с реальным кодом**. Найдено 9 расхождений, все исправлены в спеке (детали — `specs/task-127-ecs-tags-and-query/analyze-report.md` § Deep Pre-Implement Audit #2):

- 3 критических: (а) data-model/контракт описывали несуществующие формы `Component` (в т.ч. `Sos(targetPhone)` — PII в shareable-артефакте) и пропускали 2 подтипа; (б) `schemaVersion` — артефакты «1», код и TASK-120 Decision «2» (решено: **остаётся 2**, `tags` аддитивно); (в) план чинил только `observeFlows()`, а ошибка на экране рождается в `loadFlows()`.
- 3 серьёзных: молчаливое изменение порта (`observeToolbar()` при заявлении «сигнатура не меняется» — убрано, рендер тулбара → out of scope); ложное обещание forward-compat по тегам (переписано честно + fail-loud тесты + R-8 с жёстким триггером); выдуманные пути файлов (перенаправлены на реальные).
- 1 дизайн-дыра: не было capability/status gating — плитки со статусом `Failed`/`Skipped` рендерились бы мёртвыми кнопками. Добавлено правило render gating.

Подтверждено здоровым: чистота `commonMain`, порты с fake+real адаптерами, обезличенность Profile-артефакта, разделение preset-поля vs инвариант (rule 11), загрузка bundled-пресетов через адаптеры (rule 9), решение не персистить запросы.

Артефакты синхронизированы с кодом, вердикт **READY FOR IMPLEMENTATION** переиздан честно. Далее — `/speckit.implement`.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 Fresh install + wizard на Xiaomi Redmi Note 11 (adb id `17f33878`) → HomeActivity показывает плитки, не Error UI. Требуется физическая верификация.
- [ ] #2 Wizard runtime строки локализованы через `core/composeResources/values/strings_wizard.xml` (нет raw `wizard_*` ключей в UI). Проверяется на эмуляторе или физическом устройстве.
- [ ] #3 `Component.tags: Set<Tag>` добавлен, constructor-defaults покрывают все 8 subtypes. Roundtrip тест (Profile → JSON → Profile, schemaVersion 2) зелёный. `ComponentTagsFitnessTest` (reflection) подтверждает non-empty defaults.
- [ ] #4 `Profile.query` + convenience selectors (`byTag`, `byAllTags`, `byAnyTag`, `byNotTag`, `homeScreenTiles`, `toolbar`) объявлены. Unit-тесты: query по одному тегу, по комбинации тегов (AND/OR/NOT), empty result, tag-not-present, render gating (`Failed`/`Skipped` не попадают в плитки).
- [ ] #5 `ProfileBackedFlowRepository` реализован (все 4 метода порта, включая `loadFlows()` — путь регрессии), DI wire в mockBackend + realBackend flavor. `HomeComponentLoadingStateTest` расширен НОВЫМ сценарием `postManifestWizardReconcile_profileSeeded_homeReady` — verifies Profile с одним AppTile → HomeLoadingState.Ready. Existing config-based сценарии в тесте остаются зелёными (ConfigBackedFlowRepository не удаляется).
- [ ] #6 `docs/architecture/preset-model.md` создан с AI-TLDR блоком. `Preset.kt` + `Component.kt` содержат doc-комментарии с ссылкой на этот файл. `docs/dev/server-roadmap.md` содержит SRV-CONFIG-DEPRECATION запись.
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

**Applies to**: `core/src/commonMain/kotlin/com/launcher/preset/model/{Enums,Component,Preset}.kt`, `core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt` (new), `core/src/commonMain/kotlin/com/launcher/adapters/flow/ProfileBackedFlowRepository.kt` (new), `core/src/android{Mock,Real}Backend/kotlin/com/launcher/di/BackendInit.kt` (DI rewire), `core/src/commonMain/composeResources/values/strings_wizard.xml` (extend existing), `docs/architecture/preset-model.md`, `docs/dev/server-roadmap.md` (SRV-CONFIG-DEPRECATION entry). Contract: `specs/task-127-ecs-tags-and-query/contracts/profile-v2.md`.

**Trade-offs accepted**:
1. No migration writer pre-release (rule 4 MVA); first post-release breaking change = mandatory `ProfileMigrationV2toV3` + bump (one-way door per rule 5).
2. Honest forward-compat limitation: an older reader fails loud (`SerializationException`) on an unknown Tag value or Component type — kotlinx.serialization has no per-element enum leniency. Acceptable while Profile is same-device/same-binary; **hard trigger**: lenient `Set<Tag>` serializer + unknown-type policy MUST ship before admin push (spec-009) or preset sharing (rule 9). Pinned by contract tests.
3. Two orthogonal dimensions (lifecycle Preset fields + semantic Component tags) may confuse readers — mitigated by `docs/architecture/preset-model.md` + inline doc-comments.
4. `ConfigDocument` code stays temporarily, dead-code-like for HomeScreen path; SRV-CONFIG-DEPRECATION queued in server-roadmap.
5. Tags enumerated as closed enum — adding a tag requires a code change, and third-party preset authors cannot mint tags. Accepted for MVP; exit = namespaced string tags (lenient-serializer change, not a format break — ADR-012).
6. Render-gating default (hide `Failed`/`Skipped` tiles) is a product default; if user segments later need "render with tap-time fallback", it becomes a preset field per rule 11.

**Exit ramp**: If Tag+Query proves insufficient at scale, keep the Tag data model and replace linear query with indexed lookup — additive, no wire-format break. If the Preset three-field structure conflicts with Tag semantics later, add a fourth Preset field or promote tags to Preset level — both additive. If the tagged-component direction turns out wrong entirely, revert `FlowRepository` binding to `ConfigBackedFlowRepository`, keep Tag+Query as pure metadata (unused by HomeScreen path). Estimated cost of any exit ~2-3 days. Latent one-way door (single Component per entity blocks composition) documented in ADR-012 with 8–16-week canonical-ECS migration estimate and a concrete trigger.

Revision note (2026-07-15): initial scope replaced with ECS Tags+Query per owner mentor session.
Revision note (2026-07-16): Decision synced with final artifacts after deep pre-implement audit #2 (four independent auditors) — schemaVersion stays 2 (was: bump to 3 + migration writer, dropped per Q6 + audit); 10-value Tag enum (was 8); selectors renamed to final API; real file paths; all-four-port-methods surface incl. `loadFlows()` regression path; render gating added; honest forward-compat limitation recorded; framing corrected to tagged-component/label-selector per ADR-012.

<!-- SECTION:DISCUSSION:END -->

## Definition of Done

`In Progress → Verification/Done` через `pre-pr-backlog-sync` после fix + verification на Xiaomi Redmi Note 11 (тот же adb id `17f33878`, свежая установка). AC #3-#6 verifiable via unit/Robolectric — не требуют physical device. AC #1-#2 требуют физический smoke test.

## Resume note (2026-07-16, обновлено после аудита)

Возобновлена после закрытия TASK-122 (F-CRYPTO Foundation → Done, PR #50 merged) и merge ECS scope (branch `origin/task-127-launcher-presentation-builder` merged 2026-07-16). Owner переключился на этот blocker — блокирует merge TASK-126 и TASK-128 verification bucket.

**Speckit stage**: полный цикл пройден (specify → clarify → scenarios → plan → tasks → analyze). 2026-07-16 проведён **deep pre-implement audit** (4 независимых аудитора) по запросу владельца — фича краеугольная, нужна была проверка перед реализацией. Артефакты приведены в соответствие с реальным кодом (9 findings исправлены). **Следующий шаг — `/speckit.implement`** по `specs/task-127-ecs-tags-and-query/tasks.md` (23 активные задачи, 8 фаз).

**Что важно знать реализатору**:
- Начинать с T127-003/T127-004 (Tag enum → Component.tags на 8 подтипах, включая object → data class для `LauncherRole`/`StatusBarPolicy`).
- `schemaVersion` **не трогать** — остаётся 2.
- Главный фикс регрессии — `loadFlows()` в новом адаптере, не только `observeFlows()`.
- Пути файлов в tasks.md проверены против ветки; DI — в `core/src/android{Mock,Real}Backend/.../BackendInit.kt`, не в `app/`.
- Открытый вопрос реализации (T127-017): `ProfileStore` сейчас байндится в `app` `PresetModule` — проверить видимость из core backend-модуля, при необходимости перенести/прокинуть binding.
