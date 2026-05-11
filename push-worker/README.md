# launcher-push — Cloudflare Worker push-relay

Spec 007 §FR-019..025. Wakes a paired Managed device in Doze via FCM
data-message when admin writes config / issues a command / revokes pairing.

## Architecture (one paragraph)

`POST /notify` receives `{schemaVersion, linkId, type, payload?}` with
`Authorization: Bearer <Firebase ID-token>`. The Worker (1) verifies the
ID-token against Google's JWKS, (2) mints a service-account OAuth
access-token, (3) reads `/links/{linkId}` from Firestore REST and asserts
`uid == adminId`, (4) rate-limits per linkId (60s/100 req), (5) sends an
FCM HTTP v1 `messages:send` on topic `link-{linkId}` with
`android.priority=HIGH` and a flat string-only `data` map. Silent push,
no `notification` field — Managed wakes, reads Firestore, applies.

## Local setup

```sh
cd push-worker
npm install
npm run typecheck    # tsc --noEmit
npm test             # vitest, 9 test cases
```

Required tooling: Node 20+, npm 10+.

### Wrangler authentication

```sh
npx wrangler login
```

Opens a browser window. The Cloudflare account is the one configured in
`wrangler.toml` (`account_id = c8f9c8c59e930e0283d713b91c01fb13` — owner
project). When transferring ownership, replace `account_id` and re-login.

### Secret: Firebase service-account JSON

The Worker reads `FIREBASE_SA_JSON` as a Cloudflare secret (not a `vars`
entry — must never leak in `wrangler.toml`). Generate the key in Firebase
Console → Project Settings → Service Accounts → Generate new private key,
save to a local file, then upload:

```sh
Get-Content sa.json -Raw | npx wrangler secret put FIREBASE_SA_JSON
# or on bash: cat sa.json | npx wrangler secret put FIREBASE_SA_JSON
```

**Then delete the local file.** Saved as memory `feedback_secret_handling.md`
— never transfer the JSON through chat.

`FIREBASE_PROJECT_ID` is a public `var` (not a secret) and lives in
`wrangler.toml` `[vars]`. Currently `launcher-old-dev` (the dev project).

## Deploy

```sh
npx wrangler deploy
```

Pushes `src/index.ts` to `https://launcher-push.<account>.workers.dev`.
Smoke-check:

```sh
curl -i https://launcher-push.<account>.workers.dev/notify
# Expected: 404 (it's a POST endpoint)

curl -i -X POST https://launcher-push.<account>.workers.dev/notify \
  -H 'Content-Type: application/json' -d '{}'
# Expected: 401 (no Bearer token)
```

## Local dev (against Firebase Emulator)

```sh
# Terminal 1 — start Firebase emulators (project root):
firebase emulators:start --only firestore,auth

# Terminal 2 — start Worker locally:
cd push-worker
npx wrangler dev
```

Wrangler binds `localhost:8787/notify`. Point a test client at it with a
valid emulator-minted ID-token. The Worker still talks to real
`oauth2.googleapis.com` and `fcm.googleapis.com` unless you override
those URLs via env vars in a future iteration — emulator coverage in
Phase 9 lives in the integration suite, not here.

## Migration to custom domain

Currently the Worker is reachable at `launcher-push.<account>.workers.dev`
(free tier, no domain purchase needed). When we ship publicly:

1. Buy a domain (e.g. `launcher.example` — ~$10/year).
2. Add it to Cloudflare as a zone (free plan).
3. In Cloudflare DNS, add a CNAME or AAAA record pointing
   `push.launcher.example` to Cloudflare's edge.
4. Uncomment the `[[routes]]` block in `wrangler.toml`:
   ```toml
   [[routes]]
   pattern = "push.launcher.example/notify"
   custom_domain = true
   ```
5. `npx wrangler deploy`.
6. Update the admin app's `WORKER_BASE_URL` constant in
   `core/src/androidRealBackend/.../WorkerPushSender.kt` to
   `https://push.launcher.example`. (A future change will move this to a
   build-time env var so the code itself stays domain-agnostic — tracked
   as project-backlog TODO-ARCH-001.)

The Worker code itself doesn't change between `.workers.dev` and a custom
domain. Only routing + admin-side URL flip.

## Adding KV namespace (when rate-limit accuracy matters)

Spec 007 ships with in-memory rate-limit (C12=A). State is per-isolate
and resets on Worker redeploys. For production we'd want Cloudflare KV:

```sh
npx wrangler kv namespace create RATE_LIMIT
# add the returned namespace id to wrangler.toml:
#   [[kv_namespaces]]
#   binding = "RATE_LIMIT"
#   id = "<id from above>"

npx wrangler kv namespace create RATE_LIMIT --preview
# add preview_id similarly
```

Then replace `src/rate-limit.ts` with a KV-backed implementation:

```ts
export async function checkAndRecord(env: Env, linkId: string): Promise<RateLimitCheck> {
  const key = `rl:${linkId}`;
  const now = Date.now();
  const cutoff = now - WINDOW_MS;
  const existing = await env.RATE_LIMIT.get<number[]>(key, "json") ?? [];
  const recent = existing.filter(ts => ts > cutoff);
  if (recent.length >= MAX_REQS_PER_WINDOW) { /* ... 429 ... */ }
  recent.push(now);
  await env.RATE_LIMIT.put(key, JSON.stringify(recent), { expirationTtl: 120 });
  return { allowed: true };
}
```

Tracked as project-backlog TODO-ARCH-002. Trigger: traffic > 1k requests/day
on any single linkId, OR observed abuse attempts.

## Migration to alternative platform (Vercel / Fly.io / Lambda)

Per OWD-6, the Worker is a one-way door on Cloudflare specifically — but
the contract (`POST /notify` with the JSON body) is portable. To migrate:

| Target | What changes |
|---|---|
| **Vercel Edge** | Replace `wrangler.toml` with `vercel.json` + an `app/api/notify/route.ts`. The handler signature is `Request → Response` — same as Cloudflare. `jose` works unchanged. No KV: use Vercel KV (Upstash). |
| **Fly.io Apps** | Wrap in a small Node HTTP server (Hono or raw `http.createServer`). Heavier ops surface; only worth it if we outgrow Workers free tier. |
| **AWS Lambda + API Gateway** | Adapter for Lambda event → Request; otherwise unchanged. Cold start hit ~200ms vs Workers' near-zero. |
| **Cloud Functions (Blaze)** | Native fit if we upgrade Firebase to Blaze anyway (project-backlog TODO-ARCH-003). The auth/authorize logic collapses to "function runs as service account already". |

In every case the admin-app's `WORKER_BASE_URL` flips and the contract
URL stays `POST /notify`.

## Recovery procedures

### Lost Cloudflare access

- Account-level 2FA on `gpt1.jeromwork@gmail.com` is required
  (project-backlog TODO-OPS-001).
- If 2FA backup codes are also lost: Cloudflare account recovery is
  email-based; submit a support ticket from the recovery address.
- While recovering: the Worker continues to serve traffic; only deploys
  are blocked.
- Worst case: deploy a duplicate Worker from a fresh account, flip
  `WORKER_BASE_URL` in admin app.

### Compromised service-account JSON

If the JSON ever leaks (chat, screenshot, git commit):

1. Firebase Console → Project Settings → Service Accounts → see the keys
   list → revoke the compromised key.
2. Generate a new private key.
3. Re-run `wrangler secret put FIREBASE_SA_JSON` with the new file.
4. Delete the local file.
5. Document the incident in `docs/dev/project-backlog.md` under "Closed
   items" with date + commit reference (see TODO-OPS-003 for the
   2026-05-11 reference rotation).

Service-account key management via IAM REST API is wired and tested —
see memory `reference_firebase_iam_rest_works.md`.
