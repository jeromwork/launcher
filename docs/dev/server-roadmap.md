# Server Roadmap

Документ накапливает **архитектурное видение** будущего собственного серверного компонента и **список конкретных задач**, которые сейчас реализованы как client-side / Cloudflare-Worker workaround и должны переехать на свой сервер.

**Зачем существует этот файл:** при каждом MVP-решении вида «делаем на клиенте потому что бесплатно» мы создаём **скрытый долг** (rule 3 CLAUDE.md — one-way door с exit ramp). Без централизованного учёта этот долг растворяется в комментариях и забывается. Этот файл = **specific exit ramp destination** для всех таких решений.

**Связанные файлы:**
- [`CLAUDE.md`](../../CLAUDE.md) §8 — правило обязательной записи сюда.
- [`project-backlog.md`](project-backlog.md) — оперативные TODO; здесь — **архитектурный** план их группового решения через свой сервер.

---

## Текущее состояние — без собственного сервера

| Компонент | Что используем | Ограничения |
|---|---|---|
| **API endpoints** | Cloudflare Worker (`launcher-push.<account>.workers.dev`) | Free tier: 100k req/day; vendor lock-in; URL привязан к личному аккаунту |
| **Push delivery** | FCM через Worker → Firebase Cloud Messaging | Worker stateless; нет persistence между requests |
| **Auth identity** | Firebase Anonymous Auth | UID сбрасывается при reinstall; нет cross-device identity |
| **Rate limiting** | In-memory Map в Worker'е | Сбрасывается при перезапуске instance Worker'а (часто) |
| **Data validation** | Firestore Security Rules + клиентская логика | Нет server-side transformations; нельзя enforced business rules sans rules.json |
| **Server-side jobs** | **Нет** — никаких cron, triggers, batch operations | Cleanup (expired pairings, old history) — client-side, ненадёжно |
| **Config history writes** | Client-side write в `/config/history/` перед push current | Race condition (rare); spoofing (admin может подделать history) |
| **App version compatibility** | Monorelease assumption | При первом нормальном update — schema mismatch unresolved |
| **Push notifications для admin** | Возможно через тот же Worker, но не реализовано | Нет state для дедупликации, нет персистентной очереди |

---

## Архитектурное видение — что будет на собственном сервере

**Цель:** собственный backend service (например, Kotlin/Ktor или Node.js/Fastify) на собственной инфраструктуре (свой VPS или K8s), который:

1. **Хранит истинное состояние** конфигов, истории, capabilities, health.
2. **Делает integrity-critical writes** атомарно (transactions через свою БД, не client-side Firebase batches).
3. **Запускает scheduled jobs** (cron) — cleanup, retention, drift detection.
4. **Валидирует** schema, business rules, abuse patterns **на сервере**.
5. **Шлёт push'и** дедуплицированно, с retry policy.
6. **Хранит audit log** для compliance (GDPR/152-ФЗ).
7. **Принимает решения** на основе history (например, «эта учётка пыталась 100 раз подделать timestamp — заблокировать»).

**Возможные базы данных:** PostgreSQL (relational, ACID) + Redis (cache, rate-limit, queues).

**Firebase останется** для: FCM push delivery (нет смысла своё писать), Firebase Auth (как identity provider, опционально).

---

## Задачи для миграции (server-side TODO log)

Группированы по подсистемам. Каждая задача имеет:
- **Что сейчас:** текущий client/Worker workaround.
- **Когда поедет:** триггер.
- **Что должен делать сервер:** конкретика.
- **Зависимости.**

### CONFIG SYNC + HISTORY (spec 008 / 009)

**SRV-CONFIG-001: History write через server transaction.**
- *Сейчас:* client пишет `/config/history/{autoId}` перед push в `/config/current` (spec 9, FR пункта 6, выбор Q1=Б).
- *Проблема:* race condition (crash между двумя write'ами → потеря версии); spoofing (admin может подделать history).
- *Сервер должен:* принимать push новой версии config от editor'a → атомарно (one DB transaction) записывать **new current** + **old current → history**.
- *Когда поедет:* при появлении реальных пользователей и одного случая spoofing-демонстрации, или при переходе на Blaze для consistency.
- *Зависимости:* выбор backend stack + миграция data из Firestore в собственную БД.

**SRV-CONFIG-002: History housekeeping (retention 10).**
- *Сейчас:* клиент сам читает all snapshots → удаляет старейшие при ≥10 (spec 9, Q2=А).
- *Проблема:* клиент может «забыть» (баг), нагрузка на клиента при каждом push, race с concurrent editor'ом.
- *Сервер должен:* cron job раз в час → для каждого `linkId` где snapshots > 10 → удалить старейшие.
- *Когда поедет:* вместе с SRV-CONFIG-001.

**SRV-CONFIG-003: Schema transformers для history snapshots.**
- *Сейчас:* при schema bump старые snapshot'ы становятся «не откатываемые» (drop-incompatible).
- *Проблема:* история теряется при каждом breaking schema change.
- *Сервер должен:* при чтении snapshot'a со старой `schemaVersion` → применять цепочку транзформеров `vN → vN+1 → ... → vCurrent` → отдавать клиенту в актуальной schema.
- *Когда поедет:* при **первом** breaking schema change (`schemaVersion: 1 → 2`).
- *Соответствует:* CLAUDE.md rule 5 — «backward-compatible reads MUST be possible for at least one major release».

**SRV-CONFIG-004: App version compatibility detection.**
- *Сейчас:* monorelease assumption (все editor'ы одной версии, spec 008 Q4).
- *Проблема:* при первом нормальном update (часть пользователей на v1, часть на v2) — admin v2 пушит config с новыми полями, Managed v1 не понимает.
- *Сервер должен:* читать `managedAppVersion` из `/state`, валидировать config против минимально-совместимой версии, отвергать невалидные push'и с понятной ошибкой.
- *Когда поедет:* перед первым production update (`TODO-ARCH-007` в backlog).

### MONITORING + HEALTH (spec 009 пункт 2 + future wearable/security)

**SRV-MONITOR-001: Critical health → push admin.**
- *Сейчас:* spec 009 эмитит `PhoneHealthCriticalEvent` локально, нет подписчика.
- *Проблема:* без push админ узнаёт о critical только когда заглянет в приложение.
- *Сервер должен:* listener на изменения `/links/{linkId}/health` → детект Critical transition → дедупликация (один push на инцидент) → FCM push админу.
- *Когда поедет:* до production-релиза с реальными пожилыми пользователями.
- *Соответствует:* `TODO-ARCH-012` в [project-backlog.md](project-backlog.md).

**SRV-MONITOR-002: Wearable / security sensor ingest.**
- *Сейчас:* нет.
- *Сервер должен:* принимать данные с умных часов (HRV, blood pressure), охранных датчиков (motion, door, smoke); хранить в time-series БД; триггерить alerts; шлёт push в зависимости от severity.
- *Когда поедет:* `TODO-FUTURE-SPEC-001/002` в backlog.

### CONTACTS

**SRV-CONTACTS-001: Contact drift detection.**
- *Сейчас:* нет (spec 9 Q5=А — игнорируем).
- *Сервер должен:* нет, это **client-side check**. Не сюда.

**SRV-CONTACTS-002: Shared admin contact book.**
- *Сейчас:* нет.
- *Сервер должен:* хранить контакты per-admin, ссылки из `/config.contacts[]` — по UUID на shared.
- *Когда поедет:* `TODO-FUTURE-SPEC-004` в backlog.

### SECURITY + COMPLIANCE

**SRV-SEC-001: App Check validation server-side.**
- *Сейчас:* нет (Cloudflare Worker — нет App Check валидации).
- *Сервер должен:* валидировать `X-Firebase-AppCheck` header на каждом write endpoint.
- *Когда поедет:* `TODO-SEC-001` в backlog.

**SRV-SEC-002: Audit log for critical operations.**
- *Сейчас:* нет.
- *Сервер должен:* лог всех write операций (config push, rollback, contact add/delete, pairing) с UID + timestamp + payload hash; retention 90 дней.
- *Когда поедет:* `TODO-SEC-003` в backlog.

**SRV-SEC-003: GDPR data export / deletion endpoints.**
- *Сейчас:* нет.
- *Сервер должен:* endpoints `GET /admin/{id}/export` (выгрузить все данные пользователя в JSON), `DELETE /admin/{id}` (полное удаление). Обрабатывать запросы за ≤30 дней (GDPR Article 17 / 20).
- *Когда поедет:* `TODO-LEGAL-001` в backlog.

**SRV-SEC-004: Rate-limit persistent (per-UID, per-linkId).**
- *Сейчас:* in-memory Map в Worker'е (`TODO-ARCH-002` в backlog).
- *Сервер должен:* Redis-based persistent rate-limit с window + multiple dimensions (UID, linkId, IP).
- *Когда поедет:* при daily req >1000 на endpoint или попытке abuse.

### COMMANDS + REAL-TIME OPERATIONS

**SRV-CMD-001: Admin-to-Managed runtime commands.**
- *Сейчас:* выкинуто в backlog (spec 9 пункт 5 — заменено на Action open_app).
- *Сервер должен:* принимать команды от admin → queue в Redis → push на Managed через FCM → ack от Managed → удалить из queue. С TTL и retry.
- *Когда поедет:* `TODO-ARCH-NNN: admin-to-managed runtime commands` в backlog (после spec 9).

### INFRASTRUCTURE

**SRV-INFRA-001: Production deployment** (отдельно от dev).
- *Сейчас:* dev `*.workers.dev` + dev Firebase.
- *Сервер должен:* production environment, isolated credentials, monitoring, alerts.
- *Когда поедет:* `TODO-OPS-004/005` в backlog. До production-релиза.

**SRV-INFRA-002: Custom domain.**
- *Сейчас:* `*.workers.dev`.
- *Сервер должен:* собственный `push.<our-domain>` или прямо на нашем сервере.
- *Когда поедет:* `TODO-ARCH-001` в backlog.

---

## Триггеры для перехода на собственный сервер

Решение «начинаем пилить свой сервер» — **one-way door**. Триггеры, которые должны сойтись (хотя бы 2 из 4):

1. **Privacy / compliance**: реальные пользователи EU → GDPR обязателен → нужна `SRV-SEC-003`.
2. **Integrity**: один инцидент spoofing'a или потери данных из-за race condition.
3. **Scale**: Cloudflare free tier 100k req/day исчерпан, или Firestore 1 GB лимит близко.
4. **Bandwidth lock-in**: Cloudflare ужесточает условия / Google поднимает цены.

До этого момента — **каждое** новое client-side workaround решение продолжаем фиксировать здесь.

---

## Workflow

- При **каждом** принятии решения «делаем на клиенте потому что нет сервера» → добавляется задача `SRV-*-NNN` в этот файл.
- При **каждом** spec'е, делающем integrity-critical write — проверять, не относится ли это к существующим SRV-задачам.
- **Re-review** этого файла раз в квартал — приоритеты могут меняться.

---

## История изменений

| Дата | Что | Кем |
|---|---|---|
| 2026-05-15 | Создан файл; зафиксированы SRV-CONFIG-001..004, SRV-MONITOR-001..002, SRV-CONTACTS-001..002, SRV-SEC-001..004, SRV-CMD-001, SRV-INFRA-001..002 | Spec 009 pre-specify discovery, mentor-сессия |
