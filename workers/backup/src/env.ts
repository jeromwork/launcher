// T653-scaffold — Worker environment bindings.
//
// Wired via wrangler.toml `[vars]`, `[[kv_namespaces]]`.
//
// Storage decision (2026-06-29): R2 dropped in favour of Workers KV.
// Recovery blobs are ~500 bytes JSON, one per identity per install — KV's
// 25 MiB-per-value cap and free-tier write quota (1k/day) are wildly above
// our needs, and KV does not require enabling R2 / billing card binding.
// See docs/dev/server-roadmap.md SRV-RECOVERY-001 for the migration target.

export interface Env {
  readonly FIREBASE_PROJECT_ID: string;
  readonly MAX_SUPPORTED_SCHEMA_VERSION: string;

  /** KV namespace holding `backup/{stableId}/v1.json` values (JSON strings). */
  readonly RECOVERY_BLOBS: KVNamespace;

  /**
   * JWKS cache namespace — shared with push worker when bound. Optional in test
   * harness (tests use {@link InMemoryJwksKv} adapter from auth-jwt).
   * KEPT for backward compat with existing tests; no longer used by production
   * `authenticate()` (which validates our own HS256 JWT, not Firebase JWKS).
   */
  readonly JWKS_CACHE?: KVNamespace;

  /**
   * TASK-119 (2026-07-09): HS256 secret used to validate our own JWT issued
   * by auth-worker's /auth/exchange. Same value set on both workers via
   * `wrangler secret put AUTH_JWT_SECRET`.
   */
  readonly AUTH_JWT_SECRET: string;
}
