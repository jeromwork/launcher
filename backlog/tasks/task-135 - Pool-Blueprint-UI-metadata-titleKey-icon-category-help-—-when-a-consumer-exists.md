---
id: TASK-135
title: >-
  Pool Blueprint UI metadata (titleKey / icon / category / help) — when a
  consumer exists
status: Draft
assignee: []
created_date: '2026-07-16 18:13'
labels:
  - phase-2
  - preset
  - pool
  - deferred
milestone: m-1
dependencies:
  - TASK-69
priority: low
ordinal: 135000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Pool (`pool.json`) — каталог компонентов. Сегодня запись каталога (`Blueprint`)
описывает **правила**: обязателен ли компонент, как ведёт себя в мастере, какие
у него параметры. Она не описывает, **как показать компонент человеку**: нет
канонического заголовка, иконки, категории, справки.

Когда появится экран, показывающий список компонентов (Settings как вид профиля —
TASK-69, либо будущий Builder домашнего экрана), ему понадобится: «как называется
этот компонент», «какой иконкой его рисовать», «в какую группу класть».

Сейчас этих данных нет, и **брать их некому** — поэтому задача отложена.

## Зачем

Отделено от TASK-129 сознательно. Исходная TASK-129 просила добавить эти поля
немедленно, ссылаясь на `LauncherPresentationBuilder` из TASK-127. При обследовании
кода 2026-07-16 выяснилось, что такого класса **не существует**, а домашний экран
берёт заголовок и иконку **из компонента профиля** (`labelKey`, `iconKey`), а Pool
не читает вовсе. То есть потребителя у полей нет ни одного.

Добавлять их «на будущее» — прямое нарушение rule 4 (Minimum Viable Architecture)
и refuse pattern #9 (single-implementation abstraction без потребителя): пришлось бы
поднять версию схемы и написать миграцию ради кода, которого нет.

Ключевое: добавление этих полей позже — **additive** изменение. Ничего не придётся
переписывать, поэтому откладывать безопасно (мета-правило «откладываем, если потом
это дописывание, а не переписывание»).

## Что входит технически (для AI-агента)

Когда появится потребитель:

- `Blueprint` (`core/src/commonMain/kotlin/com/launcher/preset/model/Pool.kt`) получает
  опциональные поля. Кандидаты из исходной постановки: `titleKey`, `icon`,
  `category`, `helpUrl`, `supportedPlatforms`, `actions`, `validationKey`.
  **Добавлять только то, что потребитель реально читает**, не весь список скопом.
- Схема `pool.json`: **v2 → v3** (v1 — исходная, v2 занял TASK-126 под
  `requires` / `required`). Нужен migration writer + backward-compat тест
  (образец: `PoolSchemaV1ReadV2Test`) + контракт `contracts/pool-schema-v3.md`
  (rule 5 — wire format).
- **Схема ключей — явные поля** (`titleKey` рядом с существующим `descriptionKey`),
  НЕ база с суффиксами (`i18nKey` + `.title`/`.description`/`.help`). Решение
  владельца 2026-07-16: база потребовала бы миграции существующих ключей, и связь
  «ключ ↔ строка» перестала бы грепаться. См. Clarifications в
  [`specs/task-129-pool-component-declaration/spec.md`](../../specs/task-129-pool-component-declaration/spec.md).
- **`icon` резолвится через порт `IconStorage`**, а не через `LocalizedResources`:
  формат `bundled:<name>` → drawable (`BundledIconStorage`, спека 006,
  `contracts/icon-id-namespace.md`). Иконка — не текст и переводу не подлежит.
- Новые текстовые ключи автоматически попадают под гейт `PoolI18nCoverageTest`
  (TASK-129): EN + RU обязательны, иначе сборка красная.
- Переводы — AI-агентом по мере надобности; носитель языка причёсывает позже.
  В автоматический конвейер на 9 языков (`procedure-translate-spec-strings`,
  работает с `core/composeResources`) pool-строки **не** входят.

## Что НЕ входит

- Drawable-ассеты для новых иконок — отдельный UX-pass.
- `catalog-i18n-{locale}.json` для web panel — TODO(web-panel), нужен при TASK-34.
- Remote pool update — TASK-33.

## Смежное, замеченное при обследовании TASK-129

- **`iconKey` в `Component.AppTile` — псевдо-i18n-ключ.** В `pool.json` лежит
  `"iconKey": "pool.tile.whatsapp.icon"`, `ProfileBackedFlowRepository` передаёт его
  в `iconRef` **без резолва**, а EN-строка `pool_tile_whatsapp_icon = "whatsapp"`
  мертва — её никто не читает. Наследие TASK-120. Чинить — значит трогать
  `AppTileProvider` / `HomeScreenFacade` / `SlotDescriptor`. Разумно сделать это
  вместе с введением `icon` через `IconStorage`, чтобы у иконок остался один
  механизм, а не два.

## Состояние

**Draft.** Разблокируется, когда появится реальный потребитель — TASK-69 (Settings
as Profile View) либо настоящий Builder домашнего экрана. Заведена как выделенная
часть TASK-129 (решение владельца 2026-07-16).

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] Добавлены только те поля Blueprint, которые реально читает появившийся потребитель — не весь список из исходной постановки
- [ ] #2 [hand] pool.json переведён на schemaVersion=3; v1 и v2 продолжают читаться без ошибок (backward-compat тест)
- [ ] #3 [hand] Контракт contracts/pool-schema-v3.md написан до изменения формата (rule 5)
- [ ] #4 [hand] icon резолвится через IconStorage (bundled:<name>), не через строковые ресурсы
- [ ] #5 [hand] Новые текстовые ключи покрыты EN и RU — PoolI18nCoverageTest зелёный
<!-- AC:END -->
