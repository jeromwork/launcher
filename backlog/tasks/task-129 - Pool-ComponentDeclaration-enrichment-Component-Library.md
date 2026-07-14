---
id: TASK-129
title: 'Pool ComponentDeclaration enrichment — Component Library canonical UI metadata'
status: Draft
assignee: []
created_date: '2026-07-14'
updated_date: '2026-07-14'
labels:
  - phase-2
  - foundation
  - preset
  - pool
  - component-library
milestone: m-1
dependencies:
  - TASK-120
priority: medium
ordinal: 129000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Сейчас Pool (`pool.json`) описывает компоненты только с точки зрения **правил**: кто critical, какой wizardBehavior, какие параметры. Но Pool ничего не знает о том, **как объяснить компонент пользователю**: нет иконок, нет заголовков, нет help-текста, нет информации о платформах.

По шагам (нормальный сценарий):
1. LauncherPresentationBuilder (из TASK-127) создаёт экран из Profile.
2. Ему нужна иконка для кнопки HomeRole, заголовок, описание для Settings-карточки.
3. Сейчас ему негде взять эти данные — приходится hardcode или угадывать по типу Component.
4. После этой задачи: `ComponentDeclaration` в `pool.json` содержит `i18nKey`, `icon`, `category`, `helpUrl`, `supportedPlatforms`, `actions` — всё что нужно Builder'у.

Что происходит при добавлении нового компонента в Pool:
- Разработчик добавляет запись в `pool.json` с новыми полями.
- `strings.xml` получает ключи `comp.<id>.title`, `comp.<id>.description`, `comp.<id>.help`.
- CI fitness function проверяет что все i18nKey из pool.json покрыты в strings.xml.
- Ни LauncherPresentationBuilder, ни другие Builder'ы не меняются — они читают поля через ComponentDeclaration.

## Зачем

- LauncherPresentationBuilder (TASK-127) строит ConfigDocument из Profile — ему нужны иконки и заголовки для слотов.
- SettingsPresentationBuilder (TASK-69) строит Settings-экран — ему нужны category, description, helpUrl.
- WizardPresentationBuilder уже частично закрыт через `wizardFlow[i].wizardTitle` в Preset, но canonical fallback нужен.
- Pool становится **Component Library** — одним источником правды о том, что такое каждый компонент.

## Что входит технически (для AI-агента)

**Domain (core/preset/model/):**
- `ComponentDeclaration` получает новые nullable поля: `i18nKey: String?`, `icon: String?`, `category: String?`, `helpUrl: String?`, `supportedPlatforms: List<Vendor>?`, `actions: List<String>?`, `validationKey: String?`
- Все поля nullable для backward-compat: старые записи pool.json без этих полей парсятся без ошибок

**Wire format (rule 5):**
- `pool.json` schemaVersion `1 → 2`
- Migration writer: `PoolMigration.v1ToV2(pool: JsonObject): JsonObject` — добавляет `schemaVersion: 2`, не трогает существующие поля
- Backward-compat test: pool.json schemaVersion=1 читается текущим кодом с defaults

**Assets:**
- `app/src/main/assets/preset/pool.json` — обновить 4 существующих записи: добавить `i18nKey`, `icon`, `category` для `font-tile`, `tile-whatsapp`, `sos-main`, `toolbar-minimal`
- `app/src/main/res/values/strings_pool.xml` — новый файл с ключами `comp.font_tile.title`, `comp.font_tile.description`, `comp.font_tile.help`, аналогично для 3 остальных компонентов

**Fitness function (rule 7):**
- `PoolI18nCoverageTest`: grep pool.json → собрать все i18nKey → проверить что каждый ключ + суффиксы `.title`/`.description`/`.help` есть в strings_pool.xml. CI gate.

**Web Panel stub (forward-compat seam):**
- `// TODO(web-panel): catalog-i18n-{locale}.json для admin web panel — serve from CDN; pool.json i18nKeys = source of truth`
- Файл не создаём сейчас — только TODO

## Что НЕ входит (deferred)

- `catalog-i18n-{locale}.json` для Web Panel — TODO(web-panel), нужен при TASK-34 (clinic B2B)
- Remote pool update (TASK-33 Capability Registry) — pool.json остаётся bundled asset
- Русская локализация strings_pool.xml — добавить EN, RU deferred (TODO(i18n))
- Icon resource files (drawable XML) — placeholder strings достаточно для Builder; drawable assets добавляются при UX pass

## Состояние

**Draft.** Не блокирует TASK-127 (LauncherPresentationBuilder может работать с hardcoded fallbacks пока Pool не обогащён). Разблокируется после TASK-120 Done (выполнено).

---

## Готовый промт для `/speckit.specify`

```
ЧТО СТРОИМ:
Pool ComponentDeclaration enrichment — добавить canonical UI metadata поля
в pool.json ComponentDeclaration чтобы Pool стал Component Library.

ЗАЧЕМ:
LauncherPresentationBuilder (TASK-127) и SettingsPresentationBuilder (TASK-69)
нуждаются в иконках, заголовках, help-текстах для компонентов.
Сейчас эти данные нигде не хранятся — Builder'ы hardcode или пустые.

SCOPE ВКЛЮЧАЕТ:
- ComponentDeclaration: новые nullable поля i18nKey, icon, category, helpUrl,
  supportedPlatforms, actions, validationKey
- pool.json schemaVersion 1→2 с migration writer и backward-compat test
- pool.json обновлён для 4 существующих компонентов (font-tile, tile-whatsapp,
  sos-main, toolbar-minimal) с заполненными полями
- strings_pool.xml: EN ключи для 4 компонентов (.title, .description, .help)
- Fitness function: PoolI18nCoverageTest — каждый i18nKey покрыт в strings.xml (CI gate)
- TODO(web-panel) seam inline в pool.json loading code

SCOPE НЕ ВКЛЮЧАЕТ:
- catalog-i18n JSON для web panel
- Remote pool update
- RU локализация (deferred)
- Drawable icon resources (placeholder string ключей достаточно)
- Изменения в Provider или ReconcileEngine

DEPENDENCIES:
- TASK-120 (Done) — ComponentDeclaration model уже существует

ACCEPTANCE CRITERIA:
1. pool.json читается с schemaVersion=2; старый schemaVersion=1 также читается без ошибок
2. ComponentDeclaration для font-tile, tile-whatsapp, sos-main, toolbar-minimal
   содержат i18nKey и icon поля
3. strings_pool.xml содержит EN ключи для всех 4 компонентов
4. PoolI18nCoverageTest passes в CI (каждый i18nKey → .title/.description/.help в strings)
5. Backward-compat roundtrip test: pool v1 JSON → parse → serialize → schemaVersion=2

LOCAL TEST PATH:
- Unit test: PoolMigration.v1ToV2 на sample JSON
- PoolI18nCoverageTest: reflection-based, не нужен device
- BundledAssetsLoadTest (Robolectric, уже есть из TASK-120): должен проходить с v2 pool.json

CONSTITUTION GATES:
- Rule 5 (wire format versioning): schemaVersion bump + migration writer обязательны
- Rule 7 (fitness functions): PoolI18nCoverageTest как CI gate
- Rule 9 (shareability): i18nKey в pool.json, не прямые строки — locale-neutral

EFFORT: S (2-3 дня)
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [ ] #1 [hand] pool.json schemaVersion=2; BundledAssetsLoadTest проходит с v2; старый schemaVersion=1 читается без ошибок (backward-compat test)
- [ ] #2 [hand] ComponentDeclaration для font-tile, tile-whatsapp, sos-main, toolbar-minimal содержат i18nKey и icon поля в pool.json
- [ ] #3 [hand] strings_pool.xml содержит EN ключи (.title, .description, .help) для всех 4 компонентов
- [ ] #4 [hand] PoolI18nCoverageTest passes: каждый i18nKey из pool.json имеет покрытие в strings_pool.xml
<!-- AC:END -->

## Definition of Done

Все AC `[hand]` зелёные. Pool enrichment не требует device verification — все тесты Robolectric/unit.
