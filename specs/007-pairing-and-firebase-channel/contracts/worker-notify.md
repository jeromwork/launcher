# Wire format: `POST /notify` (Cloudflare Worker HTTP API)

**Source of truth**: this document.
**Used by**: spec 007 §FR-019..025, FR-036.
**Schema version**: 1.
**Hosted at (dev)**: `https://launcher-push.<account>.workers.dev/notify`.
**Migration to custom domain**: см. `push-worker/README.md` §«Migration to custom domain».

---

## HTTP request

```http
POST /notify HTTP/1.1
Host: launcher-push.<account>.workers.dev
Authorization: Bearer <Firebase ID-token>
Content-Type: application/json
X-Schema-Version: 1

{
  "schemaVersion": 1,
  "linkId": "abc123XYZ",
  "type": "config-changed",
  "extra": {
    "cmdId": "cmd-7890"
  }
}
```

### Headers

| Header | Required | Value | Notes |
|---|---|---|---|
| `Authorization` | ✓ | `Bearer <Firebase ID-token>` | Token from `FirebaseAuth.currentUser?.getIdToken(false)`; lifetime 1h. Worker verifies signature via Firebase JWKS (FR-021) |
| `Content-Type` | ✓ | `application/json` | |
| `X-Schema-Version` | ✓ | `1` | Echoes body schemaVersion; allows future routing without parse |

### Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `schemaVersion` | `1` | ✓ | |
| `linkId` | string | ✓ | Worker validates `uid == links/{linkId}.adminId` |
| `type` | string | ✓ | `config-changed` \| `command-issued` \| `revoke` |
| `extra` | object | ✗ | type-specific; e.g. `{cmdId}` for `command-issued` |

## HTTP responses

### 200 OK

```json
{
  "schemaVersion": 1,
  "status": "queued",
  "messageId": "fcm-message-id-abcd"
}
```

FCM accepted; push will be delivered (subject to OLD's reachability).

### 400 Bad Request

Malformed JSON / missing fields / unknown `type`.

```json
{
  "schemaVersion": 1,
  "error": "malformed_request",
  "details": "missing field: linkId"
}
```

### 401 Unauthorized

ID-token missing, expired, or signature invalid.

```json
{
  "schemaVersion": 1,
  "error": "unauthorized",
  "details": "id_token_expired"
}
```

Client action: refresh token (`getIdToken(true)`) и retry.

### 403 Forbidden

`uid != links/{linkId}.adminId` — пользователь пытается слать push на чужой link.

```json
{
  "schemaVersion": 1,
  "error": "forbidden",
  "details": "uid_does_not_match_admin"
}
```

Client action: stop. Может означать что link был revoked.

### 404 Not Found

`/links/{linkId}` не существует.

```json
{
  "schemaVersion": 1,
  "error": "link_not_found"
}
```

### 429 Too Many Requests

Rate limit triggered (C12=A in-memory; SC-012: 100 req/min на linkId).

```json
{
  "schemaVersion": 1,
  "error": "rate_limited",
  "retryAfterSeconds": 60
}
```

Headers also include `Retry-After: 60`.

### 502 Bad Gateway

FCM upstream returned non-2xx.

```json
{
  "schemaVersion": 1,
  "error": "fcm_upstream_error",
  "fcmStatus": 503,
  "fcmMessage": "..."
}
```

Client action: retry with exponential backoff.

### 503 Service Unavailable

Worker не смог получить FCM access-token (Google upstream down) or JWKS fetch failed.

```json
{
  "schemaVersion": 1,
  "error": "service_unavailable",
  "cause": "jwks_fetch_failed"
}
```

## Authentication flow (FR-021)

```text
Worker receives POST /notify
  │
  ├── 1. Parse Authorization header → extract ID-token
  ├── 2. Verify signature via Firebase JWKS (cached 6h)
  │       - issuer: https://securetoken.google.com/{projectId}
  │       - audience: {projectId}
  │       - algorithm: RS256
  │       - exp not expired
  │     → if fail: 401
  ├── 3. Extract uid = payload.sub
  ├── 4. Read /links/{body.linkId} from Firestore
  │     - using SA credentials (Worker service-account-token flow)
  │     → if not found: 404
  ├── 5. Assert uid == link.adminId
  │     → if fail: 403
  ├── 6. Check rate-limit (in-memory Map keyed by linkId)
  │     → if exceeded: 429
  ├── 7. Mint FCM access-token (cached 50min)
  ├── 8. POST FCM HTTP v1 send with topic="link-{linkId}"
  │     → if upstream non-2xx: 502
  └── 9. Return 200 OK with messageId
```

## Rate-limit policy

**Per linkId**: 100 requests per 60-second sliding window. Implementation: in-memory `Map<linkId, Array<timestamp>>` in Worker module scope (resets when instance restarts).

**TODO в `push-worker/src/rate-limit.ts`**: «при росте трафика — migrate to Cloudflare KV для cross-instance accurate limits; см. README §Adding KV namespace».

## Idempotency

Worker **не** дедуплицирует — client retry на 429/502/503 безопасен (FCM delivers same data twice; OLD идемпотентен per `fcm-payload.md`).

## Tests (push-worker/test/, vitest)

| Test | What it verifies |
|---|---|
| `notify.happyPath` | Valid token + valid linkId + matching adminId → 200 + FCM sent |
| `notify.invalidToken_returns_401` | Bad/expired token → 401 |
| `notify.missing_link_returns_404` | Random linkId → 404 |
| `notify.wrong_admin_returns_403` | uid != adminId → 403 |
| `notify.rate_limit_triggers_429` | 101 reqs in 60s on same linkId → 429 with Retry-After |
| `notify.fcm_5xx_returns_502` | Mocked FCM 503 → Worker returns 502 |
| `notify.unknown_type_returns_400` | type="cat" → 400 |
| `notify.malformed_body_returns_400` | Non-JSON body → 400 |
| `notify.schemaVersion_mismatch_returns_400` | schemaVersion=2 → 400 (future versioning gate) |

## Backward compatibility policy

- `schemaVersion: 1` остаётся forever-supported.
- Adding optional `extra.*` fields — OK.
- Adding new `type` value — OK; clients не знающие про новый тип просто не вызывают.
- Changing response shape — breaking; bump schemaVersion в request to `2`.

**TODO в `push-worker/src/index.ts`**: «при добавлении v=2 — отдельный route handler `if (body.schemaVersion === 2) handleV2() else handleV1()`».

## Migration to custom domain (TODO from C10=A)

Текущий URL: `launcher-push.<account>.workers.dev` (free Cloudflare subdomain).

При переходе на свой домен:
1. Купить домен (например `launcher-old.dev` ~$10/year).
2. Добавить в Cloudflare как Zone.
3. В `push-worker/wrangler.toml` добавить:
   ```toml
   routes = [
     { pattern = "push.launcher-old.dev/notify", custom_domain = true }
   ]
   ```
4. `wrangler deploy`.
5. Cloudflare auto-provisions TLS.
6. Обновить env-переменную `WORKER_BASE_URL` в admin app build config; **никаких code changes**.

См. подробности в `push-worker/README.md` §«Migration to custom domain».

---

<!-- novice summary -->

## TL;DR

Это **публичный HTTP-API** Worker'а. Admin-приложение когда хочет послать push — делает обычный HTTPS POST на `https://launcher-push.XXX.workers.dev/notify`, кладёт в заголовок Firebase ID-токен (типа удостоверение «я залогинен»), в тело — кому и какой push нужен.

Worker проверяет:
1. **Удостоверение настоящее** (подпись Firebase, не подделана).
2. **Этот юзер имеет право слать push на этот linkId** (читает Firestore).
3. **Не превышен лимит** (100 push в минуту).

Если всё ОК — отправляет в Google FCM, который доставит на бабушкин телефон. Если нет — возвращает понятный код ошибки (401/403/404/429/502).

**Контракт фиксирован** — даже если завтра переедем с Cloudflare на Vercel, этот API остаётся такой же; меняется только URL.
