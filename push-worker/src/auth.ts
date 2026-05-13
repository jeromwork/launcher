// Firebase ID-token verification (FR-021, research.md §JWT verification).
//
// Verifies that the `Authorization: Bearer <token>` header carries a valid
// Firebase Auth ID-token issued for our project. On success returns the
// authenticated `uid`; otherwise throws [AuthError] which the route handler
// translates to a 401 response.
//
// JWKS caching: jose's `createRemoteJWKSet` returns a function that
// internally caches the public-key set with HTTP cache-control respect
// (Google's JWKS endpoint sends `max-age=21600` ≈ 6h). We hold the JWKS
// fn in module scope so the cache survives between requests on the same
// Cloudflare isolate.

import { createLocalJWKSet, createRemoteJWKSet, jwtVerify, type JWK, type JWTPayload } from "jose";

const JWKS_URL = new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");

// Pluggable JWKS source. Production wires `createRemoteJWKSet(JWKS_URL)`;
// tests inject a `createLocalJWKSet` over a fixture-generated public key
// (see _setLocalJwksForTests). Both shapes implement the same JOSE
// verify-fn signature.
type JwksFn = ReturnType<typeof createRemoteJWKSet>;

let jwks: JwksFn | null = null;

function getJwks(): JwksFn {
  if (jwks) return jwks;
  jwks = createRemoteJWKSet(JWKS_URL);
  return jwks;
}

export class AuthError extends Error {
  constructor(public readonly reason: string) {
    super(`auth failed: ${reason}`);
    this.name = "AuthError";
  }
}

export interface VerifiedToken {
  readonly uid: string;
  readonly claims: JWTPayload;
}

/**
 * Verify a Firebase ID-token against Google's JWKS.
 *
 *  - `iss` must be `https://securetoken.google.com/{projectId}`
 *  - `aud` must equal `projectId`
 *  - `exp` must be in the future (jose enforces by default)
 *  - `sub` is the Firebase Auth UID
 */
export async function verifyFirebaseIdToken(
  authorizationHeader: string | null | undefined,
  firebaseProjectId: string,
): Promise<VerifiedToken> {
  if (!authorizationHeader) {
    throw new AuthError("missing Authorization header");
  }
  const match = /^Bearer\s+(.+)$/i.exec(authorizationHeader);
  if (!match) {
    throw new AuthError("Authorization header is not a Bearer token");
  }
  const token = match[1]!.trim();

  let result;
  try {
    result = await jwtVerify(token, getJwks(), {
      issuer: `https://securetoken.google.com/${firebaseProjectId}`,
      audience: firebaseProjectId,
    });
  } catch (e) {
    throw new AuthError(`jwtVerify rejected: ${(e as Error).message}`);
  }

  const uid = result.payload.sub;
  if (typeof uid !== "string" || uid.length === 0) {
    throw new AuthError("token has no sub claim");
  }
  return { uid, claims: result.payload };
}

// Test hook — vitest mocks the JWKS fn; we reset between tests so the cache
// doesn't carry mock state from previous test.
export function _resetJwksForTests(): void {
  jwks = null;
}

/** Test seam: replace the remote JWKS source with a fixture-keyed local one.
 *  Production code never calls this; tests use it to bypass the
 *  `createRemoteJWKSet` fetch path (which is awkward to stub reliably
 *  because jose caches the fn at module scope). */
export function _setLocalJwksForTests(keys: { keys: JWK[] }): void {
  // `createLocalJWKSet` returns the same callable as `createRemoteJWKSet`
  // for verification purposes — minus a few remote-only diagnostic
  // properties (coolingDown/fresh/jwks/reload). The cast is safe because
  // jwtVerify only invokes the function, never the extra members.
  jwks = createLocalJWKSet(keys) as unknown as JwksFn;
}
