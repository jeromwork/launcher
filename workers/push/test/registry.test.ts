// T083 — Structural validation tests для EVENT_TYPES registry. Per spec 019
// contracts/event-type-registry.md §Validation tests.

import { describe, it, expect } from "vitest";
import { EVENT_TYPES } from "../src/registry/event-types.js";

describe("EVENT_TYPES registry", () => {
  it("contains config-updated entry (T100)", () => {
    expect(EVENT_TYPES["config-updated"]).toBeDefined();
  });

  it("every entry has required fields", () => {
    for (const [name, entry] of Object.entries(EVENT_TYPES)) {
      expect(typeof entry.authorise, `${name}.authorise`).toBe("function");
      expect(typeof entry.collapseKey, `${name}.collapseKey`).toBe("function");
      expect(typeof entry.rateLimit.perUid, `${name}.rateLimit.perUid`).toBe(
        "number",
      );
      expect(
        typeof entry.rateLimit.windowSeconds,
        `${name}.rateLimit.windowSeconds`,
      ).toBe("number");
      expect(entry.rateLimit.perUid).toBeGreaterThan(0);
      expect(entry.rateLimit.windowSeconds).toBeGreaterThan(0);
      expect(["normal", "high"]).toContain(entry.priority);
      if (entry.ttlSeconds !== undefined) {
        expect(entry.ttlSeconds).toBeGreaterThan(0);
      }
    }
  });

  it("collapseKey is pure deterministic function", () => {
    const entry = EVENT_TYPES["config-updated"]!;
    const req = {
      schemaVersion: "1.0", minReaderVersion: "1.0", minWriterVersion: "1.0",
      eventType: "config-updated",
      targetScope: "own-and-grants" as const,
      ownerUid: "owner-1",
      payload: { configName: "main" },
    };
    const k1 = entry.collapseKey(req);
    const k2 = entry.collapseKey(req);
    expect(k1).toBe(k2);
    expect(k1).toBe("config-updated:owner-1:main");
  });

  it("collapseKey handles missing field gracefully", () => {
    const entry = EVENT_TYPES["config-updated"]!;
    const req = {
      schemaVersion: "1.0", minReaderVersion: "1.0", minWriterVersion: "1.0",
      eventType: "config-updated",
      targetScope: "own-devices" as const,
      ownerUid: "owner-1",
      payload: {},
    };
    expect(entry.collapseKey(req)).toBe("config-updated:owner-1:default");
  });
});
