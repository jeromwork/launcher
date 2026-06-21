# Contract: PushTriggerRequest v1

**Wire format**: HTTP request body для `POST /push` endpoint (client → Worker).
**Semantic version**: `1` (initial).
**Backward-compat policy**: Per [CLAUDE.md rule 5](../../../CLAUDE.md) + [spec.md Wire-format policy](../spec.md). Additive fields OK, breaking changes require schemaVersion bump.
**Roundtrip test**: `core/push/commonTest/kotlin/com/familycare/push/WireFormatRoundtripTest.kt` — fixture `core/push/commonTest/resources/push-trigger-request-v1.json`.

## JSON shape

```json
{
  "schemaVersion": 1,
  "eventType": "config-updated",
  "targetScope": "own-and-grants",
  "ownerUid": "AbCdEf1234567890",
  "payload": {
    "configName": "main"
  }
}
```

## HTTP envelope

| Header | Value | Required | Notes |
|---|---|---|---|
| `Content-Type` | `application/json; charset=utf-8` | yes | |
| `Authorization` | `Bearer <Firebase ID-token>` | yes | Per FR-002. Validated by auth-jwt module. |
| `Idempotency-Key` | UUID v4 string | yes | Per FR-010, FR-025. Worker dedupes 10-min window via KV. |

## Field definitions

### `schemaVersion: Int`

**Required.** Must be `1` (current). Worker validates `schemaVersion <= MAX_SUPPORTED_SCHEMA_VERSION` (per FR-013, Wire-format policy). Higher → HTTP 400 «Unsupported schemaVersion».

Reading order: schemaVersion MUST be deserialized first (per Wire-format policy). If fails check, остальные поля не парсятся.

### `eventType: String`

**Required.** Must match key в Worker's EventTypeRegistry (`workers/push/src/registry/event-types.ts`). Current valid values:
- `"config-updated"` (F-5c — this spec)

Future (added в их specs):
- `"sos-triggered"` (S-4)
- `"battery-critical"`, `"device-offline"`, `"activity-anomaly"` (S-9)
- `"pairing-accepted"`, `"pairing-revoked"` (S-2)
- `"entitlement-expired"`, `"grace-period-ending"` (S-10)
- `"message-arrived"`, `"call-incoming"` (V-2)
- `"album-photo-added"`, `"album-comment"` (V-3)
- `"caregiver-invited"`, `"caregiver-accepted"` (V-6)

Unknown eventType → Worker returns HTTP 400 «Unknown event type» (per FR-004).

### `targetScope: String`

**Required.** Enum-like string. Current valid values:
- `"own-devices"` — push only to owner's own devices (other devices of the same UID).
- `"own-and-grants"` — push to own devices + devices of all UIDs holding access grants в owner's namespace.

Future:
- `"specific-uid"` — push to specific UID (V-2 messenger direct message). Will require additional `targetUid` field.

Unknown → 400.

### `ownerUid: String`

**Required.** Firebase UID format (28-char alphanumeric). Namespace owner — whose data this event concerns. Caller MAY differ from owner if access grant exists (cross-UID delegation, F-5b model).

Authorization enforced by EventTypeRegistry entry's `authorise(callerClaims, ownerUid)` callback. For `config-updated`: `caller.uid === ownerUid OR caller has active write-grant в ownerUid namespace`.

### `payload: Map<String, String>`

**Required** (may be empty `{}`). Event-type-specific fields. **Flat map of strings** (FCM constraint — FCM data field is `Map<String, String>`, nested structures must be serialized).

Per-event payload schema:

| eventType | Required payload fields | Optional |
|---|---|---|
| `config-updated` | `configName: String` | — |
| Future `sos-triggered` | `lat: String`, `lng: String` | `ts: String` |
| Future `album-photo-added` | `albumId: String`, `photoId: String` | — |

Worker validates payload shape per EventTypeRegistry entry's optional `payloadSchema` (added when needed). Currently F-5c only validates field presence in handler.

## Worker response

### Success (200 OK)

```json
{
  "schemaVersion": 1,
  "status": "queued",
  "triggerId": "550e8400-e29b-41d4-a716-446655440000",
  "recipientCount": 3
}
```

- `triggerId` — Worker-generated UUID v4. Used as receiver dedupe key (FR-044). Same UUID echoed back to caller для tracing.
- `recipientCount` — number of devices к которым отправлен push. Informational only — НЕ guarantees delivery (FCM = best-effort).

### Errors

| HTTP status | Body | Cause |
|---|---|---|
| 400 | `{"error": "unsupported-schema-version", "max": 1}` | FR-013 |
| 400 | `{"error": "unknown-event-type", "value": "..."}` | FR-004 |
| 400 | `{"error": "unknown-target-scope", "value": "..."}` | FR-007 |
| 400 | `{"error": "malformed-request", "details": "..."}` | JSON parse failure, missing required field |
| 401 | `{"error": "unauthorized", "reason": "<VerificationError>"}` | FR-002 — auth-jwt module returned `{ok: false}` |
| 403 | `{"error": "forbidden", "eventType": "...", "ownerUid": "..."}` | FR-005 — authorisation rule failed |
| 429 | `{"error": "rate-limited", "retryAfterSeconds": N}` | FR-006 — per-UID per-event limit exceeded |
| 503 | `{"error": "dispatch-failed", "details": "FCM API <error>"}` | FR-007 — FCM 3 retries exhausted |

### Idempotency cache hit

If `Idempotency-Key` matches recent request (within 10-min KV TTL), Worker returns **cached response** (same HTTP status + body) without re-executing recipient resolution / FCM dispatch. Per FR-010.

## Roundtrip test fixture

File: `core/push/commonTest/resources/push-trigger-request-v1.json`

```json
{
  "schemaVersion": 1,
  "eventType": "config-updated",
  "targetScope": "own-and-grants",
  "ownerUid": "TestUid1234567890123456789",
  "payload": {
    "configName": "main"
  }
}
```

Test asserts: read fixture JSON → deserialize → assertEquals against expected DTO → serialize → assertEquals against fixture JSON.

## Versioning roadmap

**v1 → v2 trigger**: any breaking change (removal/rename field, type change). Until then, all additions are v1-compatible.

**Likely candidates для v2 в future**:
- Adding `targetUid: String?` field для `specific-uid` targetScope (V-2 messenger).
- Adding `priority: String?` override (currently per-event-type via Worker registry).
- Adding `ttlSeconds: Int?` override.

Each can be added как **optional field в v1** (additive). v2 bump only required если we restructure shape (unlikely).

---

## Краткое резюме (для не-разработчика)

Это **формат HTTP-запроса**, который клиент отправляет на Cloudflare Worker, чтобы триггерить push.

**Структура запроса**:
- В заголовках: Firebase токен (доказательство «я этот пользователь») + уникальный ID запроса (для защиты от дубликатов).
- В теле (JSON): тип события (`config-updated`), кому отправлять (`own-and-grants` = свои устройства + помощники), uid владельца данных, и поля специфичные для этого типа события.

**Что Worker возвращает**:
- `200 OK` + `triggerId` — успех, push отправлен N получателям.
- `4xx` — клиент неправильно сформулировал запрос (неизвестный тип события, неправильный токен, нет прав).
- `5xx` — Worker не смог дозвониться до Google FCM после 3 попыток.

**Версия формата**: `schemaVersion: 1` зашит в первом коммите. Когда формат будет ломаться — bump до 2, старые клиенты ничего не сломают (Worker отвечает 400, клиент знает что нужно обновиться).

**Защита от дубликатов**: каждый запрос несёт уникальный UUID. Если клиент случайно повторит запрос — Worker увидит «уже видел этот ID» и вернёт закешированный ответ, не делая работу повторно.
