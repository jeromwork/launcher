import { describe, expect, it } from "vitest";
import { handle, type AuthVerifier } from "../src/index.js";
import { InMemoryFirebaseAdmin } from "../src/firebase-admin.js";
import type { Env } from "../src/env.js";

function makeEnv(): Env {
  return { FIREBASE_PROJECT_ID: "test-project" };
}

function fakeAuth(uid: string): AuthVerifier {
  return async (token: string) => {
    if (token === "INVALID") return { ok: false, error: "invalid-signature" };
    return {
      ok: true,
      claims: { uid, iat: 1, exp: 9999999999 },
    };
  };
}

const FIXED_UUID_FOR_TESTS = "11111111-1111-4111-8111-111111111111";

describe("T664 init-claim — Q-M variant b idempotency", () => {
  it("first call generates a fresh stableId and binds it to uid", async () => {
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        headers: { Authorization: "Bearer X" },
        body: JSON.stringify({ uid: "uid-1" }),
      }),
      makeEnv(),
      {
        verify: fakeAuth("uid-1"),
        firebaseAdmin: admin,
        newUuid: () => FIXED_UUID_FOR_TESTS,
      },
    );
    expect(res.status).toBe(200);
    const json = (await res.json()) as Record<string, unknown>;
    expect(json["stableId"]).toBe(FIXED_UUID_FOR_TESTS);
    expect(await admin.readStableIdForUid("uid-1")).toBe(FIXED_UUID_FOR_TESTS);
  });

  it("second call returns the same stableId without re-binding", async () => {
    const admin = new InMemoryFirebaseAdmin();
    admin.seed("uid-1", "pre-existing-stable-id");
    let uuidCalls = 0;
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        headers: { Authorization: "Bearer X" },
        body: JSON.stringify({ uid: "uid-1" }),
      }),
      makeEnv(),
      {
        verify: fakeAuth("uid-1"),
        firebaseAdmin: admin,
        newUuid: () => {
          uuidCalls += 1;
          return FIXED_UUID_FOR_TESTS;
        },
      },
    );
    expect(res.status).toBe(200);
    const json = (await res.json()) as Record<string, unknown>;
    expect(json["stableId"]).toBe("pre-existing-stable-id");
    expect(uuidCalls).toBe(0); // no new UUID generated
  });

  it("worker derives Firebase uid from JWT, not from body (2026-06-30 wire change)", async () => {
    // Body says stableId=victim, JWT verifies as Firebase uid=attacker — worker
    // ignores body.uid entirely and binds `attacker -> victim` (treating the
    // body stableId as the client-supplied UUID). This is the new contract: the
    // verified JWT is the sole source of Firebase identity; body just tells
    // the worker which UUID F-4 already minted for it.
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        headers: { Authorization: "Bearer X" },
        body: JSON.stringify({ stableId: "victim-uuid" }),
      }),
      makeEnv(),
      {
        verify: fakeAuth("attacker-firebase-uid"),
        firebaseAdmin: admin,
      },
    );
    expect(res.status).toBe(200);
    expect(await admin.readStableIdForUid("attacker-firebase-uid")).toBe(
      "victim-uuid",
    );
    expect(await admin.readStableIdForUid("victim-uuid")).toBeNull();
  });

  it("invalid JWT → 401 INVALID_TOKEN", async () => {
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        headers: { Authorization: "Bearer INVALID" },
        body: JSON.stringify({ uid: "uid-1" }),
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1"), firebaseAdmin: admin },
    );
    expect(res.status).toBe(401);
  });

  it("missing Authorization → 401", async () => {
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        body: JSON.stringify({ uid: "uid-1" }),
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1"), firebaseAdmin: admin },
    );
    expect(res.status).toBe(401);
  });

  it("body without stableId → worker mints fresh UUID", async () => {
    // 2026-06-30: body is now optional; worker mints when client supplies no
    // stableId. This is the recovery path for clients that haven't generated
    // a UUID yet (or for tools / curl probes without F-4 state).
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        headers: { Authorization: "Bearer X" },
        body: JSON.stringify({}),
      }),
      makeEnv(),
      {
        verify: fakeAuth("uid-1"),
        firebaseAdmin: admin,
        newUuid: () => FIXED_UUID_FOR_TESTS,
      },
    );
    expect(res.status).toBe(200);
    const json = (await res.json()) as Record<string, unknown>;
    expect(json["stableId"]).toBe(FIXED_UUID_FOR_TESTS);
  });

  it("non-POST methods → 404", async () => {
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "GET",
        headers: { Authorization: "Bearer X" },
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1"), firebaseAdmin: admin },
    );
    expect(res.status).toBe(404);
  });
});
