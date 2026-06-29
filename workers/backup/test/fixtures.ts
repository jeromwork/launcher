// Shared test fixtures.

import type { Env } from "../src/env.js";
import type { AuthVerifier } from "../src/index.js";

/**
 * Minimal in-memory KV mock — implements only the subset of KVNamespace the
 * Worker actually calls (`get`, `put`, `delete`).
 */
export class InMemoryKV {
  readonly objects = new Map<string, string>();

  async get(key: string): Promise<string | null> {
    return this.objects.get(key) ?? null;
  }

  async put(key: string, value: string): Promise<void> {
    this.objects.set(key, value);
  }

  async delete(key: string): Promise<void> {
    this.objects.delete(key);
  }
}

export interface TestEnv extends Env {
  RECOVERY_BLOBS: InMemoryKV & KVNamespace;
}

export function makeEnv(overrides: Partial<TestEnv> = {}): TestEnv {
  const kv = new InMemoryKV() as InMemoryKV & KVNamespace;
  return {
    FIREBASE_PROJECT_ID: "test-project",
    MAX_SUPPORTED_SCHEMA_VERSION: "1",
    RECOVERY_BLOBS: kv,
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
