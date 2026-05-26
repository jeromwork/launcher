// Worker test suite — 9 cases per contracts/worker-notify.md §Tests (T068).
//
// Strategy: we stub `fetch` (used by auth.ts JWKS, fcm.ts OAuth + send,
// authorize.ts Firestore read) and the JWT-verify path via a fake JWKS.
// For end-to-end auth verification we still use real `jose` so a malformed
// or wrong-signature token actually fails verification.
//
// Service-account fixture uses a freshly-generated RSA key (test-only,
// committed publicly intentionally — has no real grants).

import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { SignJWT, generateKeyPair, exportPKCS8, exportJWK } from "jose";

import worker from "../src/index";
import { _resetServiceAccountCacheForTests } from "../src/env";
import { _resetJwksForTests, _setLocalJwksForTests } from "../src/auth";
import { _resetAccessTokenCacheForTests } from "../src/fcm";
import { _resetForTests as _resetRateLimitForTests } from "../src/rate-limit";

// ----- fixtures -------------------------------------------------------------

const PROJECT_ID = "test-project";
const LINK_ID = "link-abc";
const ADMIN_UID = "admin-uid-001";

interface TestKeys {
  privateKeyPem: string;
  jwk: any;
  kid: string;
}

let testKeys: TestKeys;

async function generateTestKeys(): Promise<TestKeys> {
  const { privateKey, publicKey } = await generateKeyPair("RS256", { extractable: true });
  const privateKeyPem = await exportPKCS8(privateKey);
  const jwk = await exportJWK(publicKey);
  jwk.alg = "RS256";
  jwk.use = "sig";
  jwk.kid = "test-kid";
  return { privateKeyPem, jwk, kid: "test-kid" };
}

async function mintIdToken(uid: string, opts: { issuer?: string; audience?: string } = {}): Promise<string> {
  // Sign a Firebase-like ID-token. In real Firebase the issuer is
  // "https://securetoken.google.com/{projectId}". For tests we mint with
  // our test private key and stub the JWKS endpoint to return its public key.
  const { privateKeyPem, kid } = testKeys;
  const { importPKCS8 } = await import("jose");
  const key = await importPKCS8(privateKeyPem, "RS256");
  return new SignJWT({})
    .setProtectedHeader({ alg: "RS256", typ: "JWT", kid })
    .setSubject(uid)
    .setIssuer(opts.issuer ?? `https://securetoken.google.com/${PROJECT_ID}`)
    .setAudience(opts.audience ?? PROJECT_ID)
    .setIssuedAt()
    .setExpirationTime("1h")
    .sign(key);
}

function makeEnv(): import("../src/env").Env {
  const sa = {
    client_email: "fake-sa@test-project.iam.gserviceaccount.com",
    private_key: testKeys.privateKeyPem,
    project_id: PROJECT_ID,
    token_uri: "https://oauth2.googleapis.com/token",
  };
  return {
    FIREBASE_PROJECT_ID: PROJECT_ID,
    FIREBASE_SA_JSON: JSON.stringify(sa),
    // Spec 011 — B2 fields. Worker tests for /notify don't hit B2, fake values OK.
    B2_ENDPOINT: "s3.test.backblazeb2.com",
    B2_BUCKET_NAME: "test-bucket",
    B2_KEY_ID: "test-key-id",
    B2_APPLICATION_KEY: "test-application-key",
  };
}

function mockUpstream(handlers: {
  oauthToken?: (req: Request) => Response;
  firestoreLink?: (req: Request) => Response;
  fcmSend?: (req: Request) => Response;
}): void {
  // JWKS is wired via _setLocalJwksForTests; we don't need to stub the
  // www.googleapis.com JWKS endpoint here.
  vi.stubGlobal("fetch", async (input: RequestInfo | URL, init?: RequestInit) => {
    const req = input instanceof Request ? input : new Request(input.toString(), init);
    const url = new URL(req.url);
    if (url.host === "oauth2.googleapis.com") {
      return handlers.oauthToken?.(req) ?? new Response(
        JSON.stringify({ access_token: "fake-access-token", expires_in: 3600, token_type: "Bearer" }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }
    if (url.host === "firestore.googleapis.com") {
      return handlers.firestoreLink?.(req) ?? new Response(
        JSON.stringify({ name: `projects/${PROJECT_ID}/databases/(default)/documents/links/${LINK_ID}`, fields: { adminId: { stringValue: ADMIN_UID } } }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }
    if (url.host === "fcm.googleapis.com") {
      return handlers.fcmSend?.(req) ?? new Response(
        JSON.stringify({ name: `projects/${PROJECT_ID}/messages/fake-msg-id` }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }
    throw new Error(`unexpected fetch in test: ${req.url}`);
  });
}

async function postNotify(token: string | null, body: unknown): Promise<Response> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token !== null) headers["Authorization"] = `Bearer ${token}`;
  const req = new Request("https://worker.example/notify", {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
  return worker.fetch(req, makeEnv());
}

// ----- setup ---------------------------------------------------------------

beforeEach(async () => {
  if (!testKeys) testKeys = await generateTestKeys();
  _resetServiceAccountCacheForTests();
  _resetJwksForTests();
  _setLocalJwksForTests({ keys: [testKeys.jwk] });
  _resetAccessTokenCacheForTests();
  _resetRateLimitForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

// ----- tests ---------------------------------------------------------------

describe("POST /notify", () => {

  test("happyPath_returns_200", async () => {
    mockUpstream({});
    const token = await mintIdToken(ADMIN_UID);
    const res = await postNotify(token, { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
    expect(res.status).toBe(200);
    const body = await res.json() as any;
    expect(body.ok).toBe(true);
    expect(body.messageName).toContain("messages/");
  });

  test("missing_token_returns_401", async () => {
    mockUpstream({});
    const res = await postNotify(null, { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
    expect(res.status).toBe(401);
  });

  test("invalid_token_returns_401", async () => {
    mockUpstream({});
    const res = await postNotify("not-a-valid-jwt", { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
    expect(res.status).toBe(401);
  });

  test("wrong_admin_returns_403", async () => {
    mockUpstream({
      firestoreLink: () => new Response(JSON.stringify({
        name: `projects/${PROJECT_ID}/databases/(default)/documents/links/${LINK_ID}`,
        fields: { adminId: { stringValue: "different-admin-uid" } },
      }), { status: 200, headers: { "Content-Type": "application/json" } }),
    });
    const token = await mintIdToken(ADMIN_UID);
    const res = await postNotify(token, { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
    expect(res.status).toBe(403);
  });

  test("link_not_found_returns_404", async () => {
    mockUpstream({
      firestoreLink: () => new Response("{}", { status: 404 }),
    });
    const token = await mintIdToken(ADMIN_UID);
    const res = await postNotify(token, { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
    expect(res.status).toBe(404);
  });

  test("fcm_5xx_returns_502", async () => {
    mockUpstream({
      fcmSend: () => new Response("server overload", { status: 503 }),
    });
    const token = await mintIdToken(ADMIN_UID);
    const res = await postNotify(token, { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
    expect(res.status).toBe(502);
  });

  test("unknown_type_returns_400", async () => {
    mockUpstream({});
    const token = await mintIdToken(ADMIN_UID);
    const res = await postNotify(token, { schemaVersion: 1, linkId: LINK_ID, type: "incoming-call-future-type" });
    expect(res.status).toBe(400);
  });

  test("malformed_body_returns_400", async () => {
    mockUpstream({});
    const token = await mintIdToken(ADMIN_UID);
    const req = new Request("https://worker.example/notify", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
      body: "not-json",
    });
    const res = await worker.fetch(req, makeEnv());
    expect(res.status).toBe(400);
  });

  test("schemaVersion_mismatch_returns_400", async () => {
    mockUpstream({});
    const token = await mintIdToken(ADMIN_UID);
    const res = await postNotify(token, { schemaVersion: 999, linkId: LINK_ID, type: "config-changed" });
    expect(res.status).toBe(400);
  });

  test("rate_limit_triggers_429", async () => {
    mockUpstream({});
    const token = await mintIdToken(ADMIN_UID);
    // Drive 100 successful requests on the same linkId to fill the bucket.
    for (let i = 0; i < 100; i++) {
      const ok = await postNotify(token, { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
      expect(ok.status).toBe(200);
    }
    // 101st should hit the cap.
    const blocked = await postNotify(token, { schemaVersion: 1, linkId: LINK_ID, type: "config-changed" });
    expect(blocked.status).toBe(429);
    expect(blocked.headers.get("Retry-After")).not.toBeNull();
  });

});
