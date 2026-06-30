import { describe, expect, it } from "vitest";
import { handle } from "../src/index.js";
import { InMemoryIdempotencyCache } from "../src/idempotency.js";
import { InMemoryRateLimiter } from "../src/ratelimit.js";
import { fakeAuth, makeEnv, sampleBlobJson } from "./fixtures.js";

describe("T658 idempotency (POST /backup)", () => {
  it("same key + same body → cached 200 (no second write)", async () => {
    const env = makeEnv();
    const idem = new InMemoryIdempotencyCache();
    const rl = new InMemoryRateLimiter();
    const verify = fakeAuth("uid-1");
    const body = sampleBlobJson("uid-1");

    const first = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body,
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(first.status).toBe(200);
    const stored = env.RECOVERY_BLOBS.objects.get("backup/uid-1/v1.json");
    expect(stored).toBe(body);

    // Mutate R2 directly so we can detect that the 2nd call did NOT re-write.
    env.RECOVERY_BLOBS.objects.set("backup/uid-1/v1.json", "MUTATED");

    const second = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body,
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(second.status).toBe(200);
    expect(env.RECOVERY_BLOBS.objects.get("backup/uid-1/v1.json")).toBe("MUTATED");
  });

  it("same key + different body → 409 IDEMPOTENCY_CONFLICT", async () => {
    const env = makeEnv();
    const idem = new InMemoryIdempotencyCache();
    const rl = new InMemoryRateLimiter();
    const verify = fakeAuth("uid-1");

    const first = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body: sampleBlobJson("uid-1"),
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(first.status).toBe(200);

    // Tweak ciphertext to produce a structurally identical but byte-different body.
    const mutated = JSON.stringify({
      ...JSON.parse(sampleBlobJson("uid-1")),
      ciphertext: "ZGlmZmVyZW50LWNpcGhlcmFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==",
    });
    const conflict = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body: mutated,
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(conflict.status).toBe(409);
    const json = (await conflict.json()) as Record<string, unknown>;
    expect(json["error"]).toBe("IDEMPOTENCY_CONFLICT");
  });
});
