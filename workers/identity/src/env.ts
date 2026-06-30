// Worker environment bindings.

export interface Env {
  readonly FIREBASE_PROJECT_ID: string;
  /**
   * Service-account JSON (single string with the full JSON document).
   * Owner provisions via `wrangler secret put FIREBASE_SA_JSON`.
   */
  readonly FIREBASE_SA_JSON?: string;
  readonly JWKS_CACHE?: KVNamespace;
}
