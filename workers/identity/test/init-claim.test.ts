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

  it("claims.uid != body.uid → 403 UID_MISMATCH", async () => {
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        headers: { Authorization: "Bearer X" },
        body: JSON.stringify({ uid: "uid-victim" }),
      }),
      makeEnv(),
      {
        verify: fakeAuth("uid-attacker"),
        firebaseAdmin: admin,
      },
    );
    expect(res.status).toBe(403);
    const json = (await res.json()) as Record<string, unknown>;
    expect(json["error"]).toBe("UID_MISMATCH");
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

  it("missing uid in body → 400 MALFORMED_BODY", async () => {
    const admin = new InMemoryFirebaseAdmin();
    const res = await handle(
      new Request("https://example.com/init-claim", {
        method: "POST",
        headers: { Authorization: "Bearer X" },
        body: JSON.stringify({ wrong: "field" }),
      }),
      makeEnv(),
      { verify: fakeAuth("uid-1"), firebaseAdmin: admin },
    );
    expect(res.status).toBe(400);
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
