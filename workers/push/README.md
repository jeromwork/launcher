# launcher-push-v2 (workers/push/)

Generic push-trigger Cloudflare Worker. Per [spec 019 F-5c](../../specs/019-f5c-fcm-config-updated/spec.md).

Replaces (eventually) the `/notify` endpoint of legacy
[`push-worker/`](../../push-worker/) during Phase 4 migration. Reuses
[`@familycare/auth-jwt`](../_shared/auth-jwt/) for Firebase ID-token verification.

## Endpoint

```
POST /push
Authorization: Bearer <Firebase ID-token>
Idempotency-Key: <UUID v4>
Content-Type: application/json

{
  "schemaVersion": 1,
  "eventType": "config-updated",
  "targetScope": "own-and-grants",
  "ownerUid": "...",
  "payload": { "configName": "main" }
}
```

Response codes:
- `200` — push enqueued (optionally cached idempotency hit).
- `400` — malformed body / unknown event type / unsupported schemaVersion / missing idempotency key.
- `401` — missing or invalid Firebase ID-token.
- `403` — caller not authorised for event type on ownerUid namespace.
- `429` — per-UID per-event rate limit exceeded (`Retry-After` header set).
- `500` — internal failure (FCM credentials missing, unexpected exception).

## Architecture

```
client                        Worker (this module)                    FCM
──────                        ──────────────────────                  ───
HttpPushTrigger ──POST──>  index.ts                               
                            │                                          
                            ├─ parsePushTriggerRequest             
                            ├─ verifyFirebaseIdToken (@familycare/auth-jwt)
                            ├─ lookupEventType (registry/event-types.ts)
                            ├─ entry.authorise(caller, ownerUid)
                            ├─ idempotency.ts (KV cache)
                            ├─ rate-limiter.ts (KV counter)
                            ├─ FirestoreRecipientResolver (own + grants → fcmTokens)
                            └─ FirebaseFcmDispatcher ──POST──> FCM HTTP v1
                                                              (3× retry: 500ms, 2s, 8s)
```

## Local development

```bash
cd workers/push
npm install
npm run typecheck
npm test        # vitest unit suites

# Local dev server (requires .dev.vars with FCM_SERVER_KEY etc.):
npm run dev
```

### Setup (T004 + T005 — manual steps)

KV namespaces (one-time):

```bash
wrangler kv:namespace create JWKS_CACHE
wrangler kv:namespace create JWKS_CACHE --preview
wrangler kv:namespace create IDEMPOTENCY_CACHE
wrangler kv:namespace create IDEMPOTENCY_CACHE --preview
wrangler kv:namespace create RATE_LIMIT
wrangler kv:namespace create RATE_LIMIT --preview
```

Copy returned IDs into [wrangler.toml](wrangler.toml) (currently commented-out
`[[kv_namespaces]]` blocks).

Secrets:

```bash
wrangler secret put FCM_SERVER_KEY       # service-account access-token
# OR
wrangler secret put FIREBASE_SA_JSON     # full SA JSON (alt)
```

Local `.dev.vars` (gitignored — see [.gitignore](.gitignore)):

```
FCM_SERVER_KEY=<dev-token>
```

## Adding a new event type (per FR-060 — 3-place pattern)

1. `core/push/commonMain/api/EventType.kt` — add new `data object` sealed variant.
2. `workers/push/src/registry/event-types.ts` — add entry to `EVENT_TYPES`.
3. Consumer module — register `PushHandler` impl + DI binding.

**No changes** to: `core/push` foundation, `auth-jwt`, this Worker's `index.ts`,
`LauncherFirebaseMessagingService`. Per SC-008.

## Migration from legacy push-worker/

Pre-existing [push-worker/](../../push-worker/) implements `/notify` endpoint
on spec 007 pairing model (linkId-based). Phase 4 migration in
[tasks.md T200-T207](../../specs/019-f5c-fcm-config-updated/tasks.md#phase-4-migration-existing-push-subsystem):

- New `/push` endpoint runs in parallel here.
- Legacy `/notify` continues serving spec 007/008 pairing pushes during transition.
- Eventually `/notify` deprecated → all routed through `/push`.
- Worker rename + push-worker/ removed when no in-prod clients use `/notify`.

## Server-roadmap

- [SRV-PUSH-FOUNDATION](../../docs/dev/server-roadmap.md#srv-push-foundation) — own-server migration target.
- [TODO-ARCH-001](../../docs/dev/project-backlog.md#todo-arch-001) — custom domain
  (`launcher-push-v2.<account>.workers.dev` → `push.<our-domain>`).
- [TODO-ARCH-017](../../docs/dev/project-backlog.md#todo-arch-017) — extraction trigger (V-2 spec start).
