# Feature Specification: F-5c — Push-trigger Foundation + `config-updated` Event

**Feature Branch**: `019-f5c-fcm-config-updated`
**Created**: 2026-06-20
**Status**: Draft (rescoped 2026-06-20 evening)
**Input**: User description: «F-5c FCM-trigger config-updated — мини-спека». Rescoped к **generic push-trigger foundation + config-updated as first use case** после mentor session 2026-06-20 evening — обнаружены 9+ known future consumers (S-4 SOS, S-9 Health, S-2 Pairing, S-10 Subscription, V-2 Messenger, V-3 Album, V-6 Caregiver, spec 008 rewrite, config-updated), узкий путь нарушает CLAUDE.md rule 4.

> ## 🔀 Re-scope 2026-06-20 evening
>
> Изначальный план — узкая мини-спека «один Worker endpoint для config-updated push'а» (~3-5 дней). При проектировании mentor-сессии обнаружено:
>
> - **9 known future consumers** в roadmap (Phase 2 + Phase 4) требуют push-channel.
> - Узкий путь = новый endpoint + duplicate auth/idempotency/rate-limit/JWKS handling × 9 = rewrite.
> - Generic foundation + первый use case = addition × 9.
> - Rule 4 («only add abstraction if not adding forces rewrite») — abstraction оправдана.
>
> **Новый scope** (**8-10 дней** — пересмотрено 2026-06-21 после scenarios discussion + auth-jwt extraction decision):
> - Generic Worker `POST /push` (в `workers/push/`) с EventTypeRegistry.
> - Generic `PushTrigger` port в `core/push/` (KMP).
> - Generic `PushHandler` + registry на receiver side.
> - **`BackgroundDispatcher` port** (foundation) + `WorkManagerBackgroundDispatcher` Android adapter — все push handlers по умолчанию работают в WorkManager job (без artificial 10s budget). Per-event-type `handlerTimeout` декларируется в `EventType` declaration (default 30s, future album = 5 минут, и т.д.).
> - **`workers/_shared/auth-jwt/`** — отдельный TypeScript module для Firebase ID-token verification (jose, JWKS cache с dynamic TTL, claim validation). Imported by `workers/push/`. Extraction sets up foundation для будущих Worker'ов (S-4, V-2, V-3) и для migration на свой identity provider.
> - Extraction-ready design (см. [SRV-PUSH-EXTRACTION](../../docs/dev/server-roadmap.md#srv-push-extraction-push-foundation--отдельный-repo-spec-019-f-5c-future)).
> - Первый event type: `config-updated`. Consumer: `ConfigSaver` (F-5b).
> - **Migration existing push subsystem**: 8 файлов в `core/src/.../api/push/*` + `LauncherPushReceiver` используют старый `PushPayload.linkId`. Должны быть rewritten: `PushPayload` (new shape), `PushPayloadWireFormat` (new wire), `PushReceiver` → `PushHandlerRegistry` dispatch, `LauncherPushReceiver` → handler-based (FR-028).
>
> Каждый последующий consumer (S-4 SOS, V-2 Messenger, ...) = ~30 строк кода (EventTypeRegistry entry + handler + service wrapper), без правки foundation.

> **Roadmap anchor**: [docs/product/roadmap.md Шаг 4a Phase 1](../../docs/product/roadmap.md#L112-L113).
> **Server migration tracking**: [docs/dev/server-roadmap.md SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md#srv-push-foundation-generic-push-trigger-infrastructure-spec-019-f-5c-rescoped-2026-06-20).
> **Extraction tracking**: [docs/dev/project-backlog.md TODO-ARCH-017](../../docs/dev/project-backlog.md#todo-arch-017-push-foundation-extraction-в-отдельные-repos-).
> **Зависит от**: F-5b (spec 018) — `ConfigSaver`, multi-recipient envelope, cross-UID delegation. F-4 (spec 017) — `AuthIdentity` (Firebase ID-token). Spec 007 — `LauncherFirebaseMessagingService`, `PushType`, `PushPayload` (расширяется здесь breaking-change'ем — см. Clarifications Q1).
> **Создаёт foundation для**: Spec 008 rewrite (Шаг 4b — collaborative editing), S-2 (QR pairing notifications), S-4 (SOS push), S-9 (phone health alerts), S-10 (entitlement events), V-2 (messenger), V-3 (album), V-6 (caregiver invites). Полный список и pattern — см. §«Reuse pattern для будущих consumers» ниже.

---

## Clarifications

### Session 2026-06-20 evening — resolved через mentor + cross-AI consult

| # | Question | Resolution |
|---|---|---|
| 1 | Push payload shape — sealed variant / extend extra / breaking change `PushPayload`? | **C (breaking change)**. Старых consumers `linkId` нет — единственный (spec 008) переписывается параллельно. `PushPayload` rewritten: `linkId: String?` (nullable, deprecated), новые first-class поля `ownerUid: String?`, `eventType: String` (заменяет `type: PushType` старого), `payload: Map<String, String>?`, `triggerId: String` (UUID v4 для receiver dedupe). `schemaVersion: Int = 1` (bump позже при breaking changes). |
| 2 | FCM token publishing — расширить `EnvelopeBootstrap` / отдельный port / `DeviceDirectoryPublisher`? | **B (отдельный `FcmTokenPublisher`)**. Crypto port остаётся isolated (rule 2 ACL). Lifecycle independence: pub key — long-lived (раз при Sign-In), FCM token — rotates (`onNewToken` callback). Никакого regression-risk в F-5b API post-merge. |
| 3 | Retry boundary + idempotency? | **A с research-поправками** (industry research vs Twilio/Stripe/SendGrid/CDN purge patterns). Concrete: client генерирует `Idempotency-Key: <UUID v4>` per push action, Worker dedupes через KV (10-min TTL), Worker retries FCM **3×** (не 5 — Twilio норма) с backoff 500ms/2s/8s, client **НЕ retries** Worker на 5xx (cache invalidation = at-most-once с pull fallback), FCM `collapse_key: "{eventType}:{ownerUid}:{contextKey}"` для receiver-side group, receiver idempotent с debounce 2s по `triggerId`. |
| 4 | Worker auth — Firebase Admin SDK vs ручная JWT verification? | **B (ручная JWT через `jose` library)**. Reason — НЕ cold start: `firebase-admin` = Node.js package, коряв на Workers runtime даже с `nodejs_compat` (использует `fs`, `child_process`, `https.Agent` — за рамками полифила). `jose` (panva/jose) — JS-native, type-safe, production-grade. **Critical**: JWKS cache TTL должен следовать `Cache-Control: max-age` из Google response, НЕ хардкодиться (zashitь fixed TTL > rotation_period = вся auth ложится разом после ротации). **Все claims проверять**: `alg === 'RS256'`, `kid` exists, signature verified, `iss === "https://securetoken.google.com/<projectId>"`, `aud === <projectId>`, `exp > now()` (clock skew 60s tolerance), `iat <= now() + 60s`, непустой `sub`. На `kid` cache miss (Google ротировал) — force-refresh JWKS, не ждать TTL expiry. |
| 5 | Worker hosting + endpoint URL? | **A (hardcoded URL + `*.workers.dev`)**. Бесплатно сейчас, exit ramp в [TODO-ARCH-001](../../docs/dev/project-backlog.md) на custom domain + [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md) на свой backend. Inline `TODO(server-roadmap SRV-PUSH-FOUNDATION)` маркеры в Worker source. Смена URL потом = app update (acceptable trade-off vs упрощение сейчас). |
| 6 | Debounce и rate-limit стратегия? | **Только Worker rate-limit per UID per eventType (защита от abuse)**. Client debounce **удалить** — premise был неверный: push = explicit user action per [spec 008 model](../008-bidirectional-config-sync/spec.md) (US-4 «pending changes», FR-042 Room persistence, FR-047 «Отправить сейчас» button). Нет шторма — нет нужды debounce. Per-eventType rate-limit лимиты: `config-updated` 60/min/UID, `sos-triggered` 10/min/UID, остальные — задаются в EventTypeRegistry при добавлении event. |

---

## Scope

### In scope

**Foundation (generic, reusable)**:
- Cloudflare Worker `POST /push` endpoint (в `workers/push/`) с EventTypeRegistry (whitelist + per-event-type rules: auth, rate-limit, collapse-key, priority).
- **`workers/_shared/auth-jwt/`** — отдельный TypeScript module для Firebase ID-token verification. Public API: `verifyFirebaseIdToken(token, projectId, kvBinding) → VerificationResult`. JWKS cache + dynamic TTL (Cache-Control aware) + force-refresh on `kid` cache miss + полный claims validation (alg, kid, iss, aud, exp, iat, sub). Push Worker импортирует, **не реализует JWT логику сам**.
- Idempotency dedupe через Workers KV (10-min TTL).
- Recipient resolution по `targetScope` (OwnDevices / OwnAndGrants) с fresh Firestore reads.
- FCM dispatch с bounded retry (3× exp backoff).
- Per-eventType rate-limit (защита от abuse).
- Client-side: `core/push/` KMP module — generic `PushTrigger` port, `PushHandler` + `PushHandlerRegistry`, `EventType` sealed (с `handlerTimeout: Duration` property), `TargetScope` enum, `PushPayload` wire DTO, `PushTriggerError` sealed, **`BackgroundDispatcher` port** для long-running handler dispatch.
- Android adapter: `HttpPushTrigger`, `FcmTokenPublisher` (отдельный port), `WorkManagerBackgroundDispatcher` (WorkManager-backed `BackgroundDispatcher` impl), extended `LauncherFirebaseMessagingService` для handler dispatch через BackgroundDispatcher.

**Migration scope (existing push subsystem)**:
- Rewrite `core/src/.../api/push/PushPayload.kt` под new shape (linkId nullable, ownerUid/eventType/triggerId first-class).
- Rewrite `core/src/.../api/push/PushPayloadWireFormat.kt` под новый wire-format.
- Replace `core/src/.../api/push/PushReceiver.kt` interface с `PushHandlerRegistry` dispatch.
- Rewrite `core/src/.../androidRealBackend/.../LauncherPushReceiver.kt` → handler-based (registers `ConfigUpdatedHandler` + `CommandIssuedHandler` для legacy compat).
- Update `FakePushReceiver`, `PushPayloadWireFormatTest`, `PairingEndToEndTest`, `FcmReceiverContract`.

**First event type**:
- `config-updated`: EventTypeRegistry entry + `EventType.ConfigUpdated` sealed variant + `ConfigUpdatedHandler` + integration в `ConfigSaver`.

### Out of scope

- Merge UI / collaborative editing / pending-changes warnings (→ spec 008 rewrite, Шаг 4b).
- Group management UX (add/remove admin, role enum, server arbitration) (→ S-2, Phase 2).
- New event types кроме `config-updated`: SOS (→ S-4), phone health (→ S-9), pairing notifications (→ S-2), entitlement events (→ S-10), messenger events (→ V-2), album events (→ V-3), caregiver events (→ V-6). F-5c foundation enables их через EventTypeRegistry extension, не реализует сами.
- Capability Registry integration (→ deferred к Phase 4+ когда F-2 ships).
- AI/voice trigger surfaces (→ no AI affordance — pure transport).
- iOS / desktop / web adapter (→ future ports через separate spec'ы).
- Sharing UI / sharing flows для PushTrigger (→ internal API, не user-facing).
- Custom domain Worker URL / production Worker hardening (→ post-MVP exit ramps TODO-ARCH-001, SRV-PUSH-FOUNDATION).
- Bidirectional sync / state reconciliation (→ ConfigSaver's pull-on-app-open + F-5b territory).
- Background pull polling (→ explicitly anti-pattern, F-5c IS the push-driven alternative).

---

## Сценарии использования

> Концентрированный взгляд «как это будет работать в реальной жизни». Каждый сценарий помечен FRs, которые он закрывает.

### Сценарий 0 — Foundation в действии (новый event type добавляется без правки Worker)

**Контекст**: Через полгода Виталик пишет спеку S-4 (SOS Capability). Нужно push'нуть всем admin'ам пожилого сигнал SOS.

1. Виталик пишет `SosService.kt`:
   ```kotlin
   class SosService(private val pushTrigger: PushTrigger, private val authIdentity: AuthIdentity) {
       suspend fun reportSos(lat: Double, lng: Double) {
           pushTrigger.trigger(
               eventType = EventType.SosTriggered,
               targetScope = TargetScope.OwnAndGrants,
               ownerUid = authIdentity.currentUid(),
               payload = mapOf("lat" to "$lat", "lng" to "$lng"),
           )
       }
   }
   ```
2. ★ Виталик НЕ написал: код с Firebase Messaging SDK, HTTP запроса, URL Worker'а, JWT/Idempotency-Key/retry/collapse_key/FCM token publishing.
3. Виталик добавляет одну запись в EventTypeRegistry (TypeScript Worker, ~10 строк):
   ```typescript
   'sos-triggered': {
       authorise: (caller, ownerUid) => caller.uid === ownerUid,
       rateLimit: { perUid: 10, windowSeconds: 60 },
       collapseKey: () => null,                  // SOS не collapses
       priority: 'high',                          // FCM high-priority
   },
   ```
4. ★ На receiver — handler:
   ```kotlin
   class SosTriggeredHandler(private val sosUI: SosAlertUI) : PushHandler {
       override suspend fun handle(payload: PushPayload) {
           sosUI.showCriticalAlert(payload.fields["lat"], payload.fields["lng"])
       }
   }
   ```
5. ★ DI wiring — одна строка в `SosModule`. Foundation Worker не правится.

**Что закрывает**: US-0, FR-001..010 (foundation), FR-040..045 (extensibility).

---

### Сценарий 1 — Admin сохраняет конфиг, второе устройство получает свежую копию

**Контекст**: Admin пользуется приложением на двух устройствах — телефоне и планшете. На обоих Sign-In под одним Google-аккаунтом, оба прошли F-5b envelope bootstrap.

1. Admin на телефоне передвигает плитку в editor'е, нажимает «Сохранить локально» (per spec 008 model — pending state в Room).
2. Затем нажимает «Отправить сейчас» (explicit user action — FR-047 spec 008).
3. `ConfigSaver.saveOwn(...)` шифрует config в envelope, пишет в `/users/{uid}/configs/main`.
4. ★ `ConfigSaver` инжектирует `PushTrigger` (foundation) и вызывает:
   ```kotlin
   pushTrigger.trigger(
       eventType = EventType.ConfigUpdated,
       targetScope = TargetScope.OwnAndGrants,
       ownerUid = currentUid,
       payload = mapOf("configName" to configName),
   )
   ```
5. ★ `HttpPushTrigger` (Android adapter) делает `POST https://launcher-push.workers.dev/push` с Firebase ID-token + Idempotency-Key (UUID v4) + body `{eventType: "config-updated", targetScope: "own-and-grants", ownerUid, payload: {configName: "main"}, schemaVersion: 1}`.
6. ★ Worker validates JWT через `jose`, lookup `config-updated` в EventTypeRegistry, authorise (caller = owner), check rate-limit (60/min per UID), check idempotency KV, resolve recipients (own devices + grants), build FCM payload с `collapse_key: "config-updated:{ownerUid}:main"`, dispatch FCM 3× retry на 429/5xx.
7. ★ Планшет admin'а получает push через `LauncherFirebaseMessagingService.onMessageReceived` → `PushHandlerRegistry.handlerFor("config-updated")` → `ConfigUpdatedHandler.handle(payload)` → `ConfigSaver.loadOwn("main")` → DataStore + UI refresh.

**Что закрывает**: US-1, FR-020..025 (config-updated specific), FR-030..035 (client trigger), FR-050..055 (receiver dispatch), SC-001, SC-002.

**Trouble case 1.b — планшет offline в момент push**: FCM удерживает message до connectivity restore. Планшет получает delayed push при следующем online. Если TTL истёк (FCM default 4 недели) — `ConfigSaver.loadOwn` при app foreground (pull fallback из F-5b) подтягивает свежую копию.
**Закрывает**: FR-038, SC-005.

---

### Сценарий 2 — Cross-UID delegated edit; бабушкин телефон обновляется

**Контекст**: Бабушкин телефон managed admin'ом через access-grant. Admin правит её layout с собственного телефона.

1. Admin на своём телефоне открывает бабушкин config через `ConfigSaver.loadForOther(grannyUid, "main")`.
2. Меняет тему, save локально → push.
3. `ConfigSaver.saveForOther(grannyUid, "main", ...)` шифрует под recipients = бабушкины devices + admin device, пишет в `/users/{grannyUid}/configs/main`.
4. ★ `ConfigSaver` вызывает `pushTrigger.trigger(EventType.ConfigUpdated, TargetScope.OwnAndGrants, ownerUid = grannyUid, payload = {"configName": "main"})`.
5. ★ Worker authorise: caller имеет write-grant в `grannyUid` namespace per Firestore `/users/{grannyUid}/grants/{callerUid}` — allowed.
6. Worker resolve recipients = бабушкины devices + admin devices + другие grant-holders в namespace.
7. ★ Бабушкин телефон получает push → `ConfigUpdatedHandler` → `ConfigSaver.loadOwn("main")` → её launcher автоматически перекрашивается.

**Что закрывает**: US-2, FR-002 (per-eventType authorisation), FR-006 (recipient resolution), SC-003.

**Trouble case 2.b — admin потерял grant между write и push**: Race condition. F-5b storage rule сначала проверит права на write — если права отозваны, write fails 403, push не вызывается. Если write успел до revoke — Worker резолвит recipients **на момент push** (не на момент write); revoked admin device не получит push.
**Закрывает**: FR-007, SC-006.

---

### Сценарий 3 — Worker недоступен / FCM quota исчерпан

**Контекст**: Worker возвращает 503 (cold-start error / quota exhausted / Cloudflare incident).

1. Worker делает FCM API call → 429 Quota Exceeded.
2. Worker retries FCM 3× с backoff: 500ms, 2s, 8s.
3. Все 3 retry fail → Worker возвращает 503 caller'у.
4. ★ Client **НЕ retries** на 5xx (per Q3 resolution — at-most-once delivery для cache invalidation).
5. ★ Client save flow завершился successfully (Firestore write был раньше push trigger'а). UX не блокируется.
6. ★ Остальные recipients получат свежую копию **при следующем app open** через `ConfigSaver.loadOwn` (pull fallback F-5b).

**Что закрывает**: US-3, FR-008 (Worker retry policy), FR-026 (client fire-and-forget), FR-038 (graceful degradation), SC-005.

---

### Сценарий 4 — Адрес телефона в FCM системе сменился сам

**Контекст**: Google регулярно (раз в недели/месяцы) меняет «адрес» телефона в системе FCM ради безопасности. Приложение должно это заметить и обновить запись в облаке, чтобы Worker мог продолжать пушить.

1. Дочкин планшет спокойно работает дни, получает push'и от семьи.
2. Google FCM в какой-то момент решает: «время сменить адрес этого телефона». Присылает приложению уведомление через стандартный callback.
3. ★ Приложение получает новый адрес. Сразу же обновляет одну строчку в облаке — «адрес дочкиного телефона теперь такой» (другие настройки этой записи не трогает — ключи envelope остаются).
4. С этого момента Worker, отправляя push'и на этот телефон, использует новый адрес.
5. Если Worker когда-нибудь попробует на старый адрес — Google вернёт «такого нет», Worker пометит запись как требующую очистки.
6. Дочь не замечает ничего — push'и продолжают доходить.

**Что закрывает**: FR-027 (FcmTokenPublisher.onNewToken), FR-012 (Worker stale token cleanup), SC-004.

**Trouble case 4.b — телефон offline в момент смены адреса**: новый адрес не успели записать в облако. Push'и временно не доходят. Когда телефон вернётся в сеть — запись обновится при следующем app foreground. Recipient'ы получают свежие данные через стандартный pull-on-app-open (F-5b safety net).

---

### Сценарий 5 — Большое payload (future album пример) — WorkManager делает свою работу

**Контекст**: Через год в проекте появится V-3 Family Album. Дочь добавляет фото 5MB. Push типа `album-photo-added` улетает recipients семьи. Скачивание фото на бабушкин телефон занимает 2 минуты на её плохом инете.

1. Дочь добавляет фото → её приложение пишет фото в зашифрованное хранилище (S-5 mechanism) → триггерит push типа `album-photo-added` через foundation `PushTrigger`.
2. Worker отправляет FCM push на бабушкино устройство (payload-pointer «новое фото в альбоме», без content'а).
3. Бабушкин телефон получает push в `LauncherFirebaseMessagingService`.
4. ★ Foundation смотрит EventType: `AlbumPhotoAdded.handlerTimeout = 5 минут` (больше чем 30 секунд default для config).
5. ★ Foundation **сразу** dispatches handler через `BackgroundDispatcher` (под капотом — WorkManager на Android). WorkManager имеет мягкие system limits, может работать минутами / часами.
6. Handler внутри WorkManager job: скачивает фото (2 минуты), расшифровывает, сохраняет в локальный album cache, обновляет UI.
7. ★ Если за 5 минут не успел (сеть умерла) — WorkManager автоматически retry с экспоненциальным backoff. Когда связь восстановится — финально скачает.
8. Бабушка видит новое фото когда подходит к телефону.

**Что закрывает**: FR-074 (BackgroundDispatcher default), FR-077 (per-event timeout), FR-078 (BackgroundDispatcher port). **Демонстрирует**: foundation extensible для разных payload sizes без переписывания.

**Trouble case 5.b — config-updated на той же плохой сети**: `EventType.ConfigUpdated.handlerTimeout = 30 секунд` (default). Config 50KB качается за 5-10 секунд — успевает легко. Если совсем долго (сеть полностью пропала) — WorkManager retry'ит до успеха, или recipient получает свежий config при следующем app foreground через pull (F-5b safety net).

---

### Сценарий 6 — Бабушка пользуется приложением **без** Sign-In в Google

**Контекст**: Бабушка установила приложение, прошла короткий wizard, использует launcher для своих контактов и плиток. Sign-In в Google **не** делала (по принципу проекта — каждое устройство самодостаточно, облако активируется только при cloud-действии).

1. Бабушка редактирует layout — добавляет плитку для звонка внуку.
2. Приложение сохраняет config **локально** (без облака).
3. ★ Внутри: вместо реального push-механизма работает «заглушка» — `NullPushTrigger` который ничего не отправляет, никуда не дозванивается, ничего не логирует наружу.
4. Бабушка **не видит** никаких сообщений «нужен Sign-In», «push недоступен», «синхронизация выключена». Никаких alerts.
5. Через месяц бабушка решает: «хочу чтобы внук мог управлять моим телефоном». Нажимает «Привязать к семье».
6. Только тут приложение показывает Sign-In prompt: «Для связи с семьёй войдите в Google». Бабушка входит.
7. После Sign-In приложение **переключается** с заглушки на реальный push-механизм (HttpPushTrigger). С этого момента — full cloud sync.

**Что закрывает**: Cloud-mode integration section (CLOUD-only mode + NullPushTrigger fallback + Sign-In timing inherited from F-4), spec decision 2026-06-15-deferred-cloud.

---

### Сценарий 7 — Старая версия приложения получает push нового типа события

**Контекст**: Бабушка не обновляла приложение полгода. За это время в новой версии (например v2.0) появился новый тип события `album-photo-added` (V-3 будущая фича). Дочь, у которой свежая версия, добавила фото.

1. Дочь добавляет фото → её приложение отправляет push типа «фото-в-альбоме-добавлено» на устройства семьи.
2. Бабушкино устройство (старая версия v1.0) получает push.
3. ★ Приложение смотрит: тип события `album-photo-added` — **неизвестно** (старая версия про такое не знает, в её PushHandlerRegistry такого handler'а нет).
4. Приложение **тихо игнорирует** push: записывает строчку в технический log «неизвестный тип события, пропустил», ничего не показывает пользователю, не crash'ится.
5. Когда бабушка обновит приложение до новой версии — следующий push того же типа уже будет правильно обработан (новая версия знает про альбом).

**Что закрывает**: FR-023 (silent log + ignore для unknown eventType), FR-075 (malformed payload graceful), Wire-format policy forward-compat asymmetry (client fail-soft).

**Trouble case 7.b — новая версия Worker'а присылает payload с полем `schemaVersion: 2` старому клиенту**: старый клиент видит «schemaVersion 2, мы знаем только 1» → тихо игнорирует. Те же гарантии: никаких crash'ей, recipient получит обновлённый функционал «когда обновит приложение».

---

## User Scenarios & Testing *(mandatory)*

### User Story 0 — Reusable foundation (Priority: P1)

Foundation infrastructure без специфики event type: generic `PushTrigger` port + Worker `/push` endpoint + EventTypeRegistry. Доказывается возможностью добавить новый event type в ~30 строк кода без правки Worker'а или client adapter'а.

**Why this priority**: P1, потому что foundation — пререкизит US-1 и US-2 (которые конкретные use cases). Без foundation эти US не реализуемы.

**Independent Test**: dummy event type `"test-ping"` в EventTypeRegistry (test fixture) → client `PushTrigger.trigger(EventType.TestPing, ..., payload = mapOf("nonce" to "abc"))` → assert flow «client trigger → Worker auth → resolve → FCM dispatch» работает без config-specific логики. Может быть JVM unit test с `FakeFcmDispatcher` + `InMemoryRecipientResolver` без реального Firebase.

**Acceptance Scenarios**:
1. **Given** test event type зарегистрирован в EventTypeRegistry, **When** client делает `pushTrigger.trigger(test-event, ...)`, **Then** Worker validates JWT + resolve recipients + dispatch FCM с правильным payload shape.
2. **Given** event type НЕ зарегистрирован в registry, **When** client пытается trigger, **Then** Worker возвращает 400 «Unknown event type».
3. **Given** event type зарегистрирован с `authorise = (c, o) => c.uid === o`, **When** caller != owner делает trigger, **Then** Worker возвращает 403.

---

### User Story 1 — Same-owner multi-device sync (Priority: P1)

Admin владеет двумя устройствами под одним Google-аккаунтом. После сохранения на одном — второе автоматически получает свежий config.

**Why this priority**: P1 — базовый кейс для admin'а с несколькими устройствами. Также первый concrete consumer foundation'а.

**Independent Test**: 2 эмулятора с одним signed-in аккаунтом → save на A → assert push receipt на B + DataStore updated within 5s median, 30s p95.

**Acceptance Scenarios**:
1. **Given** оба устройства online + signed-in одним account'ом, **When** A сохраняет config + триггерит push, **Then** B получает push и `ConfigSaver.loadOwn` invocation в течение 5 сек.
2. **Given** B offline в момент save, **When** B возвращается online до FCM TTL, **Then** B получает delayed push и обновляется.
3. **Given** B offline дольше FCM TTL, **When** B запускается, **Then** B получает свежую копию через pull-on-app-open (F-5b).

---

### User Story 2 — Cross-UID delegated edit (Priority: P1)

Admin правит бабушкин config через access-grant. Бабушкин телефон обновляется без её действий.

**Why this priority**: P1 — ключевой product use case (remote support).

**Independent Test**: 2 эмулятора, разные UIDs, grant подготовлен → admin save через `saveForOther` → assert бабушкин телефон получает push + UI refresh within 10s median.

**Acceptance Scenarios**:
1. **Given** active grant от granny к admin, **When** admin делает `saveForOther(grannyUid, ...)`, **Then** granny устройство получает push и применяет config.
2. **Given** admin потерял grant между write и push, **When** Worker резолвит recipients, **Then** admin device отсутствует в списке получателей push'а.

---

### User Story 3 — Graceful degradation when push fails (Priority: P2)

Push channel недоступен (Worker down, FCM quota исчерпан, network failure). Save flow продолжает работать, recipient'ы получают свежий config при следующем app open.

**Why this priority**: P2 — устойчивость > realtime. Push — оптимизация, не requirement.

**Independent Test**: simulate Worker 503 → assert `ConfigSaver.saveOwn` returns Success в течение 200ms p95; затем app open на recipient device → assert свежая копия загружена.

**Acceptance Scenarios**:
1. **Given** Worker возвращает 503, **When** client save, **Then** save returns Success без задержки от push неудачи.
2. **Given** push не доставлен recipient'у, **When** recipient запускает app, **Then** `ConfigSaver.loadOwn` подтягивает свежую копию из Firestore.

---

### Edge Cases

- **Дублированная доставка FCM** (часто): receiver idempotent через debounce 2s по `triggerId` (UUID per push action). Два разных push'а в одном окне с разными triggerId — обрабатываются оба (legitimate concurrent events).
- **FCM token rotation** (Google ротирует периодически): `FirebaseMessagingService.onNewToken` → `FcmTokenPublisher.publish(newToken)` → update `/users/{uid}/devices/{deviceId}/fcmToken` (merge, не overwrite envelope fields).
- **JWKS rotation Google'ом mid-request**: Worker JWKS cache miss на `kid` → force refresh, не ждать TTL expiry. Защита от outage.
- **Recipient revoked между push send и receive**: push доходит, но receiver уже не имеет grant'а на decrypt (`StorageError.NotARecipient`). Silent log, no UI noise.
- **Stale FCM token** (устройство удалено / переустановлено): FCM возвращает `UNREGISTERED`; Worker помечает entry как stale (best-effort cleanup).
- **Unknown event type на receiver** (пришёл push с eventType который этот клиент не знает — например старая версия app получает событие из новой): `PushHandlerRegistry.handlerFor(unknownType)` → null → silent log + ignore. Не crash.
- **schemaVersion mismatch** (Worker v1 получает request schemaVersion 2): graceful 400.
- **Worker cold start**: первый запрос после idle до 200ms — в пределах SC-002 (200ms p95 для save overhead).

---

## Requirements *(mandatory)*

### Functional Requirements

**Foundation — Worker (Cloudflare, TypeScript)**:

- **FR-001**: Worker MUST expose `POST /push` endpoint, body `{schemaVersion: Int, eventType: String, targetScope: String, ownerUid: String, payload: Map<String, String>}`.
- **FR-002**: Worker MUST authenticate caller via Firebase Auth ID-token в `Authorization: Bearer <token>` header. Validation **delegated to `workers/_shared/auth-jwt/`** module (separate concern, не push-specific). Push Worker imports `verifyFirebaseIdToken(token, projectId, kvBinding): VerificationResult` — receives validated `Claims` или typed error. **F-5c spec не описывает JWT internals** — те живут в auth-jwt module's own README + tests. F-5c только specifies: token MUST be Firebase Auth ID-token, validation MUST be cryptographically sound, errors MUST be typed (not generic).
- **FR-003**: Auth-jwt module responsibility (referenced here для completeness, не enforced by F-5c spec): JWKS cache в Workers KV с TTL равным `Cache-Control: max-age=N` из Google response (НЕ хардкоженный TTL); на `kid` cache miss — force-refresh JWKS не ждать TTL expiry. Полный claims set validated. **F-5c spec defers подробности к** [`workers/_shared/auth-jwt/README.md`](../../workers/_shared/auth-jwt/) (writes during implementation).
- **FR-004**: Worker MUST resolve `eventType` через **EventTypeRegistry** (static TypeScript const, whitelist). Unknown eventType → 400 Bad Request «Unknown event type».
- **FR-005**: Worker MUST apply per-eventType **authorisation rule** (callback `authorise(caller, ownerUid): boolean`). Failed → 403 Forbidden.
- **FR-006**: Worker MUST apply per-eventType **rate-limit** (`{perUid, windowSeconds}`) через Workers KV. Exceeded → 429 Too Many Requests.
- **FR-007**: Worker MUST resolve recipient device list per `targetScope`:
  - `OwnDevices` → `/users/{ownerUid}/devices/*`.
  - `OwnAndGrants` → `/users/{ownerUid}/devices/*` ∪ devices of UIDs in `/users/{ownerUid}/grants/*`.
  Fresh per request, без кеша (low-frequency recipient changes per spec 018).
- **FR-008**: Worker MUST send FCM data-message всем resolved recipient FCM tokens, payload: `{schemaVersion: 1, eventType, ownerUid, payload: {...}, triggerId}`. `collapse_key` per eventType (формула в registry entry; например для `config-updated` = `"config-updated:{ownerUid}:{configName}"`).
- **FR-009**: Worker MUST retry FCM API calls **3 раза** (не 5 — per industry standard) с exponential backoff 500ms, 2s, 8s на FCM 429/5xx. После 3 fails → 503 Service Unavailable caller'у.
- **FR-010**: Worker MUST dedupe по `Idempotency-Key` header через Workers KV (10-min TTL). Repeat request с тем же ID → возврат кешированного response без re-execution.
- **FR-011**: Worker MUST NOT include encrypted content в FCM payload — только pointer (`ownerUid + payload fields`). Recipient pulls свежее content через existing `ConfigSaver.loadOwn` / Firestore read.
- **FR-012**: Worker MUST drop FCM tokens that return `UNREGISTERED` / `INVALID_ARGUMENT` и mark device directory entry как stale (best-effort cleanup; exact mechanism — server-side fire-and-forget).
- **FR-013**: Worker MUST validate `schemaVersion <= MAX_SUPPORTED` (currently 1). Higher version → 400 Bad Request «Unsupported schemaVersion».

**Foundation — Client (KMP, `core/push/`)**:

- **FR-020**: `core/push/api/PushTrigger.kt` MUST expose generic port:
  ```kotlin
  interface PushTrigger {
      suspend fun trigger(
          eventType: EventType,
          targetScope: TargetScope,
          ownerUid: String,
          payload: Map<String, String>,
      ): Outcome<Unit, PushTriggerError>
  }
  ```
- **FR-021**: `core/push/api/EventType.kt` — sealed interface с whitelist event types. Initial set: `ConfigUpdated`. Future entries добавляются additively.
- **FR-022**: `core/push/api/TargetScope.kt` — enum `OwnDevices`, `OwnAndGrants`. Additive расширение в будущем (например `SpecificUid` для V-2 messenger).
- **FR-023**: `core/push/api/PushHandler.kt` + `PushHandlerRegistry.kt` MUST expose generic receiver-side port. `PushHandlerRegistry` lookup по `eventType` → dispatch к handler. Unknown eventType → silent log + ignore.
- **FR-024**: `core/push/api/PushPayload.kt` MUST be wire-format DTO с `schemaVersion: Int = 1`, `eventType: String`, `ownerUid: String?`, `triggerId: String` (UUID v4 для receiver dedupe), `fields: Map<String, String>?`, `linkId: String? = null` (deprecated, для backward-compat parsing старых событий). **Это breaking change** для существующего `PushPayload` — единственный consumer (spec 008) переписывается параллельно.
- **FR-025**: Android adapter `HttpPushTrigger` (в `core/push/androidMain/`) MUST реализовать `PushTrigger`: генерирует `Idempotency-Key: UUID v4` per call, добавляет `Authorization: Bearer <Firebase ID-token>`, делает HTTP POST на hardcoded URL (`*.workers.dev` — см. Clarification Q5).
- **FR-026**: Client MUST **NOT** retries Worker endpoint на 5xx. **Fire-and-forget specifics**: `pushTrigger.trigger(...)` MAY be launched как detached coroutine (e.g. `applicationScope.launch { pushTrigger.trigger(...) }`); caller (`ConfigSaver.saveOwn/saveForOther`) MUST complete без awaiting push result. Failure → degrade to pull-on-app-open (FR-038).
- **FR-027**: Android adapter `FcmTokenPublisher` (отдельный port, НЕ часть `EnvelopeBootstrap`) MUST публиковать current FCM token в `/users/{uid}/devices/{deviceId}/fcmToken` (merge, не overwrite envelope fields). Вызывается при app start + при `FirebaseMessagingService.onNewToken(...)` callback.
- **FR-028**: `LauncherFirebaseMessagingService.onMessageReceived` MUST parse FCM `data: Map<String, String>` → `PushPayload`, lookup handler в `PushHandlerRegistry`, dispatch coroutine.

**Operational (logging, threading, manifest, permissions)**:

- **FR-070**: Push subsystem logs (Logcat + Cloudflare Analytics) MUST use **truncated UID** (first 8 hex chars) + `eventType` + outcome category. MUST NOT include: full ownerUid, full FCM token, full Idempotency-Key, payload field values (только presence/absence). Verbose request/response logging gated behind `BuildConfig.DEBUG`. Worker analytics MUST NOT log JWT body.
- **FR-071**: `LauncherFirebaseMessagingService` AndroidManifest declaration MUST set `android:exported="false"` (Android 12+ enforcement; service receives FCM messages from system, not external apps).
- **FR-072**: F-5c MUST NOT request `POST_NOTIFICATIONS` permission. All F-5c FCM messages are **data-only** (no `notification` key в FCM payload) — delivered к `onMessageReceived` без visible notification permission. Future event types requiring visible alert (e.g. SOS в S-4) request permission separately в своих specs.
- **FR-073**: All push subsystem calls (`PushTrigger.trigger`, `FcmTokenPublisher.publish`, `PushHandler.handle`) MUST execute on `Dispatchers.IO`. UI / main thread MUST NOT directly invoke push subsystem. `FcmTokenPublisher.publish()` invocation после Sign-In MUST NOT block cold start critical path (HomeActivity ≤ 600ms per ADR-005) — launched async post-Sign-In.
- **FR-074**: Push handler dispatch MUST use foundation-provided `BackgroundDispatcher` port. `LauncherFirebaseMessagingService.onMessageReceived` parses payload, looks up handler в `PushHandlerRegistry`, **then dispatches handler через `BackgroundDispatcher.dispatch(taskName, timeout = eventType.handlerTimeout) { handler.handle(payload) }`** — НЕ invokes handler directly в onMessageReceived. Android adapter `WorkManagerBackgroundDispatcher` uses `WorkManager` под капотом (более мягкие system limits — может работать минутами / часами для big payloads вроде photo downloads). Consumer `PushHandler.handle()` НЕ manages lifecycle / timeout / retry — foundation handles. Это **default model**, не fallback: WorkManager используется для ВСЕХ event types по умолчанию, чтобы избежать coupling event types к Android 10-second budget.
- **FR-075**: Receiver `LauncherFirebaseMessagingService.onMessageReceived` MUST handle malformed `data: Map<String, String>` (missing required fields, invalid types в payload field, `schemaVersion > MAX_SUPPORTED`) gracefully: Logcat warning (category, not raw payload), silent ignore, **never crash**. Per CHK017 wire-format checklist.
- **FR-076**: `PushTrigger.trigger()` returns `Outcome<Unit, PushTriggerError>`. `PushTriggerError` sealed class MUST include variants:
  - `Unauthorized` — emitted when Worker returns 401 (JWT expired/invalid).
  - `RateLimited` — emitted when Worker returns 429 (per-UID rate limit exceeded).
  - `NetworkFailure` — emitted on network failure (no connectivity, timeout, TLS error).
  - `Backend(message: String)` — emitted on Worker 5xx (FCM dispatch failure, internal error).
  All variants MUST be logged per FR-070, then ignored by caller (fire-and-forget per FR-026).

- **FR-077**: `EventType` sealed interface MUST expose `handlerTimeout: Duration` property с default value (`30.seconds`). Consumer event types override для специфических needs:
  ```kotlin
  data object ConfigUpdated : EventType {
      override val wireValue = "config-updated"
      // uses default 30s — config небольшой
  }
  // Future:
  data object AlbumPhotoAdded : EventType {
      override val wireValue = "album-photo-added"
      override val handlerTimeout = 5.minutes  // photo download long
  }
  data object SosTriggered : EventType {
      override val wireValue = "sos-triggered"
      override val handlerTimeout = 10.seconds  // SOS должно быть fast или fail loud
  }
  ```
  `LauncherFirebaseMessagingService` uses `eventType.handlerTimeout` при dispatch через `BackgroundDispatcher` (FR-074).

- **FR-078**: `core/push/api/BackgroundDispatcher.kt` MUST expose generic port:
  ```kotlin
  interface BackgroundDispatcher {
      suspend fun dispatch(
          taskName: String,
          timeout: Duration,
          retryStrategy: RetryStrategy = RetryStrategy.ExponentialBackoff,
          block: suspend () -> Unit,
      )
  }
  ```
  Port abstracts platform-specific long-running background task mechanism. Android adapter: `WorkManagerBackgroundDispatcher` (uses `WorkManager`). iOS adapter (future): `BGTaskBackgroundDispatcher` (uses `BGTaskScheduler`). Port НЕ leaks platform types (WorkRequest, BGTask, etc.) — caller (LauncherFirebaseMessagingService) sees только generic interface.

**Graceful degradation**:

- **FR-038**: If push delivery fails for any reason (Worker 5xx, FCM quota exhausted, recipient offline beyond TTL, JWKS rotation race) — recipient MUST converge to fresh state через existing pull-on-app-open path (`ConfigSaver.loadOwn(...)` invoked at app foreground per F-5b). Local cache (F-5b three-tier model) preserved indefinitely. **No user-visible failure indicator** для F-5c push transport failures (per FR-045 + Cloud-mode integration section).

**EventType-specific — `config-updated`**:

- **FR-040**: EventTypeRegistry entry для `config-updated`:
  ```typescript
  'config-updated': {
      authorise: (caller, ownerUid) => isOwnerOrGrantHolder(caller, ownerUid),
      rateLimit: { perUid: 60, windowSeconds: 60 },
      collapseKey: (payload) => `${payload.ownerUid}:${payload.configName}`,
      priority: 'normal',
  }
  ```
- **FR-041**: `core/push/api/EventType.kt` MUST include `data object ConfigUpdated : EventType { override val wireValue = "config-updated" }`.
- **FR-042**: `ConfigSaver` impl MUST invoke `pushTrigger.trigger(EventType.ConfigUpdated, OwnAndGrants, ownerUid, mapOf("configName" to configName))` после successful write. Fire-and-forget (FR-026).
- **FR-043**: Receiver handler `ConfigUpdatedHandler : PushHandler` MUST extract `configName` из `payload.fields["configName"]`, invoke `ConfigSaver.loadOwn(configName)` if `payload.ownerUid == currentUid` else `loadForOther(payload.ownerUid, configName)`, then trigger DataStore + UI refresh.
- **FR-044**: Receiver handler MUST be idempotent — repeated push receipts для same `triggerId` within 2-second debounce window → at most one `loadOwn` invocation.
- **FR-045**: Push receipt MUST NOT pop user-visible notification — data-only FCM message, no user-facing alert (per CLAUDE.md rule 10).

**Wire format**:

- **FR-050**: `PushTriggerRequest` JSON shape с `schemaVersion: 1`. Roundtrip test (write → read → assert equal). Backward-compat test (read v0 формат, который не существует пока, но contract запрещает регрессии).
- **FR-051**: Adding new optional field — additive (старые читатели игнорят неизвестное). Adding new required field или renaming — major bump `schemaVersion 1 → 2` + parallel reads на 1 release (rule 5).
- **FR-052**: PushPayload extension to carry new event types — additive (новый sealed variant `EventType.X`). Old receiver получает unknown eventType → silent ignore (FR-023).

**Future-extensibility**:

- **FR-060**: Adding new event type требует ровно три места изменений:
  1. EventTypeRegistry entry в Worker (~10 строк TypeScript).
  2. `data object NewEvent : EventType` в `core/push/api/EventType.kt` (1 строка).
  3. Новый `PushHandler` implementation + DI registration в consumer module (~10-15 строк Kotlin).
  Никаких изменений в foundation Worker (`POST /push`, JWT, idempotency, rate-limit, FCM dispatch, recipient resolution).

### Key Entities

- **EventType** — sealed interface, whitelist event types (ConfigUpdated, future SosTriggered, MessageArrived, ...). Wire-value strings used by Worker + receivers. Carries `handlerTimeout: Duration` property для per-event-type background dispatch timeout.
- **TargetScope** — enum для recipient resolution strategy (OwnDevices, OwnAndGrants).
- **PushTriggerRequest** — wire-format DTO для `POST /push` body.
- **PushPayload** — wire-format DTO для FCM data-message receiver-side.
- **PushTriggerError** — sealed class — Unauthorized / RateLimited / NetworkFailure / Backend(message).
- **BackgroundDispatcher** — port abstracting platform's long-running background task mechanism (WorkManager на Android, BGTaskScheduler на iOS).
- **RetryStrategy** — sealed class / enum (ExponentialBackoff, NoRetry, FixedDelay) — per-event-type retry policy.
- **EventTypeRegistryEntry** (Worker) — `{authorise, rateLimit, collapseKey, priority}` per event type.
- **RecipientDeviceEntry** — F-5b directory entry `/users/{uid}/devices/{deviceId}` расширенный полем `fcmToken: String?`.
- **VerificationResult** (auth-jwt module) — sealed type `{ok: true, claims} | {ok: false, error}` — public API of `workers/_shared/auth-jwt/`.

---

## Wire-format policy

> Этот раздел fixes wire-format invariants для всех push payloads (`PushTriggerRequest` HTTP body + `PushPayload` FCM data-message). Per CLAUDE.md rule 5.

- **Deserialization order**: deserializer MUST read `schemaVersion` field **first**. If `schemaVersion > MAX_SUPPORTED` → fail fast без parsing остальных полей.
- **`MAX_SUPPORTED` location** — single source of truth:
  - Kotlin: `core/push/api/WireFormatVersion.kt` (constant `MAX_SUPPORTED_SCHEMA_VERSION: Int = 1`).
  - TypeScript: `workers/push/src/contract/wire-format.ts` (constant `MAX_SUPPORTED_SCHEMA_VERSION: number = 1`).
  - **These two constants MUST be kept in sync** при каждом bump. PR check (CI script) validates equality.
- **Forward-compat asymmetry** (intentional):
  - **Worker**: fail-closed on unknown schemaVersion → HTTP 400 (atomic deploys, controlled upgrade timeline).
  - **Client receiver**: fail-soft on unknown discriminator (eventType) → silent log + ignore (mixed-version fleet, uncontrolled upgrade timeline). On unknown schemaVersion → silent log + ignore (treats как unknown future payload).
- **URL versioning strategy**: **body-versioning** chosen (schemaVersion в body), NOT URL path versioning. Worker URL stable across versions (`/push` единственный endpoint forever). При v2 — schemaVersion в body bumps, URL остаётся.
- **Schema evolution rules** (per FR-051):
  - Adding optional field — additive (старые читатели игнорят).
  - Adding required field или renaming/removing field — major bump `schemaVersion N → N+1` + parallel reads на 1 release.
- **Roundtrip + backward-compat tests** mandatory per FR-050.

---

## Cloud-mode integration

> Этот раздел fixes F-5c behaviour vis-à-vis device self-sufficiency principle (decision 2026-06-15-deferred-cloud).

- **F-5c mode**: **CLOUD-only**. Push channel inherently требует server intermediary (FCM + Cloudflare Worker + Firestore directory). Нет local-only fallback.
- **Activation timing**: F-5c активируется **только after Sign-In completed** (F-4 `AuthIdentity` present). F-5c **не triggers** Sign-In prompt самостоятельно — relies на existing Sign-In state, established earlier by F-4 / first cloud action.
- **Local mode integration** (no Sign-In, no cloud features):
  - DI provides `NullPushTrigger` (no-op implementation): `trigger(...)` returns `Outcome.Success(Unit)` без network call.
  - `FcmTokenPublisher` not invoked (no Sign-In = no UID namespace для publishing).
  - `LauncherFirebaseMessagingService` остаётся declared в manifest, но `onMessageReceived` returns immediately если no AuthIdentity (system-level guard).
  - Flavor split: `mockBackend` flavor + `local-only` runtime config → `NullPushTrigger`. `realBackend` cloud-active → real `HttpPushTrigger`.
- **Subscription expiry handling** (S-10 territory, F-5c just respects):
  - Worker validates entitlement (S-10 will add check к event-authorisation rules). On expired → 401 Unauthorized.
  - Client receives `PushTriggerError.Unauthorized` → fire-and-forget logs, recipient'ы converge через pull-on-app-open.
  - Local cache (F-5b three-tier model) preserved indefinitely.
- **No-internet behaviour**:
  - `HttpPushTrigger.trigger()` returns `Outcome.Failure(NetworkFailure)`.
  - ConfigSaver.saveOwn() ignores (fire-and-forget per FR-026). Если save самим не succeeded (Firestore unreachable) — F-5b territory handles error UX.
  - Recipients converge при следующем foreground через `ConfigSaver.loadOwn` pull.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After admin saves config on device A, device B of same admin reflects change в **5 секунд median, 30 секунд p95** (both online, Firebase Emulator + FCM dev project).
- **SC-002**: `PushTrigger.trigger(...)` MUST NOT add more than **200ms p95** к `ConfigSaver.saveOwn(...)` call latency (fire-and-forget).
- **SC-003**: Cross-UID delegation: granny's device receives push and refreshes UI в **10 секунд median**.
- **SC-004**: FCM token rotation (simulated `FirebaseMessaging.deleteToken` + `onNewToken`) → updated directory entry в **5 секунд** of next app foreground.
- **SC-005**: Worker 503 → client save completes successfully; recipient gets fresh config via pull-on-app-open at **next foreground**.
- **SC-006**: Idempotency: 10 duplicate push receipts с same `triggerId` within 2s window → **exactly 1** `loadOwn` invocation.
- **SC-007**: Worker handles **100 concurrent `/push` calls** within CF free-tier CPU limits (<10ms per request excluding FCM API latency).
- **SC-008**: **Foundation reusability**: добавление нового event type (e.g. dummy `"test-ping"`) requires changes **only** в EventTypeRegistry (1 entry) + `EventType` sealed (1 line) + new handler + DI (≤15 lines) — **никаких** изменений в `PushTrigger` impl, Worker entrypoint, JWT verification, idempotency, rate-limit, FCM dispatch.
- **SC-009**: **Extraction-readiness**: `core/push/` MUST NOT иметь зависимостей от launcher-specific modules (`core/launcher/*`, `feature/*`). Проверяется fitness test (rule 7). `core/push/api/` package MUST NOT expose Android-specific types.

---

## Assumptions

- Cloudflare Worker free tier (`*.workers.dev`) sufficient для MVP scale. Exit ramp в [TODO-ARCH-001](../../docs/dev/project-backlog.md) (custom domain) + [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md) (own backend).
- Firebase Spark plan FCM quota (10K push/day) достаточен. ~500 admins × ~20 push/day → 10K. Beyond — upgrade Blaze.
- Recipients change infrequently (per spec 018 — 1-2 раза за всё время), Worker resolves fresh per request без кеша.
- Push = explicit user action per spec 008 model (US-4 «pending changes», FR-047 «Отправить сейчас» button). Нет шторма из auto-save → не нужен client debounce.
- Spec 008 rewrite (Шаг 4b) построит collaborative editing UI **поверх** F-5c push channel. F-5c доставляет «config-updated» pings; merge UI / pending-changes warnings — scope 008.
- FCM data-message delivery best-effort. Eventually-consistent pull fallback (`ConfigSaver.loadOwn` at app open) — acceptable safety net.

---

## Reuse pattern для будущих consumers

> Это foundation. Каждый последующий event type добавляется в 3 места × ~30 строк. Конкретные планируемые consumers с предполагаемыми event types:

| Spec | Event types | Authorisation | Rate limit | Priority | Collapse |
|---|---|---|---|---|---|
| Spec 008 rewrite (Шаг 4b) | (можно использовать `config-updated` без новых event types) | — | — | — | — |
| **S-2** Admin App + QR Pairing | `pairing-accepted`, `pairing-revoked` | owner | 20/min | normal | per pairingId |
| **S-4** SOS Capability | `sos-triggered` | owner (= senior) | 10/min | **high** | none (each SOS unique) |
| **S-9** Phone Health | `battery-critical`, `device-offline`, `activity-anomaly` | owner | 60/min | normal/high | per device |
| **S-10** Subscription Server Timer | `entitlement-expired`, `grace-period-ending` | server-internal только | — | normal | per uid |
| **V-2** Messenger (Phase 4) | `message-arrived`, `call-incoming` | sender (= caller.uid) | 100/min | **high** для calls | per conversationId |
| **V-3** Family Album (Phase 4) | `album-photo-added`, `album-comment` | family member | 60/min | normal | per albumId |
| **V-6** Caregiver Remote Invite (Phase 4) | `caregiver-invited`, `caregiver-accepted` | owner | 10/min | normal | none |

**Pattern для нового consumer**:
```
1. Add EventTypeRegistry entry (Worker TypeScript, ~10 LOC)
2. Add `data object NewEvent : EventType` (Kotlin, 1 LOC)
3. Write `NewEventHandler : PushHandler` (Kotlin, ~10-15 LOC)
4. Register handler в DI (~3 LOC)
5. Call `pushTrigger.trigger(...)` где нужно (~5 LOC)
```

Total per event type: ~30-40 LOC. Никаких изменений foundation.

---

## Local Test Path *(mandatory)*

- **Emulator / device**: Android emulator (skill `android-emulator`) — 2 эмулятора параллельно для multi-device sync verification (US-1). JVM unit tests для Worker logic (auth, registry, rate-limit, idempotency, recipient resolution). Worker tests через `wrangler dev` (локальный Worker runtime).
- **Fake adapters used**:
  - `FakeFcmDispatcher` (in Worker tests) — captures FCM messages для assertion, не вызывает Google API.
  - `FakeFirebaseAuth` — issues test ID-tokens (signed by test JWKS) для Worker auth tests.
  - `InMemoryRecipientResolver` — replaces Firestore directory read в unit tests.
  - `FakePushTrigger` (в `core/push/commonTest/fakes/`) — captures trigger calls для consumer-side tests.
  - `FakeFcmTokenPublisher` (в `core/push/commonTest/fakes/`) — captures publish calls.
  - `FakePushHandler` / `FakePushHandlerRegistry` (в `core/push/commonTest/fakes/`) — для receiver-side tests.
  - `LauncherPushReceiverTestProbe` — captures push events на Android side.
- **DI flavor split**: `mockBackend` flavor wires fakes + `NullPushTrigger` для local-only dev/test. `realBackend` flavor wires `HttpPushTrigger` + real `FcmTokenPublisher` (per existing convention F-5b).
- **Test reproducibility**: `HttpPushTrigger` MUST accept `UuidGenerator` + `Clock` через constructor — tests inject `FixedUuidGenerator` + `FixedClock` для reproducible `Idempotency-Key` + `triggerId`.
- **Fixtures / seed data**:
  - `workers/push/test/fixtures/jwks-test.json` — synthetic JWKS для signing test tokens.
  - `workers/push/test/fixtures/grant-firestore-snapshot.json` — synthetic Firestore directory.
  - `core/push/commonTest/resources/push-payload-v1.json` — wire-format roundtrip fixture.
- **Verification commands**:
  - `./gradlew :core:push:test --tests *PushTrigger*` — port contract + roundtrip.
  - `./gradlew :core:push:jvmTest --tests *WireFormat*` — wire-format JSON roundtrip + backward-compat.
  - `./gradlew :app:connectedDebugAndroidTest --tests *ConfigUpdatedPushE2ETest*` — 2 эмулятора, device A saves, device B receives push, UI verified.
  - `cd workers/push && npm test` — Worker logic (Vitest).
  - `cd workers/push && wrangler dev` + integration test от `core/push/jvmTest/` против локального Worker'а — wire-format contract test.
- **Cannot-test-locally gaps**:
  - Real FCM delivery latency из production Google infrastructure → `TODO(physical-device)` для measurement в реальной сети.
  - Real CF edge CPU limits (free-tier production может отличаться от `wrangler dev`) → manual smoke перед merge.
  - Multi-device с Xiaomi MIUI battery restrictions на background FCM → `TODO(physical-device)` per OEM matrix.
  - JWKS rotation от Google в production — нельзя simulate, monitor + alert в production.

---

## AI Affordance *(mandatory)*

No AI affordance — internal infrastructure capability only. F-5c строит generic push transport infrastructure между authenticated devices. Нет user-facing AI surface (push event — системный факт, не user intent).

Future AI features (например «AI summarises what changed in config») будут читать DataStore directly post-refresh — не будут invoking push channel напрямую. Capability Registry readiness N/A.

**Однако**: будущие consumers (V-2 Messenger) могут exposить AI surfaces поверх своих event types (например AI suggests reply на `message-arrived`). Это **их** ответственность — F-5c только транспорт.

---

## OEM Matrix *(mandatory if feature touches device behavior)*

F-5c touches FCM background message receipt — OEM-divergent territory.

| OEM / surface | Known divergence | Mitigation in this spec | Verification source |
|---|---|---|---|
| Stock Android (Pixel) | baseline — FCM data-message delivers in background | none needed | emulator `pixel_5_api_34` |
| Samsung One UI | Adaptive Battery may delay FCM for non-priority apps after 3 days idle | App must be added to «Never sleeping apps» whitelist (covered в S-1 wizard); F-5c documents requirement | `TODO(physical-device)` Samsung |
| Xiaomi MIUI | Autostart manager blocks `FirebaseMessagingService` from waking app | Reuses spec 007 mitigation (autostart deep-link в setup wizard) | `TODO(physical-device)` Xiaomi 11T (already verified spec 018) |
| Huawei EMUI | Protected apps list required для FCM background | Same as Xiaomi — wizard surface | `TODO(physical-device)` Huawei |

**Note**: F-5c не вводит новых OEM-mitigations — reuses spec 007's FCM infrastructure. Новые строки появятся только если конкретный device сломает `config-updated` data-message specifically.

---

## Notes / gotchas

- **One-way doors закрытые в Clarifications**:
  - PushPayload breaking change (Q1=C) — wire-format migration. Exit ramp: schemaVersion bump 1→2 + parallel reads (rule 5).
  - Hardcoded Worker URL (Q5=A) — смена URL = app update. Exit ramp [TODO-ARCH-001](../../docs/dev/project-backlog.md) (custom domain).
  - Worker hosting на CF free tier — exit ramp [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md) (own backend).
- **Foundation extraction** запланирована в [TODO-ARCH-017](../../docs/dev/project-backlog.md#todo-arch-017-push-foundation-extraction-в-отдельные-repos-). Триггер: V-2 spec start (Phase 4). Сейчас monorepo — правильное решение для единственного consumer.
- **Pre-extraction hygiene** (поддерживать с первого commit):
  - НЕ добавлять в `core/push/` зависимости от launcher-specific модулей.
  - НЕ позволять Android-specific типам утекать в `core/push/api/`.
  - Любое расширение wire-format — `schemaVersion` bump.
- **FCM `FCM_SERVER_KEY` storage**: Cloudflare Secrets (`wrangler secret put FCM_SERVER_KEY`), не в коде. Inline TODO в security доку: ротация каждые 90 дней.
- **Codegen vs manual sync wire-format**: сейчас manual sync `core/push/api/PushTriggerRequest.kt` ↔ `workers/push/src/contract/wire-format.ts`. Когда event types > 5 — рассмотреть codegen из общего OpenAPI. Сейчас premature.
- **Observability**: Cloudflare Analytics с custom label `eventType` — бесплатный breakdown. Inline TODO когда event types > 5: simple dashboard.
- **JWKS rotation incident playbook**: documented в [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md) — force-refresh JWKS через `wrangler kv:key delete jwks --namespace=...`.
- **Workers KV consistency model**: eventually consistent (60s globally). Idempotency dedupe на одной edge node может пропустить дубликат на другой edge node в первые 60s. Acceptable (receiver idempotency защищает downstream).
- **Spec 008 rewrite dependency**: F-5c assumption «push = explicit user action» полагается на spec 008 model (pending state + «Отправить сейчас» button). Если 008 rewrite пойдёт в другую сторону (auto-push после inactivity) — F-5c assumption ломается. Документируется в spec 008 rewrite Clarifications.

**Architectural rationale notes** (added per `/speckit.clarify` checklist actions):

- **Why module not package** (`core/push/` as standalone Gradle module): extraction-readiness preserved at near-zero cost (one extra `build.gradle.kts`). Package→module refactor когда V-2 spec triggers extraction (per [TODO-ARCH-017](../../docs/dev/project-backlog.md)) — expensive (rebuild dependency graph, rename imports across codebase). Module-now = cheap insurance.
- **Why registry over switch** (`EventTypeRegistry` Worker + `PushHandlerRegistry` client): switch statement в Worker entrypoint требовал бы foundation modification per new event type (rewrite, не addition) — contradicts FR-060. Registry pattern минимальная abstraction достигающая FR-060. 9 known consumers prevent simpler «switch + grow over time» от being practical.
- **HTTP client placement**: Ktor client lives в `core/push/commonMain/impl/DefaultPushTrigger.kt` (KMP-compatible). NOT in androidMain — позволяет future iOS / desktop ports use тот же impl без re-write. Android-specific orchestration (Firebase Messaging Service lifecycle) — отдельно в androidMain.
- **Identity convention** (inherited): `ownerUid: String` = Firebase UID directly. Inherited from F-4 (`AuthIdentity.currentUid()`) и F-5b (envelope namespace addressing). F-5c does NOT introduce new identity coupling — only propagates existing convention. Exit ramp на own-server identity = F-4/F-5b territory, не F-5c scope.
- **PushTrigger = event-level abstraction, not transport-level**: `PushTrigger` port абстрагирует **event triggering** (domain concern — «notify recipients about event X»). FCM transport остаётся **fixed implementation** behind `FcmTokenPublisher` + `LauncherFirebaseMessagingService` + Worker `dispatch/fcm-dispatcher.ts`. F-5c НЕ строит «universal push SDK абстрагируя FCM + APNS + WebPush» — это было бы over-engineering. Future iOS implementation добавит `ApnsTokenPublisher` adapter (separate adapter implementing same `FcmTokenPublisher`-equivalent port renamed), не expect/actual cross-provider layer.
- **Linking deprecated `linkId`**: existing `core/src/.../api/push/PushPayload.kt` field `linkId: String` будет changed на `linkId: String? = null` (nullable, deprecated). `TODO(removal SRV-PUSH-FOUNDATION future): drop linkId field в schemaVersion 2 bump после spec 008 rewrite ships and no in-prod legacy consumers remain`. 8 файлов affected (см. § Scope migration).
- **User mental model — push failure invisibility**: «save» в user mental model = local persistence + cloud upload (per F-5b). Push transport — system-level optimization. **Push failure transparent to user** — recipients converge через pull-on-app-open. Если в будущем user feedback показывает confusion («сохранил, но другой телефон не обновился сразу») — UI may surface pending push status в S-008 rewrite scope.
- **Failure visibility per-event-type**: `config-updated` = silent (cache invalidation — нет user-facing impact от failure). Future events (например `sos-triggered` в S-4) MAY require user-visible failure indicator — это определяется в их specs, не overrides F-5c silent default.
- **FCM listener profile**: source = FCM data-messages (subscribed implicitly via service declaration), frequency ~10 push/day per device (5 admin saves × 2 recipients), threading = Firebase background thread, battery cost ~negligible per message, fallback = pull-on-app-open.
- **WAKE_LOCK reliance**: F-5c relies on Firebase Messaging SDK's internal WAKE_LOCK для FCM message wake-up из doze. Standard Firebase behaviour. F-5c does NOT acquire custom wake locks.
- **WorkManager as default dispatch (not fallback)**: Все push handlers по умолчанию работают в WorkManager job через `BackgroundDispatcher` port (FR-074, FR-077, FR-078). Reason: F-5c — foundation для **9 known consumers**, некоторые из которых имеют long-running payloads (V-3 album = МБ photo downloads, V-2 messenger = file attachments). «Trust 10s budget + fallback to pull» работало бы для config-updated, но ломалось бы для photo events. WorkManager-by-default = единая модель для всех event types, foundation handles lifecycle, consumer пишет handler без знания о timing. Per-event-type timeout декларируется в EventType (default 30s, album 5min, SOS 10s).
- **Auth-jwt extraction** (`workers/_shared/auth-jwt/`): JWT verification — это **отдельный concern**, не push. Извлекается в standalone TypeScript module внутри monorepo с первого commit. Push Worker импортирует `verifyFirebaseIdToken()` — НЕ реализует JWT логику сам. Это setup для (a) будущих Worker'ов которые тоже нужно auth (S-4 SOS Worker если decoupled, V-3 Album Worker, и т.д.), (b) migration на свой identity provider — auth-jwt module расширяется на множественные providers без затрагивания push кода.
- **Three types of «tokens» в F-5c** (для disambiguation):
  - **Firebase Auth ID-token**: JWT который Worker валидирует (через auth-jwt module). «Кто этот пользователь». Issued by Google Firebase Auth.
  - **FCM Service Account JWT**: short-lived JWT который Worker сам генерит при call'е к FCM HTTP v1 API. «Worker = backend этого Firebase project». Generated from Service Account JSON.
  - **FCM device token**: routing identifier (не JWT) выданный Google для каждого устройства. «Куда отправлять push». Stored в `/users/{uid}/devices/{deviceId}/fcmToken`.
- **FCM Service Account migration**: если переедем на свой backend и оставим FCM как push transport — нужно зарегистрировать backend в Google FCM (Firebase project + Service Account JSON + FCM Sender role). SA private key переезжает в свой secret manager (Vault / AWS Secrets Manager). Documented в SRV-PUSH-FOUNDATION. Если переедем на свой push transport (WebPush / APNS) — FCM SA не нужен, `dispatch/fcm-dispatcher.ts` replaced с new transport.
