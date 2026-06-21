# Checklist: backend-substitution — spec 019 F-5c

**Spec**: [spec.md](../spec.md)
**Run date**: 2026-06-20
**Run context**: post-rescope clarify pass (Cloudflare Worker + Firebase services touched)

## Adapter boundary

- [x] **CHK001** No provider type в domain.
  - `core/push/api/` (public): нет `FirebaseFirestore`, `DocumentReference`, `DocumentSnapshot`, `FirebaseUser`, `FirebaseAuth`, Cloudflare Worker types.
  - `core/push/api/` использует только Kotlin primitives + domain sealed/enum.
  - Firebase Messaging types (`RemoteMessage`, `FirebaseMessaging`) — только в `androidMain/LauncherFirebaseMessagingService` + `FcmTokenPublisher` impl.
  - Cloudflare types (`ExecutionContext`, `Request`, `Response`) — только в Worker TypeScript, не в Android codebase.

- [x] **CHK002** Each provider в одном adapter.
  - **Firebase Messaging SDK** → `core/push/androidMain/` (FcmTokenPublisher impl + LauncherFirebaseMessagingService).
  - **Firebase Auth** (ID-token acquisition) → reused через existing F-4 `AuthIdentity` port. Не added в F-5c.
  - **Firestore client SDK** (для F-5b directory read из Worker side): Worker не использует Firestore client SDK (Workers runtime не supports), а Firestore REST API через jose-signed service account. Wrapped в Worker `recipient/resolver.ts`.
  - **jose library**: только в `workers/family-push/src/auth/jwks-verifier.ts`.
  - **Cloudflare Worker runtime** APIs (KV, fetch): только в Worker code.

- [x] **CHK003** «Provider disappears» test answered.
  - **FCM Google deprecates**: 3 files в 2 modules:
    - `core/push/androidMain/FcmTokenPublisher.kt` impl (replace SDK call).
    - `core/push/androidMain/LauncherFirebaseMessagingService.kt` (replace Android Service base).
    - `workers/family-push/src/dispatch/fcm-dispatcher.ts` (replace FCM HTTP API call).
  - **Cloudflare Worker disappears / unaffordable**: port full Worker logic (full `workers/family-push/`) на own backend per SRV-PUSH-FOUNDATION. ~5-7 дней work.
  - **Workers KV disappears**: replace KV-based idempotency + JWKS cache + rate-limit с Redis или alternative. 3 files в Worker.
  - **jose library deprecates**: replace JWT verification — 1 file (`workers/family-push/src/auth/jwks-verifier.ts`).
  - **All bounded к one adapter / one module**.

## Wire format

- [x] **CHK004** Persisted remotely is domain-owned, schema-versioned data class — не provider-shaped.
  - `RecipientDeviceEntry` (F-5b directory entry, расширенный с `fcmToken: String?`) — domain DTO. `fcmToken` — обычная `String`, не `InstanceIdResult` или `FirebaseMessaging` type.
  - Не Firestore `Timestamp`, не `FieldValue.serverTimestamp()`, не `arrayUnion()` semantics в persisted model.
  - Server timestamps (если нужны для `createdAt` идемпотентности) handles Firestore adapter, не leaks в domain.

- [x] **CHK005** schemaVersion field с первого commit. FR-001, FR-024.

- [x] **CHK006** Roundtrip test существует. FR-050.

## Identity

- [⚠️] **CHK007** Domain primary key для «user» — project-owned (UserId/ULID/UUID), не Firebase UID.
  - **Violation**: spec использует `ownerUid: String` throughout (`PushTrigger.trigger(..., ownerUid = currentUid)`, `pushPayload.ownerUid`). Это **Firebase UID directly**.
  - **Inherited convention**: F-5b (spec 018) использует `ownerUid` как primary key. F-4 (spec 017) AuthIdentity exposes `currentUid()` which returns Firebase UID.
  - **Status**: convention violation но **consistent** с F-4/F-5b. Не вводит new violation в F-5c.

- [⚠️] **CHK008** Provider-issued identifiers stored as credentials в auth adapter.
  - **НЕТ mapping** Firebase UID → internal UserId. UID exposed throughout.
  - **Inherited**: F-4/F-5b territory. Не F-5c concern.

- [x] **CHK009** Provider UID as domain ID — one-way door + exit ramp documented.
  - **F-5c inherits this one-way door from F-4/F-5b**. Не вводит свой.
  - **Action**: добавить note в spec.md «Identity convention inherited from F-4 (AuthIdentity) + F-5b (envelope namespace). Exit ramp на own-server identity = part of broader F-4/F-5b migration, не F-5c scope».

## Query/command surface

- [x] **CHK010** Domain talks в domain verbs.
  - `PushTrigger.trigger(eventType, scope, ownerUid, payload)` — «trigger event for recipients» = domain verb.
  - `PushHandler.handle(payload)` — «handle event».
  - `FcmTokenPublisher.publish(token)` — «publish my token».
  - Не `httpPostToWorker(json)`, не `firestore.collection("users").doc(uid).update(...)`.

- [x] **CHK011** No security-rules-shaped / transport-shaped logic в calling code.
  - `ConfigSaver` calls `pushTrigger.trigger(...)` без знания о security rules, без re-read для validation, без server-timestamp manipulation.
  - Worker `auth/event-authorisation.ts` handles per-event-type rules (config-updated requires owner ∨ grant; sos-triggered requires owner). Не leaks в client.
  - Firestore Security Rules (F-5b territory) — separate concern, не propagates в F-5c calling code.

## Server-roadmap surfacing

- [x] **CHK012** Server-roadmap entry exists.
  - `SRV-PUSH-FOUNDATION` (новый, заменил `SRV-FCM-CONFIG-UPDATE`).
  - `SRV-PUSH-EXTRACTION` (новый, future).
  - Покрывают: full Worker migration на own backend, Workers KV → Redis, jose → Java JOSE.

- [x] **CHK013** Inline `TODO(server-roadmap)` markers planned.
  - SRV-PUSH-FOUNDATION enumerates 4 inline TODOs в Worker source:
    - `auth/jwks-verifier.ts`: «port to own backend, use Java JOSE».
    - `dispatch/fcm-dispatcher.ts`: «replace with Firebase Admin SDK when off CF».
    - `dedupe/idempotency.ts`: «Workers KV → Redis on own backend».
    - `ratelimit/rate-limiter.ts`: «same pattern».
  - Planned to land при implementation (tasks.md).

## Exemptions

- [⚠️] **CHK014** Feature не classifies FCM/APNs/SMS/telephony/biometrics как «substitutable backend».
  - **Concern**: spec вводит `PushTrigger` port — это high-level abstraction.
  - **Distinction**: PushTrigger абстрагирует **event triggering** (домен-уровень), не **push transport** (платформа-уровень).
  - **Transport layer (FCM) НЕ abstracted**: только один FCM impl (`HttpPushTrigger` → Worker → FCM). Не «UniversalPushSDK с FCM + APNS branches».
  - Event-level abstraction оправдана 9 known consumers (per modular-delivery CHK017, meta-minimization CHK010). Transport-level abstraction отсутствует — нет over-engineering.
  - **Status**: технически compliant. Документировать distinction чтобы reviewers не путали.
  - **Action**: добавить note в spec.md: «`PushTrigger` — event-level domain abstraction (9 known consumers). FCM transport — fixed implementation behind `FcmTokenPublisher` + `LauncherFirebaseMessagingService`. iOS future via separate APNS adapter implementing same ports, не expect/actual cross-provider abstraction».

- [x] **CHK015** No needless cross-provider abstraction для exempt platform integration.
  - Один FCM adapter set (FcmTokenPublisher + LauncherFirebaseMessagingService), не FCM+APNS+WebPush universal layer.
  - PushTrigger NOT trying to abstract FCM↔APNS↔WebPush — оно abstract событийный layer выше.

## Cost-of-swap summary (CHK016)

> Если Cloudflare Worker заменён собственным сервером (Ktor backend на нашем хостинге):
>
> **Что переписать**:
> 1. Full `workers/family-push/` (~10-15 файлов TypeScript) → port на Kotlin Ktor (~15-20 файлов).
> 2. `jose` (JS) → Java JOSE library (Nimbus или эквивалент).
> 3. Workers KV → Redis (или Postgres) для JWKS cache + idempotency + rate-limit.
> 4. Recipient resolution: Firestore REST API → собственная БД (user-device-grant tables).
> 5. FCM dispatch: HTTP API call → Firebase Admin SDK (на сервере без CF CPU limits).
>
> **Что НЕ менять**:
> - Client `HttpPushTrigger` — только URL constant.
> - Wire format JSON (PushTriggerRequest, PushPayload) — unchanged.
> - All consumers (`ConfigSaver`, future `SosService`, ...) — unchanged.
> - All PushHandlers — unchanged.
>
> **Migration data**: FCM tokens — ephemeral, just re-publish из device. Idempotency cache — discard (10-min TTL). JWKS cache — refetch.
>
> **Estimated bounded cost**: ~15-20 файлов в одном новом модуле (own backend Ktor service) + ~5-7 days work. **Не trigger'ит** rewrite domain/UI/consumers.
>
> Если **FCM деprecated by Google** (less likely, but tested):
>
> 1. Replace `FcmTokenPublisher` impl (1 file) — другой push SDK (e.g. APNS бридж для iOS-only, WebPush для PWA, own push protocol).
> 2. Replace `LauncherFirebaseMessagingService` (1 file) — другой Android Service base или other receiver mechanism.
> 3. Replace `workers/family-push/src/dispatch/fcm-dispatcher.ts` (1 file).
> 4. Total: 3 files. Bounded.
>
> Если **Firebase Auth deprecated** (most disruptive):
>
> - F-4 (spec 017) territory. F-5c inherits identity convention, не вводит свой.
> - Cost = F-4 territory + replace `jose` Worker JWT verification для new OIDC provider (1 file).

## Summary

- **Pass**: 11/16
- **Partial/Warning**: 3/16 (CHK007, CHK008, CHK014)
- **Fail**: 0/16

**Big picture**: Backend substitution **clean**:
- Все 3 «провайдера» (FCM, Cloudflare, jose) wrapped в adapters.
- Wire format domain-owned + schemaVersion.
- Server-roadmap entries актуальны + inline TODOs planned.
- Cost-of-swap paragraph написан (CHK016 — primary deliverable).

**Concerns** (все inherited, не вводятся F-5c):
1. `ownerUid: String` = Firebase UID directly (inherited from F-4/F-5b). Не F-5c violation, но propagates.
2. `PushTrigger` as event-level abstraction vs platform-integration concern — нужна clarifying note чтобы reviewer не классифицировал как over-engineering для FCM.

## Action items (priority order)

1. **Низкая** (одна правка в spec.md Notes): «Identity convention inherited from F-4/F-5b — `ownerUid: String` = Firebase UID directly. Exit ramp on own-server identity = F-4/F-5b territory, не F-5c scope» (CHK007, CHK008, CHK009).
2. **Низкая** (одна правка в spec.md): «`PushTrigger` — event-level domain abstraction (9 consumers). FCM transport fixed behind FcmTokenPublisher. Не universal push abstraction» (CHK014).

---

## Заметка для новичка (TL;DR)

Проверено: если завтра захотим заменить Google Firebase / Cloudflare на собственный сервер — насколько дорого будет, и что нужно переписать. **Не значит** что планируем замену. Значит: убедиться, что когда придёт время, будет недели работы, а не годы.

**Хорошо сделано** (11/16):
- Никакого Firebase-типа в общем коде. Все вендоры за «портами».
- Если Cloudflare прыгнет в цене — переписать ~15 файлов в одном модуле, ~5-7 дней работы. **Не** переписать пол-проекта.
- Если FCM отключат — 3 файла.
- Все «временные решения» (Cloudflare Worker, Workers KV, Spark plan) записаны в `server-roadmap.md` с маршрутом миграции.

**Сделана главная вещь — параграф «Cost of swap»** в этом checklist'е, который явно говорит: «вот что менять, вот что не менять, вот сколько времени». Это **главная ценность** этой проверки — заставить нас думать о цене замены **сейчас**, чтобы не было сюрпризов потом.

**Чего не хватает** (3 «частично»):
- Используем Firebase UID напрямую как идентификатор пользователя. Это унаследовано из старых спек (F-4/F-5b), не вводится здесь. Но при миграции на свой сервер придётся переходить на свой формат ID — это не наша забота сейчас, но запомнить надо.
- Нужна одна строка в spec.md уточняющая «`PushTrigger` это абстракция событий, не транспорта» — чтобы будущий reviewer не подумал что мы over-engineer'нули FCM.

**Не блокирует** /speckit.plan. 2 строки правок в spec.md.
