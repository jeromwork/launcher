// Shared test fixtures.

import type { Env } from "../src/env.js";
import type { AuthVerifier } from "../src/index.js";

/**
 * Minimal in-memory R2 mock — implements only the subset of R2Bucket the
 * Worker actually calls (`get`, `put`, `delete`).
 */
export class InMemoryR2 {
  readonly objects = new Map<string, string>();

  async get(key: string): Promise<R2ObjectBody | null> {
    const value = this.objects.get(key);
    if (value === undefined) return null;
    return {
      text: async () => value,
    } as unknown as R2ObjectBody;
  }

  async put(key: string, value: string | ReadableStream | ArrayBuffer): Promise<void> {
    if (typeof value !== "string") {
      throw new Error("Test R2 mock supports string puts only");
    }
    this.objects.set(key, value);
  }

  async delete(key: string): Promise<void> {
    this.objects.delete(key);
  }
}

export interface TestEnv extends Env {
  RECOVERY_BLOBS: InMemoryR2 & R2Bucket;
}

export function makeEnv(overrides: Partial<TestEnv> = {}): TestEnv {
  const r2 = new InMemoryR2() as InMemoryR2 & R2Bucket;
  return {
    FIREBASE_PROJECT_ID: "test-project",
    MAX_SUPPORTED_SCHEMA_VERSION: "1",
    RECOVERY_BLOBS: r2,
    ...overrides,
  };
}

/**
 * Auth verifier stub that yields a fixed Claims object regardless of token.
 * Tests pass the resolved stableId so handlers can do subject-ownership checks.
 */
export function fakeAuth(
  stableIdOrClaims: string | { stableId?: string; uid: string },
): AuthVerifier {
  return async (token: string) => {
    if (token === "INVALID") return { ok: false, error: "invalid-signature" };
    if (typeof stableIdOrClaims === "string") {
      return {
        ok: true,
        claims: { uid: stableIdOrClaims, iat: 1, exp: 9999999999 },
      };
    }
    return {
      ok: true,
      claims: {
        uid: stableIdOrClaims.uid,
        iat: 1,
        exp: 9999999999,
      } as unknown as { uid: string; iat: number; exp: number },
    };
  };
}

export function sampleBlobJson(stableId: string): string {
  return JSON.stringify({
    schemaVersion: 1,
    stableId,
    salt: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    kdfParams: { algorithm: "Argon2id", iterations: 3, memoryKb: 65536, parallelism: 1 },
    ciphertext: "ZmFrZS1jaXBoZXItYnl0ZXMtZm9yLXRlc3RpbmctcHVycG9zZXM=",
    nonce: "ZmFrZS1ub25jZS1mb3ItdGVzdHM=",
    createdAt: "2026-06-29T00:00:00Z",
  });
}
