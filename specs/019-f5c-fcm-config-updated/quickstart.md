# Quickstart: F-5c Push-trigger Foundation

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-06-21

> Этот документ — onboarding для разработчика, который собирается имплементировать или модифицировать F-5c. После прочтения и выполнения шагов developer способен: запустить Worker локально, запустить unit + integration tests, сделать end-to-end smoke на 2 эмуляторах.

## Prerequisites

**Tooling**:
- Android Studio 2025.x (or newer) с KMP plugin.
- JDK 21+ (project baseline).
- Node.js 20+ + npm 10+ (для Worker dev).
- `wrangler` CLI 3.x (`npm install -g wrangler`).
- 2 Android emulators (managed через skill `android-emulator` per [memory `reference_emulators`](../../C:/Users/user/.claude/projects/c--work-launcher/memory/reference_emulators.md)).

**Accounts / credentials** (one-time setup):
- Cloudflare account (free tier) — required for `wrangler dev` local development + `wrangler deploy` для smoke.
- Firebase project (existing `launcher-old-dev` — per F-5b).
- FCM Server Key (Firebase Console → Project Settings → Cloud Messaging → Server Key) — stored via `wrangler secret put FCM_SERVER_KEY` (per Worker setup ниже).

## Repository state expected

This quickstart assumes:
- Branch `019-f5c-fcm-config-updated` checked out.
- `core/push/` module created (Phase 1 implementation).
- `workers/push/` + `workers/_shared/auth-jwt/` created.
- F-5b (`core/keys/`) merged в main (prereq).

## 1. Worker setup (one-time)

### 1.1 Auth-jwt module dependencies

```bash
cd workers/_shared/auth-jwt
npm install
```

Installs `jose` library + dev dependencies (`@cloudflare/workers-types`, `vitest`).

### 1.2 Push Worker dependencies

```bash
cd workers/push
npm install
```

Installs `@familycare/auth-jwt` (via `file:../_shared/auth-jwt` link) + dev dependencies.

### 1.3 Cloudflare Worker initialisation

**First-time Cloudflare account setup** (skip if already done):

```bash
wrangler login
```

Opens browser для OAuth — sign in to your Cloudflare account.

**Create KV namespaces** (one-time):

```bash
cd workers/push

# Production namespaces
wrangler kv:namespace create JWKS_CACHE
wrangler kv:namespace create IDEMPOTENCY_CACHE
wrangler kv:namespace create RATE_LIMIT

# Preview namespaces (для wrangler dev)
wrangler kv:namespace create JWKS_CACHE --preview
wrangler kv:namespace create IDEMPOTENCY_CACHE --preview
wrangler kv:namespace create RATE_LIMIT --preview
```

Copy generated namespace IDs into `wrangler.toml` `[[kv_namespaces]]` sections.

### 1.4 Configure secrets

```bash
cd workers/push

# Production secret
wrangler secret put FCM_SERVER_KEY
# (paste FCM server key from Firebase Console when prompted)

wrangler secret put FIREBASE_PROJECT_ID
# (paste Firebase project ID, e.g. "launcher-old-dev")
```

**For local dev** (`wrangler dev`): create `.dev.vars` (gitignored):

```
FCM_SERVER_KEY=test-fake-key-for-local-dev
FIREBASE_PROJECT_ID=launcher-old-dev
```

Local dev uses `FakeFcmDispatcher` (no real FCM calls), так что fake server key OK.

## 2. Run unit tests

### 2.1 Kotlin (KMP client)

```bash
# From repo root
./gradlew :core:push:test
```

Runs:
- `WireFormatRoundtripTest` — PushPayload + PushTriggerRequest serialize/deserialize.
- `PushHandlerRegistryTest` — dispatch logic.
- `EventTypeExtensibilityTest` — adding new EventType + handler без foundation changes (SC-008 validation).
- Other commonTest files.

**Expected**: all green, <30 seconds.

### 2.2 TypeScript (Worker + auth-jwt)

```bash
# Auth-jwt tests
cd workers/_shared/auth-jwt
npm test

# Push Worker tests
cd workers/push
npm test
```

Runs Vitest suites:
- `jwks-verifier.test.ts` — каждый VerificationError variant (expired, wrong-iss, wrong-aud, malformed-header, kid-not-found, invalid-signature).
- `registry.test.ts` — EventTypeRegistry entry structure validation.
- `integration.test.ts` — full Worker flow с fake adapters (FakeFcmDispatcher, FakeFirebaseAuth, InMemoryRecipientResolver).

**Expected**: all green, <10 seconds.

## 3. Run Worker locally (`wrangler dev`)

```bash
cd workers/push
wrangler dev
```

Starts local Worker на `http://localhost:8787`. Uses:
- Preview KV namespaces (in-memory если first run, persist между restarts).
- Local `.dev.vars` (fake FCM key).
- `FakeFcmDispatcher` instead of real FCM (configured via env var `USE_FAKE_FCM=true`).

**Smoke test the running Worker**:

```bash
# Generate a test Firebase ID-token (use test JWKS fixture)
node workers/_shared/auth-jwt/test/generate-test-token.js > /tmp/test-token

# POST a trigger request
curl -X POST http://localhost:8787/push \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat /tmp/test-token)" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "schemaVersion": 1,
    "eventType": "config-updated",
    "targetScope": "own-and-grants",
    "ownerUid": "TestUid1234567890123456789",
    "payload": {"configName": "main"}
  }'
```

**Expected**: `200 OK` with `{"status": "queued", "triggerId": "...", "recipientCount": N}`. Logs visible в `wrangler dev` output: «FakeFcmDispatcher captured N messages для recipients [...]».

## 4. Run client ↔ Worker contract test

Validates wire-format synchronization between Kotlin DTOs and TypeScript DTOs.

```bash
# Terminal 1: start local Worker
cd workers/push
wrangler dev --port 8787

# Terminal 2: run contract test
./gradlew :core:push:jvmTest --tests *ContractTest*
```

Test posts к `http://localhost:8787/push` от Kotlin client (using real `HttpPushTrigger` impl с test JWKS provider), asserts response shape matches expected.

**Expected**: green. Если fails — wire-format drift между Kotlin + TypeScript, fix `workers/push/src/contract/wire-format.ts` или `core/push/commonMain/internal/PushTriggerRequest.kt`.

## 5. Android E2E test (2 emulators)

### 5.1 Start emulators

Per skill `android-emulator`:

```bash
# (via skill workflow)
# Start two emulators:
#   - emulator-5554: pixel_5_api_34 (admin device)
#   - emulator-5556: pixel_5_api_34 (recipient device)
```

Reposition windows per `feedback_emulator_window_placement` memory.

### 5.2 Run E2E test

```bash
./gradlew :app:connectedDebugAndroidTest \
  --tests *ConfigUpdatedPushE2ETest*
```

Test:
1. Sign-in both emulators с different Google test accounts (or same account для multi-device sync test).
2. Trigger `ConfigSaver.saveOwn("main", ...)` на admin device.
3. Assert recipient device receives push within 5s median, 30s p95 (SC-001).
4. Assert recipient's DataStore reflects updated config.
5. Verify idempotency: send 3 duplicate triggers → assert recipient processes once (SC-006).

**Expected**: green. Latency metrics logged для perf-checkpoint update.

### 5.3 Known OEM gaps

E2E test passes на stock Android (Pixel emulator). Manual verification needed для:
- **Samsung One UI**: aggressive Adaptive Battery may delay FCM. `TODO(physical-device): verify push delivery on Samsung Galaxy after 3+ days idle`.
- **Xiaomi MIUI**: autostart manager. Reuse spec 007 mitigation (wizard deep-link). Already verified for F-5b on Xiaomi 11T.

## 6. Smoke test against deployed Worker

Before merge, validate against real CF Worker (not just `wrangler dev`).

### 6.1 Deploy Worker

```bash
cd workers/push
wrangler deploy
# Worker now live at https://launcher-push.<your-account>.workers.dev/push
```

Note returned URL — update `WORKER_BASE_URL` constant в `core/push/androidMain/.../HttpPushTrigger.kt` if different from current hardcoded value.

### 6.2 Re-run E2E with real Worker

Switch `BackendInit.kt` (realBackend flavor) к use deployed Worker URL вместо `wrangler dev` localhost.

Re-run `ConfigUpdatedPushE2ETest` — validates real FCM + real Worker latency.

## 7. Performance checkpoint

After implementation complete, generate `perf-checkpoint.md`:

```bash
./gradlew :app:macroBenchmark --tests *PushTrigger*
```

Measures:
- **SC-002**: `ConfigSaver.saveOwn(...)` p95 latency overhead vs F-5b baseline без push trigger. Target: ≤ 200ms.
- **SC-001**: End-to-end sync latency (device A save → device B refresh). Target: ≤ 5s median, 30s p95.
- **SC-007**: Worker CPU time per request. Run `wrk` или `k6` против deployed Worker. Target: < 10ms p99 excluding FCM API latency.
- **APK delta**: `:app:assembleRealBackendRelease` size vs main branch baseline. Target: ≤ 2MB delta.

Write findings to `specs/019-f5c-fcm-config-updated/perf-checkpoint.md`.

## Common pitfalls

### Wire-format drift

**Symptom**: `ContractTest` fails with «field missing» или «type mismatch».
**Cause**: `core/push/commonMain/internal/PushTriggerRequest.kt` (Kotlin) и `workers/push/src/contract/wire-format.ts` (TypeScript) modified asymmetrically.
**Fix**: ensure both files in same PR. CI check should fail PR if `MAX_SUPPORTED_SCHEMA_VERSION` constants mismatch.

### JWKS cache stale

**Symptom**: Worker returns 401 for all requests after Google JWKS rotation.
**Cause**: cached JWKS TTL hardcoded longer than Google's rotation period.
**Fix**: ensure auth-jwt module respects `Cache-Control: max-age=N` from Google's JWKS endpoint response. Per FR-003. Force-refresh на `kid` cache miss should kick in.
**Recovery**: `wrangler kv:key delete jwks --namespace-id=<JWKS_CACHE namespace>` to flush manually.

### Push not delivered после Sign-In

**Symptom**: Device A saves config, Device B never gets push (но online).
**Cause**: `FcmTokenPublisher` не invoked after Sign-In, FCM token not в Firestore directory.
**Fix**: ensure `LauncherApplication.onCreate` (or Sign-In observer) calls `FcmTokenPublisher.publish(token)` после AuthIdentity establishes Sign-In. Per FR-027.
**Verify**: Firebase Console → Firestore → `/users/{uid}/devices/{deviceId}/fcmToken` field present.

### Handler не invoked после push

**Symptom**: FCM push delivered (verified via Firebase Console «Send test message»), но handler logic не runs.
**Cause**: либо (a) `PushHandlerRegistry` does not have handler для eventType, либо (b) `LauncherFirebaseMessagingService` malformed payload parse fails.
**Fix**: check Logcat для warnings «Unknown eventType» или «Malformed payload». Verify DI registers handler для each EventType variant.

### `wrangler dev` slow first request

**Symptom**: First request after `wrangler dev` start takes 2-5 seconds.
**Cause**: Worker cold start + KV initialization. Expected behaviour.
**Fix**: ignore — subsequent requests fast. Production CF Workers have similar cold start (~200ms).

## Updating documentation

After Worker first deploy:
1. Update `WORKER_BASE_URL` constant в `core/push/androidMain/.../HttpPushTrigger.kt` если differs from current.
2. Update `docs/compliance/permissions-and-resource-budget.md` с F-5c entry (per security CHK018).
3. Update [perf-checkpoint.md](perf-checkpoint.md) с measured metrics.

## Useful commands cheat-sheet

| Task | Command |
|---|---|
| Build push module | `./gradlew :core:push:assemble` |
| Run all push tests | `./gradlew :core:push:test` |
| Run Worker locally | `cd workers/push && wrangler dev` |
| Deploy Worker (production) | `cd workers/push && wrangler deploy` |
| View Worker logs (production) | `cd workers/push && wrangler tail` |
| Flush JWKS cache | `wrangler kv:key delete jwks --namespace-id=<id>` |
| List KV keys | `wrangler kv:key list --namespace-id=<id>` |
| Generate test JWT | `node workers/_shared/auth-jwt/test/generate-test-token.js` |

---

## Краткое резюме (для не-разработчика)

Этот документ — **инструкция для разработчика как настроить и запустить F-5c**.

**Что нужно установить один раз**:
- Android Studio + 2 эмулятора.
- Node.js + npm + wrangler (CLI для Cloudflare Worker).
- Cloudflare account (бесплатный).
- Firebase project (уже есть — `launcher-old-dev`).

**Что делать каждый раз при работе над F-5c**:
1. `npm install` в двух модулях TypeScript.
2. `wrangler dev` — запустить Worker локально на `localhost:8787`.
3. `./gradlew :core:push:test` — прогнать unit tests.
4. На 2 эмуляторах — `connectedDebugAndroidTest` E2E тест.

**Перед merge**:
- Deploy Worker на CF (`wrangler deploy`).
- E2E прогнать с реальным Worker'ом.
- Измерить performance (cold-start, end-to-end latency).
- Обновить документацию permissions.

**Типичные грабли**:
- Wire-format рассинхрон между Kotlin и TypeScript — есть автоматический CI check.
- JWKS cache устарел — есть команда «очистить кэш вручную».
- FCM token не зарегистрирован — проверить запись в Firestore.

**Это not finished setup** — будет дополняться при implementation (например когда созданы readme файлы модулей).
