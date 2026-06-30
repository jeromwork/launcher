import { describe, expect, it } from "vitest";
import { handle, type AuthVerifier } from "../src/index.js";
import { InMemoryIdempotencyCache } from "../src/idempotency.js";
import { InMemoryRateLimiter } from "../src/ratelimit.js";
import { fakeAuth, makeEnv, sampleBlobJson } from "./fixtures.js";

describe("T657 auth (JWT signature + claims)", () => {
  it("missing Authorization → 401 INVALID_TOKEN", async () => {
    const res = await handle(
      new Request("https://example.com/backup", { method: "POST" }),
      makeEnv(),
      { verify: fakeAuth("uid-1") },
    );
    expect(res.status).toBe(401);
    const body = await res.json();
    expect((body as Record<string, unknown>)["error"]).toBe("INVALID_TOKEN");
  });

  it("invalid signature → 401 INVALID_TOKEN", async () => {
    const res = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer INVALID", "Idempotency-Key": "k1" },
        body: sampleBlobJson("uid-1"),
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1") },
    );
    expect(res.status).toBe(401);
  });

  it("POST routes blob by authed stableId, ignores body.stableId for routing (T681-FOLLOWUP)", async () => {
    // Pre-2026-06-30 the worker refused this with 403 STABLE_ID_MISMATCH. Now
    // the soft-fallback path (claims.stableId ?? claims.uid) makes the
    // routing key authed.stableId regardless of body.stableId. Blob is
    // accepted and stored under authed.stableId.
    const env = makeEnv();
    const verify = fakeAuth("authed-stable-id");
    const res = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body: sampleBlobJson("different-body-stable-id"),
      }),
      env,
      { verify },
    );
    expect(res.status).toBe(200);
    // Blob lands under authed.stableId, not body's stableId.
    expect(env.RECOVERY_BLOBS.objects.get("backup/authed-stable-id/v1.json")).toBe(
      sampleBlobJson("different-body-stable-id"),
    );
    expect(env.RECOVERY_BLOBS.objects.get("backup/different-body-stable-id/v1.json")).toBeUndefined();
  });

  it("subject-ownership mismatch on GET → 403", async () => {
    const res = await handle(
      new Request("https://example.com/backup/uid-victim", {
        method: "GET",
        headers: { Authorization: "Bearer X" },
      }),
      makeEnv(),
      { verify: fakeAuth("uid-attacker") },
    );
    expect(res.status).toBe(403);
  });

  it("subject-ownership match on GET when blob missing → 404", async () => {
    const res = await handle(
      new Request("https://example.com/backup/uid-1", {
        method: "GET",
        headers: { Authorization: "Bearer X" },
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1") },
    );
    expect(res.status).toBe(404);
  });

  it("uses fresh idempotency cache + rate limiter when supplied", async () => {
    // Smoke that injection plumbing works end-to-end (this is the test surface
    // every other test relies on).
    const idem = new InMemoryIdempotencyCache();
    const rl = new InMemoryRateLimiter();
    const res = await handle(
      new Request("https://example.com/backup/uid-1", {
        method: "GET",
        headers: { Authorization: "Bearer X" },
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1"), idempotencyCache: idem, rateLimiter: rl },
    );
    expect(res.status).toBe(404);
  });
});
