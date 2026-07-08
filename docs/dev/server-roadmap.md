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
| **Auth identity** | LOCAL-first identity (`identity_id = hash(root_public)`, [TASK-106](../../backlog/tasks/task-106%20-%20Decision-Sybil-resistance-and-signup-gate.md)) + Google Sign-In lazy при первом cloud action ([TASK-3](../../backlog/tasks/task-3%20-%20F-4-AuthProvider-Google-Sign-In.md)). Firebase Auth = JWT issuer, не identity source. | Firebase JWT verification в Worker'е ([TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) baseline); переезд на own JWT issuer — SRV-BASELINE-002 ниже. |
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

### SRV-BASELINE (zero-trust posture, [CLAUDE.md rule 12](../../CLAUDE.md) + [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md))

Consolidated non-goals и exit ramps для defense mechanisms, **не** применённых в MVP baseline. Baseline (auth / rate-limit / input validation / observability / failure modes) определён в TASK-105; ниже — что мы **осознанно отложили**.

**SRV-BASELINE-001: Persistent rate-limit tier для normal endpoints.**
- *Сейчас:* in-memory Map в Worker (Cloudflare RATE_LIMITER binding для normal). Baseline TASK-105 использует free tier — resets on Worker restart, per-instance.
- *Проблема:* attacker может обходить rate-limit через принудительный restart Worker'а (edge case) или burst из разных Cloudflare edge locations (Cloudflare distributes traffic).
- *Сервер должен:* Cloudflare Durable Objects или KV для persistent counter (сохраняет state между Worker restart'ами и edge instances).
- *Когда поедет:* Phase-3+ или при первом abuse incident. TASK-105 baseline даёт «good enough» защиту для MVP.
- *Exit ramp:* upgrade normal-tier endpoints в RATE_LIMITER → DO ladder (TASK-105 ladder pattern extension).

**SRV-BASELINE-002: Own JWT issuer.**
- *Сейчас:* Firebase Auth = JWT issuer, Worker verify'ит Firebase JWT через JWKS cache.
- *Проблема:* vendor lock-in на Firebase Auth; не может независимо issued custom claims (identity_id, delegation state); Firebase Auth pricing scales с MAU.
- *Сервер должен:* own JWT issuer (Kotlin/Ktor + jose4j или Node/fastify-jwt); Google Sign-In → own JWT exchange endpoint; Google auth token остаётся только для Sign-In verification.
- *Когда поедет:* при переезде на own backend или при первом billing pain point с Firebase Auth.
- *Exit ramp:* заменить Firebase JWT verification в Worker'е на own JWKS URL (~1-2 недели).

**SRV-BASELINE-003: Server-side DO для security-critical counters ([TASK-109](../../backlog/tasks/task-109%20-%20Decision-Durable-Objects-concrete-design-security-critical-endpoints.md) Paused).**
- *Сейчас:* recovery attempts counter + unlock attempt tracker живут in-memory или отсутствуют.
- *Проблема:* brute-force attacks против recovery blob passphrase не блокируются между Worker restart'ами.
- *Сервер должен:* concrete DO schema для recovery attempts (per identity, sliding window), unlock attempts (per identity, exponential backoff), TASK-107 abuse report deduplication.
- *Когда поедет:* когда TASK-109 unpauses и Decision block заполнен.
- *Exit ramp:* SRV-BASELINE-001 + concrete DO schema per TASK-109.

**SRV-BASELINE-004: Metadata T1 opaque adapters ([TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md)).**
- *Сейчас:* T0 — `identity_id` visible в URLs, bucket type in path, `groupId` in FCM topic.
- *Проблема:* metadata visible серверу (social graph, timing patterns, device switches).
- *Сервер должен:* принимать `OwnerRef = HMAC(root_key, "server-pseudonym")`, `BucketKey = HMAC(root_key, bucket_type)`, `PushTopic = HMAC(exporter_key, "push")` вместо raw identity_id.
- *Когда поедет:* client-side ports уже opaque (TASK-108 Decision). Server-side migration = adapter swap Worker route handlers (~2-3 недели).
- *Exit ramp:* T1 = pseudonym HMAC (`SRV-BASELINE-004a`). T2 = VOPRF sealed sender (parked, не MVP).

**SRV-BASELINE-005: Idempotency для state-modifying endpoints.**
- *Сейчас:* endpoints без explicit idempotency key. Retries могут duplicate operations (обычно idempotent by content but not guaranteed).
- *Проблема:* client retry loops могут создавать duplicate MLS commits, duplicate KeyPackage registration, duplicate config history entries.
- *Сервер должен:* idempotency-key header + dedup cache (KV, TTL 24h) для all POST/PUT/DELETE endpoints.
- *Когда поедет:* при первом incident с duplicate operations в production.
- *Exit ramp:* Cloudflare KV с idempotency-key → response cache pattern (Stripe-style).

---

### SRV-LEGAL / SRV-BCP / SRV-LOG-RETENTION (added 2026-07-07 per audit item #5, Тема 6 adjacent concerns)

**SRV-LEGAL-001: Government legal request procedure.**
- *Сейчас:* legal request procedure не документирована. Мы не знаем что можем выдать при получении GDPR / 152-ФЗ / court subpoena.
- *Что можем выдать (по существующей архитектуре)*: server видит metadata (identity_id, timestamps, IP addresses if not proxied, opaque bucket references, MLS commit sequence). Content — E2E encrypted, декодировать не можем даже под court order. Passphrase — never on server (Argon2id derivation client-side).
- *Что нужно сделать сервером*: явный legal transparency report template + документированная procedure «что может выдать / что не может» + возможность user'а инициировать account delete с cryptographic proof (encrypted-deletion via wipe recovery blob + revoke MLS group).
- *Когда поедет*: перед EU release / первым legal inquiry.
- *Exit ramp*: transparency report page (like Signal / Matrix); Warrant Canary optional.

**SRV-BCP-001: Business continuity — потеря Cloudflare account.**
- *Сейчас*: Cloudflare Worker + Firestore + FCM = единая vendor-attached stack. Компрометация / банк Cloudflare account = catastrophic (потеря KeyPackage pool, message queue, recovery blobs — если users не имеют local copies).
- *Проблема*: recovery blobs хранятся в Cloudflare KV → без backup mechanism = complete data loss for users не имеющих active session.
- *Сервер должен*: (a) periodic export (encrypted) of Cloudflare KV recovery blobs в second storage (Backblaze B2 / R2 / self-hosted S3-compatible); (b) documented recovery procedure для restore из backup; (c) client-side hint при recovery «если backup недоступен, вот путь X».
- *Когда поедет*: перед 100 paying users или при переезде на own-server (whichever first).
- *Exit ramp*: multi-vendor storage strategy — SRV-STORAGE-001 расширение (уже есть). Encrypted blob copy pattern.

**SRV-LOG-RETENTION-001: Server-side log retention policy.**
- *Сейчас*: Cloudflare Worker logs, Firestore audit logs — retention default vendor (90 days Firestore, 3 days Worker). Не документировано что логируется, что нет.
- *Проблема*: (a) logs могут содержать identity_id + IP + timing = privacy leak если leaked; (b) GDPR требует declared retention period + minimization; (c) court subpoena может требовать log preservation → нужна documented procedure.
- *Сервер должен*: (a) explicit policy «что логируется» (только error metrics + abuse counters, никаких user identifiers в стандартных logs); (b) log retention 30 days default (configurable per region); (c) legal hold mechanism (при court order — preserve logs beyond retention); (d) user right to log deletion (GDPR Article 17).
- *Когда поедет*: перед first paying customer / перед EU release.
- *Exit ramp*: structured logging library (opentelemetry-compatible); log sink to own S3; retention policy как code (rotation job).

---

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

### CRYPTO + PRIVATE MEDIA STORAGE (spec 011 e2e-crypto-foundation, spec 012 photos, future Jitsi/vendor/hardware integrations)

**SRV-CRYPTO-001: Универсальный маршрут переезда крипто-инфраструктуры на собственный backend.**
- *Сейчас (rev. 2 — 2026-05-25):* зашифрованные blob'ы лежат в **Backblaze B2** (`launcher-private-media-eastclinic`, S3-compatible, EU Central), доступ через Cloudflare Worker (`/blobs/{linkId}/{uuid}` endpoints) с Firebase ID-token auth + link-membership check. Pub-ключи устройств — в Firestore (`/links/{linkId}/devices/{deviceId}`); авторизация для Firestore — Security Rules; авторизация для blobs — серверная в Worker'е.
  - **Раньше (rev. 1, до 2026-05-25):** планировали Firebase Storage с cross-service rule. Откатили: Firebase Storage требует Blaze plan (платный), credit card недоступен на dev-фазе. B2 free tier 10 GB + Worker proxy решает.
- *Проблема:* не связана с Firebase лимитами. Триггер — запуск собственного backend проекта вне зависимости от Spark plan. Vendor lock-in (Firebase Storage SDK, Firestore SDK) на ровном месте; serverless авторизация менее гибкая, чем server-side; нет audit trail; нет возможности добавить server-side policy (например, антиспам blob upload).
- *Что переезжает:*
  - Backblaze B2 `launcher-private-media-eastclinic` → собственное файловое хранилище (S3-совместимое — drop-in replacement, потому что B2 уже S3 API).
  - Cloudflare Worker `/blobs/*` endpoints → endpoints на собственном backend'е (тот же интерфейс).
  - Firestore `/links/{linkId}/devices/{deviceId}` → таблица в БД (PostgreSQL).
  - Firestore Security Rules → серверная авторизация через JWT/session.
- *Что НЕ переезжает (остаётся на клиенте навсегда по дизайну e2e):*
  - Криптография (encryption / hashing / signing через libsodium) — клиент-сайд по дизайну, иначе нарушает e2e гарантию.
  - Приватные ключи (X25519 priv, Ed25519 priv) — в Android Keystore, **никогда** не покидают устройство.
  - Envelope format и cipherSuite — wire format, версионируется через `schemaVersion` (CLAUDE.md rule 5).
- *Маршрут миграции:*
  1. Развернуть backend с эндпоинтами:
     - `POST /links/{linkId}/blobs/{uuid}` (upload зашифрованного envelope),
     - `GET  /links/{linkId}/blobs/{uuid}` (download),
     - `DELETE /links/{linkId}/blobs/{uuid}` (cleanup, с reference counting на стороне сервера или клиента — на выбор),
     - `PUT /links/{linkId}/devices/{deviceId}/pubkey` (publish Pub),
     - `GET /links/{linkId}/devices/{deviceId}/pubkey` (fetch peer Pub).
  2. Заменить адаптеры:
     - `WorkerEncryptedMediaStorage` → `HttpEncryptedMediaStorage` против собственного backend'а (порт `EncryptedMediaStorage` не меняется — rule 1 CLAUDE.md). Если backend держит S3-compatible storage — даже client-side изменения тривиальны.
     - `FirestoreDeviceIdentityRepository` → `HttpDeviceIdentityRepository` (порт `DeviceIdentityRepository` не меняется).
  3. Перевозить blob'ы пачками: новые → сразу на свой backend; старые → background reconciler читает из Firebase, перекладывает на свой backend, верифицирует MAC, удаляет из Firebase.
  4. Envelope format и cipherSuite остаются прежними. Перешифровка blob'ов **НЕ требуется**.
  5. Server-side reference counting через Postgres транзакцию (вместо client-side `BlobReferenceLedger` SQLite) — атомарная гарантия «удалить blob если refCount = 0».
- *Когда поедет:* запуск отдельного backend проекта (планируется после спека ~35, по словам пользователя 2026-05-22). Не привязано к Firebase Storage 5 GB лимиту — переезд **в любом случае** до production-релиза с реальными пользователями.
- *Затрагивает спеки:* 011 (e2e-crypto-foundation), 012 (photos), 013 (двусторонние пары), 014 (групповые ключи), 015 (multi-device), 016 (key rotation), будущий Jitsi-integration spec, будущий vendor-integration spec, будущий hardware-integration spec.
- *Зависимости:* выбор backend stack (Kotlin/Ktor предпочтителен — KMP harmony с клиентом); схема БД для blob metadata + device identity; миграция data из Firebase в свой backend.

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

### RECOVERY + KEY MANAGEMENT (spec 018 / F-5 + S-2)

**SRV-RECOVERY-001: Own-server recovery key vault** (replaces Cloudflare Worker MVP adapter) + atomic brute-force counter.
- *Сейчас (MVP adapter, deployed 2026-06-29 per spec [task-6-root-key-hierarchy-recovery](../../specs/task-6-root-key-hierarchy-recovery/)):* recovery key пользователя зашифрован его passphrase'ом (Argon2id + AEAD) и лежит в **наших Cloudflare Worker'ах** — endpoint live на `https://launcher-backup-v1.gpt1-jeromwork.workers.dev`. **Storage — Workers KV** (binding `RECOVERY_BLOBS`, id `13e77e09a77a4faba6e743f1a59feff0`), **не R2**: владелец не хотел привязывать payment-method к Cloudflare account'у, а R2 даже на free tier требует confirmed billing. KV free tier (1k writes/day, 25 MiB-per-value) покрывает workload с огромным запасом — один blob ~500 байт, один write на установку. Eventual-consistency окно KV (~60s globally) acceptable: cross-device recovery — это flow «создал blob на устройстве A → день/недели → ставлю на B», задержка между write и read измеряется часами, не секундами. Аутентификация — Firebase JWT verified через `workers/_shared/auth-jwt/`. Identity-init worker `launcher-identity-v1` (live на `https://launcher-identity-v1.gpt1-jeromwork.workers.dev`) минтит UUID v4 как `claims.stableId` через Identity Toolkit `setCustomAttributes` REST API + пишет binding в Firestore `/identity-links/{uid}`. Backup worker сверяет `claims.stableId` (или `claims.uid` как fallback до того как identity-init отработал) с path'овым `{stableId}` (403 при несовпадении). Brute-force защита — Argon2id work-factor (interactive params 64 MiB / 3 iter / 1 par, **measured 198ms on Xiaomi 11T 2026-06-29 — 7.5× margin к 1500ms threshold**) + per-identity DataStore counter на клиенте (3 попыток / hour sliding window) + per-stableId in-memory rate-limit в Worker'е (POST 10/5min, GET 5/5min, DELETE 5/5min, `Retry-After` header). **Server-side rate-limit пока in-memory в Worker'е** — сбрасывается при перезапуске instance, обходится сменой IP. Этот недостаток — explicit MVP trade-off, закрывается в SRV-RECOVERY-001 (a-d) ниже.
- *Worker (MVP) гарантирует Article XIV §7 minimization:* (i) path содержит только `stableId` (наш UUID, **не** Google sub / email / phone); (ii) blob — ciphertext + KDF params, никакого plaintext PII; (iii) Worker access logs (Cloudflare) видят только `{stableId, timestamp, IP}` — кросс-юзерные отношения (pairing, family group) **не реконструируются** из логов backup endpoint'а; (iv) при compromise Cloudflare access logs атакующий получает мало полезного — IP+stableId+timestamp недостаточно для reidentification без отдельного источника stableId→identity маппинга.
- *Migration на собственный сервер (рекомендуется при triggers):*
  - (a) **`HttpRecoveryBackupStorage` adapter** поверх REST API собственного backend'а; wire format `RecoveryKeyBackupBlob` (JSON + schemaVersion=1) остаётся прежним — миграция blob'ов через background reconciler / dual-write window;
  - (b) явный audit log access (наши логи, не Cloudflare);
  - (c) no third-party data residency;
  - (d) **persistent atomic counter** для rate-limit — server-side counter привязан к `stableId` в БД (PostgreSQL), нельзя обнулить через Clear App Data / IP change / factory reset. Cost атаки = N attempts/hour, нет workaround'а;
  - (e) защита от schema-version downgrade (per SRV-RECOVERY-002 below);
  - (f) Article XIV §7 (b)+(c): persistent records хранят ciphertext + минимум routing-metadata; нет shared rooms / cross-user тэгов.
- *Когда поедет:* любой из триггеров — (1) первый incident-report «попытка взлома recovery» в beta; (2) Cloudflare KV/R2 free tier превышен; (3) requirement на audit log / data residency / GDPR compliance; (4) приезд PostgreSQL/own-VPS по другому компоненту (push-worker / config sync); (5) launcher выходит из MVP в production.
- *Architectural seam уже на месте:* `RecoveryKeyBackup` port в `core/keys/` — adapter swap (`WorkerRecoveryKeyBackup` → `HttpRecoveryBackupStorage`) **без** переписывания F-5 кода. Это и есть **exit ramp destination** (rule 3 CLAUDE.md) для one-way door'а «Worker как MVP backup storage».

**SRV-CRYPTO-005: Out-of-band fingerprint verification (Safety Number screen в pairing'е).**
- *Сейчас:* QR pairing полностью «чёрный ящик» с точки зрения user'а — никакого fingerprint сравнения двух pubkey'ев. Ghost device attack vector: Firebase / atttacker с server-write-access может тихо подменить pubkey recipient'а. Accepted risk per F-5 clarify 2026-06-19 («чем тупее сервер, тем лучше»).
- *Сервер должен:* добавить **endpoint для verified pubkey publication** (admin pubkey подписан собственным private key admin'а + server validates signature перед публикацией) + UI flow: admin и Managed видят 4-6-цифровой fingerprint (SHA-256 от обоих pubkey'ев, первые 24 бит), сверяют голосом / визуально перед confirmation. Закрывает Signal / WhatsApp «Safety Number» паттерн.
- *Когда поедет:* любой из триггеров (1) реальный incident ghost device attack, (2) compliance требование (EU privacy directive), (3) переезд на свой сервер — где-то в этом окне.

**SRV-CRYPTO-008: Algorithm migration job + schema-version migration policy для устаревших crypto primitives** (renumbered from SRV-CRYPTO-007 due to ID conflict with line 422 entry on encrypted_backup storage).
- *Сейчас:* F-5 использует XChaCha20-Poly1305 (AEAD) + Argon2id (KDF). Wire-format содержит `algorithm: String` + `schemaVersion: Int` fields, позволяющие сосуществование версий. Clients могут читать старые и новые форматы (forward-compat). Защита от downgrade attacks (H-2) через Firestore Rule `schemaVersion >= existing` (FR-028a) + client TOLU (FR-028b). Миграция existing data (re-encryption под новый algorithm) — manual / отдельная спека.
- *Сервер должен:* предоставлять server-triggered batch job: (a) для каждого user'а с outdated `algorithm` в `users/{uid}/recovery-key` — попросить клиент пере-wrap'ить root key под новый KDF (требует клиентского участия — passphrase должен быть введён, чтобы расшифровать старый); (b) для каждого SealedConfig в `users/{uid}/config` — re-encrypt под новый AEAD; (c) трекинг migration progress per UID; (d) deadline для clients, не мигрировавших — после deadline принудительный force-update.
- *Migration policy (WhatsApp E2E backup pattern, per FR-028c в spec 018)*:
  - **Phase 1 — Coexistence**: новые клиенты пишут v2, старые продолжают читать v1. Защита от downgrade: Firestore Rule (FR-028a) + client TOLU (FR-028b) уже работают с дня релиза v2.
  - **Phase 2 — Auto-migration (months)**: новые клиенты при каждом успешном recovery (когда пользователь ввёл passphrase) **автоматически** re-encrypt root key в v2 и записывают обратно. Через несколько месяцев большинство данных мигрированы.
  - **Phase 3 — Deprecation (after ~12 месяцев)**: клиенты v2+ отказываются читать v1, запрашивают forced app update. Min app version повышается в Play Store metadata.
- *Когда поедет:* когда XChaCha20-Poly1305 или Argon2id будут официально deprecated. Realistically — через 5-10 лет, но spec уже зарезервировал место.

**SRV-CRYPTO-006: Server-side ghost device detection / forward unsharing.**
- *Сейчас:* удалённый из пары admin теряет доступ к **новым** версиям конфига, но **старые** версии (если они когда-нибудь были) остаются доступны под его ключом. Accepted limitation MVP.
- *Сервер должен:* при unpair триггерить re-encryption всех existing config snapshots под новым CEK без removed recipient'а (forward unsharing). Требует server-side coordination.
- *Когда поедет:* future spec, скорее всего одновременно с SRV-CRYPTO-005.

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
| 2026-05-22 | Добавлен SRV-CRYPTO-001 (универсальный маршрут переезда крипто-инфраструктуры на свой backend) — независимо от Firebase Storage лимитов | Spec 011 mentor-сессия |
| 2026-05-25 | SRV-CRYPTO-001 rev. 2 — переключение storage с Firebase Storage (требует Blaze) на Backblaze B2 + Cloudflare Worker proxy. Free tier 10 GB, без credit card. Exit ramp сохранён через S3-compatible API. | Spec 011 mentor-сессия по billing |
| 2026-05-28 | Добавлены SRV-CMD-002 (Firestore Transactions inadequacy для membership ops), SRV-SEC-005 (Firestore Rules complexity → server-side auth logic), SRV-INFRA-003 (Cloudflare Worker CPU time limit), SRV-DEV-001 (staging environment), SRV-CRYPTO-002 (manual key rotation FUTURE-SPEC-010) | Pre-F-1 mentor critique walkthrough |
| 2026-06-19 | Добавлены SRV-RECOVERY-001 (own-server recovery key vault, replaces Firestore-based), SRV-CRYPTO-005 (out-of-band fingerprint verification / Safety Number screen), SRV-CRYPTO-006 (forward unsharing при removal admin'а), SRV-CRYPTO-007 (algorithm migration job для устаревших primitives) | F-5 spec 018 clarify session — multi-admin envelope перенесён в S-2, F-5 redefined как root key hierarchy + recovery |
| 2026-06-26 | Добавлен SRV-SEC-006 (server-at-rest encryption + Shamir N-of-M threshold unsealing + remote attestation + hardware-backed Cloud KMS). Создан отдельный файл [`server-requirements.md`](server-requirements.md) с консолидированными требованиями launcher'а к серверу (уровень абстракции «что launcher ожидает», отдельно от operational roadmap). | Mentor-сессия 2026-06-26 — threat-model «server stolen / code stolen» впервые explicit |

---

## SRV-CMD-002: Atomic membership operations через настоящие ACID transactions

**Контекст**: F-1 server arbitration делает membership ops (add / remove / promote / kick) через Cloudflare Worker + Firestore. Firestore Transactions работают, но cross-document атомарность ограничена. При сложных multi-document membership ops — **race conditions** возможны.

**MVP workaround**: Firestore Transactions для single-document ops + optimistic locking через `lastModified` field. Acceptable для семейного scale.

**Own-server destination**: настоящие ACID transactions across multiple tables / documents (Postgres / SQLite).

**Inline TODO в F-1**: `// TODO(SRV-CMD-002): migrate membership ops to own server with proper cross-document ACID transactions when scale exposes race conditions`.

## SRV-SEC-005: Firestore Security Rules complexity → server-side authorization

**Контекст**: Family Group + envelope encryption + role-based access делают Firestore Rules **очень сложными** (потенциально тысячи строк). Bug в rules = privacy breach.

**MVP workaround**: comprehensive Rules unit tests через Firebase Rules Test SDK + code review on Rules changes.

**Own-server destination**: authorization logic в обычном коде (typed, debuggable, fully testable).

**Trigger**: первый Rules-related privacy bug → ускоряем переход.

### Governance question — когда именно переезжаем (added 2026-07-08)

Это не блокирует MVP приложения — крипта-архитектура и приложение развиваются независимо. Но чтобы **не пропустить момент**, требуется конкретный сигнал.

**Не решаем сейчас** (нет backlog task — этот вопрос откроется когда команда возьмётся за server-side развитие). Ниже — inputs для будущего decision:

- **Metric candidates** для complexity threshold:
  - LOC в `firestore.rules` — превысило N (~500? ~1000?).
  - Cyclomatic complexity одного правила — количество `&&`/`||`/nested `if` больше M.
  - Depth вложенных `get()` calls — правило читает документ, читающий документ, ... > 3 уровней = red flag.
  - Количество коллекций покрываемых правилами.
  - Скорость роста — за квартал добавили > K новых условий или > L rule tests.

- **Event triggers** параллельно с metric:
  - Первый Rules-related privacy bug в production (уже declared выше).
  - Rules test coverage упал < 80% (стало нельзя писать тесты быстрее чем правила).
  - Первый feature, где Rules физически недостаточно (payments, complex sharing, cross-account operations).

- **Migration path** (когда trigger сработал):
  - Порядок endpoint'ов — от highest attack surface (auth, membership) к простейшим.
  - **Coexistence period** — как долго Rules + Worker работают параллельно (probably weeks per collection).
  - **Kill switch** — как быстро откатиться если migration нашла проблему.

- **Governance workflow**:
  - Кто мониторит metric — CI job раз в неделю? Manual review каждый квартал?
  - Куда пишется результат мониторинга — новый server-roadmap entry? Отдельный dashboard?
  - Threshold override — что делать если 90% threshold но нет ресурсов мигрировать?

**Когда решается**: при создании первого server-side backlog task'а (например F-2 Capability Registry или отдельный «Migrate to own server phase 1»). До тех пор — работаем над приложением, крипта-decisions не блокируются этим вопросом.

## SRV-INFRA-003: Cloudflare Worker CPU time limit

**Контекст**: Cloudflare Workers free tier = 10ms CPU per request, paid = 50ms. F-1 membership ops (signature verify + Firestore txn + FCM update) могут приблизиться к limit.

**MVP workaround**: profile в F-1, optimize hot path, split async через durable objects если нужно.

**Own-server destination**: unlimited CPU per request, proper distributed system primitives.

## SRV-DEV-001: Staging environment

**Контекст**: production debugging без staging environment — нельзя безопасно воспроизвести user-reported bugs.

**MVP workaround**: setup до S-6 release. Существующий TODO-OPS-004 в backlog → должен быть закрыт до production.

**Связанная задача (вне server-roadmap)**: предложить `checklist-dev-experience` skill для spec-kit — при написании каждой spec'и проверять, что local dev tools + reproducibility учтены (см. proposal в roadmap Cross-cutting section).

## SRV-CRYPTO-002: Manual key rotation flow (FUTURE-SPEC-010)

**Контекст**: если admin's `priv` leaked — нужна возможность rotate keys без потери всех данных. В MVP — нет infrastructure.

**MVP workaround**: account deletion (S-6) — start fresh, accept data loss как security reset. Document в Privacy Policy.

**Own-server destination**: atomic key rotation flow:
- Generate new keypair locally на admin device.
- Server-side re-wrap K (envelope wrappers) для new pub (требует server-side decrypt access? **NO** — лучше re-encrypt на client после download, через ACID-coordinated transaction).
- Atomic switch identity reference.
- Old priv invalidated, old wrappers garbage-collected.

**Research finding (pre-F-1 mentor walkthrough)**: Signal Double Ratchet — overkill для at-rest encryption (designed for real-time message streams). Matrix Megolm periodic rotation — closer pattern, но still overhead. **Recommendation: on-demand manual rotation, не automatic**. Implementation as separate spec FUTURE-SPEC-010, post-MVP.

**Sources**: [Signal Double Ratchet spec](https://signal.org/docs/specifications/doubleratchet/), [Matrix Cryptographic Analysis](https://arxiv.org/pdf/2408.12743v1).

## SRV-CFG-006: Named configs persistence (F-014.1 backup + F-014.2 encryption)

**Контекст**: F-014.0 (текущий phase) хранит admin'ские named configs **локально** в DataStore Preferences. F-014.1 планируется как backup на Firestore в `/admin-self-configs/{adminUid}/configs/{configName}/current`. Это сохраняет vendor lock-in (Firebase) и платит за cross-device sync free-tier Spark limits.

**MVP workaround**: F-014.0 local-only работает без Firebase. F-014.1 server backup depends on F-4 (Google Sign-In + AuthProvider) и вводит Firestore `/admin-self-configs/{adminUid}/configs/{configName}` collection с Security Rules: write only by `ownerUid`. Atomic single-default invariant (FR-003a) через Firestore transaction. Auto-delete орфан-configов (FR-003b) **не реализуется в F-014.1** — только UI marker; real auto-delete deferred (TODO-FUTURE-SPEC-008).

**Own-server destination**:
- `NamedConfigsLocalStore` port остаётся неизменным; добавляется parallel `RemoteNamedConfigsStore` port, реализация сменяется Firestore→REST.
- Cost-of-swap: ≤1 module (новый adapter), ConfigEditor port (спека 008) untouched.
- Real auto-delete орфан-configов через server-side cron (TODO-FUTURE-SPEC-008).

**Inline TODO в F-014**:
```kotlin
// TODO(server-roadmap): F-014.1 add RemoteNamedConfigsStore adapter;
// merge local + remote at use site via MergedNamedConfigsRepository.
```

**Trigger**: F-4 ships (Google Sign-In) → unblocks F-014.1 server backup phase. F-5 (E2E encryption) работает с этими documents автоматически без изменений в wire format.

**Source**: [spec 014 plan.md §11.4](../../specs/014-tile-editing-admin-senior-profiles/plan.md), [research.md §2](../../specs/014-tile-editing-admin-senior-profiles/research.md), [data-model.md §10](../../specs/014-tile-editing-admin-senior-profiles/data-model.md).


## SRV-CONFIG-001: NetworkConfigSource for wizard manifests (F-3 + later phases)

**Контекст**: F-3 (spec 015) ships `BundledConfigSource` only — wizard manifests, tile sets, screen layouts, system-settings pools, and UI-customization pools come from the APK assets. Users can't pull a fresh `tile-set` ("neighbours" variant for a specific care family) without app update.

**MVP workaround**: bundled assets cover the launch baseline. Marketplace import is OUT for F-3 (per OUT scope в spec.md).

**Own-server destination**:
- `NetworkConfigSource: ConfigSource` adapter fetches signed configs from our server via `https://api.launcher.app/v1/configs/{kind}/{id}`.
- Signature scheme: Ed25519 signed manifest envelope; client verifies against pinned public key.
- Cost-of-swap: ≤1 module (new adapter in `:core/androidMain/adapters/wizard/`), wire-format contract identical, `BundledConfigSource` stays as offline fallback.

**Inline TODO в F-3**:
```kotlin
// TODO(server-roadmap, SRV-CONFIG-001): NetworkConfigSource fetches signed
// configs from own server. See FR-046. Bundled stays as offline fallback.
```

**Trigger**: when the first care-family marketplace ships, or when push of an updated tile-set without app update is required.

**Source**: [spec 015 plan.md §8](../../specs/015-wizard-localization-senior-ui/plan.md), [FR-046 в spec.md](../../specs/015-wizard-localization-senior-ui/spec.md).


## SRV-PREFS-001: UserPreferences cloud sync (F-3 → spec 008)

**Контекст**: F-3 (spec 015) `UserPreferences` (theme, fontScale, languageOverride, attestedSettings, wizardCompletedAppFamilies) persisted locally в DataStore. Если у пользователя несколько устройств (managed phone + tablet) — настройки не синхронизируются.

**Own-server destination**:
- F-4 + spec 008 add `ConfigDocument.userPreferences` slot — F-3 store migrates into it.
- `UserPreferencesStore` port stays unchanged; impl swaps from `PersistentUserPreferencesStore` (DataStore) to `ConfigDocumentUserPreferencesStore` (reads/writes ConfigDocument via spec 008 ConfigEditor).
- Cost-of-swap: ≤1 file rewrite + migration tool that copies DataStore → ConfigDocument once.

**Inline TODO в F-3**: см. `core/src/commonMain/kotlin/com/launcher/api/wizard/UserPreferences.kt`.

**Trigger**: F-4 ships (Google Sign-In) + spec 008 ConfigEditor supports `userPreferences` field.

**Source**: [spec 015 FR-051](../../specs/015-wizard-localization-senior-ui/spec.md).


## SRV-TRANSLATE-001: Server-side translation cache (F-3 long-term)

**Контекст**: F-3 translation pipeline (`procedure-translate-spec-strings` skill) calls Anthropic Claude API directly from a developer's workstation. Costs are billed to the developer's API key; quality cannot be reviewed before commit; multiple developers re-translate the same key independently.

**Own-server destination**:
- `/api/translate` proxy endpoint with: (a) server-cached translations keyed by `(source, target_locale, key, context_hash)`; (b) human review queue for AR/HI/ZH/JA/KK; (c) shared API key (one bill, not per-developer).
- Skill calls our server, falls back to direct Anthropic API if server is down.

**Trigger**: when the translation skill is used by more than one developer or when AR/HI/ZH/JA/KK quality issues land in beta feedback.

**Source**: [.claude/skills/procedure-translate-spec-strings/SKILL.md](../../.claude/skills/procedure-translate-spec-strings/SKILL.md).

## SRV-CRYPTO-003: Paid security audit milestone (F-CRYPTO, billing gate)

**Контекст**: F-CRYPTO выпускается с **measurable validation set** (RFC KAT + Google Wycheproof + property tests + industrial reference baseline Signal/WhatsApp/age/Threema). Это **достаточно** для public beta launcher'а без billing. Однако для запуска **paid tier** (subscription, payments, premium features) индустриальный baseline требует **independent paid audit**.

**MVP workaround (текущее F-CRYPTO release)**: validation set описанный выше + Signal/WhatsApp как reference stack. Cost = $0.

**Own-server destination / billing-gate destination**:
- **Targeted security review** через [7ASecurity](https://7asecurity.com) (~$5-10k) или [Radically Open Security](https://www.radicallyopensecurity.com) (~€8-12k) на крипто-код (`core/crypto/` + ConfigCipher + media blob encryption).
- Scope: review libsodium binding usage, key storage wrap pattern, key derivation flows, nonce policy, replay protection.
- Output: report с findings + remediation, публикуется на нашем сайте (transparency).

**Trigger**: за 4-6 недель до планируемого запуска **paid tier** / subscription (S-10 Subscription Server Timer). Не блокирует beta release, S-1..S-8 (free, non-billing).

**Inline TODO в F-CRYPTO spec**: `// TODO(SRV-CRYPTO-003): paid security audit required before billing launch — see docs/dev/server-roadmap.md`.

**Источник**: F-CRYPTO mentor session 2026-06-17 — solo-dev без сети криптографов; friend crypto review снят; платный аудит перенесён на billing gate.

## SRV-CRYPTO-004: Multi-device recovery via social recovery (future spec TBD, per ADR-008)

> **Numbering note 2026-06-18**: This entry previously referenced future multi-device-recovery spec (TBD). future multi-device-recovery spec (TBD) has been reassigned to F-4 AuthProvider + Google Sign-In. The multi-device-recovery spec will receive its number at `/speckit.specify` time. Все упоминания "future multi-device-recovery spec (TBD)" / "future multi-device-recovery spec (TBD)" ниже читать как "future multi-device-recovery spec (TBD)".

**Контекст**: F-CRYPTO предоставляет `KeyEscrow` port (interface-only, real-impl = stub). Real-implementation — future multi-device-recovery spec (TBD) (multi-device-recovery) per [ADR-008](../adr/ADR-008-social-recovery-architecture.md). **Не passphrase-only escrow** (это было бы слабо: атакующий с access к Firestore + слабый PIN бабушки = взлом). Решение: **social recovery** — multi-factor через **(passphrase бабушки) + (2FA confirmation от trusted peer) + (email auth)**.

**MVP workaround (F-CRYPTO)**: `KeyEscrow.export()` / `restore()` ports есть, но real-impl = stub; F-CRYPTO лишь предоставляет примитивы (`KeyDerivation` HKDF, `AeadCipher` XChaCha20, `AsymmetricCrypto.sealCEK`/`unsealCEK`), на которых будет построен flow. Если пользователь теряет телефон **сейчас** (до future multi-device-recovery spec (TBD)) — потеря ключей; documented limitation для beta.

**Own-server destination / отдельная спека (future multi-device-recovery spec, TBD)**:
- Setup phase: бабушка задаёт PIN; генерируется `peer_nonce` (32 байта); `recovery_key = HKDF(passphrase, peer_nonce, "launcher-recovery-aead-v1")`; `encrypted_backup = AEAD(recovery_key, priv_keys_bundle)`; `encrypted_backup → сервер`, `peer_nonce → encrypted_for_peer (через sealCEK)`, `PIN → в голове бабушки`.
- Recovery phase: новое устройство → email auth → server initiates 2FA push к trusted peer → peer тапает «подтвердить» → peer's device пере-sealCEK'ает `peer_nonce` для freshly-generated Pub нового устройства → новое устройство просит PIN → derives recovery_key → decrypts backup → priv keys восстановлены.
- **3-фактор**: знание PIN + знание email/password + физическое подтверждение от peer-device.
- Атомарное активирование новых ключей + invalidation старых.

**Open design questions для future multi-device-recovery spec (TBD)** (важно для архитектуры сейчас):
- **Где хранить `encrypted_backup`** — Firestore document `/backups/{externalId}` (size limit 1 MiB, достаточно для ключей) или Firebase Storage `/backups/{externalId}/v1` (для будущей extension под larger payload). **Главное ограничение от владельца 2026-06-17**: «структура должна **легко переезжать** на собственный сервер». Это означает: что бы мы ни выбрали, abstrahировать через `RecoveryBackupStorage` port в `core/recovery/api/` — тогда переезд = новый adapter, не переписывание. **См. SRV-CRYPTO-007**.
- TTL для `peer_nonce` — статический или 90-дневная rotation.
- Multi-peer (Shamir N-of-M) — MVP 1-of-N, future feature.

**Trigger**: после F-5 + S-3 (re-pairing flow) + F-4 (AuthProvider для email auth) + F-CRYPTO (все примитивы).

**Источник**: ADR-008 social recovery architecture (2026-05-23), reconfirmed F-CRYPTO mentor session 2026-06-17 (владелец напомнил про multi-factor recovery).

## SRV-CRYPTO-006: Server-side rate-limiting на recovery attempts (post-future-recovery-spec)

**Контекст**: future multi-device-recovery spec (TBD) (multi-device-recovery) допускает попытки восстановления. Без rate-limit'а атакующий с компрометированными email+password может brute-force'ить PIN бабушки 4-6 цифр (10^4 - 10^6 попыток).

**MVP workaround (future multi-device-recovery spec (TBD) baseline)**: client-side rate-limit (delay между попытками) + Firestore Security Rules на максимум N попыток в hour per externalId. Acceptable для MVP но **обходимо**: атакующий может удалить app data, обнулить client-side counter, продолжить.

**Own-server destination**:
- Atomic counter в Cloudflare KV или Firestore transaction: block after N failed attempts в час per externalId.
- Push notification бабушке (через trusted peer): «Кто-то пытался восстановить ваш аккаунт».
- Audit log: attempted recovery from {platform, IP-hash, timestamp}.

**Trigger**: первый incident-report «недопустимая попытка взлома recovery» в beta.

**Источник**: ADR-008 §Future enhancements + F-CRYPTO mentor session 2026-06-17.

## SRV-CRYPTO-007: Storage для encrypted_backup — substitution-ready (future multi-device-recovery spec, TBD)

**Контекст**: future multi-device-recovery spec (TBD) будет хранить `encrypted_backup` на сервере. На момент дизайна — кандидаты Firestore document vs Firebase Storage. **Constraint от владельца 2026-06-17**: «выберите так, чтобы потом легко переехали на собственный сервер». Это **substitution-readiness** (checklist-backend-substitution rule).

**MVP workaround**: в future multi-device-recovery spec (TBD) выбираем один из двух (Firestore document — проще для MVP, 1 MiB limit достаточно для priv keys bundle). **Но**: abstrahируем через `RecoveryBackupStorage` port в `core/recovery/api/`:

```kotlin
interface RecoveryBackupStorage {
  suspend fun upload(externalId: ExternalId, schemaVersion: Int, blob: ByteArray): Result<Unit>
  suspend fun download(externalId: ExternalId): Result<EncryptedBackup>
  suspend fun delete(externalId: ExternalId): Result<Unit>
}
```

MVP adapter — `FirestoreRecoveryBackupStorage`. Domain (HKDF derivation, AEAD encrypt/decrypt, sealCEK/unsealCEK) **не знает** про Firestore.

**Own-server destination**: `HttpRecoveryBackupStorage` adapter поверх REST API собственного backend'а. Wire format `encrypted_backup` (JSON или CBOR с `schemaVersion`) остаётся прежним — миграция blob'ов через background reconciler как в SRV-CRYPTO-001.

**Trigger**: переход на собственный backend (после spec ~35 per backlog).

**Источник**: F-CRYPTO mentor session 2026-06-17 — владелец явно потребовал «легко переезжать на свой сервер».

## SRV-CRYPTO-005: Server-side re-encryption for key rotation (post-F-5)

**Контекст**: F-CRYPTO предоставляет `KeyRotation` port (interface-only). При реальной ротации **identity key** admin'а — все исторические зашифрованные config'и (`/config/history/`) и media blob'ы нужно перешифровать новым ключом, чтобы старый retired key можно было удалить.

**MVP workaround (F-CRYPTO)**: `KeyRotation.rotateIdentityKey()` port есть, но real-impl = stub. Если ротация **нужна сейчас** — retired keys остаются в `keyHistory()` для decryption старых ciphertext'ов; новые writes — новым ключом. Acceptable но накапливает retired keys.

**Own-server destination**:
- Server-side batch job re-encrypts `/config/history/*` под новый recipient key.
- Atomic rotation: новые keys активны + старые ciphertext'ы перешифрованы + retired keys deleted.
- Reference counting: server отслеживает, какие ciphertext'ы ссылаются на какие retired keys.

**Trigger**: первая реальная ротация identity key (например, suspected compromise сценарий) или regulator requirement (GDPR right-to-erasure требует cryptographic erasure).

**Источник**: F-CRYPTO mentor session 2026-06-17 — scenario D (suspected compromise) обсуждён.

## SRV-AUTH-001: Auth credential exchange — Firebase → own backend (spec 017 F-4)

**Контекст**: `GoogleSignInAuthAdapter.signIn()` напрямую вызывает Firebase
`signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))`.
Это означает что Google ID Token уходит к Firebase, не к нам. Когда мы
поедем на свой backend, exchange должен идти через `POST /auth/google-signin`
к нашему серверу, который сам проверит Google ID Token и выпустит наш
session token.

**MVP workaround**: Firebase Auth — convenient, free, batteries-included.
Vendor lock-in для auth flow, но `AuthProvider` port abstrahирует так,
что adapter swap = новая реализация без переписывания consumer'ов
(CLAUDE.md §1, §2).

**Own-server destination**:
1. Backend endpoint `POST /auth/google-signin` принимает Google ID Token.
2. Verifies через Google Token Info API (или JWKS public key).
3. Issues наш JWT с claim `stableId`.
4. `GoogleSignInAuthAdapter.signIn()` переключается на наш endpoint,
   убирает Firebase exchange.

**Effort estimate**: 3-5 дней (server endpoint + client switch + tests).

**Trigger**: own-server cutover (Phase 2+, post-MVP) ИЛИ Firebase pricing change.

**Источник**: Spec 017 F-4 AuthProvider (2026-06-18).

## SRV-AUTH-IDENTITY-001: Identity-links migration (spec 017 F-4)

**Контекст**: `identity-links/google/{providerAccountId}` — Firestore collection,
authoritative mapping Google `sub` → наш `stableId` UUID. Если переключим
storage без миграции существующих ссылок, у вернувшихся пользователей
сгенерируется новый UUID, что сломает delegation/pair-key.

**MVP workaround**: Firestore — convenient atomic transaction для
race-safe lookup-or-create.

**Own-server destination**:
1. Backend table `identity_links` (3 поля + providerKind + providerAccountId).
2. One-time export Firestore `identity-links/google/*` → SQL.
3. Client switches lookup endpoint к нашему backend.
4. Read-only режим Firestore identity-links collection ещё 30 дней
   как fallback.

**Effort estimate**: 1 week dev + 1 day migration window.

**Trigger**: own-server cutover. **MUST migrate first**, чтобы preserve
UUID stability.

**Risk if skipped**: stableId UUIDs могут разойтись между client
expectations и server state → broken delegations, broken config-sync.

**Источник**: Spec 017 F-4 AuthProvider research.md §R4.

## SRV-AUTH-IDENTITY-002: Server-verified googleSub custom claim (spec 017 F-4)

**Контекст**: `firestore.rules` для `identity-links/google/{sub}` сейчас
проверяет `request.auth.uid == providerAccountId`. Это требует чтобы
Firebase Auth UID совпадал с Google `sub` claim. Firebase **не делает это
автоматически** — работает потому что Firebase SDK устанавливает UID =
первый providerData entry UID, а GoogleAuthProvider даёт
`providerData.uid = google.sub`. Это **fragile**.

**MVP workaround**: rely on SDK behaviour, document fragility в Rules
comments. Backed by Rules unit tests (`firestore-tests/rules.auth.test.ts`).

**Own-server destination**:
1. Cloud Function triggered on user create.
2. Извлекает Google `sub` claim из `additionalUserInfo`.
3. Устанавливает Firebase Auth custom claim `googleSub`.
4. Firestore Rule меняется на `request.auth.token.googleSub == providerAccountId`.

**Effort estimate**: 2 дня + production rollout.

**Trigger**: до production launch (P0 hardening) — это блокер для billing tier.

**Risk if skipped**: если Firebase SDK поменяет UID generation strategy,
identity-links rules станут insecure (любой пользователь сможет
претендовать на чужой Google sub).

**Источник**: Spec 017 F-4 firestore.rules.

## SRV-CRYPTO-PARAMS-REVIEW: Argon2id parameters periodic review cadence (spec 018 F-5, H-5)

**Контекст**: F-5 использует Argon2id с interactive params (64 MiB / 3 iter / 1 par) — OWASP 2024 рекомендация. К 2030 году рекомендация скорее всего поднимется. Wire-format уже поддерживает upgrade (`RecoveryVaultBlob.kdfParams`).

**Требование**: review Argon2id params каждые 2 года против OWASP актуальных рекомендаций. **Next review due: 2028-06**.

**Process**:
1. Сверить с OWASP Password Storage Cheat Sheet.
2. Если params устарели — bump в `Argon2idPassphraseKdf` default params; existing vaults читаются со старыми params из blob field, новые setup'ы — с новыми.
3. Если algorithm устарел (Argon2id deprecated) — отдельная migration spec (FR-033, SRV-CRYPTO-007).

**Источник**: Spec 018 F-5 analyze-report.md 2026-06-19 (H-5 finding).

## SRV-STORAGE-001: EnvelopeStorage own-server replacement (spec 018 F-5b)

**Контекст**: F-5b `EnvelopeStorage` сейчас реализован через
`FirestoreEnvelopeStorage` (`app/src/realBackend/.../data/envelope/`). Один
`Envelope` document = одна Firestore document по пути
`/users/{namespace}/data/{escapedKey}`. Atomic write/read через Firestore SDK.

**Требование при переезде на свой сервер**: REST endpoint:
```
PUT  /users/{namespace}/data/{key}          ← envelope JSON body
GET  /users/{namespace}/data/{key}          ← returns envelope JSON
LIST /users/{namespace}/data?prefix={p}     ← returns list of keys
DELETE /users/{namespace}/data/{key}
```

С JWT auth (Bearer token). Server-side validation: schemaVersion monotonic
increase (для downgrade defence), envelope shape (Maps/Blobs typed).

**Path migration**: domain port `EnvelopeStorage` не меняется. Меняется только
`OwnServerEnvelopeStorage` adapter; existing data migrate'ится через одноразовый
ETL: Firestore → REST upload.

**Источник**: Spec 018 F-5b Batch 2 inline TODO.

## SRV-PKD-001: PublicKeyDirectory own-server replacement (spec 018 F-5b)

**Контекст**: F-5b `PublicKeyDirectory` хранит per-device X25519 public keys
и access-grants. Сейчас Firestore через `FirestorePublicKeyDirectory`. Доступ
к чужой directory protected through Security Rules (`hasActiveGrant`).

**Требование при переезде на свой сервер**: REST endpoints:
```
PUT  /users/{uid}/devices/{deviceId}/pub-key   ← X25519 pub bytes
GET  /users/{uid}/devices                       ← list of (deviceId, pubKey) with grant check
GET  /users/{uid}/access-grants                 ← list of grant holders
PUT  /users/{ownerUid}/access-grants/{helperUid} ← create grant (owner only)
DELETE /users/{ownerUid}/access-grants/{helperUid}
```

Server-side enforces:
1. Owner-only write to own pub-key entries.
2. Helper read of owner devices iff non-revoked grant exists.
3. Atomic grant create/revoke (transactional).

Server-side grant state simplifies client logic: no race conditions при concurrent
grant change. Каждое client device subscribes to push notification "your grant
status changed" → pulls fresh state.

**Migration**: domain port unchanged. Existing Firestore directory bulk-exported
+ replayed into own server during cutover.

**Источник**: Spec 018 F-5b Batch 2 inline TODO.

## SRV-DEVICEID-001: DeviceId allocation collision-resistance (spec 018 F-5b)

**Контекст**: F-5b `AndroidDeviceIdentity` allocates DeviceId как 16 random
bytes (hex). Collision probability астрономически мала (2^64 для half-collision
по birthday bound), но это всё ещё **client-side allocation** — нет глобальной
гарантии uniqueness между UID'ами.

**Требование при переезде на свой сервер**: server-allocated DeviceId через
endpoint:
```
POST /users/{uid}/devices/allocate   ← server returns unique deviceId
```

Server transactionally reserves deviceId in directory с проверкой of uniqueness
within namespace. Eliminates collision residual entirely.

**Альтернатива (cheaper)**: server validates client-proposed deviceId на
collision при `publishMyDevice`; rejects with 409 Conflict если занят.

**Migration**: domain port unchanged (`DeviceIdentity.thisDeviceId()` continues
to return stable string). Implementation в `AndroidDeviceIdentity` switches
from local random → server-allocated.

**Risk if skipped**: client с broken RNG может collide DeviceId existing'у в
своём namespace, нарушив envelope.recipientKeys map (последний write wins;
старый recipient теряет доступ). Низкая вероятность, но не нулевая.

**Источник**: Spec 018 F-5b Batch 3 inline TODO.

## SRV-PUSH-FOUNDATION: Generic push-trigger infrastructure (spec 019 F-5c, rescoped 2026-06-20)

> **Renamed/rescoped 2026-06-20 evening** из узкого `SRV-FCM-CONFIG-UPDATE`.
> Изначальный план был «один endpoint для config-updated». При проектировании
> обнаружено **9 known future consumers** (S-4 SOS, S-9 Health, S-2 Pairing,
> S-10 Subscription, V-2 Messenger, V-3 Album, V-6 Caregiver, spec 008 rewrite +
> config-updated). Узкий путь требовал rewrite × 9; generic foundation = addition × 9.
> Per CLAUDE.md rule 4 «only add abstraction if not adding forces rewrite» —
> abstraction оправдана. Spec 019 строит foundation + первый use case.

**Контекст**: Generic push-trigger infrastructure для семейного приложения —
permits любой event type делать «trigger push на recipients семьи». Изначальный
use case (cache invalidation для F-5b configs) — один из 9+ known consumers.

**Сейчас в коде (после spec 019 implementation)**:
- `core/push/` KMP module: `PushTrigger` port + `PushHandler` + `EventType` sealed
  + `TargetScope` enum + wire-format DTOs.
- `core/push-android/`: `HttpPushTrigger`, `FcmTokenPublisher`,
  `LauncherFirebaseMessagingService` extension.
- `workers/push/`: TypeScript Cloudflare Worker с `POST /push` endpoint,
  EventTypeRegistry, JWT verification (jose), KV-based idempotency + rate limit,
  FCM dispatch.

**Endpoint** (Cloudflare Worker, временное решение per rule 8):
```
POST /push
  Header: Authorization: Bearer <Firebase ID-token>
  Header: Idempotency-Key: <UUID v4>
  Body: {
    schemaVersion: 1,
    eventType: "config-updated" | "sos-triggered" | ...,
    targetScope: "own-devices" | "own-and-grants",
    ownerUid: "<uid>",
    payload: { ...event-specific fields... }
  }
```

Worker:
1. Validates JWT через `jose` library с dynamic JWKS TTL (Cache-Control из Google response).
2. Lookup eventType в EventTypeRegistry (static TypeScript const, whitelist).
3. Per-event-type authorisation rule (config-updated = owner ∨ grant-holder; sos-triggered = owner; entitlement-expired = server-internal only).
4. Per-event-type rate-limit (config-updated 60/min; sos-triggered 10/min; etc.).
5. Idempotency dedupe через Workers KV (10-min TTL).
6. Recipient resolution per TargetScope (Firestore `/users/{uid}/devices/*` + `/users/{uid}/grants/*`).
7. FCM dispatch: `{schemaVersion, eventType, ownerUid, ...payload}` + `collapse_key: "{eventType}:{ownerUid}:{contextKey}"`.
8. Bounded retry на FCM 429/5xx (3 attempts: 500ms, 2s, 8s).

Client `MessagingService.onMessageReceived` → `PushHandlerRegistry.handlerFor(eventType)` → dispatch к event-specific handler (например, `ConfigUpdatedHandler` → `ConfigSaver.loadForOther`).

**Retry policy** (поправлено per industry research, spec 019 Q3):
- Client НЕ retries Worker endpoint на 5xx (fire-and-forget; recipient'ы получат свежий state через pull-on-app-open).
- Worker retries FCM 3× (не 5 — per Twilio/SendGrid/Stripe industry standard для transient errors).
- Receiver idempotent (debounce 2s на `(ownerUid, eventType, triggerId)`).

**Требование при переезде на свой сервер**:
Когда launcher переезжает с CF Worker free tier на собственный backend (например Ktor) — портировать:
1. `POST /push` endpoint generic (та же wire-format).
2. JWT verification: `jose` (JS) → Java JOSE library (Nimbus или эквивалент).
3. EventTypeRegistry со всеми event types зарегистрированными к моменту migration.
4. Recipient resolution: Firestore client SDK → собственная БД (user-device-grant таблицы).
5. FCM dispatch: Firebase Admin SDK (на своём сервере без CF CPU limits — теперь OK).
6. Idempotency: Workers KV → Redis или Postgres.
7. Rate limit: Workers KV → Redis.
8. JWKS cache: тот же Cache-Control pattern, но в memory cache своего сервера.

**Что НЕ меняется при migration**:
- Client-side `HttpPushTrigger` — только URL меняется (через app update; см. spec 019 Q5 — hardcoded URL вариант принят).
- Wire-format JSON — unchanged.
- `PushHandler` registry на receiver — unchanged.
- `BackgroundDispatcher` port (foundation) — unchanged. `WorkManagerBackgroundDispatcher` adapter unchanged.
- Все consumers (`ConfigSaver`, `SosService`, etc.) — unchanged (используют port).

**`workers/_shared/auth-jwt/` extraction (separate concern)**:
JWT verification (Firebase ID-token validation) extracted в standalone TypeScript module `workers/_shared/auth-jwt/` с первого commit (spec 019). Не push-specific — может быть reused любыми future Workers (V-3 album metadata, S-4 SOS если decoupled, etc.).

При migration на own backend — **auth-jwt module portируется ОТДЕЛЬНО от push transport**:
- Если оставляем Firebase Auth as identity provider → auth-jwt module impl остаётся, переносится 1:1 (jose → Java JOSE library типа Nimbus).
- Если переходим на свой identity provider (F-4 / spec 017 territory): auth-jwt module расширяется на multiple JWT issuers (Firebase + наш own). Mapping table «Google identity → internal UID» лежит в auth-server, не в auth-jwt.

Это позволяет **независимую migration** auth и push transports.

**FCM Service Account JWT** (Worker → Google FCM HTTP v1 API):
При переезде на own backend и сохранении FCM:
- Нужен **Firebase project** (или просто Google Cloud Project с FCM enabled) — для FCM trust.
- **Service Account JSON** с FCM Sender role — private key переезжает в свой secret manager (HashiCorp Vault, AWS Secrets Manager, и т.д.), НЕ в plain file.
- Backend (Ktor сервер) при каждом FCM call'е генерит short-lived JWT через SA private key. Same mechanism как Worker сейчас, только в другом host.
- Если переезжаем на own push transport (WebPush/APNS/own protocol) — FCM SA не нужен, `dispatch/fcm-dispatcher.ts` полностью replaced.

**Inline TODOs** в Worker (`workers/push/src/`):
- `auth/jwks-verifier.ts`: `TODO(server-roadmap SRV-PUSH-FOUNDATION): port to own backend, use Java JOSE`
- `dispatch/fcm-dispatcher.ts`: `TODO(server-roadmap SRV-PUSH-FOUNDATION): replace with Firebase Admin SDK when off CF`
- `dedupe/idempotency.ts`: `TODO(server-roadmap SRV-PUSH-FOUNDATION): Workers KV → Redis on own backend`
- `ratelimit/rate-limiter.ts`: same pattern

**Estimated migration cost**: 5-7 дней (когда own server уже существует, остаётся только port логики 1:1).

**Spark plan FCM quota**: 10K push/day. Для MVP scale (~500 admins × ~20 push/day) с запасом. Beyond — upgrade Blaze или client-side rate-limit.

**Why CF Worker сейчас, не свой сервер**: per CLAUDE.md rule 8 — «бесплатный обход». Cost экономии: $0/month vs ~$10-30/month managed Ktor hosting + ~5-7 дней dev времени.

**Источник**: Spec 019 F-5c rescope discussion 2026-06-20 evening — owner approved generic foundation per rule 4 (forces rewrite × 9).

---

## SRV-PUSH-EXTRACTION: Push foundation → отдельный repo (spec 019 F-5c future)

**Контекст**: `core/push/` (KMP module) и `workers/push/` (TypeScript Worker) спроектированы как **extraction-ready** с первого commit — независимые билды, общая только wire-format JSON. Сейчас (Phase 1) живут в monorepo `c:\work\launcher` — правильное решение для единственного consumer (Android launcher).

**Триггер extraction**: появление **второго независимого consumer**. Конкретно — начало работы над спекой **V-2 Elderly-Friendly Messenger** (Phase 4), которая будет **отдельным приложением** в отдельном repo.

**Не выносить пока**:
- Только один consumer (launcher).
- Wire-format ещё стабилизируется через несколько спек-итераций (S-4, S-9 могут обнаружить design промахи в начальной версии).
- Простой рефакторинг внутри одного репо.

**Что готово к extraction уже сейчас** (design-level):

| Компонент | Готовность | Действие при extraction |
|---|---|---|
| `workers/push/` | ✅ полностью | `git subtree split --prefix=workers/push -b push-worker` → push в новый repo |
| `core/push/` (KMP) | ✅ полностью | `mv core/push/ ../push-client/` + настроить Maven publishing + заменить `project(":core:push")` на `"com.familycare:push-client:1.0.0"` |
| Wire-format contract | ✅ versioned (schemaVersion) | unchanged |
| Зависимости `core/push/` от других `core/*` модулей | минимальные (только `core/auth/api` для AuthIdentity) | `core/auth/api` тоже extraction-ready (мелкий refactor если нужно) |

**Estimated extraction cost**: ~1 день работы (publishing setup + Maven coordinates + git subtree split + замена project deps на artifact deps + тесты).

**Inline TODOs**:
- `core/push/build.gradle.kts`: `TODO(extraction SRV-PUSH-EXTRACTION): when V-2 starts, extract to standalone Maven artifact com.familycare:push-client`
- `workers/push/wrangler.toml`: `TODO(extraction SRV-PUSH-EXTRACTION): extraction-ready via git subtree split`

**Pre-extraction hygiene** (что делать ДО extraction, чтобы не превратить в задачу):
- НЕ добавлять в `core/push/` зависимости от launcher-specific модулей (`core/launcher/*`, `feature/*`).
- НЕ позволять Android-specific типам утекать в public API (`api/` package).
- Любое расширение wire-format — через `schemaVersion` bump (rule 5).

**Status**: 🟢 Nice-to-have, выполняется когда триггер срабатывает. До тех пор — design discipline (см. pre-extraction hygiene) поддерживает extraction-readiness без работы.

**Источник**: Spec 019 F-5c architectural discussion 2026-06-20 evening — extraction roadmap explicitly approved.

---

## SRV-FCM-CONFIG-UPDATE: replaced by SRV-PUSH-FOUNDATION (2026-06-20)

Этот entry **заменён** на `SRV-PUSH-FOUNDATION` выше. Узкий scope «один endpoint для config-updated» расширен до generic push infrastructure при проектировании spec 019. См. SRV-PUSH-FOUNDATION для актуальной планировки migration на свой сервер.

---

## SRV-SEC-006: Server-at-rest encryption + threshold unsealing + remote attestation

**Контекст**: при переезде на собственный backend весь metadata (identity-links, public keys, access-grants, audit log, recovery vault ciphertext, envelope storage, blob references) попадает в БД на нашей инфре. **E2E inviolable** (server не видит plaintext config/media/keys) защищает только данные пользователя — **metadata же открыта** для любого, кто получит доступ к БД (insider, leaked DB dump, stolen disk image, скомпрометированный hosting provider).

**Threat model, который этот entry закрывает**:
- **TM-1 (stolen DB dump)**: атакующий получает копию Postgres. Без at-rest encryption — видит весь metadata. С naive at-rest encryption (master key на том же сервере) — взлом тривиален. Нужен **threshold unsealing**: master key не лежит на сервере, собирается из shard'ов при boot.
- **TM-2 (stolen running server)**: атакующий получает live VM с decrypted master key in-memory. Закрывается **periodic remote attestation** + **bounded session TTL** для master key (auto re-seal через N часов).
- **TM-3 (compromised hosting provider / insider)**: hosting admin может dump memory или modify binary. Закрывается **hardware attestation** (TPM PCR / AWS Nitro / GCP Confidential VMs) + **shard custody вне hosting provider** (shard'ы у разных operator'ов / cloud providers).
- **TM-4 (long-term forward compromise)**: master key compromised в момент T → все данные до T читаемы. Закрывается **periodic master key rotation** + background re-encryption.

**Требование к серверу**:

1. **At-rest encryption всего metadata**:
   - Field-level encryption для sensitive columns (recovery vault ciphertext, audit log payload hash, identity-links — всё кроме того, что нужно для index lookup в plain).
   - Disk-level encryption (LUKS / cloud-provider TDE) как defence-in-depth, **не как primary**.
   - Encryption key = `master_data_key`, derived from `master_unseal_key`.

2. **Threshold unsealing (Shamir N-of-M)**:
   - `master_unseal_key` разделён на **N shards** через Shamir's Secret Sharing.
   - Server при boot ожидает **M-of-N shards** (recommendation: 3-of-5 или 2-of-3 для MVP).
   - Shards вводятся через secure channel (отдельный admin endpoint `POST /ops/unseal`, mTLS, IP allowlist).
   - До unseal'а: server в `sealed` state — отвечает только на `/health` и `/ops/unseal`, отбрасывает все остальные requests с 503.
   - После unseal: `master_unseal_key` в memory, derives `master_data_key`, открывает БД.
   - Reference: HashiCorp Vault `vault operator unseal` pattern.

3. **Shard custody policy**:
   - Каждый shard хранится **отдельным operator'ом** (физически разделённые люди / accounts / clouds).
   - Recommended distribution: 1 shard offline cold storage (paper / metal backup в сейфе), 1 shard на hardware token у владельца, 1 shard у trusted operator (юрист / партнёр), 1 shard в cloud KMS другого провайдера.
   - **Никакие 2 shard'а не лежат вместе** (иначе threshold нарушен).

4. **Hardware-backed unsealing (preferred path)**:
   - Если deployment поддерживает: master_unseal_key wrapped через **Cloud HSM / KMS** (AWS KMS + Nitro Enclaves, GCP Cloud KMS + Confidential VMs, Azure Key Vault + Confidential Computing).
   - KMS releases key **только** если attestation document от instance проходит verification (PCR measurements / Nitro attestation).
   - Это закрывает TM-3 (insider): hosting provider не может dump memory без trip attestation.

5. **Periodic remote attestation**:
   - Каждые **N часов** (recommendation: 6h) server отправляет attestation document на **external attestation service** (отдельный endpoint, отдельный provider).
   - Attestation document: hash of running binary + hash of config + TPM PCR values + uptime + boot timestamp.
   - Attestation service сравнивает с known-good baseline. На mismatch → alert + auto-shutdown via revoke unseal token.
   - Owner получает push на каждую failed attestation.

6. **Auto re-seal on TTL**:
   - `master_unseal_key` в memory имеет **bounded TTL** (recommendation: 24h).
   - После TTL: server переходит в `sealed` state, требует свежий threshold unsealing.
   - Защищает от long-running compromise.

7. **Master key rotation**:
   - `master_data_key` ротируется раз в **90 дней** (recommendation).
   - Background job re-encrypts старые ciphertext'ы под новый key.
   - Старый key хранится для read для `re-encryption period + 30 дней`, затем удаляется.
   - Audit log записывает rotation events.

8. **Forensic-safe audit log**:
   - Audit log сам по себе зашифрован под отдельный `audit_key` (не master_data_key).
   - `audit_key` shards распределены по другим operator'ам (отличным от master_unseal_key custody).
   - Append-only structure, tamper-evident через hash chain (каждая запись содержит hash предыдущей).

**Что НЕ closes**:
- Кража server **до** unseal'а — атакующий получает только encrypted blob, ничего полезного.
- Threat-model «leaked source code» — закрывается тем, что в source нет secrets (все ключи derive'ятся at runtime через unseal). Это нужно явно verify через secret-leak scanner в CI.
- Threat-model «physical access к hardware token у владельца» — accepted risk; mitigation = shard distribution + audit log alert на unauthorized unseal attempt.

**MVP simplification path** (если full threshold + HSM overkill для phase 1):
- **Phase 1 (single-server, < 100 users)**: master_unseal_key в env var через secret manager (Vault / AWS Secrets Manager). Disk encryption LUKS. **Documented limitation** — закрывает только stolen-disk vector, не insider/running-server.
- **Phase 2 (production, > 100 users)**: добавить Shamir N-of-M threshold unsealing. Operator endpoint `POST /ops/unseal`.
- **Phase 3 (regulated / GDPR-heavy)**: добавить hardware-backed (Cloud KMS + attestation) + periodic remote attestation + auto re-seal.
- Migration между phases — additive (port `MasterKeyProvider` остаётся, swap implementation).

**Что нужно от launcher-клиента**:
- **Ничего** — это полностью server-side concern. Клиент продолжает работать с REST endpoint'ами как обычно. При server `sealed` state клиент получает 503 → retry с exponential backoff (та же логика, что и для любого server outage).

**Когда поедет**: вместе с собственным backend проектом. Phase 1 (env var) — day 1 own server. Phase 2 (threshold) — до первых **реальных** users. Phase 3 (HSM + attestation) — до billing tier или EU/GDPR launch, что раньше.

**Зависимости**:
- Выбор backend stack (Ktor поддерживает custom startup hooks для unseal flow).
- Выбор cloud provider (KMS + Confidential Computing availability).
- Identification операторов для shard custody (юридический / организационный вопрос, не технический).

**Связанные SRV-задачи**:
- SRV-RECOVERY-001 — server-side atomic counter лежит под этим encryption (без unseal БД не открыта, counter недоступен).
- SRV-SEC-002 — audit log использует separate audit_key (защита от tampering при компрометации master).
- SRV-CRYPTO-008 — algorithm migration policy applies к server-side encryption тоже (XChaCha20-Poly1305 + Argon2id deprecation path).
- SRV-INFRA-001 — production deployment должен включать unseal ceremony как часть deployment runbook.

**Источник**: 2026-06-26 mentor-сессия с владельцем — впервые поднят threat-model «server stolen / code stolen», категория **server-at-rest encryption + threshold unsealing + remote attestation** добавлена как explicit requirement.

**Industry references**:
- [HashiCorp Vault — Seal/Unseal](https://developer.hashicorp.com/vault/docs/concepts/seal) — pattern для threshold unsealing.
- [Shamir's Secret Sharing](https://en.wikipedia.org/wiki/Shamir%27s_secret_sharing) — math foundation.
- [AWS Nitro Enclaves Attestation](https://docs.aws.amazon.com/enclaves/latest/user/nitro-enclave-concepts.html) — hardware attestation.
- [GCP Confidential VMs](https://cloud.google.com/confidential-computing/confidential-vm/docs/about-cvm) — alternative.
- [Signal — Sealed Sender](https://signal.org/blog/sealed-sender/) — metadata protection pattern (relevant к field-level encryption).

## SRV-OPAQUE-TOKENS-001: Opaque token store for cross-app handoff (mentor session 2026-07-08)

**Контекст**: обсуждение launcher-anchored spoke app onboarding (будущий TASK-115). Владелец подчеркнул: сервер **не должен знать содержимое handoff'а** между launcher'ом и spoke app'ом (мессенджер, фотоальбом). Роль сервера — минимально opaque bank: сохранить факт «был issued token X, действителен до T, single-use», проверить при redeem'е.

**Требование к серверу (zero-knowledge invariants)**:

1. **Store endpoint**:
   ```
   POST /v1/opaque-tokens/store
   Authorization: Bearer <launcher_JWT>
   Body: { "token_id": "<32 bytes hex, client-generated>", "ttl_seconds": 900 }
   ```
   - Rate limit per identity (не более N tokens/hour).
   - Records: `{ token_id, issued_by: launcher_identity_id, expires_at, redeemed: false }`.
   - **Не хранит**: содержимое handoff'а, target app_id, никакой семантики.

2. **Redeem endpoint** (unauthenticated — spoke app ещё не в системе):
   ```
   POST /v1/opaque-tokens/redeem
   Body: { "token_id": "<hex>" }
   ```
   - Returns: `{ valid: true, issued_by: launcher_identity_id }` если token существует, не истёк, не redeemed.
   - Sets `redeemed = true` (single-use).
   - Returns `{ valid: false }` иначе.

3. **What server sees**:
   - Authenticated identity создаёт opaque tokens (кто и когда).
   - Некто redeem'ит tokens (без auth).
   - Timing: token issued at T1, redeemed at T2.

4. **What server does NOT see**:
   - Содержимое handoff'а (encrypted client-side через sealed_box для spoke app's public key, доставляется через Play Install Referrer вне server visibility).
   - Какой spoke app redeem'ит (не требуем app_id в redeem запросе).
   - Отношение между tokens (batching / correlation resistance при rate limit).

5. **Storage tier**: Cloudflare KV с TTL (auto-expiry). Не Durable Object — не нужна strong consistency (single-use redemption tolerable через optimistic check-then-set в KV).

**Zero-trust baseline (TASK-105) compliance**:
- Store: JWT verify, rate limit, input validation (`token_id` = 64 hex chars, `ttl_seconds` ≤ 900), idempotent (retry с тем же token_id — 409 conflict).
- Redeem: без JWT (`[public]` justified — spoke app до redeem'а не имеет identity в нашей системе), rate limit per IP + per token_id (anti-brute-force для guessing token_id).

**Metadata leakage assessment**:
- Сервер видит паттерн «launcher X часто создаёт opaque tokens» — слабая signal.
- **Не видит** какое приложение установлено, кому предназначен handoff, что внутри.
- Considered acceptable для family threat model. Enterprise / clinic threat model — future review.

**Migration path**:
- Cloudflare Worker MVP — 1-2 дня implementation.
- Own Go microservice — ~1 неделя (JWT verify + KV-like storage tier + rate limit).

**Trigger**: активация TASK-115 (launcher-anchored spoke app onboarding) с реальной implementation. До тех пор — spec-only, не блокирует MVP приложения.

**Cross-references**:
- Consumer: будущий TASK-115.
- Related: [TASK-105](../../backlog/tasks/task-105%20-%20Decision-Server-side-abuse-defense-baseline.md) zero-trust baseline.
- Related: [TASK-108](../../backlog/tasks/task-108%20-%20Decision-Metadata-privacy-what-server-sees.md) metadata privacy T0 tier.

**Источник**: 2026-07-08 mentor-сессия про cross-app trust bootstrap.
