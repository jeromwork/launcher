// Typed Cloudflare Worker `env` binding (FR-022, FR-067).
// Wrangler injects:
//   FIREBASE_PROJECT_ID — vars in wrangler.toml [vars] (public)
//   FIREBASE_SA_JSON    — secret via `wrangler secret put FIREBASE_SA_JSON`
//
// Never log SA_JSON contents. The full file is a private key — must stay
// inside the Worker isolate.

export interface Env {
  readonly FIREBASE_PROJECT_ID: string;
  readonly FIREBASE_SA_JSON: string;
}

export interface ServiceAccount {
  readonly client_email: string;
  readonly private_key: string;
  readonly project_id: string;
  readonly token_uri: string;
}

// Module-scope cache so repeated requests on the same isolate parse the
// SA JSON exactly once (Cloudflare Workers keep module state alive between
// requests on the same instance — see C12 in plan.md).
let cachedSa: ServiceAccount | null = null;

export function parseServiceAccount(env: Env): ServiceAccount {
  if (cachedSa) return cachedSa;
  const sa = JSON.parse(env.FIREBASE_SA_JSON) as Partial<ServiceAccount>;
  if (!sa.client_email || !sa.private_key || !sa.project_id) {
    throw new Error("FIREBASE_SA_JSON missing required fields (client_email/private_key/project_id)");
  }
  const resolved: ServiceAccount = {
    client_email: sa.client_email,
    private_key: sa.private_key,
    project_id: sa.project_id,
    token_uri: sa.token_uri ?? "https://oauth2.googleapis.com/token",
  };
  cachedSa = resolved;
  return resolved;
}

// Test hook — vitest clears between tests so cached state doesn't leak.
export function _resetServiceAccountCacheForTests(): void {
  cachedSa = null;
}
