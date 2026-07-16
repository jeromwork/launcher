# Feature Specification: Pool Blueprint enrichment — Component Library canonical UI metadata

**Feature Branch**: `task-129-pool-component-declaration`
**Created**: 2026-07-16
**Status**: Draft
**Input**: TASK-129 «Pool ComponentDeclaration enrichment — Component Library canonical UI metadata»

## Контекст: что изменилось с момента написания задачи

Задача TASK-129 написана 2026-07-14, до вливания TASK-126 и TASK-127. Спека
описывает **фактическое** состояние кода на `main` (c961504), а не постановку задачи:

| Постановка TASK-129 | Фактическое состояние | Следствие для спеки |
|---|---|---|
| модель `ComponentDeclaration` | модель называется `Blueprint` (`core/preset/model/Pool.kt`) | спека оперирует `Blueprint` |
| `pool.json` schemaVersion 1→2 | v2 уже занят TASK-126 (`requires` / `required`) | наш bump — **2→3** |
| 4 компонента в pool | 9 записей (ECS добавил `ws-main`, `flow-main`, `flow-apps`, `btn-main`, `btn-apps`) | scope покрытия — см. FR-008 |
| добавить `i18nKey` | `descriptionKey` уже существует в `Blueprint` | конфликт схем именования — см. FR-002 |

Дополнительно обнаружено при обследовании: **ни одного ключа `pool.*` не существует
в строковых ресурсах**. `AndroidLocalizedResources.resolve()` при промахе возвращает
сам ключ, поэтому пользователь на экране видит буквальный текст `pool.font.description`.
Существующий fitness-тест `WireFormatI18nKeysTest` проверяет только *форму* ключа
(dotted identifier), но не его *покрытие* в ресурсах — поэтому дыра не была поймана.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Builder получает канонические заголовок и иконку компонента (Priority: P1)

`LauncherPresentationBuilder` (TASK-127) строит домашний экран из Profile. Для каждого
слота ему нужны иконка и заголовок. Сегодня их негде взять: Pool знает про компонент
только правила (critical, wizardBehavior, параметры), но не знает, как его *показать*.
Builder вынужден либо hardcode'ить по типу компонента, либо оставлять пусто.

После изменения Builder читает `Blueprint.icon` и ключ заголовка — одинаково для любого
компонента, без ветвления по типу.

**Why this priority**: это единственная причина существования задачи — без неё
TASK-127 остаётся с hardcoded fallback'ами, а TASK-69 (Settings as Profile View)
не может стартовать.

**Independent Test**: unit-тест на `Blueprint` + чтение обогащённого `pool.json`;
проверяем что для каждого слота доступны icon и ключ заголовка. Device не нужен.

**Acceptance Scenarios**:

1. **Given** `pool.json` v3 с заполненными `icon` и ключами, **When** Builder запрашивает
   Blueprint по id, **Then** получает icon и ключ заголовка без обращения к типу компонента.
2. **Given** Blueprint без `icon` (поле опущено), **When** Builder запрашивает его,
   **Then** получает `null` и применяет собственный fallback — чтение не падает.

---

### User Story 2 — Пользователь видит человеческий текст вместо ключа (Priority: P1)

Сегодня, если компонент показывает описание, на экране появляется `pool.font.description` —
потому что строки с таким именем нет. Пользователь видит техническую строку.

После изменения каждый ключ, объявленный в `pool.json`, имеет строку в ресурсах,
а CI-гейт не даёт добавить компонент без строк.

**Why this priority**: это существующий пользовательский дефект, а не будущая
оптимизация; закрывается тем же изменением, что и US1.

**Independent Test**: `PoolI18nCoverageTest` — читает `pool.json`, собирает все ключи,
проверяет наличие каждого в строковых ресурсах. Robolectric/JVM, без устройства.

**Acceptance Scenarios**:

1. **Given** `pool.json` объявляет ключ `pool.font.description`, **When** прогоняется
   `PoolI18nCoverageTest`, **Then** тест зелёный только если строка существует.
2. **Given** разработчик добавил в pool компонент без строк, **When** прогоняется CI,
   **Then** сборка падает с указанием отсутствующего ключа.

---

### User Story 3 — Старый pool.json продолжает читаться (Priority: P2)

Профиль/pool могут прийти от предыдущей версии приложения (bundled asset сейчас
физически `schemaVersion: 1`). Чтение не должно падать.

**Why this priority**: rule 5 обязателен, но это невидимый пользователю инвариант —
ниже приоритетом, чем два user-visible сценария выше.

**Independent Test**: roundtrip + backward-compat тесты на JVM, по образцу
существующего `PoolSchemaV1ReadV2Test`.

**Acceptance Scenarios**:

1. **Given** pool v1 JSON, **When** он декодируется текущей моделью, **Then** новые
   поля получают defaults, ошибки нет.
2. **Given** pool v2 JSON, **When** он декодируется, **Then** `requires`/`required`
   сохраняются, новые поля — defaults.

### Edge Cases

- Ключ объявлен в `pool.json`, строки нет → CI-гейт падает (US2), в рантайме —
  текущий fallback «вернуть сам ключ», поведение не меняем.
- Компонент объявляет `supportedPlatforms`, не включающий текущее устройство →
  [NEEDS CLARIFICATION: поле только описательное (метаданные для будущего фильтра)
  или Builder обязан скрывать такой компонент уже сейчас? Второе = поведенческое
  изменение и расширение scope].
- Незнакомое поле в pool.json от более новой версии → уже покрыто `ignoreUnknownKeys`;
  строгое поведение для незнакомых *типов* — территория TASK-131, не эта задача.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `Blueprint` MUST получить опциональные поля UI-метаданных; все поля
  nullable/с defaults, чтобы pool v1 и v2 декодировались без ошибок.
- **FR-002**: Схема именования ключей MUST быть единой.
  [NEEDS CLARIFICATION: у Blueprint уже есть `descriptionKey`. Варианты: (a) добавить
  `i18nKey` как префикс-базу, из которой выводятся `.title`/`.description`/`.help`,
  и признать `descriptionKey` устаревшим — требует миграции существующих ключей;
  (b) не вводить `i18nKey`, добавить рядом явные `titleKey` / `helpKey` по образцу
  существующего `descriptionKey` — без миграции, но поля перечисляются вручную.
  Выбор влияет на FR-003 и на fitness-тест].
- **FR-003**: `pool.json` MUST нести `schemaVersion: 3`; модель MUST читать v1, v2, v3.
- **FR-004**: MUST существовать migration writer v2→v3, не изменяющий существующие поля.
- **FR-005**: Bundled `pool.json` MUST быть обновлён до v3 (сейчас физически v1).
- **FR-006**: Каждый ключ, объявленный в bundled `pool.json`, MUST иметь строку
  в ресурсах на языке EN.
- **FR-007**: MUST существовать fitness function (CI gate), проверяющая FR-006 —
  покрытие ключей, а не только их форму.
- **FR-008**: Покрытие компонентов.
  [NEEDS CLARIFICATION: задача называет 4 компонента (`font-tile`, `tile-whatsapp`,
  `sos-main`, `toolbar-minimal`), но в pool сейчас 9. Гейт FR-007 по построению
  требует покрыть все объявленные ключи — иначе он красный. Значит либо покрываем
  все 9, либо гейт скоупится на подмножество (что делает его дырявым). Рекомендация:
  все 9].
- **FR-009**: `icon` MUST быть строковым идентификатором (не ссылкой на drawable
  ресурс) — drawable-ассеты вне scope, Builder резолвит идентификатор сам.
- **FR-010**: Место загрузки pool MUST нести inline `// TODO(web-panel)` seam.
- **FR-011**: Изменения MUST NOT затрагивать Provider и ReconcileEngine.

### Key Entities

- **Blueprint**: запись Pool о компоненте. Сегодня — правила применения (`critical`,
  `wizardBehavior`, `requires`, `required`) + `descriptionKey`. Эта задача добавляет
  канонические UI-метаданные: иконка, заголовок, категория, help, платформы, действия.
- **Pool**: набор Blueprint + `schemaVersion`. Становится Component Library —
  единственным источником правды о том, что такое каждый компонент.
- **Строковые ресурсы**: EN-значения для ключей, объявленных в Pool. RU отложен.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 [backlog]**: Pool несёт для каждого объявленного компонента канонические
  UI-метаданные (иконка + ключ заголовка), доступные Builder'у без ветвления по типу.
- **SC-002 [backlog]**: Пользователь не видит технических ключей вида
  `pool.font.description` — каждый объявленный ключ имеет EN-строку.
- **SC-003 [backlog]**: Добавление компонента в Pool без строк роняет CI —
  дыра не может доехать до пользователя незамеченной.
- **SC-004 [backlog]**: `pool.json` предыдущих версий (v1, v2) продолжает читаться
  без ошибок после перехода на v3.
- **SC-005**: Backward-compat тесты v1→v3 и v2→v3 зелёные (по образцу
  `PoolSchemaV1ReadV2Test`).
- **SC-006**: Roundtrip-тест: pool v3 → parse → serialize → эквивалентен исходному.
- **SC-007**: `BundledAssetsLoadTest` (TASK-120) остаётся зелёным на v3 asset'е.
- **SC-008**: Существующий `WireFormatI18nKeysTest` (fitness #10) остаётся зелёным —
  новые ключи имеют dotted-форму.

## Assumptions

- Blueprint — та же сущность, что в задаче названа `ComponentDeclaration`;
  переименование произошло в ECS-волне, семантика не изменилась.
- Bundled pool остаётся ассетом; remote pool update — территория TASK-33.
- RU-локализация отложена (TODO(i18n)), EN достаточно для гейта.
- Drawable-ресурсы иконок не входят; строковый идентификатор достаточен для Builder'а.
- `LauncherPresentationBuilder` из TASK-127 присутствует на ветке (main c961504) —
  проверено, ECS-код влит через PR #52.

## Local Test Path *(mandatory)*

- **Emulator / device**: не нужен — логика, wire format и fitness проверяются на JVM.
- **Fake adapters used**: не требуются; тесты читают bundled asset напрямую
  и оперируют моделью `Pool` / `Blueprint`.
- **Fixtures / seed data**: inline JSON-фикстуры v1 / v2 / v3 в тестах
  (образец — `core/src/commonTest/.../PoolSchemaV1ReadV2Test.kt`);
  bundled `app/src/main/assets/preset/pool.json`.
- **Verification command**: `./gradlew :core:test` (модель, миграция, roundtrip,
  backward-compat) + `./gradlew :app:testDebugUnitTest` (покрытие ключей,
  `BundledAssetsLoadTest`).
- **Cannot-test-locally gaps**: none. Device verification не требуется —
  задача не меняет рантайм-поведение на устройстве.

## AI Affordance *(mandatory)*

- **Exposable capabilities**: `describeComponent(blueprintId) → {title, description,
  icon, category, help}` — доменный глагол чтения Component Library. Позже AI-агент
  мог бы объяснять пользователю, что делает компонент, или подбирать компоненты
  по категории: `listComponents(category)`.
- **Required affordances on data**: read-only по Pool. PII отсутствует по построению —
  Pool описывает типы компонентов, а не данные пользователя.
- **Provider-agnostic shape**: Pool — доменная модель в `commonMain`, без vendor-типов;
  чтение идёт через существующий port. Ни Gemini/OpenAI/Claude типов, ни MCP.
- **Out of scope for this spec**: реализация провайдера, промпты, телеметрия,
  Capability Registry (TASK-33).

## OEM Matrix

Not applicable — задача чисто на уровне wire format, доменной модели и строковых
ресурсов; поведение на устройстве не затрагивается, background work / permissions /
launcher role не задействованы.

## Open Questions для `/speckit.clarify`

1. FR-002 — `i18nKey` как база с суффиксами против явных `titleKey` / `helpKey`
   рядом с существующим `descriptionKey`.
2. FR-008 — покрываем все 9 компонентов или подмножество из 4.
3. Edge case — `supportedPlatforms` описательное или поведенческое.
4. Задача перечисляет поля `category`, `helpUrl`, `actions`, `validationKey`.
   Ни один известный потребитель их сегодня не читает (TASK-69 Settings — ещё Draft).
   Добавлять сейчас против rule 4 (MVA: абстракция только если её отсутствие
   заставит переписывать)? Или ограничиться тем, что нужно TASK-127 сейчас,
   а остальное добавить additively, когда TASK-69 стартует?
