# @familycare/auth-jwt

Standalone Firebase ID-token JWT verification module for Cloudflare Workers.
Per [spec 019 F-5c](../../../specs/019-f5c-fcm-config-updated/spec.md) FR-002,
FR-003 + [TODO-ARCH-018](../../../docs/dev/project-backlog.md#todo-arch-018) (extraction trigger).

## Why standalone?

JWT verification is **not** a push concern. Future Workers (V-3 album,
own-server migration target) reuse the same auth surface. Living in
`workers/_shared/auth-jwt/` with own `package.json` keeps the boundary clear
**before** extraction is required.

## Public API

```typescript
import { verifyFirebaseIdToken } from "@familycare/auth-jwt"

const result = await verifyFirebaseIdToken(token, projectId, env.JWKS_CACHE)

if (result.ok) {
  // result.claims.uid, .email, .emailVerified, .iat, .exp
} else {
  // result.error: 'invalid-signature' | 'expired' | 'wrong-issuer'
  //             | 'wrong-audience' | 'malformed-header' | 'malformed-payload'
  //             | 'unsupported-algorithm' | 'kid-not-found' | 'jwks-fetch-failed'
}
```

## Integration example

```typescript
// workers/push/src/index.ts (snippet)
import { verifyFirebaseIdToken } from "@familycare/auth-jwt"

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const authHeader = request.headers.get("Authorization")
    if (!authHeader?.startsWith("Bearer ")) {
      return new Response("unauthorized", { status: 401 })
    }
    const token = authHeader.slice("Bearer ".length)
    const verify = await verifyFirebaseIdToken(
      token,
      env.FIREBASE_PROJECT_ID,
      env.JWKS_CACHE,
    )
    if (!verify.ok) {
      return new Response(verify.error, { status: 401 })
    }
    // Proceed with verify.claims.uid as authenticated subject.
  }
}
```

## KV namespace requirement

Caller must provide a Cloudflare KVNamespace (or compatible interface — see
[`JwksCacheKv`](src/types.ts)) bound to the Worker. Recommended name:
`JWKS_CACHE`.

```toml
# wrangler.toml
[[kv_namespaces]]
binding = "JWKS_CACHE"
id = "<prod-id>"
preview_id = "<preview-id>"
```

Single namespace can be shared across multiple Workers (same JWKS keys).

## TTL policy (FR-003)

Cache lifetime parses `Cache-Control: max-age` from Google's JWKS response.
Clamped to `[60s, 86400s]`:
- floor 60s — defence against Google misconfig (max-age=0).
- ceiling 24h — defence against infinite-cache misconfig (Google rotates ~weekly).

On `kid-not-found` (signed token references a key not in cache) — force-refresh
is attempted before failing.

## Development

```bash
cd workers/_shared/auth-jwt
npm install
npm test           # unit tests (claims.test.ts, jwks-cache.test.ts)
npm run typecheck  # tsc --noEmit
```

## Test approach

- **`claims.test.ts`** — pure validation rules (iss/aud/sub/iat/exp). No crypto,
  no network. Fast feedback loop.
- **`jwks-cache.test.ts`** — `parseCacheControlMaxAge` boundary cases.
- **Integration test** (deferred — T065 acceptance): full `verifyFirebaseIdToken`
  flow with runtime-generated RS256 key pair + signed token + in-memory KV fake.
  See [T065 in tasks.md](../../../specs/019-f5c-fcm-config-updated/tasks.md#-t065).

## Extraction roadmap

Per [TODO-ARCH-018](../../../docs/dev/project-backlog.md#todo-arch-018):

- **Now (Phase 1)**: lives in `workers/_shared/auth-jwt/` as standalone package
  with own `package.json` + `tsconfig.json` + `vitest.config.ts`. First step
  of extraction.
- **Trigger for full extraction**: second Worker consumer needs JWT auth. Run
  `git subtree split --prefix=workers/_shared/auth-jwt -b auth-jwt` →
  push to new repo → publish to npm.

## License

Internal. Same as parent monorepo.
