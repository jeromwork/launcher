# Feature Specification: Canonical ECS — foundational launcher-config model

**Feature Branch**: `task-136-ecs-canonical-foundational-model`
**Created**: 2026-07-18
**Status**: Draft
**Backlog task**: [task-136](../../backlog/tasks/task-136%20-%20Decision-ECS-canonical-foundational-model.md)
**Input**: TASK-136 Decision block (2026-07-18) — canonical ECS (composition) supersedes the tagged-component discriminated-union model of TASK-120/127 (ADR-012).
**Supersedes**: TASK-120, TASK-127, ADR-012.

---

## Тип фичи (важно для чтения этой спеки)

Это **фундаментальная / архитектурная** фича, а не пользовательская UI-фича. Никакого нового экрана здесь нет. «Пользователи» этой фичи — двое:

1. **Downstream feature-таски**, которые строятся поверх модели: TASK-69 (Settings as Profile View, сейчас Paused), TASK-71, TASK-68, TASK-19, а также TASK-70/103 (admin-lock).
2. **Разработчик / AI-агент**, который сопровождает модель и добавляет к ней компоненты.

Поэтому раздел «User Scenarios» переформулирован в **developer-facing capability stories** — «сценарии способностей модели». Приёмка (Acceptance) — это **свойства модели** (композиция работает, запросы работают, сериализация делает roundtrip, устаревших доков не осталось), проверяемые тестами и fitness-функциями, а не тапом по экрану. Там, где стандартный шаблон ждёт end-user story, мы явно берём developer-capability story и говорим об этом.

---

## Clarifications

### Сессия 2026-07-18 (speckit-clarify pass)

Все спорные точки прожаты против **ECS FAQ (Sander Mertens)** — канонического источника по entity-component-system. По ходу владелец исправил **две мои ошибки в продуктовой модели**, а остальные открытые вопросы (CL-1..CL-5) свёл к ECS-канону. После этой сессии открытых clarify-маркеров в спеке не остаётся — все пять резолюций ниже.

**Correction A — admin-lock НЕ per-entity флаг.** Моя ранняя формулировка «admin-lock как навесной компонент на любую сущность» — **ОШИБКА**. Admin-lock — это **profile-level серверный edit-lock**: администратор скачивает профиль бабушки → ставит на сервере флаг «редактирование запрещено» → правит → загружает обратно; флаг показывается **другому администратору** (чтобы двое не редактировали одновременно). Это НЕ признак на отдельной сущности внутри профиля. Поэтому admin-lock **снят** как мотивация композиции (он живёт в TASK-70, на уровне всего профиля, не в этой модели).

**Correction B — зачем на самом деле ECS.** Настоящая мотивация владельца: (1) **чистая каноническая модель** — переиспользовать продуманную индустриальную структуру (entity / component / tag / system / query), а не переизобретать ad-hoc каждый раз; (2) **композиция тегов** — сущность реально несёт несколько zero-data маркеров одновременно (например `Tile` + facade-placement + settings-placement); это используется. Многокомпонентная композиция **на уровне данных** — бесплатная способность канонической модели, а не следствие одной подтверждённой фичи.

| ID | Вопрос | Резолюция |
|----|--------|-----------|
| **CL-1** | Фиксировать ли `AdminLocked` placeholder-компонент сейчас? | **Отозвано.** Нет `AdminLocked` placeholder'а. Композиция демонстрируется **тегами** (сущность уже несёт несколько маркеров). Facade = паттерн GoF Facade: бабушкин курируемый передний экран; настройки (шрифт) живут **за** фасадом, не в самом фасаде; никакого per-setting замка бабушке не показывается. |
| **CL-2** | Как пресет описывает «доп. компоненты» (base vs extra)? | **Растворено.** Канонический ECS (ECS FAQ): сущность — *просто набор компонентов*; бандлы разворачиваются в **плоский набор**; концепции «базовые vs extra» НЕТ. Запись пресета просто перечисляет набор компонентов (bundle-ref разворачивается + опц. inline-компоненты, всё схлопывается в один набор). Остаётся лишь деталь: писать inline или по ссылке (DRY-удобство) — финализируется в plan. |
| **CL-3** | Можно ли два компонента одного типа в мешке? | **At-most-one-per-type** (ECS-канон). `get<T>(): T?` однозначен; обеспечивается fitness-тестом. |
| **CL-4** | Кто назначает теги теперь, когда они сняты с компонента? | **Явно, как в Fleks** (Fleks ЕСТЬ канонический ECS): тег — zero-data компонент, добавляется/снимается **явно** (ECS FAQ). Никакой авто-деривации. Бандл декларирует теги при спавне; композирующий код ставит теги явно. «Противоречие» было выдумано — снято. |
| **CL-5** | Гранулярность `status` сущности? | **Канонично: нет специального поля `status`.** Состояние = компонент(ы)/маркер(ы) (`Pending`/`Applied`/`Failed(reason)`/`Skipped`/`Unverifiable`), которые System (`ReconcileEngine`) добавляет/снимает. ECS FAQ: «no special status fields — state exists only as component data that systems query and modify». Точная кодировка (`LifecycleState` sealed-компонент vs отдельные state-теги; `Failed` несёт данные → не чистый zero-data тег) финализируется в plan. |

---

## Контекст (что уже построено)

- TASK-120/127 построили модель, которую владелец назвал «tagged-component, ECS-inspired» (ADR-012). Её текущее состояние в коде:
  - `Entity(id, component: Component, wizardBehavior, critical, status, parentId)` — сущность несёт **ровно один** `Component` ([Profile.kt:16](../../core/src/commonMain/kotlin/com/launcher/preset/model/Profile.kt#L16)). Это **discriminated union**, не композиция.
  - `Component` — `sealed class` с полем `abstract val tags: Set<Tag>` и 11 подтипами (`AppTile`, `FontSize`, `Sos`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`, `Workspace`, `Flow`, `ToolbarButton`), каждый со своим default-набором тегов в конструкторе ([Component.kt](../../core/src/commonMain/kotlin/com/launcher/preset/model/Component.kt)).
  - `Blueprint(id, component: Component, wizardBehavior, critical, …)` — каталожная заготовка в `Pool`, тоже с **одним** `component` ([Pool.kt:6](../../core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt#L6)).
  - `ProfileFactory.create()` собирает `Profile` из `Preset` + `Pool`, спавня по одной `Entity` на запись, `applyOverride()` мержит `paramsOverride` в единственный `component` ([ProfileFactory.kt](../../core/src/commonMain/kotlin/com/launcher/preset/engine/ProfileFactory.kt)).
  - `ProfileQuery` — extension-функции (`byTag`, `children`, `flows`, `tilesOf`, `toolbarButtons`, `homeScreenTiles`), читающие `it.component.tags` ([ProfileQuery.kt:39](../../core/src/commonMain/kotlin/com/launcher/preset/query/ProfileQuery.kt#L39)).
  - `Tag` (13 значений), `ComponentStatus` (5, включая `Unverifiable`), `Outcome` (включая `NeedsUserConfirmation`), `ValidationError` (`DanglingParentRef`/`CircularParentRef`/`DanglingTargetRef`) — существуют в `core/preset/model/`.
  - Сериализация — `kotlinx.serialization` polymorphic с `classDiscriminator="type"`; `Profile.CURRENT_SCHEMA_VERSION = 2`.
- **Что меняется этим task'ом** (по Decision block TASK-136): сущность перестаёт быть «один Component» и становится **свободным мешком** компонентов (`components: List<Component>`), теги переезжают с `Component` на `Entity`, `Blueprint` становится **Bundle** (шаблон спавна, а не застывшая идентичность), `ProfileFactory` — «спавн» из бандла (плоский набор компонентов, без «extra»-концепции), запросы работают над мешком + типизированный `get<T>()`, статус выражается компонентом-маркером (нет поля `status`). Модель приобретает **канонический ECS** — композицию.
- **Почему сейчас**: приложение не выпущено, persisted-профилей в проде нет → цена смены формата = только переписывание кода, не миграция пользователей (rule 3 timing — самый дешёвый момент для one-way door).
- **Реальная движущая сила** (уточнено в clarify 2026-07-18): (1) **чистая каноническая модель** — переиспользовать продуманную индустриальную структуру (entity / component / tag / system / query) вместо ad-hoc переизобретения; (2) **композиция тегов** — сущность реально несёт несколько zero-data маркеров одновременно (`Tile` + facade-placement + settings-placement). Многокомпонентная композиция на уровне данных — бесплатная способность канонической модели. ⚠️ Ранняя формулировка «admin-lock как per-entity флаг `AdminLocked`» **ОТОЗВАНА**: admin-lock — это profile-level серверный edit-lock (TASK-70), показываемый между администраторами, а НЕ признак на отдельной сущности. См. `## Clarifications` (Correction A/B).

Грани решения зафиксированы в Decision block TASK-136 (verified против реальных движков — Fleks Snapshot + Bevy Bundle, не выдумано). Эта спека **проецирует** Decision, а не расширяет его.

---

## Scope (In / Out)

> Консолидация big-bang-границы (раньше была размазана по FR-016 / FR-021 / Assumptions — фикс requirements-quality CHK012). Одна когерентная правка, дёшево только пока pre-release (нет пользователей, нет persisted prod-данных).

### В scope (одна big-bang правка, FR-016)

- **Переписать форму модели**: `Entity` (один Component → свободный мешок `components: List<Component>` + `tags: Set<Tag>`, без поля `status`), `Component` (`sealed class` → `sealed interface`, убрать поле `tags` со всех подтипов), `Blueprint` (→ Bundle: набор компонентов + теги), `LifecycleState` (состояние как компонент вместо enum `ComponentStatus`).
- **Переписать движок и запросы**: `ProfileFactory` (спавн из бандла в плоский набор), `ReconcileEngine` (состояние через смену компонента-маркера), `ProfileQuery` (селекторы над `entity.tags` + `get<T>()`), `validateHierarchy`, `PresetValidator`, `PresetDiff`.
- **Собственный ECS-core** (~200-400 LOC, форма Fleks API) в новом пакете `preset/ecs/`.
- **Переписать всех потребителей на месте**: `ProfileBackedFlowRepository`, `WizardScreen`, `PostWizardKioskApply`, фикстуры, bundled-пресеты (`pool.json`, `bundled-presets/*.json`), тесты.
- **Cleanup inventory** (FR-017..FR-021): переписать `docs/architecture/preset-model.md`, написать `ADR-013` + header «Superseded by ADR-013» на `ADR-012`, проставить `superseded-by: TASK-136` на TASK-120/127.

### Вне scope

- **Мигратор / backward-compat / schemaVersion bump** — не пишем (Article XX pre-MVP; поле `schemaVersion` остаётся = 2, dev `ProfileStore` сбрасывается fresh-install'ом).
- **Правки downstream-спек** (TASK-69/71/68/19) — только notice/указатель «See TASK-136 Decision» через `dependencies:`, сами спеки не трогаем (FR-021).
- **`AdminLocked` / per-entity admin-lock** — не добавляется (CL-1 отозвано; admin-lock — profile-level, TASK-70).
- **Sharing UI / marketplace / import-from-file** — не строятся; сохраняется только шов `BundledSource`-as-one-of-many + `// TODO(shareability)` (rule 9).
- **Lenient reader** для незнакомых type/Tag — отдельный будущий шаг перед cross-device обменом (не здесь).
- **Индексация запросов, type-grouped storage, game-loop планировщик** — exit ramps, не MVP.
- **Изменение UI-контракта `FlowDescriptor` и экранов** — не трогается (проекция уже иерархична, наследие TASK-127).
- **Переезд на Fleks напрямую** — exit ramp (требует Kotlin 2.0→2.4 + String→Int id remap), не сейчас.

---

## Developer / Consumer Scenarios & Testing *(mandatory)*

> Формат адаптирован: вместо end-user stories — **capability stories** (что модель должна уметь). «Actor» — downstream-таск или разработчик. Каждый сценарий имеет независимый тест на уровне `core` (JVM/commonTest), без устройства.

### Capability Story 1 — Композиция: несколько маркеров + компонент навешивается на сущность любого типа (Priority: P0, foundational)

Модель должна поддерживать **композицию** на двух уровнях: (a) одна сущность несёт **несколько тегов-маркеров** одновременно (`Tile` + facade-placement + settings-placement — реальный, используемый случай), и (b) компонент-данные можно **добавить в мешок сущности любого типа** после её сборки, не меняя тип сущности и не добавляя поле в каждый подтип.

**Why this priority**: это **сама суть** канонической модели. Если сущность может нести только один маркер, или добавление нового признака требует правки каждого подтипа `Component` — мы остались в discriminated union и переход не состоялся. (Примечание: admin-lock здесь НЕ фигурирует — это profile-level серверный edit-lock TASK-70, не признак на сущности; см. `## Clarifications` Correction A.)

**Independent Test**: unit, две части.
- *Теги (реальный случай):* `Entity` несёт `Set<Tag>` из нескольких маркеров одновременно; `byAllTags(...)` / `byAnyTag(...)` находят её по любой комбинации — множественная маркировка одной сущности работает.
- *Компонент любого типа (test-only fake):* взять `Entity` с `AppTile` в мешке, добавить **тестовый** компонент `TestFlag` (fake, существует только в `commonTest`, доказывает механизм, не продуктовая фича) через операцию мешка → `entity.get<TestFlag>()` возвращает его, `entity.get<AppTile>()` по-прежнему возвращает `AppTile`. Проделать то же с `Entity`, несущей `Workspace` — тот же код, ни одного нового поля ни в одном подтипе.

**Acceptance Scenarios**:

1. **Given** собранная `Entity`, несущая несколько тегов-маркеров, **When** выполняются `byAllTags`/`byAnyTag`, **Then** сущность находится по любой валидной комбинации своих маркеров — множественная маркировка одной сущности работает (композиция тегов).
2. **Given** собранная `Entity` с одним компонентом-данными, **When** к её мешку композиционно добавляется тестовый компонент `TestFlag`, **Then** оба компонента доступны через `get<T>()`, тип сущности не изменился, ни один подтип `Component` не потребовал нового поля.
3. **Given** две сущности разных «базовых» типов (плитка и workspace), **When** к обеим добавлен один и тот же тестовый компонент `TestFlag`, **Then** запрос находит обе по этому компоненту — признак выражается один раз, а не N раз (композиция данных — бесплатная способность модели).

---

### Capability Story 2 — Запросы над мешком компонентов + типизированный доступ (Priority: P0)

Downstream-таск (TASK-69 Settings-as-Profile-View, TASK-71) должен уметь спрашивать профиль «какие сущности несут тег X / компонент типа T» и типобезопасно доставать компонент из сущности.

**Why this priority**: весь UI-слой строится на запросах. Если после смены формы `Entity` запросы ломаются или требуют кастов на каждом call-site — модель не выполняет свою роль.

**Independent Test**: unit — `Profile` из нескольких `Entity`; `byTag(Tag.Tile)` возвращает плитки; `entity.get<Component.Flow>()?.order` возвращает порядок; `byNotTag`, `byAllTags`, `byAnyTag` дают ожидаемые множества.

**Acceptance Scenarios**:

1. **Given** `Profile` с сущностями, несущими разные теги, **When** вызывается `byTag`/`byAllTags`/`byAnyTag`/`byNotTag`, **Then** результат совпадает с ожидаемым множеством (те же семантики, что и в TASK-127, но над `entity.tags`).
2. **Given** сущность с известным компонентом в мешке, **When** вызывается `inline fun <reified T> Entity.get(): T?`, **Then** возвращается типизированный компонент или `null`, без ручного `as?`.

---

### Capability Story 3 — Спавн из бандла: Blueprint как шаблон, не идентичность (Priority: P0, foundational)

`ProfileFactory` должен «спавнить» сущность из записи пресета: базовый бандл (`Blueprint`) + опциональные extra-компоненты + `paramsOverride` + `parentRef` → **свободный мешок**. После сборки бандл **забыт** — идентичность сущности не «Blueprint X», а её текущий набор компонентов.

**Why this priority**: если бандл ретейнится как тип сущности («это сущность-Blueprint-X»), мы заново получаем discriminated-union-жёсткость и admin-lock снова упирается в стену (ошибка, которую владелец поймал в OQ-3, verified против Bevy Bundle: «zero runtime significance after creation»).

**Independent Test**: unit — пресет-запись с бандлом из 2 компонентов + 1 extra → `ProfileFactory` даёт `Entity` с 3 компонентами в мешке; в собранной сущности нет ссылки на id бандла как на «тип»; запрос про сущность спрашивает «какие компоненты», не «каким бандлом сделана».

**Acceptance Scenarios**:

1. **Given** запись пресета с базовым бандлом и extra-компонентами, **When** `ProfileFactory` собирает профиль, **Then** результат — свободный мешок, `(Bundle, Extra)` схлопнуты, бандл после сборки не хранится как идентичность.
2. **Given** две записи из одного бандла, но с разными extra/override, **When** обе собраны, **Then** это две независимые сущности с разными мешками — общий бандл не связывает их идентичностью.

---

### Capability Story 4 — Сериализация ECS-мира делает roundtrip (Priority: P0)

Профиль (ECS-мир) должен сериализоваться в entity-grouped JSON (зеркало Fleks `Snapshot`) и десериализоваться обратно в идентичный мир.

**Why this priority**: профиль — wire-format (rule 5) и opaque blob для zero-knowledge sync (rule 13). Без надёжного roundtrip'а сохранение/загрузка/admin-push сломаны.

**Independent Test**: unit — `Profile` → JSON → `Profile`, assert equal. JSON-форма сущности: `{id, parentId, components:[{type,...},…], tags:[…]}`. Полиморфизм — kotlinx `classDiscriminator="type"`, ноль кастомного сериализатора.

**Acceptance Scenarios**:

1. **Given** `Profile` со смешанными сущностями (плитки, flow, toolbar-кнопки, сущность со сквозным компонентом), **When** он сериализован и прочитан обратно, **Then** получается эквивалентный `Profile` (roundtrip green).
2. **Given** сущность с несколькими компонентами в мешке, **When** она сериализована, **Then** компоненты пишутся как полиморфный список внутри сущности (entity-grouped), а не размазаны по type-таблицам.

---

### Capability Story 5 — Иерархия и системы переживают рефактор (Priority: P1)

Иерархические запросы (`workspace`, `flows`, `tilesOf`, `toolbarButtons`, `children`, `roots`) и «системы» (`ReconcileEngine` + `Provider`/`ProviderRegistry`) продолжают работать в новой форме без изменения своей роли.

**Why this priority**: TASK-127 уже построил иерархию (flat storage + `parentId`) и движок примирения. Переход на канонический ECS не должен их сломать — меняется только чтение (`it.component.tags` → `it.tags`, `it.component is X` → `it.get<X>()`).

**Independent Test**: unit — `Profile` с `Workspace` + 3 × `Flow` + плитками + `Toolbar` + 3 × `ToolbarButton`; `flows()` → 3 в порядке, `tilesOf(flowId)` → только свои плитки (минус `Failed`/`Skipped`), `toolbarButtons()` → 3 с валидными `targetFlowId`. Валидация ловит `DanglingParentRef`/`CircularParentRef`/`DanglingTargetRef`.

**Acceptance Scenarios**:

1. **Given** профиль с 2-уровневой иерархией, **When** вызываются иерархические селекторы, **Then** результаты совпадают с TASK-127-поведением (иерархия — свойство, сохранённое рефактором).
2. **Given** битый профиль (сирота / цикл / dangling target), **When** `ProfileFactory.validateHierarchy()`, **Then** возвращаются те же типизированные ошибки.

---

### Edge Cases

- **Сущность с пустым мешком компонентов** (`components = emptyList()`): валидное «пустое» состояние; ни один селектор её не находит; не crash. Fitness-правило: сущности, произведённые из бандлов, всегда непусты.
- **Два компонента одного типа в одном мешке**: **не допускается** — модель at-most-one-per-type (ECS-канон, CL-3). `get<T>()` возвращает единственный компонент или `null`; дубликат типа — нарушение инварианта, ловится fitness-тестом (FR-015d).
- **Незнакомый `type` компонента или незнакомое значение `Tag` в JSON**: fail-loud `SerializationException` у читателя (тот же честный контракт, что и TASK-127) — pre-release, cross-device ещё нет; lenient-reader — отдельный будущий шаг перед admin-push/preset-sharing.
- **Профиль без `Workspace`/`Flow`** (вырожденный simple-launcher, US-1 из TASK-127): `homeScreenTiles()` возвращает все плитки — тот же код, вырожденное дерево.
- **`schemaVersion` в JSON**: поле присутствует, номер **не двигается** (остаётся 2). Отсутствия миграции достаточно — pre-release чистим формат/фикстуры на месте.
- **Назначение тегов после сборки**: теги ставятся **явно** — бандлом при спавне и явной аугментацией композирующего кода (CL-4, как в Fleks). Авто-деривации тегов из компонентов нет; консистентность проверяется fitness-тестом (FR-015e).
- **apply-состояние без поля `status`**: состояние (`Pending`/`Applied`/`Failed(reason)`/`Skipped`/`Unverifiable`) выражается компонентом-маркером, который добавляет/снимает `ReconcileEngine` (FR-STATE); render gating читает его через мешок. `Failed` несёт `reason` → не чистый zero-data тег.

---

## Requirements *(mandatory)*

### Functional Requirements — canonical ECS shape

- **FR-001** (free-bag Entity): `Entity` MUST принять форму `Entity(id: String, components: List<Component>, tags: Set<Tag>, parentId: String? = null)` (+ bundle-declared метаданные `wizardBehavior`, `critical`, если ещё нужны). **Поля `status` НЕТ** — apply-состояние выражается компонентом/маркером (см. FR-STATE ниже, CL-5). Компоненты добавляются/снимаются **независимо после сборки** — это и есть механизм композиции. `parentId` остаётся flat storage, дерево вычисляется запросами (сохранено из TASK-127).

- **FR-002** (Component = closed set, без tags): `Component` MUST быть `sealed interface` с **закрытым набором** data class-подтипов. Поле `tags` MUST быть **удалено** из каждого подтипа (переезжает на `Entity`, FR-003). Закрытый набор сохраняет exhaustive `when` для сериализации + coverage-тестов (compile-time safety recovery). Закрытый набор подтипов = существующие **11** (`AppTile`, `FontSize`, `Sos`, `Toolbar`, `LauncherRole`, `Theme`, `Language`, `StatusBarPolicy`, `Workspace`, `Flow`, `ToolbarButton`). `AdminLocked` **НЕ добавляется** здесь: admin-lock — это profile-level серверный edit-lock (TASK-70), а не признак на сущности (CL-1 отозвано; см. `## Clarifications` Correction A). Композиция-на-уровне-данных доказывается **test-only** fake-компонентом `TestFlag` (Capability Story 1), а не продуктовым placeholder'ом. Новые подтипы добавляются additively (`@SerialName`) по мере реальной нужды.

- **FR-003** (Tag на Entity): теги MUST жить как `Set<Tag>` на `Entity` — **zero-data компоненты-маркеры** (ECS FAQ: «a tag is a component that has no data»), компактно кодированные как enum-множество (форма Fleks `Snapshot.tags` — отдельный список маркеров). Теги добавляются/снимаются **явно** (как в Fleks, который ЕСТЬ канонический ECS): бандл декларирует теги при спавне, композирующий код ставит теги явно. **Никакой авто-деривации** тегов из компонентов (CL-4). `Tag` остаётся тем же enum (13 значений, additive-only, rule 5). Запрос — по membership. Одна сущность может нести **несколько** тегов одновременно (композиция тегов, Capability Story 1).

- **FR-004** (Blueprint = Bundle): `Blueprint` MUST стать **Bundle** — именованным набором компонентов в `Pool`: `Blueprint(id, components: List<Component>, tags: Set<Tag>, wizardBehavior, critical, …)`. Bundle — **шаблон спавна ТОЛЬКО**, он **НЕ ретейнится как идентичность сущности** (verified: Bevy Bundle = «zero runtime significance after creation»). После спавна идентичность бандла исчезает.

- **FR-005** (ProfileFactory = spawn): `ProfileFactory` MUST реализовать «спавн»: запись пресета декларирует **набор компонентов** сущности — bundle-ref (`Blueprint`) разворачивается в компоненты, опционально плюс inline-компоненты, **всё схлопывается в один плоский набор** (нет привилегированного «base vs extra» — сущность есть просто её набор компонентов, CL-2) + `paramsOverride` + `parentRef` → свободный мешок `Entity`; бандл забыт после сборки. Остаётся лишь деталь записи: inline vs по ссылке (DRY-удобство) — финализируется в plan.

- **FR-006** (composition after assembly): модель MUST предоставлять операции добавления/снятия компонента у собранной `Entity` (напр. `Entity.with(component)` / `Entity.without<T>()` или эквивалент над `Profile`), не меняющие тип сущности и не требующие поля-на-подтип. Это операционализация Capability Story 1.

- **FR-007** (typed access): MUST существовать `inline fun <reified T : Component> Entity.get(): T?` (через `filterIsInstance` над мешком) для типобезопасного доступа без ручных кастов. Модель — **at-most-one-per-type** (ECS-канон, CL-3): в мешке не более одного компонента каждого типа, поэтому `get<T>()` однозначно возвращает единственный компонент или `null`. Инвариант обеспечивается fitness-тестом (FR-015).

- **FR-008** (Query API над мешком): `ProfileQuery` extension-функции MUST работать над `entity.tags` + `entity.components`: `query`, `byTag`, `byAllTags`, `byAnyTag`, `byNotTag` (переключаются с `it.component.tags` на `it.tags`), плюс селекторы по компонентам через `get<T>()`/`filterIsInstance`. Линейный проход (~20-40 сущностей).

- **FR-009** (иерархия сохранена): иерархические селекторы `children`, `roots`, `workspace`, `flows`, `tilesOf`, `toolbar`, `toolbarButtons`, `homeScreenTiles` MUST сохранить TASK-127-семантику, переведённые на новую форму `Entity` (чтение компонента через `get<T>()` вместо `it.component as? X`). Render gating (минус `Failed`/`Skipped`) сохраняется.

- **FR-010** (serialization, entity-grouped): сериализация MUST быть **entity-grouped, зеркало Fleks `Snapshot`**: `{id, parentId, components:[{type,...}], tags:[...]}`. MUST переиспользовать kotlinx polymorphic (`classDiscriminator="type"`) — **ноль кастомного сериализатора**. **НЕТ schemaVersion bump, НЕТ мигратора, НЕТ backward-compat теста** (pre-release clean-in-place, owner directive 2026-07-18). Поле `schemaVersion` MUST остаться (бесплатный шов, точка отсчёта в день релиза), номер **не двигается**. Zero-knowledge (rule 13) не задет: весь `Profile` — один opaque blob.

- **FR-011** (systems unchanged): «системы» MUST остаться существующими `ReconcileEngine` + `Provider`/`ProviderRegistry` в неизменной роли — **никакого game-loop планировщика систем** (в отличие от Fleks). Меняется только то, как они читают сущность (`get<T>()` / `filterIsInstance`).

- **FR-STATE** (status как компоненты, не поле): apply-состояние сущности MUST быть представлено **компонентом(ами)/маркером(ами) состояния**, которые переводит System (`ReconcileEngine`), а НЕ специальным полем `status` на `Entity` (CL-5, ECS-канон: «no special status fields — state exists only as component data that systems query and modify»). Набор состояний (тот же семантический, что старый `ComponentStatus`): `Pending` / `Applied` / `Failed(reason)` / `Skipped` / `Unverifiable`. `ReconcileEngine` добавляет/снимает эти маркеры по ходу примирения; запросы (render gating, FR-009) читают их через мешок. Замечание: `Failed` несёт данные (`reason`) → это НЕ чистый zero-data тег. Точная кодировка — единый `LifecycleState` sealed-компонент (несущий variant + reason) vs отдельные state-теги плюс `Failed`-компонент-данные — **финализируется в plan**.

- **FR-012** (own core, Fleks-shaped): реализация MUST быть **собственным маленьким core (~200-400 LOC), миррорящим форму API Fleks** (`world.entity {}`, `entity[Type]`, `world.family { all(...) }`-подобно), а НЕ Fleks напрямую (Fleks — игровой рантайм, требует Kotlin 2.4.0, мы на 2.0.21). Call-sites MUST быть Fleks-совместимы по словарю. MUST быть добавлен inline-шов `// TODO(ecs-fleks-migration): World internals swappable to Fleks; cost = Kotlin 2.0→2.4 upgrade + String→Int id remap; persistence stays ours` у core World.

- **FR-013** (id стабилен): id сущности MUST оставаться `String` (стабилен для сериализации + zero-knowledge, rule 13), в отличие от Fleks `Int`-index. При гипотетическом переезде на Fleks — ремап `String → Int` (входит в цену exit ramp, не бесплатен).

- **FR-014** (validation preserved): `ProfileFactory.validateHierarchy()` MUST сохранить типизированные ошибки `DanglingParentRef`, `CircularParentRef`, `DanglingTargetRef`, переведённые на чтение компонента через `get<T>()`.

- **FR-015** (fitness functions, rule 7): MUST существовать fitness-функции: (a) **coverage** — каждый подтип `Component` имеет serializer + (где применимо) `Provider`; (b) **import-guard** — движок/домен не тянут Android/vendor SDK (существующий `checklist-domain-isolation`); (c) **paramsOverride schema** roundtrip; (d) **at-most-one-per-type** — в мешке сущности не более одного компонента каждого типа (обеспечивает однозначность `get<T>()`, CL-3); (e) **tag-consistency** — теги назначаются явно бандлом при спавне + явной аугментацией композирующего кода, авто-деривации нет (CL-4).

### Functional Requirements — big-bang scope

- **FR-016** (big-bang rewrite): scope MUST быть **big-bang** — одна когерентная правка. «Наполовину» мигрировать форму `Entity` нельзя, держать две формы параллельно дороже. В одну правку входят: `Entity`, `Component` (sealed interface), `Blueprint` (bundle), `Tag`-читатели, `ProfileFactory` (спавн из бандла + extra), сериализация, все `ProfileQuery`-селекторы, **все потребители** (`ProfileBackedFlowRepository` + все `when(entity.component)` / `entity.component as? X` в UI и адаптерах), фикстуры, bundled-пресеты JSON, тесты.

### Cleanup / consistency requirements (pre-release clean-in-place, owner directive 2026-07-18)

> Правило владельца: **не накапливать «устаревшее с пометкой deprecated» — переписывать на месте до текущей правды**, потому что будущий AI-агент читает старый файл и уходит думать в отменённую сторону. Пока нет пользователей и persisted prod-data — чистим по максимуму. Это часть deliverable, а не «потом».

- **FR-017** (live docs rewrite): `docs/architecture/preset-model.md` (и `data-model.md`, если существует) MUST быть переписаны **на месте** в каноническое ECS-решение. Текст «мы НЕ канонический ECS» / «tagged-component / discriminated union» MUST быть удалён (иначе собьёт будущего агента). AI-TLDR блок обновляется под новую модель.

- **FR-018** (ADR): MUST быть написан **ADR-013** (canonical ECS adopted — разворачивает ADR-012 по двум основаниям: реальная composition-нужда admin-lock + pre-release timing). `ADR-012` MUST получить header «Superseded by ADR-013» (файл остаётся как история, rule 11).

- **FR-019** (superseded pointers): `TASK-120` и `TASK-127` MUST получить `superseded-by: TASK-136` во frontmatter (остаются как архив per rule 11, но агент перескакивает на актуальное). `TASK-136` frontmatter `decision-supersedes: [TASK-120, TASK-127]` уже проставлен.

- **FR-020** (code/fixtures/presets in place): код, wire-format-фикстуры, bundled-пресеты (`pool.json`, `bundled-presets/*.json`) MUST быть переписаны в ECS-форму **на месте** — без слоёв совместимости, без мигратора, без schemaVersion bump. `specs/task-127-ecs-foundation/*` MUST остаться как spec-архив TASK-127; TASK-136 получает собственную спеку (эту).

- **FR-021** (downstream contract notice): downstream-зависимости, ссылавшиеся на TASK-120/127 контракт (TASK-69, TASK-71, TASK-68, TASK-19), MUST получить ссылку на новый контракт (через `dependencies:` / «See TASK-136 Decision», rule 11 cross-task references). Правки самих downstream-спек — не в scope этой спеки; здесь — только notice/указатель.

### Non-Functional Requirements

- **NFR-001** (domain isolation): все новые/изменённые доменные типы (`Entity`, `Component` sealed interface, `Blueprint` bundle, core World) MUST быть pure Kotlin, zero Android/vendor imports (`checklist-domain-isolation`).
- **NFR-002** (query performance): запросы над мешком на MVP scale (~20-40 сущностей) MUST укладываться в < 1 мс (linear scan приемлем; indexing — exit ramp под масштаб).
- **NFR-003** (core size budget): собственный ECS-core MUST оставаться в бюджете ~200-400 LOC. Превышение бюджета — сигнал, что мы строим игровой рантайм (регресс к отвергнутому Fleks-use-case).
- **NFR-004** (no migration machinery): в кодовой базе MUST NOT появиться migration writer / backward-compat reader для профилей в рамках этого task'а (pre-release). Первый мигратор — post-release, при первом breaking change.

---

## Key Entities

> **Ментальная модель (ECS ≈ таблица БД)**: `Profile` = таблица (World); `Entity` = строка (id + **мешок компонентов** + теги + ссылка на родителя); `Component` = типизированные ячейки данных; `Tag` = метка для `WHERE`; `query` = `SELECT … WHERE`; `parentId` = внешний ключ; `ReconcileEngine` + `Provider` = «системы» (единственные, кто меняет мир — в т.ч. добавляют/снимают компонент-маркер состояния).

- **`Entity`** (переработан): `Entity(id, components: List<Component>, tags: Set<Tag>, parentId?, wizardBehavior?, critical?)`. **Свободный мешок** — компоненты навешиваются/снимаются независимо после сборки. Это ключевое отличие от TASK-127 (`component: Component`, один). **Поля `status` нет** — apply-состояние выражается компонентом-маркером (FR-STATE).
- **`Component`** (переработан): `sealed interface`, **закрытый набор** data class-подтипов, **без** поля `tags`. Exhaustive `when` для сериализации/coverage сохраняется.
- **`Tag`**: enum (13 значений), теперь живёт как `Set<Tag>` на `Entity`, а не на `Component`.
- **`Blueprint`** (переработан): **Bundle** — именованный набор компонентов + теги в `Pool`; шаблон спавна, НЕ идентичность сущности.
- **`ProfileFactory`**: «спавн» — запись пресета (бандл + extra + override + parentRef) → свободный мешок `Entity`.
- **Query API**: `query` + tag-селекторы (`byTag`/`byAllTags`/`byAnyTag`/`byNotTag`) над `entity.tags` + `get<T>()` над `entity.components` + иерархические селекторы (`children`/`roots`/`workspace`/`flows`/`tilesOf`/`toolbar`/`toolbarButtons`/`homeScreenTiles`).
- **ECS core (own)**: маленький `World` в форме Fleks API (swap-compatible), с inline-швом `TODO(ecs-fleks-migration)`.
- **`ReconcileEngine` + `Provider`/`ProviderRegistry`**: «системы» ECS, роль неизменна (нет game-loop планировщика).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: **Композиция работает** — (a) сущность несёт несколько тегов-маркеров сразу, `byAllTags`/`byAnyTag` находят её по комбинациям; (b) test-only fake-компонент `TestFlag` навешивается на `Entity` любого «базового» типа после сборки, `get<T>()` его находит, тип сущности не меняется, ни один подтип `Component` не получил нового поля ради этого. Unit-тест.
- **SC-002 [backlog]**: **Запросы работают** — `byTag`/`byAllTags`/`byAnyTag`/`byNotTag` над `entity.tags` + `get<T>()` над мешком дают ожидаемые результаты (AND/OR/NOT/empty/not-present). Unit-тесты.
- **SC-003**: **Сериализация делает roundtrip** — `Profile` → entity-grouped JSON → `Profile`, assert equal; компоненты пишутся как полиморфный список внутри сущности; ноль кастомного сериализатора. Unit-тест.
- **SC-004**: **Типизированный доступ** — `inline fun <reified T> Entity.get()` возвращает компонент/`null` без ручных кастов; ни одного `entity.component as? X` не осталось в коде. Grep + unit-тест.
- **SC-005 [backlog]**: **Иерархия и спавн сохранены** — профиль `Workspace` + 3 × `Flow` + плитки + `Toolbar` + 3 × `ToolbarButton` раскладывается запросами как в TASK-127; `ProfileFactory` спавнит свободный мешок из бандла + extra, бандл не ретейнится; валидация ловит `Dangling`/`Circular` ошибки. Unit-тесты.
- **SC-006**: **Coverage fitness** — каждый подтип `Component` имеет serializer (и `Provider`, где применимо); exhaustive `when` компилируется. Fitness-тест.
- **SC-007 [backlog]**: **Устаревших доков не осталось** — grep по live-докам (`docs/architecture/preset-model.md`, `data-model.md`) не находит «tagged-component» / «not canonical ECS» / «discriminated union» как описания текущей модели; `ADR-012` несёт header «Superseded by ADR-013»; `ADR-013` существует; `TASK-120`/`TASK-127` несут `superseded-by: TASK-136`.
- **SC-008**: **Import-guard fitness** — `checklist-domain-isolation` зелёный на `Entity`/`Component`/`Blueprint`/core World (zero Android/vendor imports).
- **SC-009**: **Никакой migration machinery** — `schemaVersion` поле присутствует, номер = 2 (не двигался); в кодовой базе нет migration writer / backward-compat reader для профилей. Grep.
- **SC-010 [backlog]**: **Big-bang согласован** — после переписывания все потребители (`ProfileBackedFlowRepository`, UI-callsites, фикстуры, bundled-пресеты) компилируются, `:core` тесты зелёные; нет параллельно живущих двух форм `Entity`.
- **SC-011**: **Core в бюджете** — собственный ECS-core ≤ ~400 LOC; `TODO(ecs-fleks-migration)` шов присутствует на World.

---

## Assumptions

- **Decision block TASK-136 — authority**: эта спека проецирует его; OQ-1..OQ-7 закрыты и **не переоткрываются** здесь. Открытые ранее пункты CL-1..CL-5 закрыты каноном ECS в clarify-сессии 2026-07-18 (см. `## Clarifications`); открытых clarify-пунктов не осталось.
- **Существующая инфраструктура переживает рефактор**: `Tag` (13), 5 apply-состояний (`Pending`/`Applied`/`Failed`/`Skipped`/`Unverifiable` — бывший `ComponentStatus`, теперь выражены компонентом-маркером, FR-STATE), `Outcome` (`NeedsUserConfirmation`), `ValidationError` (`Dangling`/`Circular`/`DanglingTargetRef`), `ReconcileEngine`, `Provider`/`ProviderRegistry`, порт `FlowRepository` — остаются; меняется форма `Entity` и способ хранения состояния (поле → компонент).
- **Pre-release**: нет пользователей, нет persisted prod-профилей → смена формата = только code-refactor; dev `ProfileStore` можно сбросить.
- **`schemaVersion` = 2, не двигается**: `tags`-на-Entity, `components: List` и bundle-форма — переписываются на месте, не через миграцию.
- **UI-контракт `FlowDescriptor` не меняется**: проекция ECS-мира в `List<FlowDescriptor>` уже иерархична (наследие TASK-127) — здесь не трогается.

---

## OEM / Device Matrix

**Not applicable.** Это чистый доменный / модельный рефактор в `core/commonMain` (pure Kotlin). Никакого device-specific behaviour, permission-flow, OEM-quirk или HOME-role-взаимодействия он не вводит и не меняет. OEM-матрица не заполняется намеренно — фабриковать её было бы шумом.

---

## Local Test Path

- **Composition + query:** `./gradlew :core:test --tests "*EntityCompositionTest*"` — навешивание/снятие компонентов, `get<T>()`, tag-селекторы над `entity.tags`.
- **Spawn:** `./gradlew :core:test --tests "*ProfileFactoryTest*"` — спавн свободного мешка из бандла + extra + override; бандл не ретейнится.
- **Serialization roundtrip:** `./gradlew :core:test --tests "*ProfileSerializationRoundtripTest*"` — `Profile` → entity-grouped JSON → `Profile`, assert equal; fail-loud на незнакомом type/Tag.
- **Hierarchy + validation:** `./gradlew :core:test --tests "*ProfileQueryTest*"` и `"*ValidateHierarchyTest*"`.
- **Fitness:** `./gradlew :core:test --tests "*ComponentCoverageFitnessTest*"` (serializer/Provider coverage), `checklist-domain-isolation` (import-guard).
- **Consistency (cleanup):** grep-проверки SC-007/SC-009 — устаревших описаний в live-доках нет; `superseded-by` проставлены; migration machinery отсутствует.
- **Device:** не требуется (см. OEM / Device Matrix).

---

## AI Affordance

Как AI-агент/downstream-сессия должен использовать результат этого task'а:

- **Одна точка правды по модели** — `docs/architecture/preset-model.md` (AI-TLDR первым) после FR-017-переписывания. Если TL;DR отвечает на вопрос — глубже не читать.
- **Контракт для downstream** (TASK-69/71/68/19, TASK-70/103) — Decision block TASK-136 (English, immutable после старта implementation). Читать Decision, а не весь Discussion.
- **Anti-drift**: `TASK-120`/`TASK-127` несут `superseded-by: TASK-136` и `ADR-012` — header «Superseded by ADR-013»; агент, наткнувшись на них, обязан перескочить на TASK-136/ADR-013, а не действовать по их содержимому.
- **Как добавить компонент**: добавить подтип в `sealed interface Component` (additive, `@SerialName`), назначить теги **явно** на уровне бандла/сущности (CL-4, авто-деривации нет), покрыть coverage-fitness. Никаких правок в каждый существующий подтип.
- **Как навесить сквозной признак на уровне данных**: добавить компонент в мешок нужных сущностей — не заводить поле в подтипах. (Admin-lock сюда НЕ относится — это profile-level lock TASK-70, не признак на сущности.)

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Кратко по-русски (для владельца и будущего AI)

**Что делаем.** Меняем **фундамент** настройки лаунчера. Сейчас каждая «сущность» (плитка, вкладка, кнопка) — это **ровно один тип из меню**, данные вшиты в тип. Это удобно, пока всё простое, но не даёт **композиции**: сущность не может нести несколько независимых признаков-маркеров сразу, а новый признак приходится вшивать в каждый тип.

**Зачем на самом деле ECS** (уточнено с владельцем 2026-07-18): (1) взять **чистую, продуманную индустриальную модель** (сущность / компонент / тег / система / запрос) и перестать каждый раз переизобретать структуру заново; (2) реальная **композиция тегов** — одна сущность уже несёт несколько маркеров одновременно (например «плитка» + «в фасаде» + «в настройках»). Это используется. Многокомпонентная композиция на уровне данных — приятный бесплатный бонус модели, а не следствие одной конкретной фичи.

**Про admin-lock (исправление моей ошибки).** Раньше я приводил admin-lock как «навесной флаг на любую сущность» — это **неверно**. Admin-lock — это блокировка **на уровне всего профиля, на сервере**: администратор скачивает профиль бабушки, ставит на сервере пометку «профиль сейчас редактируется», правит, загружает обратно; пометку видит **другой администратор** (чтобы двое не редактировали разом). Бабушке никакого «замочка на настройке» не показывается — её **фасад** (курируемый передний экран) вообще не показывает настройки: настройки (например шрифт) живут **за** фасадом. Admin-lock живёт в отдельной задаче TASK-70, не в этой модели.

**Как решаем.** Переходим на **канонический ECS** (это индустриальный подход, как в игровых движках Bevy/Fleks и в самом Android-лаунчере). Сущность становится **мешком компонентов**: базовые данные + сколько угодно навесных признаков и тегов-маркеров, которые вешаются и снимаются **отдельно, после сборки**.

**Мысленная модель — таблица базы данных.** Профиль = таблица; сущность = строка; компоненты = ячейки данных; теги = метки для «выбрать где…»; запрос = «SELECT … WHERE»; `parentId` = ссылка на родителя (дерево вычисляется, не хранится вложенно). «Системы» (движок примирения + провайдеры) — единственные, кто меняет мир.

**Почему сейчас.** Приложение не выпущено, живых профилей нет — сменить формат стоит только «переписать код», не «переселять пользователей». Это самый дешёвый момент. После релиза было бы в разы дороже.

**Важные решения (уже приняты в Decision-блоке, verified против реальных движков):**
- Пишем **свой маленький движок (~200-400 строк)** в форме API Fleks, а не берём Fleks напрямую (Fleks игровой и требует новее Kotlin). Форму API мирроим, чтобы при желании можно было переехать — это запасной выход.
- **Blueprint** (заготовка в каталоге) — это **шаблон для рождения** сущности, а не её застывший тип. После сборки заготовка забыта — сущность живёт своим мешком. (Ошибку «заготовка = тип» владелец поймал и отверг — иначе снова получилась бы жёсткая модель без композиции.)
- **Теги переезжают** с компонента на сущность (так делает и Fleks в своём Snapshot).
- **Никакого мигратора и никакого bump'а версии** — чистим формат, фикстуры и пресеты на месте (pre-release). Поле версии остаётся как бесплатный задел.
- **Чистим устаревшее до конца**: переписываем доки и ADR на месте, старым таскам TASK-120/127 ставим пометку «заменено TASK-136». Причина: будущий AI-агент не должен читать отменённое решение и уходить думать в неправильную сторону.

**Что осталось уточнить.** Ничего — все ранее открытые вопросы (CL-1..CL-5) **закрыты каноном ECS** в clarify-сессии 2026-07-18 (см. раздел `## Clarifications`):
1. `AdminLocked` — **не** фиксируем (это profile-level lock TASK-70); композицию показываем тегами + тестовым fake-компонентом.
2. «Base vs extra» в пресете — понятия нет: сущность есть просто плоский набор компонентов.
3. Два компонента одного типа — **нельзя** (at-most-one-per-type); `get<T>()` однозначен.
4. Теги назначаются **явно** (бандл при спавне + композирующий код), без авто-деривации.
5. Статус — **не поле**, а компонент-маркер, который двигает движок примирения (точная кодировка — в plan).

**На что смотреть с осторожностью:** это **one-way door** — переписываем весь фундамент разом (big-bang). Дёшево только пока не выпустились. Запасной выход (переезд на Fleks) описан, но не бесплатен (обновление Kotlin + ремап id).
<!-- NOVICE-SUMMARY:END -->
