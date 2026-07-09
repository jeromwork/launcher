// TASK-119 auth-worker (2026-07-09) — repurposed from identity-worker.
//
// Single endpoint: POST /auth/exchange
//
// Request:  Authorization: Bearer <Firebase ID token>
// Response: { token: <our JWT, HS256, sub=stableId>, stableId: string,
//             expiresAt: <epoch ms> }
//
// Pipeline (single transaction, no propagation waits):
//   1. Verify Firebase ID token via shared Firebase JWKS.
//   2. Read stableId from KV IDENTITY_MAP[firebase_uid].
//   3. If missing → generate UUID v4, put into KV.
//   4. Sign our own JWT with HS256(AUTH_JWT_SECRET), sub=stableId,
//      iss=launcher-auth-v1, iat=now, exp=now+1h.
//   5. Return {token, stableId, expiresAt}. Done — no async propagation,
//      client can use token immediately for backup-worker calls.
//
// backup-worker (and future workers) validate our JWT with the same shared
// secret. This decouples all downstream services from Firebase Auth custom
// claim propagation timing — a race that made the pre-2026-07-09 recovery
// probe flow unreliable (see TASK-119 for symptoms).
//
// Legacy /init-claim endpoint (Firebase setCustomAttributes-based) removed.
// FirebaseAdmin / RestFirebaseAdmin no longer referenced.

import { verifyFirebaseIdToken, type JwksCacheKv, type Claims } from "@familycare/auth-jwt";
import type { Env } from "./env.js";

export type AuthVerifier = (
  token: string,
  projectId: string,
  kv: JwksCacheKv,
) => Promise<{ ok: true; claims: Claims } | { ok: false; error: string }>;

const defaultVerifier: AuthVerifier = async (token, projectId, kv) => {
  const r = await verifyFirebaseIdToken(token, projectId, kv);
  if (r.ok) return { ok: true, claims: r.claims };
  return { ok: false, error: r.error };
};

export interface HandlerDeps {
  readonly verify?: AuthVerifier;
  readonly newUuid?: () => string;
  readonly now?: () => number;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return handle(request, env);
  },
};

/** JWT expiry — 1 hour. Client refreshes by calling /auth/exchange again. */
const JWT_TTL_MS = 60 * 60 * 1000;
const JWT_ISSUER = "launcher-auth-v1";

export async function handle(
  request: Request,
  env: Env,
  deps: HandlerDeps = {},
): Promise<Response> {
  const verify = deps.verify ?? defaultVerifier;
  const newUuid = deps.newUuid ?? generateUuidV4;
  const now = deps.now ?? Date.now;
  try {
    return await routeRequest(request, env, verify, newUuid, now);
  } catch (e) {
    const err = e as Error;
    console.error("Unhandled error", err.name, err.message, err.stack);
    return jsonResponse(500, { error: "INTERNAL" });
  }
}

async function routeRequest(
  request: Request,
  env: Env,
  verify: AuthVerifier,
  newUuid: () => string,
  now: () => number,
): Promise<Response> {
  const url = new URL(request.url);
  if (request.method !== "POST" || url.pathname !== "/auth/exchange") {
    return jsonResponse(404, { error: "NOT_FOUND" });
  }

  const header = request.headers.get("Authorization");
  if (!header || !header.startsWith("Bearer ")) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "missing" });
  }
  const firebaseToken = header.substring("Bearer ".length).trim();
  if (firebaseToken.length === 0) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "empty" });
  }
  const kvLike = jwksKvFromEnv(env);
  const verifyResult = await verify(firebaseToken, env.FIREBASE_PROJECT_ID, kvLike);
  if (!verifyResult.ok) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: verifyResult.error });
  }

  const firebaseUid = verifyResult.claims.uid;
  if (!firebaseUid || firebaseUid.length === 0) {
    return jsonResponse(401, { error: "INVALID_TOKEN", reason: "missing-uid" });
  }

  // Lookup-or-create stableId. KV eventually-consistent globally but a single
  // uid is written from one place, so read-your-writes holds within a Worker
  // isolate.
  const existing = await env.IDENTITY_MAP.get(firebaseUid);
  const stableId = existing ?? newUuid();
  if (existing === null) {
    await env.IDENTITY_MAP.put(firebaseUid, stableId);
  }

  const nowMs = now();
  const expiresAt = nowMs + JWT_TTL_MS;
  const ourJwt = await signJwtHS256(
    {
      sub: stableId,
      iss: JWT_ISSUER,
      iat: Math.floor(nowMs / 1000),
      exp: Math.floor(expiresAt / 1000),
    },
    env.AUTH_JWT_SECRET,
  );

  return jsonResponse(200, { token: ourJwt, stableId, expiresAt });
}

function jsonResponse(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

function jwksKvFromEnv(env: Env): JwksCacheKv {
  if (env.JWKS_CACHE) {
    return {
      get: (k) => env.JWKS_CACHE!.get(k),
      put: (k, v, opts) => env.JWKS_CACHE!.put(k, v, opts),
    };
  }
  const map = new Map<string, string>();
  return {
    async get(k: string) {
      return map.get(k) ?? null;
    },
    async put(k: string, v: string) {
      map.set(k, v);
    },
  };
}

/** RFC 4122 §4.4 UUID v4 via crypto.getRandomValues (available in Workers). */
function generateUuidV4(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6]! & 0x0f) | 0x40;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex: string[] = [];
  for (const b of bytes) hex.push(b.toString(16).padStart(2, "0"));
  return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10, 16).join("")}`;
}

/**
 * Minimal HS256 JWT signer using WebCrypto — no external library, keeps the
 * worker small. Payload MUST be JSON-serializable.
 */
export async function signJwtHS256(
  payload: Record<string, unknown>,
  secret: string,
): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const enc = new TextEncoder();
  const headerB64 = b64url(enc.encode(JSON.stringify(header)));
  const payloadB64 = b64url(enc.encode(JSON.stringify(payload)));
  const signingInput = `${headerB64}.${payloadB64}`;
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(signingInput));
  const sigB64 = b64url(new Uint8Array(sig));
  return `${signingInput}.${sigB64}`;
}

function b64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
