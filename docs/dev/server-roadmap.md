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
