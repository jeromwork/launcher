# Implementation Plan: F-5c — Push-trigger Foundation + `config-updated` Event

**Branch**: `019-f5c-fcm-config-updated` | **Date**: 2026-06-21 | **Spec**: [spec.md](spec.md)

## Summary

F-5c строит **generic push-trigger infrastructure** — reusable foundation для 9+ known consumers (config-updated, future SOS/health/messenger/album/pairing/subscription/caregiver). Foundation = Cloudflare Worker `POST /push` endpoint (в `workers/push/`) + `core/push/` KMP module (port `PushTrigger`, sealed `EventType` с per-event `handlerTimeout`, `PushHandlerRegistry` для receiver dispatch, `BackgroundDispatcher` port) + `workers/_shared/auth-jwt/` standalone module для Firebase ID-token verification. Первый use case — `config-updated` event, который ConfigSaver (F-5b) триггерит после успешного save для оповещения других recipient devices. Migration scope: 8 existing файлов в `core/src/.../api/push/*` + `LauncherPushReceiver` rewritten под новый foundation.

## Technical Context

**Language/Version**: Kotlin 2.1+ (KMP), TypeScript 5.x (Worker).
**Primary Dependencies**:
- Client (KMP): Ktor HTTP client (commonMain), kotlinx.coroutines, kotlinx.serialization, WorkManager (androidMain), Firebase Messaging SDK (androidMain).
- Worker (TS): `jose` library (JWT verification), Cloudflare Workers runtime API (KV, fetch), Firestore REST API (via SA JWT).
- Auth-jwt module: `jose` only (no Firebase Admin SDK — incompatible с Workers runtime).
**Storage**:
- Client: Firestore `/users/{uid}/devices/{deviceId}/fcmToken` (extends F-5b directory entry, merge update).
- Worker: Workers KV для (a) JWKS cache, (b) idempotency dedupe (10-min TTL), (c) rate-limit counters (per-window TTL).
- No client-side persistence beyond F-5b.
**Testing**:
- JVM unit tests: `:core:push:test` (port contracts + wire-format roundtrip + receiver dispatch).
- Android instrumented: `:app:connectedDebugAndroidTest` (2-эмулятор multi-device sync E2E).
- Worker tests: Vitest (`cd workers/push && npm test`).
- Auth-jwt tests: Vitest (`cd workers/_shared/auth-jwt && npm test`).
- Contract tests: integration `core/push/jvmTest/` vs `wrangler dev` локальный Worker.
**Target Platform**:
- Android API 26+ (consistent with project baseline).
- Cloudflare Workers (V8 isolate runtime).
- Future: iOS via separate APNS adapter (port for BackgroundDispatcher already enabled).
**Project Type**: Mobile + Edge Worker (Android KMP client + TypeScript serverless Worker).
**Performance Goals**:
- `PushTrigger.trigger(...)` adds ≤ 200ms p95 к save latency (FR-031 fire-and-forget).
- Worker handles 100 concurrent `/push` calls within <10ms CPU per request (SC-007).
- End-to-end sync latency: ≤ 5s median, 30s p95 (SC-001 — both online).
- FCM token rotation reflected в Firestore directory ≤ 5s of next app foreground (SC-004).
**Constraints**:
- Cloudflare Worker free tier: 10ms CPU per request (cold start budget critical).
- Firebase Spark plan FCM quota: 10K push/day (≈500 admins × 20 push/day).
- Workers KV free tier: 100K reads/day.
- FCM data-message size: 4KB max payload.
- Android FirebaseMessagingService budget: ~10s (mitigated через WorkManager dispatch).
**Scale/Scope**:
- MVP: ~500 admins × ~10 push/day = 5K daily Worker calls.
- Foundation scope ≈ 25-30 файлов (15 client KMP + 8 Worker + 4 auth-jwt + tests).
- Migration scope: 8 existing файлов в `core/src/.../api/push/*` rewritten.
- Total effort: **8-10 дней**.

## Constitution Check

*Gate inlined per `procedure-constitution-check` review (run 2026-06-21).*

| Gate | Status | Notes |
|---|---|---|
| **G1. Architecture (Articles I-III)** | ✅ PASS | KMP module structure (`core/push/commonMain` / `androidMain`) per ADR-005. No platform leakage в commonMain. Worker artifact independent build (`workers/push/`). |
| **G2. Core/System Integration (Article IV)** | ✅ PASS | All integration через ports (PushTrigger, PushHandler, FcmTokenPublisher, BackgroundDispatcher). Vendor SDK behind adapters (FCM only в androidMain). Auth-jwt extracted (separate concern). |
| **G3. Configuration (Article VII)** | ✅ PASS | Wire formats (PushTriggerRequest, PushPayload) carry schemaVersion=1 from first commit (FR-024, FR-050). Backward-compat policy explicit (FR-051). EventTypeRegistry as static config (not Firestore-mutable — security). |
| **G4. Required Context Review (Article XII §7)** | ✅ PASS | All relevant artifacts linked (см. §Required Context Review ниже). |
| **G5. Accessibility (Article VIII)** | N/A | F-5c — pure infrastructure, no UI surface. Future SOS event type (S-4) — там UX accessibility concerns. |
| **G6. Battery/Performance (Article IX)** | ⚠️ NOTE | FCM background receipt — standard Firebase. WorkManager dispatch — respects doze. FRs explicit (FR-073 IO dispatcher, FR-074 WorkManager default, FR-077 per-event timeout). Perf checkpoint в quickstart.md. **APK delta** +1-2MB — R8 minification dependency (TODO-ARCH-006 уже в backlog). |
| **G7. Testing (Article X + CLAUDE.md rule 6)** | ✅ PASS | Every port has fake adapter (FakePushTrigger, FakeFcmTokenPublisher, FakePushHandler, FakeFcmDispatcher). Every wire format has roundtrip test (FR-050). Contract test client ↔ Worker via wrangler dev. Fitness functions: no commonMain Android imports, no launcher-specific deps в core/push (SC-009). |
| **G8. Simplicity (Article XI + CLAUDE.md rule 4)** | ⚠️ NOTE | Foundation abstraction оправдана rule 4 self-test (9 known consumers force rewrite). Documented в [checklists/meta-minimization.md](checklists/meta-minimization.md) — все Tests passed. Не вводим preemptive features (subscription gating = S-10, group management = S-2, photo handling = V-3). |

**Result**: 6 PASS, 2 NOTE (battery/performance + simplicity — both substantive but acceptable). **No FAIL.** Proceeding to implementation.

## Required Context Review

Per Article XII §7, this plan respects:

**Constitution & rules**:
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Articles V (Modularization), VII (Configuration), IX (Performance), XIV (Privacy).
- [CLAUDE.md](../../CLAUDE.md) — rules 1, 2 (domain isolation, ACL), 3 (one-way doors), 4 (MVA), 5 (wire-format), 6 (mock-first), 8 (server-roadmap), 10 (notification minimization).

**Architectural decisions** (relevant):
- [docs/product/decisions/2026-05-30-f4-identity/03-anonymous-removed.md](../../docs/product/decisions/2026-05-30-f4-identity/03-anonymous-removed.md) — named auth only, used via F-4 AuthIdentity.
- [docs/product/decisions/2026-06-15-deferred-cloud/](../../docs/product/decisions/2026-06-15-deferred-cloud/) — device self-sufficiency; F-5c CLOUD-only, NullPushTrigger в local mode.
- [docs/product/decisions/2026-06-15-deferred-cloud/03-server-validated-entitlement.md](../../docs/product/decisions/2026-06-15-deferred-cloud/03-server-validated-entitlement.md) — S-10 territory; F-5c respects 401 from Worker.

**Spec dependencies**:
- [specs/017-f4-auth-provider/spec.md](../017-f4-auth-provider/spec.md) — `AuthIdentity` port, Firebase ID-token acquisition.
- [specs/018-f5-config-e2e-encryption/spec.md](../018-f5-config-e2e-encryption/spec.md) — `ConfigSaver`, `EnvelopeBootstrap`, directory entry schema (extending с `fcmToken`).
- [specs/007-pairing-and-firebase-channel/spec.md](../007-pairing-and-firebase-channel/spec.md) — existing PushPayload + LauncherFirebaseMessagingService (rewritten в this spec migration scope).
- [specs/008-bidirectional-config-sync/spec.md](../008-bidirectional-config-sync/spec.md) — explicit save/push user model (FR-042, FR-047 «Отправить сейчас» button — F-5c assumption).

**Server roadmap (rule 8)**:
- [docs/dev/server-roadmap.md SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md#srv-push-foundation-generic-push-trigger-infrastructure-spec-019-f-5c-rescoped-2026-06-20) — full Worker migration plan.
- [docs/dev/server-roadmap.md SRV-PUSH-EXTRACTION](../../docs/dev/server-roadmap.md#srv-push-extraction-push-foundation--отдельный-repo-spec-019-f-5c-future) — extraction trigger.

**Project backlog**:
- [TODO-ARCH-001](../../docs/dev/project-backlog.md#todo-arch-001) — custom Worker domain (current `*.workers.dev` exit ramp).
- [TODO-ARCH-006](../../docs/dev/project-backlog.md#todo-arch-006) — R8 minification (APK delta concern).
- [TODO-ARCH-017](../../docs/dev/project-backlog.md#todo-arch-017) — push foundation extraction trigger.
- [TODO-ARCH-018](../../docs/dev/project-backlog.md#todo-arch-018) — auth-jwt extraction trigger.

## Architecture

### Module map

```
core/
├── push/                            ← NEW (KMP module)
│   ├── build.gradle.kts             ← TODO(extraction TODO-ARCH-017): publishable artifact
│   └── src/
│       ├── commonMain/kotlin/com/familycare/push/
│       │   ├── api/                 ← PUBLIC port surface
│       │   │   ├── PushTrigger.kt
│       │   │   ├── PushHandler.kt
│       │   │   ├── PushHandlerRegistry.kt
│       │   │   ├── EventType.kt
│       │   │   ├── TargetScope.kt
│       │   │   ├── PushPayload.kt
│       │   │   ├── PushTriggerError.kt
│       │   │   ├── BackgroundDispatcher.kt
│       │   │   ├── RetryStrategy.kt
│       │   │   ├── FcmTokenPublisher.kt
│       │   │   └── WireFormatVersion.kt        ← MAX_SUPPORTED_SCHEMA_VERSION const
│       │   ├── impl/
│       │   │   ├── DefaultPushTrigger.kt       ← uses Ktor (commonMain)
│       │   │   ├── DefaultPushHandlerRegistry.kt
│       │   │   ├── IdempotencyKeyGenerator.kt  ← UuidGenerator injection point
│       │   │   └── NullPushTrigger.kt          ← local-mode no-op fallback
│       │   └── internal/
│       │       ├── PushTriggerRequest.kt       ← wire DTO
│       │       └── PushTriggerWireFormat.kt    ← Kotlin serialization
│       ├── commonTest/kotlin/
│       │   ├── fakes/
│       │   │   ├── FakePushTrigger.kt
│       │   │   ├── FakeFcmTokenPublisher.kt
│       │   │   ├── FakePushHandler.kt
│       │   │   └── FakeBackgroundDispatcher.kt
│       │   ├── WireFormatRoundtripTest.kt
│       │   ├── PushHandlerRegistryTest.kt
│       │   └── resources/
│       │       └── push-payload-v1.json        ← roundtrip fixture
│       └── androidMain/kotlin/com/familycare/push/android/
│           ├── HttpPushTrigger.kt              ← real impl (uses Ktor + F-4 AuthIdentity)
│           ├── FcmTokenPublisherImpl.kt        ← Firebase Messaging SDK
│           ├── WorkManagerBackgroundDispatcher.kt ← WorkManager-backed dispatch
│           └── LauncherFirebaseMessagingService.kt ← replaces existing (migration)
│
└── src/                             ← MIGRATION (8 files rewritten)
    ├── commonMain/kotlin/com/launcher/api/push/
    │   ├── PushPayload.kt           ← REWRITE — new shape, linkId nullable
    │   ├── PushPayloadWireFormat.kt ← REWRITE — encode/decode new fields
    │   ├── PushReceiver.kt          ← REPLACE — становится PushHandlerRegistry
    │   ├── PushType.kt              ← KEEP for backwards-compat (CommandIssued, Revoke)
    │   ├── FcmReceiverContract.kt   ← UPDATE — signature changes
    │   └── ...
    ├── commonTest/kotlin/com/launcher/api/push/
    │   └── PushPayloadWireFormatTest.kt  ← UPDATE — new fields
    ├── commonMain/kotlin/com/launcher/fake/push/
    │   └── FakePushReceiver.kt      ← UPDATE — match new interface
    └── androidRealBackend/kotlin/com/launcher/adapters/push/
        └── LauncherPushReceiver.kt  ← REWRITE — становится PushHandlerRegistry-based

workers/
├── push/                            ← NEW (renamed from family-push)
│   ├── package.json                 ← depends on "../_shared/auth-jwt"
│   ├── wrangler.toml                ← KV namespace bindings
│   ├── src/
│   │   ├── index.ts                 ← POST /push entrypoint
│   │   ├── registry/
│   │   │   └── event-types.ts       ← EventTypeRegistry (static const)
│   │   ├── auth/
│   │   │   └── event-authorisation.ts  ← per-event-type rules
│   │   ├── recipient/
│   │   │   └── resolver.ts          ← Firestore directory + grants read
│   │   ├── dispatch/
│   │   │   └── fcm-dispatcher.ts    ← FCM HTTP v1 API client + retry
│   │   ├── dedupe/
│   │   │   └── idempotency.ts       ← KV-based dedupe
│   │   ├── ratelimit/
│   │   │   └── rate-limiter.ts      ← KV-based per-UID per-event
│   │   └── contract/
│   │       └── wire-format.ts       ← MUST match Kotlin DTOs (manual sync)
│   ├── test/
│   │   ├── fixtures/
│   │   │   ├── grant-firestore-snapshot.json
│   │   │   └── valid-trigger-request.json
│   │   └── integration.test.ts
│   └── README.md                    ← Cloudflare setup + dev workflow
│
└── _shared/                         ← NEW
    └── auth-jwt/                    ← TypeScript module, importable by Workers
        ├── package.json             ← "name": "@familycare/auth-jwt", "private": true
        ├── tsconfig.json
        ├── src/
        │   ├── index.ts             ← public API: verifyFirebaseIdToken()
        │   ├── jwks-cache.ts        ← KV-based, dynamic TTL (Cache-Control)
        │   ├── claims.ts            ← iss/aud/exp/iat/sub/alg/kid validation
        │   └── types.ts             ← Claims, VerificationResult, VerificationError
        ├── test/
        │   ├── fixtures/
        │   │   ├── jwks-test.json
        │   │   ├── valid-token.json
        │   │   ├── expired-token.json
        │   │   ├── wrong-iss-token.json
        │   │   └── wrong-aud-token.json
        │   └── jwks-verifier.test.ts
        └── README.md
```

### Data flow — config save → recipient refresh

```
[Admin device A]
   ConfigSaver.saveOwn("main", bytes)
   │
   ├──► RemoteStorage.put(uid, "config/main", encryptedBytes)
   │    └──► Firestore write
   │
   └──► PushTrigger.trigger(
          eventType = EventType.ConfigUpdated,
          targetScope = TargetScope.OwnAndGrants,
          ownerUid = currentUid,
          payload = mapOf("configName" to "main"),
        )  ◄── fire-and-forget detached coroutine
        │
        └──► HttpPushTrigger (Android adapter)
             │  generates UUID v4 idempotency-key
             │  acquires Firebase ID-token via F-4 AuthIdentity
             │
             └──► POST https://launcher-push.workers.dev/push
                  Authorization: Bearer <Firebase ID-token>
                  Idempotency-Key: <UUID>
                  Body: {schemaVersion: 1, eventType: "config-updated", targetScope: "own-and-grants", ownerUid, payload: {configName: "main"}}

[Cloudflare Worker — workers/push/]
   index.ts
   │
   ├──► auth-jwt module: verifyFirebaseIdToken(token, projectId, env.KV)
   │    └──► returns Claims или VerificationError → 401
   │
   ├──► event-types.ts: lookup "config-updated" в EventTypeRegistry
   │    └──► returns rule {authorise, rateLimit, collapseKey, priority}
   │
   ├──► event-authorisation.ts: authorise(caller, ownerUid)
   │    └──► owner ∨ grant-holder → 403 если нет
   │
   ├──► idempotency.ts: check KV by Idempotency-Key
   │    └──► if hit: return cached response
   │
   ├──► rate-limiter.ts: check rate-limit per (uid, eventType)
   │    └──► 429 если exceeded (60/min для config-updated)
   │
   ├──► recipient/resolver.ts: resolve recipients
   │    └──► fresh Firestore read: own devices + grant-holder devices → list of FCM tokens
   │
   ├──► fcm-dispatcher.ts: dispatch FCM data-message
   │    │  payload: {schemaVersion: 1, eventType: "config-updated", ownerUid, configName: "main", triggerId: <UUID>}
   │    │  collapse_key: "config-updated:{ownerUid}:main"
   │    │  retry 3× (500ms, 2s, 8s) on 429/5xx
   │    └──► FCM HTTP v1 API
   │
   └──► return 200 OK или 503

[Recipient device B — Admin's tablet]
   LauncherFirebaseMessagingService.onMessageReceived(RemoteMessage)
   │
   ├──► parse data Map<String,String> → PushPayload
   │    └──► malformed → silent log + ignore (FR-075)
   │
   ├──► PushHandlerRegistry.handlerFor("config-updated")
   │    └──► returns ConfigUpdatedHandler instance (или null → ignore)
   │
   └──► BackgroundDispatcher.dispatch(
          taskName = "push-config-updated-{triggerId}",
          timeout = EventType.ConfigUpdated.handlerTimeout,  // 30s default
        ) {
          ConfigUpdatedHandler.handle(payload)
          │
          └──► ConfigSaver.loadOwn("main")  [or loadForOther if ownerUid != currentUid]
               │
               ├──► RemoteStorage.get(ownerUid, "config/main")
               │    └──► Firestore read + envelope decrypt (F-5b)
               │
               └──► DataStore write + UI refresh (consumer responsibility)
        }
        │
        └──► WorkManagerBackgroundDispatcher (Android adapter)
             enqueues WorkManager job with timeout
             на iOS future: BGTaskBackgroundDispatcher
```

## Data model

См. [data-model.md](data-model.md) — описание `PushPayload`, `PushTriggerRequest`, `EventType` sealed, `TargetScope` enum, `PushTriggerError` sealed, `RecipientDeviceEntry` extension, `EventTypeRegistryEntry` (Worker side).

## Wire formats

3 contract files в [contracts/](contracts/):
- [push-trigger-request-v1.md](contracts/push-trigger-request-v1.md) — HTTP body для `POST /push`.
- [push-payload-v1.md](contracts/push-payload-v1.md) — FCM data-message payload (receiver side).
- [event-type-registry.md](contracts/event-type-registry.md) — реестр event types + per-event rules (Worker static const).

## Dependency impact

**New runtime dependencies (KMP client)**:
- **Ktor HTTP client** (`io.ktor:ktor-client-core` + engine) — for HTTP POST к Worker. Estimated +500KB compressed. Already declined в base, no other consumer in core/. **Justification**: KMP-compatible HTTP client, future iOS port reuses тот же DefaultPushTrigger без re-write. Alternative (OkHttp) — Android-only, would couple HTTP client choice к androidMain. Per Article XIII §3.
- **kotlinx.serialization** — already in project (F-5b uses), no new cost.
- **kotlinx.coroutines** — already in project, no new cost.
- **WorkManager** (`androidx.work:work-runtime-ktx`) — for WorkManagerBackgroundDispatcher. Estimated +300KB. Already used elsewhere в project (F-5b SRV-PUSH-FOUNDATION mentions WorkManager async queue). No new dep, just new consumer.
- **Firebase Messaging KTX** (`com.google.firebase:firebase-messaging-ktx`) — already в project (F-5b/spec 007). No new dep.

**New Worker dependencies**:
- `jose` (npm) — JWT verification в auth-jwt module. ~30KB bundle.
- `@cloudflare/workers-types` — dev dependency for TypeScript autocomplete.
- No Firebase Admin SDK (deliberately rejected — incompatible с Workers runtime).

**APK delta**: ~1-2MB (Ktor + small new code). **Concern**: release APK уже at ~13MB после F-5b (over budget 12MB per ADR-005). **Mitigation**: R8 minification ([TODO-ARCH-006](../../docs/dev/project-backlog.md#todo-arch-006)) — already known blocker pre-production. F-5c push сделает R8 even more critical.

## Test strategy

Per CLAUDE.md rules 6 + 7:

**Contract tests** (highest priority):
- `WireFormatRoundtripTest.kt` (commonTest): write PushPayload → encode → decode → assertEquals.
- `WireFormatBackwardCompatTest.kt` (commonTest): read v1 fixture from JSON file, assert deserialization успешна.
- Worker `integration.test.ts`: receive valid request, dispatch к FakeFcmDispatcher, assert payload shape.
- Auth-jwt `jwks-verifier.test.ts`: each VerificationError variant covered (expired, wrong-iss, wrong-aud, malformed, kid-not-in-jwks).

**Integration tests** (cross-component):
- 2-эмулятор Android E2E: `ConfigUpdatedPushE2ETest.kt` — device A saves, assert device B receives push + ConfigSaver.loadOwn invoked within 5s median.
- Client ↔ Worker contract test: `core/push/jvmTest/` POSTs к `wrangler dev` локальному Worker'у с fake FCM dispatcher, assert full flow.

**Fake adapter tests** (every port has fake):
- `FakePushTrigger` — captures trigger calls для consumer tests.
- `FakeFcmTokenPublisher` — captures publish calls.
- `FakePushHandler` / `FakePushHandlerRegistry` — для receiver-side dispatch tests.
- `FakeBackgroundDispatcher` — synchronously executes block без real WorkManager (для unit tests).
- `FakeFcmDispatcher` (Worker side) — captures FCM messages.
- `FakeFirebaseAuth` — issues test ID-tokens signed by test JWKS fixture.
- `InMemoryRecipientResolver` — replaces Firestore reads.

**Fitness functions** (CLAUDE.md rule 7):
- `core/push/api/` MUST NOT import android.* — verified by Gradle module config (commonMain compileClasspath не имеет android deps).
- `core/push/` MUST NOT depend on `core/launcher/*`, `feature/*` — verified by lint rule (Konsist or manual gradle dependency check).
- `core/push/api/` MUST NOT expose Android-specific types — Konsist test «public API surface contains no Android imports».
- Worker `workers/push/src/` MUST import auth-jwt via package boundary, not relative path beyond `../_shared` — eslint rule.
- Wire-format synchronization check: CI script сравнивает `MAX_SUPPORTED_SCHEMA_VERSION` константы в Kotlin + TypeScript, fails if mismatch.

**Per-event-type contract test** (extensibility validation, SC-008):
- `EventTypeExtensibilityTest.kt`: добавляем dummy `EventType.TestPing` + dummy handler → trigger через FakePushTrigger → assert dispatch flow works БЕЗ изменений в foundation Worker / core/push/api/.

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **R1. Worker JWT validation incorrectly accepts invalid tokens** | Low | High (security breach) | Auth-jwt module thorough test suite (each VerificationError variant), `jose` library production-grade, full claims set validated (FR-002). |
| **R2. JWKS cache TTL mismatch — all auth fails after Google rotation** | Low | High (outage) | Dynamic TTL from Cache-Control (FR-003), force-refresh on kid cache miss. Tested explicitly. |
| **R3. APK delta pushes release over budget** | High | Medium (Play Store rejection risk) | TODO-ARCH-006 R8 minification — pre-existing блокер, F-5c делает его obligatory ASAP. Measured в perf-checkpoint.md. |
| **R4. FCM quota exhausted (10K/day)** | Low at MVP scale | Medium (sync silent fail) | Per-event-type Worker rate-limit (FR-006), client debounce N/A (push = explicit user action per spec 008). Beyond MVP scale — Blaze upgrade exit ramp documented в SRV-PUSH-FOUNDATION. |
| **R5. Cloudflare Worker free-tier CPU exceeded (10ms)** | Medium | Medium (degraded UX) | Lean implementation (manual JWT via jose, не firebase-admin), JWKS cached в KV (no synchronous Google call per request). Measured в perf-checkpoint via wrangler dev profiling. Exit ramp: Cloudflare paid tier ($5/month) или own backend. |
| **R6. WorkManagerBackgroundDispatcher delays push handler by minutes** | Medium | Low (consistency lag) | WorkManager constraints (network available) + immediate execution priority. Acceptable per UX — pull-on-app-open safety net (FR-038). |
| **R7. PushPayload breaking change breaks dev devices (linkId removed-required)** | Low (no prod yet) | Low (dev only) | Spec 008 rewrite в parallel branch — coordinated migration. Pre-merge: grep `linkId` audit done (8 files), all migration scope. Inline TODO для schemaVersion 2 final removal. |
| **R8. Xiaomi MIUI blocks WorkManager job after FCM wake** | Medium (per OEM matrix) | Medium (push не triggers handler) | Reuse spec 007's autostart deep-link mitigation. Verified for spec 018 on Xiaomi 11T — same mechanism. F-5c does not introduce new OEM-specific code. |
| **R9. FCM token rotation race — Worker pushes to stale token** | High frequency | Low (per-message) | Worker drops stale tokens (FR-012). Receiver re-publishes via FcmTokenPublisher.onNewToken. Eventually consistent. |
| **R10. Wire-format Kotlin ↔ TypeScript drift** | Medium | High (silent prod failure) | Manual sync per CI script + contract test (`core/push/jvmTest/` vs `wrangler dev`). Когда event types > 5 — consider codegen (currently premature). |

## Rollout / verification

**Phase 1: Foundation implementation** (~5-6 дней):
1. Создать `core/push/` KMP module (build.gradle.kts, source sets, fakes).
2. Создать `workers/_shared/auth-jwt/` module (package.json, jose-based verifier, tests).
3. Создать `workers/push/` Worker (package.json, entrypoint, registry, recipient resolver, FCM dispatcher, KV bindings).
4. Implement ports + impls + fakes.
5. Wire format contract tests.

**Phase 2: Migration существующего push subsystem** (~1.5 дня):
1. Rewrite `core/src/.../api/push/PushPayload.kt` (new shape).
2. Rewrite `core/src/.../api/push/PushPayloadWireFormat.kt`.
3. Replace `core/src/.../api/push/PushReceiver.kt` → bridge к новому `PushHandlerRegistry`.
4. Rewrite `core/src/androidRealBackend/.../LauncherPushReceiver.kt` → handler-based.
5. Update tests (FakePushReceiver, PushPayloadWireFormatTest, PairingEndToEndTest).

**Phase 3: Integration + first consumer** (~1.5 дня):
1. ConfigSaver invokes pushTrigger.trigger(...) после save.
2. ConfigUpdatedHandler implementation + DI registration.
3. EventTypeRegistry entry `config-updated` в Worker.
4. 2-эмулятор E2E test.

**Phase 4: Verification** (~1 день):
1. Perf checkpoint — measure SC-001, SC-002, SC-007 + APK delta.
2. Fitness functions verification.
3. Manual smoke test против deployed Worker (wrangler deploy + Android real device).
4. Update [docs/compliance/permissions-and-resource-budget.md](../../docs/compliance/permissions-and-resource-budget.md) с F-5c entry.

**Done criteria**:
- Все FRs implemented + tests green.
- All SC measured + meet targets.
- Constitution Check re-run, still PASS.
- No new R8 minification regressions.
- Push delivery verified на 2 эмуляторах + Xiaomi 11T (existing hardware per spec 018).

## Project Structure

См. § Architecture / Module map выше для concrete tree.

**Structure Decision**: Single project monorepo с двумя build systems:
- Gradle (Android KMP) для `core/push/` + migration files в `core/`.
- npm/wrangler (TypeScript) для `workers/push/` + `workers/_shared/auth-jwt/`.

Соответствует existing convention (F-5b similar split: Kotlin core + TypeScript Firestore rules tests).

## Complexity Tracking

> Constitution Check вернул 2 NOTE (G6 battery, G8 simplicity). Оба acceptable, документированы. No FAIL violations.

| Concern | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| `BackgroundDispatcher` port (new abstraction) | 9 known consumers с разными timeout needs (config 30s, photo 5min, SOS 10s). Inline WorkManager в каждом handler = duplication + tight coupling к Android. Port позволяет iOS future port. | Direct WorkManager call в `LauncherFirebaseMessagingService` — KMP-incompatible, repeats per handler. |
| Separate `auth-jwt` module | JWT verification — auth concern, не push concern. Future Workers (V-3 album, etc.) reuse. Migration к own backend — auth-jwt portируется отдельно от push transport. | Inline JWT verification в push Worker — couples auth к push, blocks future reuse. |
| WorkManager-by-default (не fallback) | Foundation для 9 consumers с varied payload sizes (config 50KB → photo 5MB). Inline coroutine + 10s budget работает для config, ломается для photo. Единая модель проще чем two-path. | Inline coroutine с 10s timeout + WorkManager fallback — two code paths, complexity for marginal config-only gain. |
| Migration existing push subsystem (8 files) | Existing PushPayload uses `linkId` field from anonymous-pair model — incompatible с F-5c shape. Cannot deprecate without rewrite. | Keep old PushPayload + add new PushPayloadV2 — two wire formats, indefinite migration, increased complexity per CLAUDE.md rule 4. |

---

## Краткое резюме (для не-разработчика)

**Что строим**:
- Generic push-infrastructure для семейного приложения. Сейчас один use case (оповестить про обновление config'а), но foundation спроектирован для 9 будущих use case'ов (SOS, фото в альбом, входящие сообщения, и т.д.).
- Два независимых компонента: Android-клиент (KMP modules) + Cloudflare Worker (TypeScript serverless function).
- Между ними — единый HTTP контракт JSON, версионируемый с первого commit'а.

**Главные архитектурные решения**:
1. **WorkManager — везде по умолчанию**. Не fallback, а основной механизм. Чтобы будущее скачивание фото (5MB) не требовало переписывания foundation.
2. **JWT-валидация — отдельный модуль** (`workers/_shared/auth-jwt/`). Не push concern. При переезде на свой сервер этот модуль портируется отдельно, push transport — отдельно.
3. **Per-event-type timeout** объявляется в EventType (config = 30s, фото = 5min). Foundation handles lifecycle, consumer пишет только handler логику.

**Объём работы**: 8-10 дней по 4 фазам (foundation → migration → integration → verification).

**Главный риск**: APK становится на 1-2MB больше — release-сборка уже над лимитом, нужно срочно включить R8 minification (это уже стоит в backlog'е как TODO-ARCH-006). Без R8 — Play Store gate fails.

**Constitution Check**: 6 PASS, 2 NOTE (battery + simplicity — оба обоснованы и acceptable), 0 FAIL.

**Что дальше**: `/speckit.tasks` — генерация tasks.md с пошаговым execution plan (~30-50 atomic tasks).
