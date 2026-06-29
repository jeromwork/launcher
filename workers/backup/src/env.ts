// T653-scaffold — Worker environment bindings.
//
// Wired via wrangler.toml `[vars]`, `[[r2_buckets]]`, `[[kv_namespaces]]`.

export interface Env {
  readonly FIREBASE_PROJECT_ID: string;
  readonly MAX_SUPPORTED_SCHEMA_VERSION: string;

  /** R2 bucket holding `backup/{stableId}/v1.json` objects. */
  readonly RECOVERY_BLOBS: R2Bucket;

  /**
   * JWKS cache namespace — shared with push worker when bound. Optional in test
   * harness (tests use {@link InMemoryJwksKv} adapter from auth-jwt).
   */
  readonly JWKS_CACHE?: KVNamespace;
}
