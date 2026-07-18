---
id: TASK-136
title: 'Decision: ECS canonical foundational model'
status: In Progress
assignee: []
created_date: '2026-07-18'
updated_date: '2026-07-18'
labels:
  - phase-2
  - foundation
  - ecs
  - decision
  - one-way-door
milestone: m-1
dependencies:
  - TASK-65
decision-supersedes:
  - TASK-120
  - TASK-127
superseded-by: null
priority: high
ordinal: 136000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Пересматриваем **фундамент** настройки лаунчера. Сейчас модель — «tagged-component, ECS-inspired» (TASK-120/127, ADR-012): каждая сущность (`Entity`) — это **ровно один тип** из закрытого меню (`sealed class Component`), данные вшиты в тип. Это **discriminated union**, не настоящий ECS — композиции нет.

Владелец решил (2026-07-18) перейти на **канонический ECS**: сущность = id + **мешок компонентов** (`ComponentMap`), которые вешаются и снимаются независимо. Причина — устал от повторяющихся всплывающих архитектурных вопросов, которые в ECS уже продуманы; композиция всё равно понадобится (admin-lock / удалённое управление — TASK-70/103 вешают флаг «заблокировано» на сущности любого типа).

По шагам (что меняется):
1. `Entity` перестаёт быть «один Component» → становится `Entity(id, components: ComponentMap, parentId?)`.
2. Компоненты (`Renderable`, `Parent`, `AdminLocked`, `AppTile`-данные, …) вешаются композиционно.
3. Запросы (`byTag`, `children`, `tilesOf`) остаются, но работают над мешком компонентов.
4. Сериализация мешка в версионированный JSON — новый дизайн (правило 5 + zero-knowledge правило 13).

## Зачем

- **Снять целый класс будущих арх-вопросов** одним продуманным решением, а не решать каждый по мере всплытия (owner motivation).
- **Композиция реально приходит**: admin-lock (TASK-70/103) — сквозной флаг на сущности разных типов; sealed-union решает это только через «поле-на-каждый-тип» (death spiral).
- **Дёшево сейчас**: приложение не выпущено, миграции профилей пользователей нет (только переписывание кода) — pre-release момент минимальной цены для one-way door.

## Что входит технически (для AI-агента)

Решается в Discussion-сессии (ниже). Ключевые оси:
- **Свой маленький ECS-core (~сотни строк) vs Fleks (KMP, но игровой)** — TASK-120 уже отверг Fleks/Ashley по scale; проверить снова под composition-нужду.
- **Compile-time safety recovery**: держать `sealed interface Component` для **закрытого набора типов** (exhaustive `when` для сериализации + coverage-тест сохраняются), а композицию давать через `ComponentMap` (typed `get<T>()`, bundles, required-components).
- **Сериализация ECS-мира** в wire-format: `Entity(id, Map<KClass, Component>)` → versioned JSON → назад; совместимость с zero-knowledge sync (правило 13, opaque blob).
- **Миграция существующего кода**: `Entity`/`Component`/`ProfileQuery`/`ProfileFactory`/`Provider` уже есть (TASK-120/127) — что переживёт, что переписывается.
- **Exit ramp** (правило 3): если canonical ECS окажется overkill — как откатиться.

## Состояние

**Draft, Decision block заполнен 2026-07-18.** Discussion завершён за одну сессию (OQ-1..OQ-7 закрыты, все grounded против Fleks Snapshot + Bevy Bundle). Готов к `/speckit.specify`. Decision **mutable до старта имплементации** (rule 11). Supersedes TASK-120 + TASK-127 (оба Done — `superseded-by` проставляется в implementation-фазе вместе с ADR-013 + cleanup). Decision block mutable до первого implementation-коммита.

Блокирует: **TASK-69** (Settings as Profile View — поставлен на Paused 2026-07-18, строит проекцию на этой модели). Downstream, зависевшие от TASK-120 (TASK-71, TASK-68, TASK-19, draft-1), получат обновлённый контракт.

<!-- SECTION:DESCRIPTION:END -->

## Discussion

<!-- SECTION:DISCUSSION:BEGIN -->

### Как сюда пришли (2026-07-18, mentor-сессия внутри speckit-clarify TASK-69)

Разбирали проекцию Settings → упёрлись в модель Component/Preset/Profile. Владелец (самоопределённый новичок) критически прожал текущую «tagged-component» модель:

1. **Снял аргумент «миграция дорогая»**: приложение не выпущено, пользователей нет — переписывание формата почти бесплатно pre-release. AI переоценил цену (было заявлено «мигратор для каждого профиля» — фактически только code-refactor).
2. **Поймал противоречие «меняется пару раз в жизнь» vs «плитка серая 30 сек ломает систему»**: разрешено — transient UI-state (cooldown) живёт в ViewModel, не в Profile; cooldown был **плохим примером** composition-стены со стороны AI.
3. **Реальная composition-стена** — не темы (тема = один компонент с полями, additive), а **admin-lock**: сквозной сохраняемый флаг на сущностях разных типов (TASK-70/103 roadmap).
4. **Мотивация решения** (owner, дословно смысл): «используем ECS — это снимает будущие арх-вопросы, они уже продуманы; надоело каждый раз решать всплывающее, что в ECS уже решено».

AI честно surface'ил (anti-yes-man): «ECS снимает вопросы — верно наполовину». ECS закрывает семейство composition/query/modifier, но открывает своё: **на KMP нет зрелого не-игрового ECS** → скорее всего свой маленький core (снова "строим своё", но по проверённому чертежу) + **дизайн сериализации** ECS-мира в наш wire-format не бесплатен.

### Установленный инсайт (переносится в Decision-кандидат)

**Compile-time safety восстановима почти полностью** — теряется только exhaustiveness над *сочетаниями* компонентов (её никто не хочет, экспоненциальна), не над *набором типов*:
- `sealed interface Component` → набор типов ЗАКРЫТ → exhaustive `when` для сериализации/coverage сохраняется.
- Хранение меняется на `Entity(id, components: ComponentMap)` → композиция.
- `get<T>(): T?` (typed), bundles (именованные конструкторы), required-components (Bevy 0.15 pattern), typed queries, coverage-тест (уже есть — TASK-120 fitness #3).

Это «канонический ECS без потери Kotlin-безопасности».

### Pre-release cleanup policy (owner directive 2026-07-18) — applies to whole TASK-136 execution

**Не накапливать «устаревшее с пометкой deprecated» — переписывать на месте до текущей правды.** Rationale: будущий AI-агент читает старый файл, видит прошлое решение, уходит думать в ту сторону, не зная что прошло несколько итераций. Пока не выпустились (нет пользователей, нет persisted data в проде) — чистим по максимуму, migration machinery правила 5 преждевременна.

Применение:
- **Код / wire-format / фикстуры / bundled-пресеты** — переписываем в ECS-форму на месте, без слоёв совместимости, без мигратора, без schemaVersion bump. Поле `schemaVersion` остаётся (бесплатный шов), номер не двигаем.
- **Живые доки** (`docs/architecture/preset-model.md`, `data-model.md`) + **ADR-012** — переписать/заменить на каноническое ECS-решение; удалить текст «мы НЕ канонический ECS» (собьёт агента).
- **Архивные decision-таски** (TASK-120/127) — исключение: остаются как история (rule 11), но получают `superseded-by: TASK-136`, чтобы агент перескакивал на актуальное, а не действовал по их содержимому.

### Open questions для сессии

- **OQ-1** ✅ **RESOLVED (2026-07-18)**: **Свой маленький core (~200-400 строк) в форме Fleks**, не Fleks напрямую.
  - **Fleks requirements (verified, v2.14 May 2026)**: Kotlin **2.4.0** (мы на **2.0.21** — разрыв; adoption = Kotlin upgrade, тянет Compose MP 1.7.3 → BOM-риск), KMP JVM/Android/iOS/JS/Native ✅, zero deps + zero reflection ✅, MIT, `io.github.quillraven.fleks:Fleks:2.14`. Entity = `value class Int` (index, нестабилен между snapshot'ами); component = data class + companion `ComponentType`; query = `family { all/any/none }`; systems = `IteratingSystem`.
  - **Почему свой core**: Fleks игровой (планировщик систем каждый кадр — нам не нужен, у нас `ReconcileEngine`+`Provider`); половина нашего уже есть (`ProfileQuery`, движок, провайдеры) — меняем только хранение `Entity` (один Component → `ComponentMap`); Kotlin-разрыв; сериализацию в наш zero-knowledge формат всё равно проектируем сами.
  - **Fleks-swappability (exit ramp, rule 3, НЕ heavyweight свитч-порт per rule 4 / refuse #9)**: API и словарь миррорим под Fleks (`world.entity {}`, `entity[Type]`, `world.family { all(...) }`) → call sites уже Fleks-совместимы; миграция = замена внутренностей World на биндинги Fleks, потребители не трогаются.
  - **Швы, НЕ переезжающие бесплатно** (в цену миграции): (1) **id — `String`** у нас (стабилен для сериализации + zero-knowledge, rule 13), Fleks Int index → ремап при переезде; (2) **сериализация/wire-format остаётся нашей** всегда (rule 5 + 13) — exit ramp покрывает рантайм, не персистентность.
  - **Action**: inline-TODO `// TODO(ecs-fleks-migration): World internals swappable to Fleks; cost = Kotlin 2.0→2.4 upgrade + String→Int id remap; persistence stays ours` у core World.
- **OQ-2** ✅ **RESOLVED (2026-07-18)**: **Entity-grouped сериализация, зеркало Fleks Snapshot** — verified против движка (не теория).
  - **Verified**: `world.snapshot()` в Fleks = `Map<Entity, Snapshot>`, `Snapshot(components: List<Component>, tags: List<UniqueId>)`. JSON entity-grouped: сущность → `{components:[{type,...}], tags:[...]}`. Значит entity-grouped — каноническая форма сериализации ECS (даже сам Fleks так делает).
  - **Форма**: `Entity(id: String, parentId: String?, components: List<Component>, tags: Set<Tag>)`. `components` — полиморфный список (переиспользуем kotlinx `classDiscriminator="type"`, ноль кастома). `tags` **вынесены наружу** из компонента (канонично — тег это zero-size маркер, не поле данных) → см. OQ-4.
  - **Type-grouped (C) отклонён**: archetype/sparse-set — это in-memory perf-layout под 10k×60fps, не наш масштаб (rule 4); Fleks и сам сериализует entity-grouped, так что C для wire — не «более ECS».
  - **НЕТ schemaVersion bump, НЕТ мигратора, НЕТ backward-compat теста** (owner directive 2026-07-18, pre-release): чистим формат/фикстуры/пресеты/код на месте в ECS-форму. Поле `schemaVersion` оставляем как бесплатный шов (в день релиза = точка отсчёта), номер не двигаем.
  - Zero-knowledge (rule 13) не задет: весь Profile — один opaque blob, внутри ECS-мир.

- **OQ-4** ✅ **RESOLVED (2026-07-18)**: **`Set<Tag>` enum на `Entity`** (не marker-компоненты).
  - Grounded в verified Fleks `Snapshot(components, tags)`: канонический движок держит теги **отдельным списком маркеров** (`List<UniqueId>`), не в данных. Семантика «тег = отдельный маркер на сущности» уже канонична (вынесли из компонента в OQ-2).
  - `Set<Tag>` = компактная кодировка ровно того, что Fleks пишет как `tags:[{type:...}]` — совпадает по структуре, отличается лишь тем что маркер = enum-имя, а не тип-объект. При закрытом наборе (13 значений) marker-как-тип даёт только взрыв типов + многословную сериализацию.
  - **Exit ramp**: если тег должен нести данные / требовать typed-query как компонент → повышаем из enum-значения в marker/data-компонент (additive, тот же список).
- **OQ-3** ✅ **RESOLVED (2026-07-18)**: объём миграции + **Blueprint-форк** (verified против Bevy Bundle).
  - **Blueprint = Bundle** (именованный набор компонентов в Pool) — **только шаблон спавна, НЕ застывший тип сущности**. Verified Bevy: bundle = spawn-time grouping, «zero runtime significance after creation», после спавна идентичность бандла исчезает, entity — свободный мешок (add/remove компонентов независимо), `(Bundle, Extra)` схлопываются.
  - **Ошибка, которую поймал owner**: ранняя формулировка «Blueprint = готовая сущность» рисковала retain'ить бандл как идентичность → заново discriminated-union жёсткость → admin-lock снова у стены. Отвергнуто.
  - **Модель**: `ProfileFactory` = «спавн»: preset-запись → бандл → `Entity(id, components=[...], tags, parentId)`, бандл забыт после сборки. Preset-запись = одна Entity = базовый бандл + опц. extra-компоненты + `paramsOverride` + `parentRef` (схлопываются). Запросы спрашивают «какие компоненты/теги», НЕ «каким blueprint сделана».
  - **Migration scope**:
    - *Переживёт (правка тела, API стоит)*: `ProfileQuery` (`it.component.tags` → `it.tags`), `Provider`/`ProviderRegistry`, `ReconcileEngine`, `PresetValidator`.
    - *Переписываем на месте*: `Entity` (`component: Component` → `components: List<Component>` + `tags: Set<Tag>`), `Component` (убрать поле `tags`), `Blueprint` (`component` → `components: List<Component>` + `tags` = бандл), `ProfileFactory` (спавн: bundle разворачивается в плоский набор компонентов, без «extra»-концепции — CL-2), все `when(entity.component)` / `entity.component as? X` в UI + `ProfileBackedFlowRepository`, фикстуры, bundled-пресеты JSON, тесты.
  - **Exit-ramp «pure Variant 1»** (preset собирает entity из отдельных component-ref'ов без бандла) — если понадобится авторская сборка из кусочков; additive.
- **OQ-5** ✅ **RESOLVED (2026-07-18)**: **big-bang**, не incremental. Смена формы `Entity.component → components: List` пронизывает всех потребителей — «наполовину» мигрировать тип нельзя; держать две формы параллельно дороже, чем переписать разом (усилено pre-release clean-in-place). Все 11 Component-типов + Blueprint + ProfileFactory + сериализация + queries + фикстуры + bundled-пресеты — одна когерентная правка.
- **OQ-6** ✅ **RESOLVED**: exit ramps собраны в Decision block ниже (Fleks swap / type-grouped index / marker-component tags / revert to discriminated-union).
- **OQ-7** ✅ **RESOLVED**: написать **ADR-013** (canonical ECS adopted); `ADR-012` получает header «Superseded by ADR-013»; `TASK-120`/`TASK-127` → `superseded-by: TASK-136`. Исполняется в implementation-фазе (см. cleanup inventory в Decision).

### Clarifications (speckit-clarify pass, 2026-07-18) — grounded against ECS FAQ (Sander Mertens)

Owner corrected two of my product-model errors + steered all opens to ECS canon. Key correction: **admin-lock is profile-level, not per-entity** (see Rationale 1) — my per-entity example was wrong; ECS adopted for the **clean canonical model + tag composition**, not that example.

- **CL-1** (AdminLocked now vs TASK-70) → **retracted**. AdminLocked is a *profile-level server edit-lock* (TASK-70), not a per-entity component. Composition is demonstrated by **tags** (an entity already carries several markers) — no placeholder component needed now. Facade/settings split is modeled by tags (GoF Facade: grandma's curated front; settings live behind, not in the facade; no per-setting lock shown to her).
- **CL-2** (extra components in preset) → **dissolved**. ECS FAQ: «entities are simply collections of components… prefabs/bundles expand into flat component sets». No "base vs extra" — an entity is just its component set. A preset entry lists that set (bundle-ref expands + optional inline components, all flattened). Only remaining detail: inline vs by-ref writing (DRY convenience), finalized in plan.
- **CL-3** (dup component type) → **at-most-one-per-type** (ECS canon). `get<T>(): T?` unambiguous; enforced by fitness test.
- **CL-4** (who assigns tags) → **explicit, like Fleks**. Fleks IS canonical ECS; tag = zero-data component added/removed **explicitly** (ECS FAQ). No auto-derivation. Bundle declares tags at spawn; composing code sets tags explicitly. The "contradiction" was invented — dropped.
- **CL-5** (status granularity) → **canonical: state = components, System moves them**. ECS FAQ: «no special status fields — state exists only as component data that systems query and modify». Drop the `Entity.status` field; represent apply-state as state component(s)/marker(s) (`Pending`/`Applied`/`Failed(reason)`/`Skipped`/`Unverifiable`) that `ReconcileEngine` (the System) transitions. Exact encoding (a `LifecycleState` sealed component vs individual state-tags; `Failed` carries data so not a pure tag) finalized in plan.

### Decision (English)

> **Mutable until implementation starts** (rule 11 mutability window). Supersedes TASK-120 + TASK-127 + ADR-012.

**Choice**: Adopt **canonical ECS (composition)** as the foundational launcher-config model, implemented as a **small in-house core (~200-400 LOC) mirroring Fleks's API shape** (swap-compatible), replacing the tagged-component discriminated-union model of TASK-120/127.

Concrete shape:
- **Entity** = `Entity(id: String, components: List<Component>, tags: Set<Tag>, parentId: String?)`. A **free bag** — canonical ECS: an entity is *simply a set of components* (ECS FAQ: «entities are simply collections of components»). Components/tags added/removed independently. **No special `status` field** — apply-state is itself a component/marker the System transitions (CL-5, canonical: «no special status fields — state exists only as component data that systems query and modify»). `tags: Set<Tag>` = compact encoding of **zero-data marker components** (ECS FAQ: «a tag is a component that has no data»), added/removed explicitly like Fleks. `parentId` = flat storage, tree computed by queries (kept from TASK-127).
- **Component** = `sealed interface Component`, **closed set** of data classes. Closed set preserves Kotlin exhaustive `when` for serialization + coverage tests (compile-time safety recovery). `tags` field **removed** from every subtype (moved to `Entity`).
- **Tag** = `Set<Tag>` enum on `Entity` — compact encoding of Fleks `Snapshot.tags` (separate marker list). Query by membership.
- **Blueprint** = **Bundle** (named component set in Pool) — spawn-time template **only, NOT retained as entity identity** (verified: Bevy Bundle = "zero runtime significance after creation"). `Blueprint(id, components: List<Component>, tags, wizardBehavior, critical, ...)`.
- **ProfileFactory** = the "spawn": a preset entry declares an entity's **component set** — a bundle-ref that expands into components, optionally with more inline components, **all flattened into one set** (no privileged "base vs extra" — an entity is just its component set, CL-2) + `paramsOverride` + `parentRef` → free-bag `Entity`; bundle discarded after.
- **Serialization** = entity-grouped, mirror Fleks `Snapshot`: `{id, parentId, components:[{type,...}], tags:[...]}`. Reuse kotlinx polymorphic (`classDiscriminator="type"`) — zero custom serializer. **NO schemaVersion bump, NO migrator, NO backward-compat test** (pre-release clean-in-place); `schemaVersion` field retained as a free seam.
- **Query** = `ProfileQuery` extension fns over `entity.tags` + `entity.components` (`byTag`, `children`, `flows`, `tilesOf`, …) + typed access `inline fun <reified T> Entity.get(): T?` via `filterIsInstance`. Linear scan (~20-40 entities).
- **Systems** = existing `ReconcileEngine` + `Provider`/`ProviderRegistry` — the ECS "system" (the loop that queries entities and mutates them). It records apply-state **canonically** by adding/removing state components/markers (CL-5), not by writing a `status` field. No game-loop scheduler.
- **Scope** = big-bang: one coherent rewrite of Entity/Component/Blueprint/ProfileFactory/serialization/queries + all consumers + fixtures + bundled presets.

**Rationale**:
1. **Clean canonical model** (owner's actual motivation, corrected 2026-07-18): adopt ECS to **reuse a thought-through, industry-standard model** (entity / component / tag / system / query) and stop re-deriving ad-hoc structure that keeps surfacing new architecture questions. ⚠️ The earlier «admin-lock as a per-entity cross-cutting component» justification was **RETRACTED in clarify** — admin-lock is a *profile-level* server edit-lock shown between admins (download profile → set "editing forbidden" on server → edit → upload; visible to another admin), NOT a per-entity flag (TASK-70). Real composition here is at the **tag level** — an entity carries several zero-data markers (e.g. Tile + facade-placement + settings-placement), genuine and used; data-level multi-component composition is a **free capability** of the canonical model, not driven by a single confirmed feature.
2. **Pre-release = cheap one-way door**: no users, no persisted prod data → switch cost is code-refactor only, not user migration. Cheapest possible moment (rule 3 timing).
3. **Owner motivation** (2026-07-18): adopt a thought-through, industry-canonical model to stop re-deriving ad-hoc structure that keeps surfacing new architecture questions. Verified against real engines (Fleks Snapshot, Bevy Bundle) — decisions grounded, not invented.
4. **Compile-time safety recovered**: `sealed interface Component` keeps the type-set closed (exhaustive `when` for serialization/coverage survives). We lose only exhaustiveness-over-*combinations* (exponential, unwanted). Typed `get<T>()` + bundles + coverage fitness test cover the rest.
5. **Own core, not Fleks-direct**: Fleks is a game runtime (per-frame system scheduler we don't use) and needs Kotlin 2.4.0 (we're on 2.0.21). A ~200-400 LOC core in Fleks's API shape gives canonical semantics + Fleks-swappability without the game-runtime/scale mismatch (TASK-120 rejection of Fleks stands for the *runtime*, not the *pattern*).
6. **Consistency over strict build-by-need — a deliberate, rule-4-valid call** (owner decision 2026-07-18, after admin-lock retraction). Honest state: every entity today holds exactly one data-component + tags, so the multi-data-component capability has **no current product consumer** (meta-minimization checklist flagged this). We adopt canonical ECS anyway — NOT for a feature, but because a **bespoke non-canonical model imposes an ongoing "correction tax"**: a novice owner must permanently hold "this isn't pure ECS" in mind, catch agent drift, and correct it every session (observed repeatedly this session). That recurring rework is exactly the future cost rule 4 guards against; a documented industry-standard model removes it (agents + owner lean on standard ECS knowledge, not a local deviation). Canonical multi-component is the *model's intrinsic shape*, not a bolted-on abstraction. Trade-off accepted consciously: we pay one cheap pre-release rewrite now to stop paying the correction tax forever. `Entity.with()/without()` runtime-compose API is included as the model's natural surface but currently exercised only by tags + a test-only fake (`TestFlag`); first real data-level consumer is welcome additively.

**Applies to**:
- **Domain** (`core/preset/`, commonMain, pure Kotlin): `Entity`, `Component` (sealed interface), `Blueprint` (bundle), `Tag`, `ProfileFactory`, `ProfileQuery`, `Outcome`, ports (`Provider`, `ProviderRegistry`, `PoolSource`, `PresetSource`, `ProfileStore`).
- **Serialization**: kotlinx polymorphic over `Component`; `Profile`/`Entity` DTO entity-grouped.
- **Adapter** (`app/androidMain/`): `ProfileBackedFlowRepository` + all `when(entity.component)` callsites → `entity.get<T>()` / `filterIsInstance`.
- **Assets**: `pool.json` (blueprints as bundles), `bundled-presets/*.json` — rewritten in place.
- **Fitness (rule 7)**: coverage (every `Component` subtype has serializer + Provider), import-guard on engine, `paramsOverride` schema, tag-presence.
- **CLEANUP INVENTORY (pre-release consistency, executes in implementation phase)**:
  - `docs/architecture/preset-model.md` — rewrite in place to canonical ECS (remove "tagged-component / not-canonical-ECS" text).
  - `ADR-012` — add "Superseded by ADR-013" header; write `ADR-013` (canonical ECS adopted, reverses ADR-012 on composition-need + pre-release timing).
  - `TASK-120`, `TASK-127` — set `superseded-by: TASK-136`.
  - Code / fixtures / bundled-presets — rewrite in place (no migrator, no schemaVersion bump).
  - `specs/task-127-ecs-foundation/*` — left as TASK-127's spec archive; TASK-136 gets its own spec.
  - `// TODO(ecs-fleks-migration)` inline seam on core World.

**Trade-offs**:
- **Vs tagged-component discriminated union (TASK-120/127)**: gain composition + canonical model; lose compile-time exhaustiveness-over-combinations (unwanted anyway); pay one big-bang rewrite (cheap pre-release).
- **Vs Fleks-direct**: gain no Kotlin-2.4 upgrade now + no game-runtime; pay ~200-400 LOC own core (mitigated: half already exists — queries, engine, providers).
- **Vs type-grouped (archetype/sparse-set) storage**: gain kotlinx-free serialization + readability; lose in-memory perf layout (irrelevant at ~20-40 entities).
- **Vs incremental migration**: gain single coherent change; lose nothing (can't half-migrate the Entity type shape).

**Exit ramp** (rule 3):
- **To Fleks runtime** if own core insufficient: API already mirrors Fleks → swap World internals; cost = Kotlin 2.0→2.4 upgrade + `String`→`Int` id remap; persistence stays ours. Inline `TODO(ecs-fleks-migration)`.
- **To type-grouped storage** if scale ever hits (unlikely): build `Map<ComponentType, List<Entity>>` index at load — additive, no wire change.
- **To marker-component tags** if a tag must carry data / typed-query: promote enum value → marker/data component — additive, same list.
- **Revert to discriminated union** (unlikely): re-collapse `components: List` → single `component`; only viable while pre-release.

<!-- SECTION:DISCUSSION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Decision block заполнен (Choice / Rationale / Applies to / Trade-offs / Exit ramp) на English
- [x] #2 OQ-1..OQ-7 закрыты в Discussion
- [ ] #3 ADR-013 написан (заменяет ADR-012), TASK-120/127 помечены superseded-by TASK-136 — implementation-фаза
- [ ] #4 Downstream dependencies обновлены (TASK-69/71/68/19 получают ссылку на новый контракт) — implementation-фаза
<!-- AC:END -->
