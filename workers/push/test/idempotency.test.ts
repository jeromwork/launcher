// Tests для InMemoryIdempotencyStore (T075 acceptance).

import { describe, it, expect } from "vitest";
import { InMemoryIdempotencyStore } from "../src/dedupe/idempotency.js";

describe("InMemoryIdempotencyStore", () => {
  it("returns null on miss", async () => {
    const store = new InMemoryIdempotencyStore();
    expect(await store.get("uid", "key")).toBeNull();
  });

  it("returns cached value on hit", async () => {
    const store = new InMemoryIdempotencyStore();
    await store.put("uid", "key", '{"ok":true,"recipientCount":1}');
    expect(await store.get("uid", "key")).toBe('{"ok":true,"recipientCount":1}');
  });

  it("isolates by uid namespace (cross-uid collision defence)", async () => {
    const store = new InMemoryIdempotencyStore();
    await store.put("uidA", "shared-key", "responseA");
    expect(await store.get("uidB", "shared-key")).toBeNull();
  });
});
