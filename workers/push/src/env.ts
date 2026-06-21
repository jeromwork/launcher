// Worker Env bindings. Mirror wrangler.toml configuration.

import type { JwksCacheKv } from "@familycare/auth-jwt";

export interface Env {
  // Vars (wrangler.toml [vars]).
  readonly FIREBASE_PROJECT_ID: string;
  readonly MAX_SUPPORTED_SCHEMA_VERSION: string;

  // KV namespaces (wrangler.toml [[kv_namespaces]]).
  readonly JWKS_CACHE: KVNamespace & JwksCacheKv;
  readonly IDEMPOTENCY_CACHE: KVNamespace;
  readonly RATE_LIMIT: KVNamespace;

  // Secrets (wrangler secret put).
  readonly FCM_SERVER_KEY?: string;
  readonly FIREBASE_SA_JSON?: string;
}
