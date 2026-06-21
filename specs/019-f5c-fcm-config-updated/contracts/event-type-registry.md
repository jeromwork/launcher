# Contract: EventTypeRegistry

**Type**: Server-side static configuration (Worker TypeScript const), not a wire format per se.
**Semantic version**: managed via git (no schemaVersion in data — code-level evolution).
**Backward-compat policy**: additions are non-breaking (new event types — old Worker version returns 400 для unknown until deployed); removals require coordinated migration (mark event type deprecated, deploy receiver-side handler, deploy Worker without entry).
**Reference test**: `workers/push/test/registry.test.ts` — validates entries structure.

## Purpose

EventTypeRegistry — это **central whitelist** of allowed event types в Worker. **Caller of `POST /push` cannot trigger arbitrary event** — only those registered. Каждая запись specifies:
- **Authorisation rule**: who's allowed to trigger this event type для given ownerUid.
- **Rate limit**: per-UID per-event limit.
- **Collapse key**: FCM collapse formula (или null = no collapse).
- **Priority**: FCM message priority.
- **TTL** (optional): FCM message TTL override.

## File location

`workers/push/src/registry/event-types.ts` — single static const exported, imported by Worker entrypoint (`index.ts`) для lookup per request.

**Why static const, not Firestore-configurable**: security. If EventTypeRegistry lived в Firestore, attacker compromising one admin's credentials could add new event types or modify authorisation rules. As code, registry changes require PR review + Worker redeploy.

## Schema

```typescript
// workers/push/src/registry/event-types.ts

import type { Claims } from '@familycare/auth-jwt'
import type { PushTriggerRequest } from '../contract/wire-format'

export interface EventTypeRegistryEntry {
  /** Returns true if caller authorised для this event type на ownerUid namespace. */
  authorise: (callerClaims: Claims, ownerUid: string, env: Env) => boolean | Promise<boolean>

  /** Per-UID per-event rate limit. */
  rateLimit: { perUid: number; windowSeconds: number }

  /**
   * FCM collapse_key formula. null = no collapse (каждый message unique).
   * Worker uses returned key как `android.collapse_key` в FCM payload.
   * If two messages have same collapse_key, FCM delivers только последний (на recipient side, для offline → online batch).
   */
  collapseKey: (payload: PushTriggerRequest) => string | null

  /** FCM message priority. */
  priority: 'normal' | 'high'

  /** Optional: max FCM TTL в секундах (default = FCM default 4 weeks). */
  ttlSeconds?: number

  /** Optional: payload schema validation (if defined, Worker validates payload shape per Zod schema или similar). */
  payloadSchema?: PayloadSchema
}

export const EVENT_TYPES: Record<string, EventTypeRegistryEntry> = {

  'config-updated': {
    authorise: async (caller, ownerUid, env) =>
      caller.uid === ownerUid || await hasActiveWriteGrant(caller.uid, ownerUid, env),
    rateLimit: { perUid: 60, windowSeconds: 60 },
    collapseKey: (payload) =>
      `config-updated:${payload.ownerUid}:${payload.payload.configName ?? 'default'}`,
    priority: 'normal',
  },

  // Future event types add here. Examples (NOT in F-5c scope, добавятся в их specs):

  /*
  'sos-triggered': {
    authorise: (caller, ownerUid) => caller.uid === ownerUid,  // только сам senior may trigger SOS
    rateLimit: { perUid: 10, windowSeconds: 60 },
    collapseKey: () => null,                       // каждый SOS unique
    priority: 'high',                              // wake device immediately
    payloadSchema: SosPayloadSchema,               // require lat, lng, ts
  },

  'album-photo-added': {
    authorise: (caller, ownerUid) => isFamilyMember(caller.uid, ownerUid),
    rateLimit: { perUid: 60, windowSeconds: 60 },
    collapseKey: (payload) => `album-photo-added:${payload.payload.albumId}`,
    priority: 'normal',
  },

  'entitlement-expired': {
    authorise: (caller) => caller.uid === '__server__',  // server-internal only (cron trigger)
    rateLimit: { perUid: 1, windowSeconds: 86400 },     // 1/day per user
    collapseKey: (payload) => `entitlement-expired:${payload.ownerUid}`,
    priority: 'high',
  },
  */
}

/** Helper: check if grant table has active write-grant. */
async function hasActiveWriteGrant(
  callerUid: string,
  ownerUid: string,
  env: Env,
): Promise<boolean> {
  // Firestore REST API read: /users/{ownerUid}/grants/{callerUid}
  // Returns true if doc exists AND grant.role === 'write' AND grant.expiresAt > now.
  // Cached в KV для 30 seconds (per-request acceptable staleness).
  // Implementation в workers/push/src/auth/event-authorisation.ts.
  return await fetchGrant(callerUid, ownerUid, env)
}
```

## Lookup flow

```
Worker request handling (workers/push/src/index.ts):

1. const body: PushTriggerRequest = await req.json()
2. const entry = EVENT_TYPES[body.eventType]
   if (!entry) → return 400 "unknown-event-type"
3. const claims = await verifyFirebaseIdToken(authHeader, env)
   if (!claims.ok) → return 401
4. const allowed = await entry.authorise(claims.claims, body.ownerUid, env)
   if (!allowed) → return 403
5. const limited = await checkRateLimit(claims.claims.uid, body.eventType, entry.rateLimit, env.KV)
   if (limited) → return 429
6. if (entry.payloadSchema) validate body.payload против schema → 400 if invalid
7. resolve recipients per body.targetScope → list of FCM tokens
8. dispatchFcm(recipients, body, triggerId, entry)  // uses entry.collapseKey, entry.priority, entry.ttlSeconds
```

## Per-event rationale

### `config-updated` (F-5c)

| Field | Value | Rationale |
|---|---|---|
| `authorise` | owner ∨ grant-holder | Cross-UID delegation — admin editing granny's config через grant must be allowed |
| `rateLimit` | 60/min per UID | Generous — typical admin может do 5-10 saves в minute when actively editing |
| `collapseKey` | `config-updated:{ownerUid}:{configName}` | Coalesce bursts of saves на same config — recipient only gets latest |
| `priority` | `normal` | Config changes — not time-critical |
| `ttlSeconds` | (default 4 weeks) | If recipient offline weeks — pull-on-app-open will fetch. Push delivery до 4 weeks acceptable |

### Future `sos-triggered` (S-4 — informational, не в F-5c scope)

| Field | Value | Rationale |
|---|---|---|
| `authorise` | `caller.uid === ownerUid` | Only senior themselves trigger SOS (admin can't fake SOS) |
| `rateLimit` | 10/min per UID | Prevent SOS spam; legitimate SOS rare |
| `collapseKey` | null | Каждый SOS unique event, don't coalesce |
| `priority` | `high` | Wake device immediately, override doze |
| `ttlSeconds` | 300 (5 minutes) | If admin offline > 5 min, SOS info stale (call emergency services directly) |

### Future `album-photo-added` (V-3 — informational)

| Field | Value | Rationale |
|---|---|---|
| `authorise` | family member | Anyone в family group may add to shared album |
| `rateLimit` | 60/min per UID | Bursts when sharing multiple photos OK |
| `collapseKey` | `album-photo-added:{albumId}` | Multiple photos в album — coalesce, recipient gets one «new photos» indicator |
| `priority` | `normal` | Not time-critical |

## Extending the registry (для future specs)

When adding a new event type:

1. **In `event-types.ts`**: add new entry в `EVENT_TYPES` const.
2. **In `core/push/api/EventType.kt`**: add new `data object` variant.
3. **In consumer module** (e.g., `app/sos/`): write `PushHandler` implementation + DI registration.
4. **In consumer's spec.md**: document event type + tests.
5. **Update этот файл** (event-type-registry.md): add per-event rationale table.
6. **Document в Reuse pattern table** spec.md если applicable.

**No changes to**:
- `workers/push/src/index.ts` (generic lookup logic).
- `workers/_shared/auth-jwt/` (JWT verification unchanged).
- `core/push/api/PushTrigger.kt` (port unchanged).
- `LauncherFirebaseMessagingService.kt` (generic dispatch logic).
- Worker recipient resolution (generic targetScope-driven).

Per FR-060: adding new event type requires ровно 3 places + ~30-40 LOC total.

## Validation tests

`workers/push/test/registry.test.ts`:

1. **Structural**: каждая entry has all required fields.
2. **collapseKey purity**: function pure, deterministic (same input → same output).
3. **rateLimit sanity**: perUid > 0, windowSeconds > 0.
4. **priority enum**: 'normal' | 'high' only.
5. **Smoke**: для каждого known eventType, mock claims + ownerUid → assert authorise returns boolean без exception.

---

## Краткое резюме (для не-разработчика)

Это **список разрешённых типов событий** в системе push. Worker не пропустит «триггер чего угодно» — только событий из этого списка.

Для каждого типа события прописано:
- **Кто может триггерить**: для `config-updated` — владелец или помощник с правами; для будущего SOS — только сам senior; для будущего «истёкла подписка» — только server.
- **Сколько раз в минуту**: для config = 60, для SOS = 10 (защита от спама), для подписки = 1 в день.
- **Как объединять дубликаты**: для config — по имени конфига (5 saves подряд = 1 push последний); для SOS — не объединять (каждый SOS уникален).
- **Приоритет**: normal или high (high будит устройство из режима экономии).
- **TTL**: сколько ждать в очереди если получатель offline (для SOS — 5 минут, потом устарело; для config — 4 недели стандартно).

**Почему это static код, не настраивается через UI**: security. Если бы список был в Firestore — взлом одного пользователя позволил бы добавить новые типы событий или поменять правила. Изменения требуют PR + redeploy Worker'а.

**Добавление нового типа события** в будущем — это 3 места по 10-30 строк кода, и **никаких изменений в foundation** Worker'е.
