// Tests для wire-format parser (T070).

import { describe, it, expect } from "vitest";
import {
  parsePushTriggerRequest,
  SCHEMA_VERSION,
  isValidTargetScope,
} from "../src/contract/wire-format.js";

describe("SCHEMA_VERSION", () => {
  it("matches Kotlin WireFormatVersion (T402 invariant)", () => {
    expect(SCHEMA_VERSION).toBe("1.0");
  });
});

describe("parsePushTriggerRequest", () => {
  const valid = {
    schemaVersion: "1.0", minReaderVersion: "1.0", minWriterVersion: "1.0",
    eventType: "config-updated",
    targetScope: "own-and-grants",
    ownerUid: "owner-1",
    payload: { configName: "main" },
  };

  it("accepts valid body", () => {
    expect(parsePushTriggerRequest(valid)).not.toBeNull();
  });

  it("rejects schemaVersion != 1", () => {
    expect(parsePushTriggerRequest({ ...valid, schemaVersion: 2 })).toBeNull();
  });

  it("rejects empty eventType", () => {
    expect(parsePushTriggerRequest({ ...valid, eventType: "" })).toBeNull();
  });

  it("rejects unknown targetScope", () => {
    expect(parsePushTriggerRequest({ ...valid, targetScope: "future" })).toBeNull();
  });

  it("rejects empty ownerUid", () => {
    expect(parsePushTriggerRequest({ ...valid, ownerUid: "" })).toBeNull();
  });

  it("rejects payload with non-string values (FCM constraint)", () => {
    expect(
      parsePushTriggerRequest({ ...valid, payload: { count: 42 } }),
    ).toBeNull();
  });

  it("rejects null body", () => {
    expect(parsePushTriggerRequest(null)).toBeNull();
  });
});

describe("isValidTargetScope", () => {
  it("recognises supported scopes", () => {
    expect(isValidTargetScope("own-devices")).toBe(true);
    expect(isValidTargetScope("own-and-grants")).toBe(true);
  });

  it("rejects future / unknown scopes", () => {
    expect(isValidTargetScope("specific-uid")).toBe(false);
    expect(isValidTargetScope("")).toBe(false);
  });
});
