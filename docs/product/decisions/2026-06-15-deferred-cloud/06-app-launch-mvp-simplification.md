# 2026-06-15 / 06 — App launch в MVP: упрощение до «installed-only, без параметров»

## Status

**ACCEPTED 2026-06-15** в разговоре владельца с AI после серии итераций по архитектуре provider catalogue.

## Context

Лаунчер должен запускать сторонние apps (не только контакты). Спека 005 (action architecture v2) уже даёт 8 встроенных провайдеров с deep-link templates (phone, sms, browser, whatsapp, telegram, viber, youtube, app) — этого хватает для S-3 (контакты + messengers), но **не хватает** для произвольных apps с параметрами (Uber с готовым адресом, региональные такси, доставка, banking).

В обсуждении 2026-06-15 рассматривались варианты:

1. **Bundled catalogue в APK** — отпадает, ожидаемый размер 50-100 тыс. apps в каталоге.
2. **Серверный recipe catalogue + локальный inventory** (полная схема: публичные recipes на сервере, зашифрованный inventory senior'а для admin'а, resolve в момент тапа). Owner признал архитектурно правильным, **но** объёмным для MVP.
3. **MVP-упрощение** (это решение): только installed apps на admin-устройстве, запуск без параметров, fallback на Play Store. Полная схема — Phase 3.

Owner-quote (2026-06-15):

> «Значит, мы давай будем использовать только установленные приложения с флэбэком, если они не установлены. Не используем тогда их параметры, то есть просто позапускаем приложение без параметров, потому что мы не знаем, о чем это. Не используем пока серверную часть, серверную логику. Просто берем те приложения, которые установлены на телефоне admin или».

## Decision

### Phase 2 (MVP core): упрощённая модель запуска apps

- **Источник списка apps** — только `PackageManager` на устройстве **admin'а** в момент редактирования. То, что у admin'а не установлено, в редакторе не предлагается.
- **Параметры запуска** — **не используются**. `Intent(ACTION_MAIN).setPackage(packageName)` — open-only.
  - Исключение: встроенные провайдеры из spec 005 (phone, sms, browser, whatsapp, telegram, viber, youtube), которые работают в S-3 для контактов и тоже принимают параметры (phone number, URL).
- **Fallback на senior-устройстве**:
  - App установлен → запуск.
  - App не установлен → `market://details?id=<package>` (Play Store).
  - Без web-fallback в Phase 2.
- **Иконки**: `PackageManager.getApplicationIcon(packageName)` локально на senior-устройстве. Если app не установлен → дефолтная «not installed» иконка. Admin может приложить кастомную иконку в config — она используется независимо от установки.
- **Inventory senior'а** (что у бабушки установлено) — admin **не видит**. Настраивает «вслепую» из своего списка. Если у бабушки app нет — fallback на Play Store при тапе.
- **Серверный каталог recipes** — **нет**.
- **Никакой telemetry о тапах**.

### Phase 3: P-8 + P-9

Полная схема, отложенная сюда:

- **P-8 Provider Recipe Catalogue** — серверный публичный каталог deep-link templates с параметрами + локальный кэш на устройствах. Tile в config содержит `{recipeId, parameters}`, recipe резолвится из кэша в момент тапа. Curator workflow — отдельная серверная sub-спека.
- **P-9 Device Inventory Sync** — senior-устройство собирает список своих установленных apps локально через `PackageManager`, шифрует тем же ключом, что и config (envelope encryption из F-5), пушит на сервер. Admin читает в редакторе и видит «реальное» состояние бабушкиного телефона.

## Архитектурные принципы, зафиксированные в этом решении

1. **Recipe не лежит в config'е как копия** — иначе при изменении Uber'ом deep-link схемы все existing configs «протухают». Tile в config хранит **только ссылку** `recipeId`. Recipe резолвится в момент запуска из локального кэша каталога, который синкается с сервером отдельным каналом.

2. **Recipe-каталог — публичные данные**, конфиг — зашифрованные. Разные каналы sync. Сервер видит регион устройства (из query на каталог), но **не** видит, какие плитки настроены и что бабушка тапает.

3. **Inventory — отдельный wire format**, не часть config'а. Разные жизненные циклы: config меняется по решению admin'а, inventory — по факту install/uninstall на senior-устройстве.

4. **Privacy boundary fixed early**:
   - Pull recipe-каталога — **всего региона целиком**, не по одному recipe. Сервер не знает, какой recipe юзер интересует.
   - Никакой telemetry о тапах плиток на сервер. Ни в Phase 2, ни в Phase 3.
   - Inventory зашифрован тем же ключом, что и config — сервер видит blob, не структуру.

5. **Fallback chain — единственный механизм работы с «нет app у бабушки»**. Никаких real-time sync «бабушка установила X» в Phase 2/3. Fallback на Play Store по факту неудачного запуска — достаточно.

6. **Локализация имени app — через Android, не через recipe**. `PackageManager.getApplicationLabel(packageName)` на устройстве senior'а. Recipe хранит canonical name только для admin UI.

## Что эти решения **отменяют** / уточняют

| Документ | Что |
|----------|-----|
| spec 005 (action architecture v2) | Остаётся как есть — 8 встроенных провайдеров используются и в Phase 2, и в Phase 3. Generic provider `"app"` в Phase 2 = open-only без параметров; в Phase 3 расширяется через recipe-каталог. |
| spec 008 (bidirectional config sync) | Inventory переиспользует механизм sync (envelope encryption + push notification), реализованный для config'а. P-9 — additive use case. |
| spec 014 (tile editing) | В Phase 2 редактор показывает только admin's `PackageManager`. В Phase 3 — пересечение с inventory senior'а + recipe-каталог. Расширение additive. |

## Что **не** входит даже в Phase 3

- ❌ User-generated recipes (admin создаёт свой recipe для приватного app) — L-фаза, marketplace-like.
- ❌ Real-time push «бабушка только что установила X» — не нужно, fallback покрывает.
- ❌ Server-side фильтрация recipe-каталога по конкретному recipe (не по региону) — это **дополнительный** privacy compromise, отдельное решение если понадобится.
- ❌ Marketplace / community / ratings — L-2.
- ❌ AI agent trigger actions через recipes — F-2 Capability Registry (Phase 4).

## Exit ramp

Если выяснится, что MVP-упрощения недостаточно для product-market fit (юзеры жалуются «не могу настроить Uber с готовым адресом для бабушки»):

- **Не нужно** менять wire format в Phase 2 — tile продолжает хранить `recipeId`-like структуру, просто в MVP `recipeId` совпадает с `packageName` и `parameters` пустой.
- **Phase 3 P-8/P-9** = additive add: появляется отдельный namespace recipes на сервере, появляется кэш на устройстве, появляется inventory sync. Existing tiles в config бабушки продолжают работать (open-only fallback всегда жив).
- Точка возврата на свой сервер ([rule 8](../../../../CLAUDE.md)): когда поедем со Cloudflare Worker + KV на свой server, recipe endpoint становится REST `/recipes?region=X&since=Y`. Структура recipe не меняется.

## Downstream effects

| Артефакт | Что |
|----------|-----|
| `docs/product/roadmap.md` Phase 2 intro | Добавлен note про MVP-ограничение (installed-only, без параметров) |
| `docs/product/roadmap.md` Phase 3 | Добавлены P-8 + P-9 (7 → 9 P-спек). Critical path обновлён. |
| `specs/005-action-architecture-v2/` | Не меняется. В Phase 3 `recipeId`-механизм будет надстроен сверху, не сломав встроенные 8 провайдеров. |
| Будущая P-8 spec | Должна явно описать privacy boundary (pull всего региона, не по одному recipe). |
| Будущая P-9 spec | Должна явно описать TTL inventory + behavior при Android 11+ package visibility ограничениях. |

## TODO(server-roadmap)

- Recipe catalogue в Phase 3 хостится на Cloudflare Worker + KV / R2 (Worker tier бесплатный). Когда поедем на свой сервер — REST endpoint `/recipes?region=X&since=Y` с той же структурой recipe. Добавить в `docs/dev/server-roadmap.md` отдельным пунктом при старте P-8.

## Notes

- **MVP-простота — сознательный выбор**. Owner явно отказался от complexity ради ускорения первого выпуска. Полная схема готова к add'у без рефакторинга благодаря fallback chain spec 005.
- **Privacy boundary зафиксирован до первой строки кода P-8/P-9** — это правильно: privacy-decisions делать после написания кода намного дороже.

## Plain Russian summary (что внутри)

Бабушкин лаунчер должен уметь запускать сторонние приложения (не только звонки контактам). В MVP делаем максимально просто:

1. Когда родственник (admin) настраивает плитки на бабушкином телефоне со своего телефона — он видит только те apps, которые установлены **у него самого**.
2. Плитки запускают app «как иконку с рабочего стола» — без передачи параметров (без «открой Uber с готовым адресом»).
3. Если у бабушки этот app **не установлен** — при тапе откроется Play Store с предложением установить. Никаких ошибок, никаких пустых экранов.
4. Иконку app'а рисуем через стандартный Android API. Если app у бабушки нет — рисуем серую «не установлено» иконку.

Полную схему (серверный каталог рецептов с параметрами для тысяч apps + зашифрованный список бабушкиных установок для admin'а) откладываем в Phase 3 (P-8 + P-9). Сейчас не нужно — fallback на Play Store покрывает все случаи.

Privacy с первого дня: сервер не знает, какие плитки настроил admin и что бабушка тапает. Каталог в Phase 3 будет качаться **целиком по региону**, без раскрытия отдельных интересов юзера.
