// Tests для InMemoryRecipientResolver (T073 acceptance fake).

import { describe, it, expect } from "vitest";
import { InMemoryRecipientResolver } from "../src/recipient/resolver.js";

describe("InMemoryRecipientResolver", () => {
  it("own-devices returns only ownerUid devices", async () => {
    const resolver = new InMemoryRecipientResolver(
      {
        owner: [{ uid: "owner", deviceId: "d1", fcmToken: "tok-owner-1" }],
        holder: [{ uid: "holder", deviceId: "d2", fcmToken: "tok-holder-1" }],
      },
      { owner: ["holder"] },
    );
    const devices = await resolver.resolveRecipients("owner", "own-devices");
    expect(devices.map((d) => d.fcmToken)).toEqual(["tok-owner-1"]);
  });

  it("own-and-grants includes grant-holder devices", async () => {
    const resolver = new InMemoryRecipientResolver(
      {
        owner: [{ uid: "owner", deviceId: "d1", fcmToken: "tok-owner-1" }],
        holder: [{ uid: "holder", deviceId: "d2", fcmToken: "tok-holder-1" }],
      },
      { owner: ["holder"] },
    );
    const devices = await resolver.resolveRecipients(
      "owner",
      "own-and-grants",
    );
    expect(devices.map((d) => d.fcmToken).sort()).toEqual([
      "tok-holder-1",
      "tok-owner-1",
    ]);
  });

  it("own-and-grants with no grants returns own only", async () => {
    const resolver = new InMemoryRecipientResolver({
      owner: [{ uid: "owner", deviceId: "d1", fcmToken: "tok-owner-1" }],
    });
    const devices = await resolver.resolveRecipients(
      "owner",
      "own-and-grants",
    );
    expect(devices.map((d) => d.fcmToken)).toEqual(["tok-owner-1"]);
  });
});
