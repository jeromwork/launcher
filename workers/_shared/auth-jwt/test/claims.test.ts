// T065 (claims unit tests) — pure validation rules без сетевого вызова или
// crypto. Faster + isolated tests для each VerificationError variant.
//
// Full integration test (signature + JWKS) — jwks-verifier.test.ts.

import { describe, it, expect } from "vitest";
import {
  validateFirebaseClaims,
  validateFirebaseHeader,
} from "../src/claims.js";

const PROJECT_ID = "launcher-old-dev";
const NOW = 1_750_000_000;

const validPayload = {
  iss: `https://securetoken.google.com/${PROJECT_ID}`,
  aud: PROJECT_ID,
  sub: "TestUid1234567890123456789AB",
  iat: NOW - 60,
  exp: NOW + 3600,
  email: "user@example.com",
  email_verified: true,
};

describe("validateFirebaseHeader", () => {
  it("returns kid on valid RS256 header", () => {
    const result = validateFirebaseHeader({ alg: "RS256", kid: "abc123" });
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.kid).toBe("abc123");
  });

  it("rejects unsupported algorithm", () => {
    const result = validateFirebaseHeader({ alg: "HS256", kid: "abc" });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("unsupported-algorithm");
  });

  it("rejects missing kid", () => {
    const result = validateFirebaseHeader({ alg: "RS256" });
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("malformed-header");
  });
});

describe("validateFirebaseClaims", () => {
  const ctx = { projectId: PROJECT_ID, now: () => NOW };

  it("accepts valid payload", () => {
    const result = validateFirebaseClaims(validPayload, ctx);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.claims.uid).toBe("TestUid1234567890123456789AB");
      expect(result.claims.email).toBe("user@example.com");
      expect(result.claims.emailVerified).toBe(true);
    }
  });

  it("rejects wrong issuer", () => {
    const result = validateFirebaseClaims(
      { ...validPayload, iss: "https://attacker.example/" },
      ctx,
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("wrong-issuer");
  });

  it("rejects wrong audience", () => {
    const result = validateFirebaseClaims(
      { ...validPayload, aud: "different-project" },
      ctx,
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("wrong-audience");
  });

  it("rejects expired token", () => {
    const result = validateFirebaseClaims(
      { ...validPayload, exp: NOW - 1 },
      ctx,
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("expired");
  });

  it("rejects missing sub", () => {
    const { sub: _unused, ...withoutSub } = validPayload;
    const result = validateFirebaseClaims(withoutSub, ctx);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("malformed-payload");
  });

  it("rejects empty sub", () => {
    const result = validateFirebaseClaims({ ...validPayload, sub: "" }, ctx);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("malformed-payload");
  });

  it("rejects iat too far in future (>60s clock skew)", () => {
    const result = validateFirebaseClaims(
      { ...validPayload, iat: NOW + 120 },
      ctx,
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("malformed-payload");
  });

  it("rejects non-numeric iat/exp", () => {
    const result = validateFirebaseClaims(
      { ...validPayload, iat: "not-a-number" as unknown as number },
      ctx,
    );
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toBe("malformed-payload");
  });

  it("preserves emailVerified=false correctly", () => {
    const result = validateFirebaseClaims(
      { ...validPayload, email_verified: false },
      ctx,
    );
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.claims.emailVerified).toBe(false);
  });

  it("omits email when absent", () => {
    const { email: _e, email_verified: _ev, ...withoutEmail } = validPayload;
    const result = validateFirebaseClaims(withoutEmail, ctx);
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.claims.email).toBeUndefined();
      expect(result.claims.emailVerified).toBeUndefined();
    }
  });
});
