// T062 — Claims validation rules для Firebase ID-token. Per spec 019 FR-002,
// Q4 resolution.
//
// Firebase ID-token (RS256 JWT) header + payload validation rules:
//   • header.alg === "RS256" (Firebase issues RS256 only).
//   • header.kid present (used для JWKS lookup).
//   • payload.iss === `https://securetoken.google.com/{projectId}`.
//   • payload.aud === `{projectId}`.
//   • payload.exp > now.
//   • payload.iat <= now.
//   • payload.sub non-empty (Firebase UID).
//
// Note: jose library уже verifies exp/iat via `jwtVerify` defaults; this
// module adds Firebase-specific iss/aud/sub validation.

import type { Claims, VerificationError } from "./types.js";

export interface ClaimsValidationContext {
  readonly projectId: string;
  /** Inject-able clock для tests (returns seconds since epoch). */
  readonly now: () => number;
}

/**
 * Validates payload claims (post-signature-verification). Returns null on
 * success, [VerificationError] on failure.
 *
 * Caller (index.ts) ловит jose's own errors для invalid-signature / expired
 * cases.
 */
export function validateFirebaseClaims(
  payload: Record<string, unknown>,
  ctx: ClaimsValidationContext,
): { ok: true; claims: Claims } | { ok: false; error: VerificationError } {
  const expectedIss = `https://securetoken.google.com/${ctx.projectId}`;
  const iss = payload["iss"];
  if (iss !== expectedIss) {
    return { ok: false, error: "wrong-issuer" };
  }

  const aud = payload["aud"];
  if (aud !== ctx.projectId) {
    return { ok: false, error: "wrong-audience" };
  }

  const sub = payload["sub"];
  if (typeof sub !== "string" || sub.length === 0) {
    return { ok: false, error: "malformed-payload" };
  }

  const iat = payload["iat"];
  const exp = payload["exp"];
  if (typeof iat !== "number" || typeof exp !== "number") {
    return { ok: false, error: "malformed-payload" };
  }
  const now = ctx.now();
  if (exp <= now) {
    return { ok: false, error: "expired" };
  }
  // iat may be slightly in future due to clock skew — allow 60s tolerance.
  if (iat > now + 60) {
    return { ok: false, error: "malformed-payload" };
  }

  const email = payload["email"];
  const emailVerified = payload["email_verified"];

  const claims: Claims = {
    uid: sub,
    iat,
    exp,
    email: typeof email === "string" ? email : undefined,
    emailVerified:
      typeof emailVerified === "boolean" ? emailVerified : undefined,
  };

  return { ok: true, claims };
}

/**
 * Validates JWT header. RS256 only, kid required.
 */
export function validateFirebaseHeader(
  header: Record<string, unknown>,
): { ok: true; kid: string } | { ok: false; error: VerificationError } {
  const alg = header["alg"];
  if (alg !== "RS256") {
    return { ok: false, error: "unsupported-algorithm" };
  }
  const kid = header["kid"];
  if (typeof kid !== "string" || kid.length === 0) {
    return { ok: false, error: "malformed-header" };
  }
  return { ok: true, kid };
}
