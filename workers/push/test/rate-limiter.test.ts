// Tests для InMemoryRateLimiter (T076 acceptance).

import { describe, it, expect } from "vitest";
import { InMemoryRateLimiter } from "../src/ratelimit/rate-limiter.js";

describe("InMemoryRateLimiter", () => {
  it("allows under limit", async () => {
    const limiter = new InMemoryRateLimiter();
    for (let i = 0; i < 5; i++) {
      const result = await limiter.checkAndRecord("uid", "config-updated", 5, 60);
      expect(result.allowed).toBe(true);
    }
  });

  it("denies at limit (returns 429 hint)", async () => {
    const limiter = new InMemoryRateLimiter();
    for (let i = 0; i < 3; i++) {
      await limiter.checkAndRecord("uid", "config-updated", 3, 60);
    }
    const result = await limiter.checkAndRecord("uid", "config-updated", 3, 60);
    expect(result.allowed).toBe(false);
    expect(result.retryAfterSeconds).toBeGreaterThan(0);
  });

  it("isolates uids", async () => {
    const limiter = new InMemoryRateLimiter();
    for (let i = 0; i < 3; i++) {
      await limiter.checkAndRecord("uidA", "e", 3, 60);
    }
    const a = await limiter.checkAndRecord("uidA", "e", 3, 60);
    const b = await limiter.checkAndRecord("uidB", "e", 3, 60);
    expect(a.allowed).toBe(false);
    expect(b.allowed).toBe(true);
  });

  it("isolates event types", async () => {
    const limiter = new InMemoryRateLimiter();
    for (let i = 0; i < 3; i++) {
      await limiter.checkAndRecord("uid", "config-updated", 3, 60);
    }
    const blocked = await limiter.checkAndRecord("uid", "config-updated", 3, 60);
    const allowed = await limiter.checkAndRecord("uid", "sos-triggered", 3, 60);
    expect(blocked.allowed).toBe(false);
    expect(allowed.allowed).toBe(true);
  });
});
