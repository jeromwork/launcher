# Contract: Worker API v1 (Recovery Key Backup)

**Name**: `worker-api-v1`
**Version**: 1 (initial)
**Status**: Draft
**Created**: 2026-06-28
**Spec**: [task-6 spec.md FR-010](../spec.md) — F-5 Root Key Hierarchy / Owner Recovery
**Worker module**: `workers/backup/` (Cloudflare Worker, free `*.workers.dev` tier — per [server-roadmap.md](../../../docs/dev/server-roadmap.md))
**JWT verification**: reuses [`workers/_shared/auth-jwt/`](../../../workers/_shared/auth-jwt/README.md)

This contract defines the HTTP API between the Android client (`core/recovery/`)
and the Cloudflare Worker that stores [`RecoveryKeyBackupBlob`](./recovery-key-backup-v1.md)
on R2. The API is **single-user**: each authenticated subject can only address
its own opaque `stableId` — no listing, no enumeration, no cross-user surfaces.

## 1. Endpoint overview

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/backup` | Upload `RecoveryKeyBackupBlob` (first-time setup OR Fallback re-setup after key rotation). |
| `GET` | `/backup/{stableId}` | Fetch blob (cross-device recovery flow, or check-existence probe). |
| `DELETE` | `/backup/{stableId}` | Delete blob (Fallback wipe — destructive flow, used when owner deliberately drops recoverability). |

URL base: `<account>.workers.dev/backup` (MVP). Client reads from
`BuildConfig.RECOVERY_BACKUP_WORKER_URL` (exit ramp for custom domain — see §10).

## 2. Authentication

Per [FR-010 Q-M variant (i)](../spec.md):

- **Header**: `Authorization: Bearer <Firebase ID-token>`.
- **Custom claim**: JWT MUST carry `stableId: String (UUID v4)`, set by Firebase
  Admin SDK at first sign-in (one-time write — Firebase UID ↔ stableId binding
  is established once and persists).
- **Signature verification**: Worker calls
  `verifyFirebaseIdToken(token, env.FIREBASE_PROJECT_ID, env.JWKS_CACHE)` from
  `@familycare/auth-jwt`. JWKS cached per its TTL policy (clamped 60s..86400s,
  daily refresh in steady state).
- **Subject-ownership check**:
  - `POST`: `claims.stableId` MUST equal `body.stableId`.
  - `GET`:  `claims.stableId` MUST equal path `{stableId}`.
  - `DELETE`: `claims.stableId` MUST equal path `{stableId}`.
- **Mismatch** → HTTP 403 with `{"error": "STABLE_ID_MISMATCH", ...}`.
- **Invalid signature / expired / wrong-issuer / wrong-audience** → HTTP 401
  with `{"error": "INVALID_TOKEN", ...}` (cause from `verify.error`).

Rationale: `stableId` (opaque UUID) — not Firebase UID — is the addressable
identifier. This decouples the recovery surface from identity-provider PII per
constitution Article XIV §7 (path contains zero identity-provider data).

## 3. Idempotency (POST only)

- **Header** `Idempotency-Key: <UUID v4>` — **REQUIRED** on every `POST /backup`.
- **Dedup window**: in-memory Map per Worker instance, key = `stableId + idempotencyKey`,
  TTL 24h (eviction on instance restart — acceptable for MVP since clients retry with
  fresh keys after multi-hour gaps).
- **Re-POST same key + identical body** → 200 OK with the cached response (no
  R2 write, no new `createdAt`).
- **Re-POST same key + DIFFERENT body** → 409 Conflict, `IDEMPOTENCY_CONFLICT`.
- **Missing header** → 400 `MISSING_IDEMPOTENCY_KEY`.
- `GET` and `DELETE` are naturally idempotent — no header required.

> **TODO(server-roadmap)**: persistent idempotency store (KV or own-server DB)
> deferred per [SRV-RECOVERY-001 (d)](../../../docs/dev/server-roadmap.md).
> In-memory limitation acceptable while Worker is the only consumer.

## 4. Endpoints

### 4.1 `POST /backup`

**Headers**

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <Firebase ID-token>` | yes |
| `Idempotency-Key` | UUID v4 | yes |
| `Content-Type` | `application/json; charset=utf-8` | yes |

**Body**: `RecoveryKeyBackupBlob` JSON — see
[`recovery-key-backup-v1.md`](./recovery-key-backup-v1.md). Worker parses
`schemaVersion` first; rejects unsupported versions before any other field.

**Responses**

| HTTP | Body | When |
|---|---|---|
| 200 | `{"status": "stored", "createdAt": "<iso8601>"}` | Blob written to R2 (or cached idempotency hit). |
| 400 | `{"error": "MALFORMED_BODY", ...}` | JSON parse failure / required field missing / `body.stableId` not UUID. |
| 400 | `{"error": "UNSUPPORTED_SCHEMA", "max": 1}` | `schemaVersion > 1`. |
| 400 | `{"error": "MISSING_IDEMPOTENCY_KEY", ...}` | Header absent or empty. |
| 401 | `{"error": "INVALID_TOKEN", "reason": "<verify.error>"}` | JWT signature / expiry / issuer / audience. |
| 403 | `{"error": "STABLE_ID_MISMATCH", ...}` | `claims.stableId ≠ body.stableId`. |
| 409 | `{"error": "IDEMPOTENCY_CONFLICT", ...}` | Same `Idempotency-Key`, different body. |
| 429 | `{"error": "RATE_LIMITED", "retryAfterSeconds": N}` | Per-`stableId` POST counter exceeded (see §5). `Retry-After` header set. |
| 507 | `{"error": "R2_QUOTA_EXCEEDED", ...}` | R2 free-tier write-quota hit. Client surfaces to owner; retry after 24h. |

### 4.2 `GET /backup/{stableId}`

**Headers**

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <Firebase ID-token>` | yes |

**Responses**

| HTTP | Body | When |
|---|---|---|
| 200 | `RecoveryKeyBackupBlob` JSON (full body) | Blob found and JWT subject matches. |
| 401 | `{"error": "INVALID_TOKEN", ...}` | JWT problem. |
| 403 | `{"error": "STABLE_ID_MISMATCH", ...}` | `claims.stableId ≠ path stableId`. |
| 404 | `{"error": "NOT_FOUND", ...}` | No blob for this `stableId` (never set up, or previously wiped). **Legitimate** outcome — client distinguishes "no recovery configured" from "wrong identity". |
| 429 | `{"error": "RATE_LIMITED", "retryAfterSeconds": N}` | Per-`stableId` GET counter exceeded (tighter than POST, see §5). |

### 4.3 `DELETE /backup/{stableId}`

**Headers**

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <Firebase ID-token>` | yes |

**Responses**

| HTTP | Body | When |
|---|---|---|
| 204 | (empty) | Blob deleted, OR blob did not exist (idempotent semantics — owner sees the same outcome either way). |
| 401 | `{"error": "INVALID_TOKEN", ...}` | JWT problem. |
| 403 | `{"error": "STABLE_ID_MISMATCH", ...}` | `claims.stableId ≠ path stableId`. |
| 429 | `{"error": "RATE_LIMITED", "retryAfterSeconds": N}` | Per-`stableId` DELETE counter exceeded. |

## 5. Rate-limit policy

Per [spec.md Q-I](../spec.md) (partial resolution — full persistent counter
deferred to own-server cutover).

| Endpoint | Limit | Window | Rationale |
|---|---|---|---|
| `POST /backup` | 10 attempts | 5 min | Allows exponential-backoff retry on transient R2 errors. |
| `GET /backup/{stableId}` | 5 attempts | 5 min | Defends against brute-force on a stolen JWT (recovery blob is the cryptographic crown jewel). |
| `DELETE /backup/{stableId}` | 5 attempts | 5 min | Same as GET — destructive surface should not be hammered. |

- Counter scope: **per-`stableId`** (NOT per-IP — multi-NAT users would share
  a counter; identity-bound counter is more accurate for the threat model).
- Implementation: in-memory `Map<stableId, RingBuffer<timestamp>>` per Worker
  instance. Resets on instance restart (Cloudflare cycles instances every few
  hours — accepted MVP limitation).
- On 429: `Retry-After: <seconds>` HTTP header set to the time until the
  oldest counted attempt falls out of the window.

> **TODO(server-roadmap)**: persistent per-`stableId` rate-limit (Worker KV or
> own-server DB) deferred per [SRV-RECOVERY-001 (d)](../../../docs/dev/server-roadmap.md).
> Current implementation is intentionally weak — known and accepted.

## 6. Error response format (uniform)

All non-2xx responses (except 204 DELETE success) return JSON:

```json
{
  "error": "<ERROR_CODE>",
  "message": "<human-readable hint, English, no PII>"
}
```

Some errors carry extra fields (`max` for `UNSUPPORTED_SCHEMA`,
`retryAfterSeconds` for `RATE_LIMITED`, `reason` for `INVALID_TOKEN`).

**Error code enum** (closed set):

| Code | HTTP | Meaning |
|---|---|---|
| `MALFORMED_BODY` | 400 | JSON parse failed, or required field missing/wrong type. |
| `UNSUPPORTED_SCHEMA` | 400 | `schemaVersion` higher than Worker supports. |
| `MISSING_IDEMPOTENCY_KEY` | 400 | `Idempotency-Key` header absent or empty on POST. |
| `INVALID_TOKEN` | 401 | JWT signature / expiry / issuer / audience check failed. |
| `STABLE_ID_MISMATCH` | 403 | `claims.stableId` does not equal target stableId. |
| `NOT_FOUND` | 404 | No blob exists for this `stableId` (GET only). |
| `IDEMPOTENCY_CONFLICT` | 409 | Same idempotency key, different body. |
| `RATE_LIMITED` | 429 | Per-`stableId` per-endpoint counter exceeded. |
| `R2_QUOTA_EXCEEDED` | 507 | R2 free-tier write quota hit. |

**8 codes total.** Client treats any unknown code as a generic transient
failure (forward-compatible) and surfaces the HTTP status to the owner.

## 7. Privacy / data minimization

Per constitution.md Article XIV §7:

- **Path**: contains only opaque `stableId` UUID — no Firebase UID, no email,
  no phone, no device-identifying string. Cloudflare access logs (which see
  `{method, path, status, source-IP, timestamp}`) therefore record only an
  opaque ID, not user-identifying information.
- **No correlation endpoints**: there is **no** `/list-users`, `/find-by-email`,
  `/list-blobs`, `/groups`, `/share`. The API is single-user-only. Two users
  with different `stableId` values are cryptographically and operationally
  indistinguishable to any observer who lacks the JWT.
- **No body logging**: Worker code MUST NOT log JWT tokens, blob bytes, header
  values (other than the standard method/path/status), or any request body. A
  lint-rule / contract test in `workers/backup/src/__tests__/no-logging.test.ts`
  enforces this (greps source for `console.log`/`console.info` patterns calling
  on `request.body`, `body.*`, `token`, `Authorization`).
- **R2 isolation**: bucket `recovery-blobs` is dedicated. No other Worker
  (`workers/push/`, future album/messenger Workers) shares this bucket.
  Cross-feature correlation via shared storage is impossible.
- **Body content**: opaque ciphertext blob (encrypted by AES-256-GCM with a key
  derived from the recovery code — Worker holds zero cleartext key material).
  Even total R2 compromise yields ciphertext only.

## 8. Versioning

- **Current**: v1 (this contract).
- **URL prefix**: none. Worker base URL identifies version implicitly
  (`<account>.workers.dev/backup` = v1).
- **Breaking change**: deploy a parallel `workers/backup-v2/` at
  `<account>.workers.dev/backup-v2`. Client toggle via
  `BuildConfig.RECOVERY_BACKUP_WORKER_URL`. v1 Worker continues serving v1
  clients for at least one major release (per CLAUDE.md rule 5).
- **Additive changes** (new optional response fields, new error codes the
  client treats as unknown): allowed within v1.
- **Wire-format**: see [`recovery-key-backup-v1.md`](./recovery-key-backup-v1.md).

## 9. Deployment / configuration

| Resource | Value (MVP) | Exit ramp |
|---|---|---|
| Worker URL | `<account>.workers.dev/backup` | Custom domain `backup.familycare.app` (later) — switch via `BuildConfig.RECOVERY_BACKUP_WORKER_URL`. |
| R2 bucket | `recovery-blobs` | Migrate to own-server S3-compatible store per [SRV-RECOVERY-001](../../../docs/dev/server-roadmap.md). |
| `FIREBASE_PROJECT_ID` | env var (public) | n/a |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | `wrangler secret put` (private — needed for setting `stableId` custom claim at first sign-in via Admin SDK) | n/a — rotated per IAM rotation cadence. |
| `JWKS_CACHE` | Cloudflare KV namespace binding (shared with `workers/push/`) | n/a |

> **TODO(server-roadmap)**: full migration to own-server (Postgres + S3 +
> reverse-proxy auth) per [SRV-RECOVERY-001](../../../docs/dev/server-roadmap.md).
> Trigger: Cloudflare free-tier R2 quota exhaustion OR custom-domain auth
> requirements.

## 10. Test contracts (Worker-side)

Living in `workers/backup/src/__tests__/`. **6 test files required:**

| Test file | Covers |
|---|---|
| `auth-jwt.test.ts` | JWT signature verification — valid, expired, tampered, wrong-issuer, wrong-audience. Uses `@familycare/auth-jwt` test fixtures. |
| `stable-id-check.test.ts` | `claims.stableId` matching for POST/GET/DELETE — match → 200/204/200, mismatch → 403 `STABLE_ID_MISMATCH`. |
| `idempotency.test.ts` | Same key + same body → 200 cached. Same key + different body → 409 `IDEMPOTENCY_CONFLICT`. Missing header → 400 `MISSING_IDEMPOTENCY_KEY`. |
| `rate-limit.test.ts` | POST 11th attempt within 5 min → 429 with `Retry-After`. GET 6th attempt → 429. DELETE 6th attempt → 429. Counter resets after window. |
| `r2-roundtrip.test.ts` | POST → 200 → GET returns identical blob → DELETE → 204 → GET returns 404 `NOT_FOUND`. |
| `no-logging.test.ts` | Source-grep contract: no `console.*` calls referencing request body, token, or Authorization header. Enforces §7 privacy assertion. |

**6 test files total.** Each runs in `vitest` against the Worker's
`@cloudflare/workers-types` environment with an in-memory R2 fake and
in-memory `JWKS_CACHE` fake. No real network, no real Firebase, no real R2 —
mock-first per CLAUDE.md rule 6.

Roundtrip cross-check with the Android client lives in
`core/recovery/commonTest/.../WireFormatRoundtripTest.kt` and references the
shared fixture from [`recovery-key-backup-v1.md`](./recovery-key-backup-v1.md).

---

<!-- NOVICE-SUMMARY:BEGIN -->
## Краткое резюме (для не-разработчика)

Это **описание HTTP-API** между Android-приложением и нашим маленьким сервером
на Cloudflare (бесплатный tier), который хранит **зашифрованный «слепок ключей»**
владельца. Слепок нужен только для одного: если владелец потеряет основное
устройство и захочет восстановить доступ на новом — мы скачаем этот слепок,
владелец введёт recovery-код (12 слов), и ключи расшифруются заново.

**Три действия, которые умеет сервер**:
1. **Положить слепок** (`POST /backup`) — при первой настройке или после ротации
   ключей. Запрос обязан нести Firebase-токен (доказательство «я тот же
   человек») + уникальный ID запроса (защита от случайных дубликатов).
2. **Забрать слепок** (`GET /backup/{stableId}`) — при восстановлении на новом
   устройстве. Сервер отдаёт зашифрованный блок только тому, чей токен
   соответствует `stableId` в пути.
3. **Удалить слепок** (`DELETE /backup/{stableId}`) — деструктивный сценарий
   («Fallback wipe»): владелец сознательно отказывается от возможности
   восстановления, всё стирается.

**Защита**:
- На каждый запрос — проверка Firebase-токена (подпись, срок, проект).
- В токене зашит `stableId` — это **не** Firebase-UID и **не** email, это
  непрозрачный UUID. Cloudflare видит в логах только этот UUID — никаких email,
  телефонов, имён.
- Один пользователь может трогать **только свой** `stableId`. Чужой → 403.
- Ограничение по частоте: 10 POST / 5 минут, 5 GET / 5 минут, 5 DELETE / 5 минут
  на каждый `stableId` (защита от перебора украденного токена).
- Никаких endpoint'ов «дай список пользователей» / «найди по email» — API
  устроен так, что узнать о существовании других пользователей в принципе
  невозможно.

**Что внутри слепка**: зашифрованный AES-256-GCM блоб. Ключ шифрования
вычисляется из 12-словного recovery-кода, который владелец записал на бумаге.
Сервер этот код **никогда не видит** — даже если Cloudflare целиком сольют,
без бумажки восстановить ничего нельзя.

**Версия API**: v1. Когда формат сломается — поднимем `workers/backup-v2/`
параллельно, старые клиенты продолжат работать с v1 ещё минимум один мажорный
релиз (правило 5 из CLAUDE.md про backward-compat).

**Где живёт код**:
- Cloudflare Worker: `workers/backup/` (TypeScript).
- JWT-проверка: переиспользуется общий модуль `workers/_shared/auth-jwt/`
  (тот же, что Worker для push-уведомлений использует).
- R2-бакет: `recovery-blobs` — отдельный, не пересекается с другими Worker'ами.

**Exit ramp** (правило 8 — server-roadmap): когда упрёмся в лимиты Cloudflare
или захотим свой домен с собственной авторизацией — переезжаем на свой сервер
(Postgres + S3-совместимое хранилище). Маршрут зафиксирован в
`docs/dev/server-roadmap.md` под пунктом SRV-RECOVERY-001.
<!-- NOVICE-SUMMARY:END -->
