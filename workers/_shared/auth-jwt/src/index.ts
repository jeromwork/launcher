// T063 — Public API: verifyFirebaseIdToken orchestrating jose verify +
// jwks-cache + claims validation. Per spec 019 data-model.md §VerificationResult.
//
// Module mission per TODO-ARCH-018: standalone, reusable, separate from push.
// First consumer: workers/push/src/index.ts.

import {
  decodeProtectedHeader,
  importX509,
  jwtVerify,
  type KeyLike,
} from "jose";
import {
  forceRefreshJwks,
  getJwks,
  JwksFetchError,
} from "./jwks-cache.js";
import { validateFirebaseClaims, validateFirebaseHeader } from "./claims.js";
import type {
  Claims,
  JwksCacheKv,
  VerificationError,
  VerificationResult,
} from "./types.js";

export type { Claims, JwksCacheKv, VerificationError, VerificationResult };

export interface VerifyOptions {
  /** Injectable clock для tests (returns seconds since epoch). Default: Date.now. */
  readonly now?: () => number;
}

/**
 * Verifies Firebase ID-token (RS256 JWT). Sequence:
 *   1. Decode unverified header → extract `kid` + verify alg=RS256.
 *   2. Lookup public key (PEM) в JWKS cache by kid; force-refresh if miss.
 *   3. Verify signature via jose `jwtVerify` (handles exp/iat automatically).
 *   4. Validate Firebase-specific claims (iss/aud/sub) via [validateFirebaseClaims].
 *   5. Return [VerificationResult] discriminated union.
 *
 * Caller responsibility: pass `Bearer <token>` header → strip "Bearer " prefix
 * before invoking.
 *
 *  • [token] — raw JWT string (header.payload.signature).
 *  • [projectId] — Firebase project ID (e.g. "launcher-old-dev").
 *  • [kv] — JWKS cache backing storage (Cloudflare KVNamespace или test fake).
 *  • [options] — optional clock override.
 */
export async function verifyFirebaseIdToken(
  token: string,
  projectId: string,
  kv: JwksCacheKv,
  options: VerifyOptions = {},
): Promise<VerificationResult> {
  const now = options.now ?? (() => Math.floor(Date.now() / 1000));

  let headerRaw: Record<string, unknown>;
  try {
    headerRaw = decodeProtectedHeader(token) as Record<string, unknown>;
  } catch {
    return { ok: false, error: "malformed-header" };
  }

  const headerCheck = validateFirebaseHeader(headerRaw);
  if (!headerCheck.ok) return { ok: false, error: headerCheck.error };
  const { kid } = headerCheck;

  // Lookup key с force-refresh fallback (handle Google JWKS rotation).
  let publicKey: KeyLike;
  try {
    publicKey = await resolveKey(kid, kv);
  } catch (e) {
    if (e instanceof KidNotFoundError) {
      return { ok: false, error: "kid-not-found" };
    }
    if (e instanceof JwksFetchError) {
      return { ok: false, error: "jwks-fetch-failed" };
    }
    throw e;
  }

  // Signature verification via jose. exp/iat checked by library default.
  let payloadRaw: Record<string, unknown>;
  try {
    const result = await jwtVerify(token, publicKey, {
      algorithms: ["RS256"],
      clockTolerance: 60, // seconds — match validateFirebaseClaims tolerance.
      currentDate: new Date(now() * 1000),
    });
    payloadRaw = result.payload as Record<string, unknown>;
  } catch (e) {
    return { ok: false, error: mapJoseError(e) };
  }

  const claimsCheck = validateFirebaseClaims(payloadRaw, { projectId, now });
  if (!claimsCheck.ok) return { ok: false, error: claimsCheck.error };

  return { ok: true, claims: claimsCheck.claims };
}

async function resolveKey(kid: string, kv: JwksCacheKv): Promise<KeyLike> {
  const cached = await getJwks(kv);
  const cert = cached.keys[kid];
  if (cert) {
    return await importX509(cert, "RS256");
  }
  // Cache miss → force refresh (key may have rotated since last fetch).
  const fresh = await forceRefreshJwks(kv);
  const freshCert = fresh.keys[kid];
  if (!freshCert) throw new KidNotFoundError(kid);
  return await importX509(freshCert, "RS256");
}

function mapJoseError(e: unknown): VerificationError {
  if (e instanceof Error) {
    const code = (e as { code?: string }).code;
    switch (code) {
      case "ERR_JWT_EXPIRED":
        return "expired";
      case "ERR_JWS_SIGNATURE_VERIFICATION_FAILED":
        return "invalid-signature";
      case "ERR_JWS_INVALID":
      case "ERR_JWT_INVALID":
        return "malformed-payload";
      default:
        // Conservative default — treat unknown jose errors as signature
        // failure (NOT silent success).
        return "invalid-signature";
    }
  }
  return "invalid-signature";
}

class KidNotFoundError extends Error {
  constructor(kid: string) {
    super(`kid not found in JWKS: ${kid}`);
    this.name = "KidNotFoundError";
  }
}
