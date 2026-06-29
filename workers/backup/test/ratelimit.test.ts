import { describe, expect, it } from "vitest";
import { handle } from "../src/index.js";
import { InMemoryIdempotencyCache } from "../src/idempotency.js";
import { InMemoryRateLimiter } from "../src/ratelimit.js";
import { fakeAuth, makeEnv, sampleBlobJson } from "./fixtures.js";

describe("T660 rate-limit (POST 10/5min, GET 5/5min)", () => {
  it("11th POST in window → 429 RATE_LIMITED", async () => {
    let now = 1_000_000;
    const rl = new InMemoryRateLimiter(() => now);
    const verify = fakeAuth("uid-1");
    const idem = new InMemoryIdempotencyCache();

    for (let i = 0; i < 10; i++) {
      const res = await handle(
        new Request("https://example.com/backup", {
          method: "POST",
          headers: { Authorization: "Bearer X", "Idempotency-Key": `k${i}` },
          body: sampleBlobJson("uid-1"),
        }),
        makeEnv(),
        { verify, idempotencyCache: idem, rateLimiter: rl },
      );
      expect(res.status).toBe(200);
      now += 1; // 1ms apart
    }
    const blocked = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k-overflow" },
        body: sampleBlobJson("uid-1"),
      }),
      makeEnv(),
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("Retry-After")).not.toBeNull();
  });

  it("counter resets after 5-minute window", async () => {
    let now = 1_000_000;
    const rl = new InMemoryRateLimiter(() => now);
    const verify = fakeAuth("uid-1");
    const idem = new InMemoryIdempotencyCache();
    const env = makeEnv();

    for (let i = 0; i < 10; i++) {
      await handle(
        new Request("https://example.com/backup", {
          method: "POST",
          headers: { Authorization: "Bearer X", "Idempotency-Key": `k${i}` },
          body: sampleBlobJson("uid-1"),
        }),
        env,
        { verify, idempotencyCache: idem, rateLimiter: rl },
      );
      now += 1;
    }
    now += 6 * 60 * 1_000; // window + 1 minute
    const after = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k-after" },
        body: sampleBlobJson("uid-1"),
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(after.status).toBe(200);
  });
});
