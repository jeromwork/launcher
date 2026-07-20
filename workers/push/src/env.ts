// Worker Env bindings. Mirror wrangler.toml configuration.

import type { JwksCacheKv } from "@familycare/auth-jwt";

export interface Env {
  // Vars (wrangler.toml [vars]).
  readonly FIREBASE_PROJECT_ID: string;

  // KV namespaces (wrangler.toml [[kv_namespaces]]).
  readonly JWKS_CACHE: KVNamespace & JwksCacheKv;
  readonly IDEMPOTENCY_CACHE: KVNamespace;
  readonly RATE_LIMIT: KVNamespace;

  // Secrets (wrangler secret put).
  // FIREBASE_SA_JSON — Service Account private-key JSON для OAuth2 (JWT-bearer)
  // access-token exchange (см. auth/service-account.ts).
  readonly FIREBASE_SA_JSON?: string;
}
