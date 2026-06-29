import { describe, expect, it } from "vitest";
import { handle } from "../src/index.js";
import { InMemoryIdempotencyCache } from "../src/idempotency.js";
import { InMemoryRateLimiter } from "../src/ratelimit.js";
import { fakeAuth, makeEnv, sampleBlobJson } from "./fixtures.js";

describe("T659 POST → GET → DELETE round-trip via KV mock", () => {
  it("upload then fetch returns the stored blob byte-equal", async () => {
    const env = makeEnv();
    const body = sampleBlobJson("uid-1");
    const idem = new InMemoryIdempotencyCache();
    const rl = new InMemoryRateLimiter();
    const verify = fakeAuth("uid-1");

    const uploadRes = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body,
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(uploadRes.status).toBe(200);

    const fetchRes = await handle(
      new Request("https://example.com/backup/uid-1", {
        method: "GET",
        headers: { Authorization: "Bearer X" },
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(fetchRes.status).toBe(200);
    const fetched = await fetchRes.text();
    expect(fetched).toBe(body);
  });

  it("DELETE removes the blob; subsequent GET returns 404", async () => {
    const env = makeEnv();
    const body = sampleBlobJson("uid-1");
    const idem = new InMemoryIdempotencyCache();
    const rl = new InMemoryRateLimiter();
    const verify = fakeAuth("uid-1");

    await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body,
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );

    const delRes = await handle(
      new Request("https://example.com/backup/uid-1", {
        method: "DELETE",
        headers: { Authorization: "Bearer X" },
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(delRes.status).toBe(204);

    const fetchRes = await handle(
      new Request("https://example.com/backup/uid-1", {
        method: "GET",
        headers: { Authorization: "Bearer X" },
      }),
      env,
      { verify, idempotencyCache: idem, rateLimiter: rl },
    );
    expect(fetchRes.status).toBe(404);
  });

  it("UNSUPPORTED_SCHEMA on schemaVersion=2", async () => {
    const body = JSON.stringify({
      schemaVersion: 2,
      stableId: "uid-1",
      salt: "AAA=",
      kdfParams: { algorithm: "Argon2id", iterations: 3, memoryKb: 65536, parallelism: 1 },
      ciphertext: "AAA=",
      nonce: "AAA=",
      createdAt: "2026-06-29T00:00:00Z",
    });
    const res = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body,
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1") },
    );
    expect(res.status).toBe(400);
    const json = (await res.json()) as Record<string, unknown>;
    expect(json["error"]).toBe("UNSUPPORTED_SCHEMA");
    expect(json["max"]).toBe(1);
  });

  it("malformed JSON → 400 MALFORMED_BODY", async () => {
    const res = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X", "Idempotency-Key": "k1" },
        body: "not-json",
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1") },
    );
    expect(res.status).toBe(400);
  });

  it("missing Idempotency-Key → 400 MISSING_IDEMPOTENCY_KEY", async () => {
    const res = await handle(
      new Request("https://example.com/backup", {
        method: "POST",
        headers: { Authorization: "Bearer X" },
        body: sampleBlobJson("uid-1"),
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1") },
    );
    expect(res.status).toBe(400);
    const json = (await res.json()) as Record<string, unknown>;
    expect(json["error"]).toBe("MISSING_IDEMPOTENCY_KEY");
  });
});
