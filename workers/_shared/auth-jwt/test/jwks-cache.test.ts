// T061 (acceptance: «unit test mocks fetch, asserts TTL parsing»).

import { describe, it, expect } from "vitest";
import { parseCacheControlMaxAge } from "../src/jwks-cache.js";

describe("parseCacheControlMaxAge", () => {
  it("parses standard max-age directive", () => {
    expect(parseCacheControlMaxAge("public, max-age=3600")).toBe(3600);
  });

  it("clamps to MIN when null", () => {
    expect(parseCacheControlMaxAge(null)).toBe(60);
  });

  it("clamps to MIN when max-age=0 (Google misconfig defence)", () => {
    expect(parseCacheControlMaxAge("max-age=0")).toBe(60);
  });

  it("clamps to MAX when oversized", () => {
    expect(parseCacheControlMaxAge("max-age=99999999")).toBe(86_400);
  });

  it("ignores non-max-age directives", () => {
    expect(parseCacheControlMaxAge("no-cache")).toBe(60);
  });

  it("handles whitespace", () => {
    expect(parseCacheControlMaxAge("max-age = 1800")).toBe(1800);
  });

  it("is case-insensitive on directive", () => {
    expect(parseCacheControlMaxAge("Max-Age=300")).toBe(300);
  });
});
