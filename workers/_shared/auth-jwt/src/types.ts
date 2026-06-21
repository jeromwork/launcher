// T060 — Public types для auth-jwt module. Per spec 019 data-model.md
// §VerificationResult.
//
// Module mission (per TODO-ARCH-018): standalone JWT verification — separate
// concern от push. Reusable across multiple Workers. First consumer: workers/push.

/**
 * Validated claims извлечённые из Firebase ID-token (RS256 JWT).
 *
 *  • [uid] — mapped from `sub` claim (Firebase convention: `sub` === Firebase UID).
 *  • [iat] — issued-at, seconds since epoch.
 *  • [exp] — expiration, seconds since epoch. Validator уже проверил `exp > now`.
 */
export interface Claims {
  readonly uid: string;
  readonly email?: string;
  readonly emailVerified?: boolean;
  readonly iat: number;
  readonly exp: number;
}

/**
 * Result wrapper для [verifyFirebaseIdToken]. Discriminated union — caller does
 * `if (result.ok) { ... } else { handle(result.error) }`.
 */
export type VerificationResult =
  | { readonly ok: true; readonly claims: Claims }
  | { readonly ok: false; readonly error: VerificationError };

/**
 * Failure variants. Each covered by dedicated test fixture (T064-T065).
 *
 * Adding new variant — additive change (caller switch should have default branch).
 */
export type VerificationError =
  | "invalid-signature"
  | "expired"
  | "wrong-issuer"
  | "wrong-audience"
  | "malformed-header"
  | "malformed-payload"
  | "unsupported-algorithm"
  | "kid-not-found"
  | "jwks-fetch-failed";

/**
 * Subset of Cloudflare KVNamespace API used by jwks-cache. Allows test
 * substitution с in-memory fake без полной KVNamespace mock.
 */
export interface JwksCacheKv {
  get(key: string): Promise<string | null>;
  put(
    key: string,
    value: string,
    options?: { expirationTtl?: number },
  ): Promise<void>;
}
