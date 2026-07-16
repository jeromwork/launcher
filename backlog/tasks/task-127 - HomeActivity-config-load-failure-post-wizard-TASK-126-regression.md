---
id: TASK-127
title: 'HomeActivity config load: ECS Tags foundation + Query pattern'
status: In Progress
assignee: []
created_date: '2026-07-13'
updated_date: '2026-07-16 14:35'
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

> **Версии описания.** Создан 2026-07-13 как узкая bug-fix задача «bridge Profile → ConfigDocument через LauncherPresentationBuilder». Пересобран 2026-07-15 после mentor-сессии владельца: изначальный подход отброшен, новый scope — ECS-native расширение TASK-120 (Tags + Query pattern).

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
3. Новый `ProfileBackedFlowRepository` (реализация существующего `FlowRepository` порта) читает `Profile` напрямую, вызывает `profile.query { it.hasTag(Tag.Presentation) }`, проецирует результат в `HomeState`.
4. `HomeActivity` рендерит плитки — Error UI больше не показывается.

## Зачем

- **Разблокировать физическое тестирование** на Xiaomi Redmi Note 11 (сейчас Error UI блокирует TASK-128 verification bucket #1 и #2).
- **Устранить архитектурный дубликат**: `Profile` + `ConfigDocument` как два параллельных источника правды — long-term maintenance boom.
- **Заложить ECS-нотацию** для будущих UI-фич (кнопки тулбара, экраны, hint-overlays, safety indicators): tags дают ортогональную категоризацию без forced single-category выбора.
- **Стандартный паттерн**: ECS Query — well-understood индустрией (Bevy, Unity DOTS, Flecs). Future maintainers сразу узнают.

## Что входит технически (для AI-агента)

### Phase 1 — Tag enum + Component.tags

- **Новый** `core/preset/model/Tag.kt` (pure Kotlin, zero Android imports):
  ```kotlin
  enum class Tag {
      Presentation,        // видно на HomeScreen
      Appearance,          // визуальные overrides (fontScale, theme)
      System,              // системные (rotation lock, kiosk)
      Safety,              // SOS, emergency call
      Capabilities,        // permissions, features
      Communication,       // call, sms, messenger
      Accessibility,       // large-text, high-contrast
      Emergency,           // panic mode, medical alert
  }
  ```
- **Component.tags: Set<Tag>** — новое поле на sealed класс `Component` (TASK-120). Каждый variant (`AppTile`, `Sos`, `FontSize`, `Toolbar`, ...) объявляет свой tag-set в defaults.
- **schemaVersion bump v2 → v3** в wire-format `Profile`. Migration writer читает v2 (без tags), заполняет defaults по типу Component.
- **Roundtrip тест**: `write(v3) → read(v3) → assertEqual`.
- **Backward-compat тест**: `read(v2 fixture) → assertTagsPopulated`.

### Phase 2 — Profile.query + selectors

- **Новый API** на `Profile`:
  ```kotlin
  fun query(predicate: (Component) -> Boolean): List<Component>
  ```
- **Convenience selectors** (top-level extension functions или методы):
  ```kotlin
  fun Profile.presentation(): List<Component> = query { it.hasTag(Tag.Presentation) }
  fun Profile.safety(): List<Component>
  fun Profile.homeScreenTiles(): List<AppTile>
  ```
- **Unit-тесты**: single-tag query, multi-tag combination, empty result, tag-not-present.

### Phase 3 — ProfileBackedFlowRepository

- **Новый adapter** `core/adapters/preset/ProfileBackedFlowRepository.kt`, реализует существующий `FlowRepository` порт из HomeScreen path.
- Читает `Profile` из `ProfileStore` (TASK-120), проецирует через `profile.homeScreenTiles()` → `Flow` / `Slot` DTOs, эмитит через `Flow<HomeState>`.
- **DI rewire**: заменить `ConfigBackedFlowRepository` на `ProfileBackedFlowRepository` в обоих backend'ах (`mockBackend`, `realBackend`).
- **`HomeComponentLoadingStateTest`** расширить: Profile с одним `AppTile(tags = [Presentation])` → `HomeLoadingState.Ready`.

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

### Затронутые файлы

- `core/preset/model/Tag.kt` (новый)
- `core/preset/model/Component.kt` — добавить `tags: Set<Tag>` в sealed класс + defaults per variant
- `core/preset/model/Profile.kt` — добавить `query` API + comment header
- `core/preset/model/Preset.kt` — только comment header про две дименсии
- `core/preset/serialization/ProfileSerializer.kt` — schemaVersion v2→v3 + migration writer
- `core/adapters/preset/ProfileBackedFlowRepository.kt` (новый)
- `app/di/{mock,real}Backend*.kt` — rewire `FlowRepository` binding
- `core/composeResources/values/strings_wizard.xml` — новые ключи
- `docs/architecture/preset-model.md` (новый)
- `docs/dev/server-roadmap.md` — SRV-CONFIG-DEPRECATION запись
- `HomeComponentLoadingStateTest.kt` — расширенный сценарий

## Про роли в этой задаче

Не применяется — TASK-127 внутренний архитектурный fix. Роли `primary user` / `remote administrator` / `restricted caregiver` не отличаются в этом контексте.

## Состояние

**In Progress** с 2026-07-15 после mentor-сессии владельца. Изначальный подход (`LauncherPresentationBuilder` bridge Profile → ConfigDocument) **отброшен** как построение моста на плохую архитектуру. Новый подход — **ECS-native расширение TASK-120** через Tags + Query pattern. Далее — `/speckit.specify` для `specs/task-127-ecs-tags-and-query/`.

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 Fresh install + wizard на Xiaomi Redmi Note 11 (adb id `17f33878`) → HomeActivity показывает плитки, не Error UI. Требуется физическая верификация.
- [ ] #2 Wizard runtime строки локализованы через `core/composeResources/values/strings_wizard.xml` (нет raw `wizard_*` ключей в UI). Проверяется на эмуляторе или физическом устройстве.
- [ ] #3 `Component.tags: Set<Tag>` добавлен + migration writer v2 → v3 реализован. Roundtrip тест (v2 fixture → read → assert tags populated by defaults) зелёный.
- [ ] #4 `Profile.query` + convenience selectors (`byTag`, `byAllTags`, `byAnyTag`, `homeScreenTiles`) объявлены. Unit-тесты: query по одному тегу, по комбинации тегов (AND/OR), empty result, tag-not-present.
- [ ] #5 `ProfileBackedFlowRepository` реализован, DI wire в mockBackend + realBackend flavor. `HomeComponentLoadingStateTest` расширен НОВЫМ сценарием `postManifestWizardReconcile_profileSeeded_homeReady` — verifies Profile с одним AppTile → HomeLoadingState.Ready. Existing config-based сценарии в тесте остаются зелёными (ConfigBackedFlowRepository не удаляется).
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

**Choice**: Extend TASK-120 Component model with ECS-standard tag+query patterns. Add `Component.tags: Set<Tag>` field (multiple tags per component, serialized, schemaVersion=3). Add `Profile.query(predicate)` selector API with convenience selectors (`presentation()`, `safety()`, `homeScreenTiles()`). Rewire HomeScreen from `ConfigBackedFlowRepository` to a new `ProfileBackedFlowRepository` which reads `Profile` directly and projects via query. `ConfigDocument` stays in codebase for future admin push scenarios but is no longer used by HomeScreen path. Preset three-field structure (`wizardFlow` / `settingsMap` / `activeComponents`) preserved — describes lifecycle dimension, orthogonal to `Component.tags` semantic-domain dimension.

**Rationale**:
1. TASK-120 was already ECS-inspired per Session 2 notes; adding Tag+Query completes the pattern rather than introducing a foreign paradigm.
2. Multiple tags per component allow orthogonal categorization without forcing single-category choice (an SOS component is both `Safety` and `Emergency` and `Presentation`).
3. Profile-as-source-of-truth eliminates ConfigDocument duplication in the HomeScreen path — one source, one migration story.
4. Standard ECS pattern (Bevy Query, Unity EntityQuery, Flecs) — well-understood by future maintainers; no bespoke abstraction to explain.
5. Additive to TASK-120, not superseding — sealed hierarchy unchanged, just new field. TASK-120 Decision block remains valid.

**Applies to**: `core/preset/model/{Component,Profile,Tag,Preset}.kt`, `core/preset/serialization/ProfileSerializer.kt`, `core/adapters/preset/ProfileBackedFlowRepository.kt` (new), `app/di/{mock,real}Backend*.kt` (DI rewire), `core/composeResources/values/strings_wizard.xml`, `docs/architecture/preset-model.md` (new), `docs/dev/server-roadmap.md` (SRV-CONFIG-DEPRECATION entry).

**Trade-offs accepted**:
1. schemaVersion bump v2→v3 requires migration writer + backward-compat test (one-way door per rule 5).
2. Two orthogonal dimensions (lifecycle Preset fields + semantic Component tags) may confuse readers — mitigated by explicit `docs/architecture/preset-model.md` + inline doc-comments on both files.
3. `ConfigDocument` code stays temporarily, becoming dead-code-like for HomeScreen path; SRV-CONFIG-DEPRECATION queued in server-roadmap for cleanup.
4. Tags enumerated as closed set (enum, not free-form strings) — adding new tag requires code change. Accepted: freeform strings would drift into typo hell.

**Exit ramp**: If Tag+Query proves insufficient at scale (e.g., tag combinations need indexing beyond linear scan), keep the Tag data model and replace linear query with indexed lookup — additive change, no wire-format break. If the Preset three-field structure conflicts with Tag semantics later, add a fourth Preset field or promote tags to Preset level — both additive. If ECS-native direction turns out wrong entirely (unlikely — pattern is battle-tested industry-wide), revert `FlowRepository` binding to `ConfigBackedFlowRepository`, keep Tag+Query as pure metadata on Component (unused by HomeScreen path). Estimated cost of any exit ~2-3 days.

Revision note (2026-07-15): initial scope replaced with ECS Tags+Query per owner mentor session — see Decision above.

<!-- SECTION:DISCUSSION:END -->

## Definition of Done

`In Progress → Verification/Done` через `pre-pr-backlog-sync` после fix + verification на Xiaomi Redmi Note 11 (тот же adb id `17f33878`, свежая установка). AC #3-#6 verifiable via unit/Robolectric — не требуют physical device. AC #1-#2 требуют физический smoke test.

## Resume note (2026-07-16)

Возобновлена после закрытия TASK-122 (F-CRYPTO Foundation → Done, PR #50 merged) и merge ECS scope (branch `origin/task-127-launcher-presentation-builder` merged 2026-07-16). Owner переключился на этот blocker — блокирует merge TASK-126 и TASK-128 verification bucket. Speckit stage: spec.md готов, checklists пройдены (chat-only per ADR-011 §5 revised), следующий шаг — `/speckit.clarify` или сразу `/speckit.scenarios` / `/speckit.plan` (уточнить у owner).
