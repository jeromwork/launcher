---
id: TASK-129
title: Pool i18n coverage gate (was: ComponentDeclaration enrichment)
status: Done
assignee: []
created_date: '2026-07-14'
updated_date: '2026-07-16 17:30'
labels:
  - phase-2
  - foundation
  - preset
  - pool
  - fitness
milestone: m-1
dependencies:
  - TASK-120
references:
  - specs/task-129-pool-component-declaration/
priority: medium
ordinal: 129000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->

## Что это простыми словами

Pool (`pool.json`) — каталог компонентов внутри приложения. В нём лежат не сами
надписи, а **ключи** — имена вроде `pool.font.description`. Настоящие слова лежат
отдельно, в файлах приложения: английские в `values/`, русские в `values-ru/`.
Android сам подставляет нужный язык по настройкам телефона. Благодаря этому пресет
можно передавать между устройствами — внутри него нет текста ни на одном языке.

Проблема: связь «ключ в каталоге ↔ слово в файле» **никем не проверяется**. Обычно
за этим следит компилятор, но наши ключи приходят из JSON — компилятор их не видит.
Забыли добавить перевод — узнает об этом пользователь: вместо надписи он увидит на
экране `pool.font.description`.

По шагам (после этой задачи):
1. Разработчик (или AI-агент) добавляет компонент в `pool.json` с новым ключом.
2. Забывает добавить русский перевод.
3. Сборка **падает сразу** и называет ключ и язык, которого не хватает.
4. Пользователь никогда не увидит технический текст вместо надписи.

## Зачем

Это единственная дыра в pool-локализации, которую можно закрыть сегодня, не завися
от будущих фич. Стоит дёшево, страхует навсегда, и работает в первую очередь на
AI-агента: он добавляет компоненты чаще человека и получает машинную обратную связь
вместо молчаливой ошибки.

## Что входит технически (для AI-агента)

- `PoolI18nCoverageTest` (`core/src/androidUnitTest/.../fitness/preset/`) — JVM-гейт:
  читает bundled `pool.json`, собирает текстовые ключи (`labelKey`, `titleKey`,
  `descriptionKey`) на любой глубине дерева, проверяет наличие непустой строки
  в EN и RU. Правило имён зеркалит рантайм (`AndroidLocalizedResources`):
  точки → подчёркивания.
- EN и RU равноправно строгие — RU основной рынок продукта.
- `iconKey` / `layoutKey` из проверки исключены — это идентификаторы, не текст;
  решение закреплено отдельным тестом, чтобы его не «починили» обратно.
- Гейт падает, если не смог прочитать входные файлы — иначе он зелёный вхолостую.
- Модель, wire format, версия схемы, Provider, ReconcileEngine — **не тронуты**.

## Состояние

**Done** (2026-07-17). Гейт реализован, зелёный на текущем
`pool.json` (14 текстовых ключей в EN и RU), способность падать проверена мутацией
(удалили RU-строку — упал с точным именем ключа и языка). Весь `:core` зелёный.
Ветка `task-129-pool-component-declaration` запушена; PR за владельцем — `gh`
в dev-окружении не авторизован.

### Scope ужат относительно исходной постановки — почему

Исходная задача просила обогатить Blueprint полями `i18nKey`, `icon`, `category`,
`helpUrl`, `supportedPlatforms`, `actions`, `validationKey` и поднять схему.
Обследование кода на `main` (после вливания TASK-126 / TASK-127) показало, что
**посылка задачи не подтвердилась**:

- `LauncherPresentationBuilder`, ради которого всё затевалось, **не существует** —
  TASK-127 его не создавала.
- Домашний экран строится из Profile (`Entity` → `Component`), заголовок и иконку
  берёт **из компонента** (`labelKey`, `iconKey`), а Pool не читает вообще
  (`ProfileBackedFlowRepository.toSlot()`).
- Мастер берёт заголовки шагов из `Preset.wizardFlow`, не из Blueprint.
- Все 15 объявленных ключей **уже покрыты** EN-строками — заявленной дыры нет.
- Схема уже v2 (TASK-126 занял версию под `requires` / `required`), а не v1.
- Модель называется `Blueprint`, а не `ComponentDeclaration` (ECS-переименование).

То есть у полей не было ни одного потребителя: мы подняли бы версию схемы и
написали миграцию ради кода, которого нет (нарушение rule 4 MVA + refuse pattern #9).
Решение владельца 2026-07-16 — ужать до гейта, поля отложить до появления
потребителя. Отложенное вынесено в **TASK-135**; добавление будет additive,
без переписывания.

Спека: [`specs/task-129-pool-component-declaration/spec.md`](../../specs/task-129-pool-component-declaration/spec.md)
— там же Clarifications (почему XML, а не JSON; почему явные ключи; почему EN+RU
строго) и раздел «Замеченное, но не исправленное здесь» (`iconKey` как
псевдо-i18n-ключ; риск `shrinkResources` при динамическом резолве).

---

## Готовый промт для `/speckit.specify` (historical — постановка до обследования)

Промт ниже описывает **исходную** модель задачи (обогащение полями + schemaVersion
1→2). Она не подтвердилась при контакте с кодом; оставлен как исторический след.

```
ЧТО СТРОИМ:
Pool ComponentDeclaration enrichment — добавить canonical UI metadata поля
в pool.json ComponentDeclaration чтобы Pool стал Component Library.

ЗАЧЕМ:
LauncherPresentationBuilder (TASK-127) и SettingsPresentationBuilder (TASK-69)
нуждаются в иконках, заголовках, help-текстах для компонентов.

SCOPE ВКЛЮЧАЕТ:
- ComponentDeclaration: новые nullable поля i18nKey, icon, category, helpUrl,
  supportedPlatforms, actions, validationKey
- pool.json schemaVersion 1→2 с migration writer и backward-compat test
- pool.json обновлён для 4 существующих компонентов
- strings_pool.xml: EN ключи для 4 компонентов (.title, .description, .help)
- Fitness function: PoolI18nCoverageTest — каждый i18nKey покрыт (CI gate)
- TODO(web-panel) seam inline в pool.json loading code

EFFORT: S (2-3 дня)
```

<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria

<!-- AC:BEGIN -->
- [x] #1 [hand] Гейт падает, если у объявленного в pool.json текстового ключа нет EN- или RU-строки; сообщение называет ключ и язык
- [x] #2 [hand] Гейт зелёный на текущем pool.json — 14 текстовых ключей покрыты в EN и RU
- [x] #3 [hand] iconKey / layoutKey не требуют переводов (закреплено отдельным тестом)
- [x] #4 [hand] Модель, wire format и версия схемы не тронуты — существующие тесты (WireFormatI18nKeysTest, весь :core) зелёные
<!-- AC:END -->

## Definition of Done

Все AC `[hand]` зелёные. Device verification не требуется — гейт это JVM-тест,
отложенных `[auto:deferred-*]` гейтов нет.

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->

Закрыто 2026-07-17. Реализован один артефакт — `PoolI18nCoverageTest`
([core/src/androidUnitTest/.../fitness/preset/PoolI18nCoverageTest.kt](../../core/src/androidUnitTest/kotlin/com/launcher/test/fitness/preset/PoolI18nCoverageTest.kt)),
JVM-гейт покрытия ключей Pool переводами в EN и RU.

**Что он закрывает**: ключи `pool.json` приходят из JSON, поэтому компилятор их не
видит (обычный Android-путь `R.string.foo` роняет сборку на опечатке). При промахе
`AndroidLocalizedResources` возвращает сам ключ — пользователь увидел бы на экране
`pool.font.description`. Соседний `WireFormatI18nKeysTest` проверял только **форму**
ключа, не **покрытие**.

**Проверено**: зелёный на текущем `pool.json` (14 текстовых ключей × 2 языка); весь
`:core` зелёный; способность падать подтверждена мутацией — удаление RU-строки
`pool_sos_description` роняет гейт с точным сообщением
`RU: pool.sos.description -> <string name="pool_sos_description"> is missing`.

**Чего не делали и почему**: исходная постановка (поля `i18nKey` / `icon` /
`category` / `helpUrl` / `supportedPlatforms` / `actions` / `validationKey` +
bump схемы) отменена — у полей не нашлось ни одного потребителя:
`LauncherPresentationBuilder` не существует, домашний экран читает `Component`,
а не `Blueprint`, мастер берёт заголовки из `Preset.wizardFlow`. Отложено
в **TASK-135** (additive, когда потребитель появится).

**Оставлено сознательно** (зафиксировано в spec.md § «Замеченное»): `iconKey` как
псевдо-i18n-ключ (наследие TASK-120, чинить — трогать `AppTileProvider` /
`HomeScreenFacade`); риск `shrinkResources` против динамического `getIdentifier`.

<!-- SECTION:FINAL_SUMMARY:END -->
