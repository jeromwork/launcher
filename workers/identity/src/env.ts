// Worker environment bindings.

export interface Env {
  readonly FIREBASE_PROJECT_ID: string;
  readonly JWKS_CACHE?: KVNamespace;
  /**
   * TASK-119 (2026-07-09): mapping `firebase_uid → stableId`. Single source of
   * truth for identity. Written by /auth/exchange, read on every subsequent
   * /auth/exchange call (idempotent). backup-worker does NOT touch this KV —
   * it validates our JWT and reads `sub = stableId` from claims directly.
   */
  readonly IDENTITY_MAP: KVNamespace;
  /**
   * HS256 secret used to sign our own JWT. Provisioned via
   * `wrangler secret put AUTH_JWT_SECRET`. Shared with backup-worker (same
   * secret) so it can validate the signature.
   */
  readonly AUTH_JWT_SECRET: string;
}
