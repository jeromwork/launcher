---
id: TASK-23
title: Provider Recipe Catalogue
status: Draft
assignee: []
created_date: '2026-06-23 05:39'
updated_date: '2026-06-23 06:24'
labels:
  - phase-3
  - p-spec
  - p-8
  - server
  - recipes
  - deep-link
  - privacy
milestone: m-2
dependencies:
  - TASK-16
priority: high
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
> **Про роли в этой задаче.** Сценарий ниже описан на примере **family-варианта** (бабушка как `primary user`, дочка-родственник как `remote administrator`, сиделка как `restricted caregiver`). Это — **иллюстрация для понятности**. В реальной модели роли называются `primary user` / `remote administrator` / `restricted caregiver` (см. CLAUDE.md «Personas vs domain roles»). Те же flow работают и для других сегментов: clinic (пациент / доктор / медсестра), B2B (сотрудник / HR / IT-support), self-care (один user во всех ролях).

## Что это простыми словами

Сервер с готовыми «рецептами» как открывать конкретные приложения. Сейчас бабушка может тапнуть плитку «Uber» только если admin вручную прописал нужную команду. Эта задача — каталог тысяч рецептов для региональных приложений (Uber / Bolt / 99 / Yandex Taxi / Kakao T / любое такси / банк / мессенджер), который скачивается автоматически.

**Что происходит по шагам (admin создаёт плитку):**
1. Admin в редакторе плиток (TASK-13 S-8) выбирает «Добавить приложение».
2. Видит список приложений, отфильтрованный по тому что установлено у бабушки (через TASK-24 P-9).
3. Выбирает «Яндекс Такси».
4. Сервер уже знает рецепт: «как открыть Яндекс Такси с заполненным адресом подачи».
5. Admin вписывает адрес «дача» → save.

**Что происходит когда бабушка тапает плитку:**
1. Приложение берёт `recipeId: "yandex-taxi"` из плитки.
2. Достаёт рецепт из локального кэша (обновляется раз в день).
3. Подставляет параметр «адрес = дача» в шаблон deep-link.
4. Запускает Яндекс Такси с уже введённым адресом.
5. Если Яндекс Такси не установлен — fallback на webFallback (открыть в браузере).
6. Если ни то ни другое — fallback на playStoreUrl (предложить установить).

**Как работает privacy:**
- Локальный кэш скачивает **весь регион** (например, все рецепты для России), не по одному рецепту.
- Сервер не знает, какой рецепт бабушка реально тапнула.
- В конфиге бабушки лежит только `recipeId` (например, `"uber"`) — не копия рецепта (рецепты протухают при изменении схемы Uber'ом).

## Зачем

Сейчас в spec 005 встроено 8 провайдеров (phone, sms, browser, whatsapp, telegram, viber, youtube, app). Этого мало для региональных рынков. С каталогом — admin может подключить любое региональное такси / банк / мессенджер.

## Что входит технически (для AI-агента)

- Recipe wire format `schemaVersion: 1` с первого коммита.
- Cloudflare Worker endpoint `GET /recipes?region=X&since=Y` (incremental).
- Локальный кэш recipes на admin + senior устройствах.
- Resolver в момент тапа: `config.tile.recipeId` → кэш → `deepLinkTemplate` + params → Intent.
- Fallback chain: deepLink → webFallback → playStoreUrl.
- Admin UI: пересечение `recipe-каталог × installed apps` (использует TASK-24).
- Curator workflow — **отдельная серверная sub-спека** (CMS / парсинг / CI fitness function «recipe deep-link реально открывается на эмуляторе»).

## Состояние

**Planned.** Зависит от TASK-16 (P-1 v2 schema — место для `recipeId` в tile).

---

## Готовый промт для `/speckit.specify`

```
Реализуй P-8: Provider Recipe Catalogue.

ЧТО СТРОИМ:
Серверный публичный каталог launch recipes — «как открыть конкретный app с параметрами через deep-link» для региональных apps. Recipe wire format schemaVersion=1 (packageName, parameterTypes, deepLinkTemplate, webFallback, playStoreUrl, availableRegions, lastUpdatedAt). Cloudflare Worker /recipes?region=&since= endpoint. Локальный кэш TTL 1 day. Resolver в момент тапа. В config бабушки лежит только {recipeId, parameters}, не копия recipe.

ЗАЧЕМ:
spec 005 даёт 8 встроенных провайдеров — мало для региональных рынков. Каталог расширяет до сотен/тысяч (Uber/Bolt/Yandex Taxi/Kakao T/etc.).

SCOPE ВКЛЮЧАЕТ:
- Recipe wire format schemaVersion=1 с roundtrip + backcompat tests.
- Cloudflare Worker endpoint GET /recipes?region=&since= (ETag-based incremental).
- Локальный кэш на admin + senior (TTL 1 day, refresh at admin app launch).
- Resolver в момент тапа: recipeId → cache → deepLinkTemplate + params → Intent.
- Fallback chain: deepLink → webFallback → playStoreUrl (extends spec 005).
- Admin UI: пересечение recipe-каталог × installed apps (uses TASK-24 P-9).
- Privacy: pull всего региона, без telemetry о тапах, без server-side фильтрации по конкретному recipe.

SCOPE НЕ ВКЛЮЧАЕТ:
- Curator workflow (CMS / parsing / CI test) — отдельная серверная sub-спека.
- User-generated recipes (TASK-35 L-2 marketplace).
- Telemetry о тапах — explicitly NEVER.
- Recipe для приватных apps пользователя — explicitly NOT, только public catalog.

DEPENDENCIES:
- TASK-16 (P-1 schema v2 с recipeId в tile).
- TASK-24 (P-9 inventory) — optional, для intersection UX в admin UI.

ACCEPTANCE CRITERIA:
- Admin открыл редактор плиток → увидел «Яндекс Такси» в списке доступных.
- Создал плитку «такси-дача» с recipeId=yandex-taxi и адресом → бабушка увидела плитку за <10 секунд.
- Бабушка тапнула плитку → Яндекс Такси открылось с заполненным адресом подачи.
- Удалила Яндекс Такси с телефона → тап на плитку открыл webFallback в браузере.
- Кэш каталога обновляется автоматически раз в день, видно «обновлено N часов назад».
- Сервер не получает telemetry «какой recipe тапнула бабушка» (проверка через mock server).

LOCAL TEST PATH:
- Эмулятор pixel_5_api_34 с установленным Яндекс Такси.
- Mock Cloudflare Worker для unit-tests.
- E2E с реальным staging Worker + 3 sample recipes.

CONSTITUTION GATES:
- Rule 1 (domain isolation): RecipeResolver — port в core/intents/.
- Rule 2 (ACL): Cloudflare Worker client не вытекает в domain.
- Rule 5 (wire format): Recipe schemaVersion=1, roundtrip test.
- Rule 8 (server migration): own server takeover path — inline TODO в Worker adapter.
- Rule 14 (security): explicit NO telemetry.

EFFORT: Medium (~2-3 weeks клиент + отдельная серверная спека).
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Cloudflare Worker endpoint GET /recipes?region=&since=
- [ ] #2 Локальный кэш на admin+senior с TTL 1 день
- [ ] #3 Resolver в момент тапа: recipeId → cache → deepLinkTemplate с parameters
- [ ] #4 Admin UI: пересечение recipe-каталог × installed apps
- [ ] #5 Privacy: НЕТ telemetry о тапах, НЕТ server-side фильтрации
- [ ] #6 Admin открыл редактор плиток → увидел 'Яндекс Такси' в списке доступных
- [ ] #7 Создал плитку 'такси-дача' с адресом → бабушка увидела плитку за <10 секунд
- [ ] #8 Бабушка тапнула плитку → Яндекс Такси открылось с заполненным адресом подачи
- [ ] #9 Удалила Яндекс Такси с телефона → тап на плитку открыл webFallback в браузере
- [ ] #10 Кэш каталога обновляется автоматически раз в день, видно 'обновлено N часов назад'
- [ ] #11 Сервер не получает telemetry 'какой recipe тапнула бабушка' (проверка через mock server)
<!-- AC:END -->
